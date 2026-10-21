package com.driver.pro.utils

import com.driver.pro.RideRequest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Client score (17 Oct 2026):
 *
 * 1. net £/h = [Y − (C+D+A)×cost] × 60 / (E+F+B+4)
 * 2. look up net £/h in the 0.50 band table (19.01–19.50 → 39)
 * 3. add rating points (4.51–5.00 → 10)
 * 4. subtract pickup postcode points for that hour, add drop postcode points
 *
 * A and B come from the drop postcode sheet row (London / missing = 0).
 */
internal const val UNKNOWN_HOUR_POINTS = -1

data class PostcodeScoreRow(
    /** Hours 0..24. Index 1 is X1 (01:00), index 24/0 is X24 (00:00). -1 = missing. */
    val hourPoints: IntArray,
    val returnMiles: Double = 0.0,
    val returnMinutes: Int = 0,
) {
    fun pointsAtHour(hour: Int): Int? {
        val slot = when (hour) {
            0, 24 -> 24
            in 1..23 -> hour
            else -> return null
        }
        val pts = hourPoints.getOrNull(slot) ?: return null
        return pts.takeIf { it != UNKNOWN_HOUR_POINTS }
    }
}

private fun kodyHourArray(x1ToX24: IntArray): IntArray {
    val out = IntArray(25) { UNKNOWN_HOUR_POINTS }
    for (i in x1ToX24.indices) {
        out[i + 1] = x1ToX24[i]
    }
    out[0] = out[24]
    return out
}

private val kodyScoreRows: Map<String, PostcodeScoreRow> by lazy {
    buildMap {
        for ((code, ref) in KodyTable.rows) {
            val pattern = KodyTable.patterns.getOrNull(ref.pattern) ?: continue
            put(
                code,
                PostcodeScoreRow(
                    hourPoints = kodyHourArray(pattern),
                    returnMiles = ref.returnMiles.coerceAtLeast(0.0),
                    returnMinutes = ref.returnMinutes.coerceAtLeast(0),
                ),
            )
        }
    }
}

/** KODY.xlsx outward-code rows (X1–X24, A miles, B minutes). */
val POSTCODE_SCORE_TABLE: Map<String, PostcodeScoreRow>
    get() = kodyScoreRows

/**
 * Extra hour points during major London events (Wembley, concerts, etc.).
 * Added on top of the KODY base table. Empty until an event overlay is set.
 * Keys are outward codes; values are X1–X24 extras (length 24).
 */
@Volatile
var kodyEventUplifts: Map<String, IntArray> = emptyMap()

fun eventHourUplift(postcode: String?, hour: Int): Int {
    val outward = ukOutwardCode(postcode) ?: return 0
    val extras = kodyEventUplifts[outward]
        ?: ukPostcodeArea(outward)?.let { kodyEventUplifts[it] }
        ?: return 0
    val idx = when (hour) {
        0, 24 -> 23
        in 1..23 -> hour - 1
        else -> return 0
    }
    return extras.getOrNull(idx)?.coerceAtLeast(0) ?: 0
}

fun lookupPostcodeScoreRow(postcode: String?): PostcodeScoreRow? {
    val outward = ukOutwardCode(postcode) ?: return null
    kodyScoreRows[outward]?.let { return it }
    val area = ukPostcodeArea(outward) ?: return null
    return kodyScoreRows[area]
}

/** 19.01–19.50 → 39, 19.51–20.00 → 40. Same as ceil(£/h / 0.50). */
fun poundsPerHourToPoints(poundsPerHour: Double): Int {
    if (!poundsPerHour.isFinite() || poundsPerHour == 0.0) return 0
    val pts = ceil(abs(poundsPerHour) / 0.50 - 1e-12).toInt()
    return if (poundsPerHour < 0.0) -pts else pts
}

/** 4.51–5.00 → 10, 4.01–4.50 → 9, …, 1.00–1.50 → 3. */
fun ratingToPoints(rating: Double): Int = when {
    rating >= 4.51 -> 10
    rating >= 4.01 -> 9
    rating >= 3.51 -> 8
    rating >= 3.01 -> 7
    rating >= 2.51 -> 6
    rating >= 2.01 -> 5
    rating >= 1.51 -> 4
    rating >= 1.00 -> 3
    else -> 0
}

fun parseRideHour(createdAt: String?): Int? {
    val t = createdAt?.trim().orEmpty()
    if (t.isEmpty()) return null
    val normalized = t.replace('T', ' ').replace('/', '-')
    val hourToken = Regex("""\b(\d{1,2}):\d{2}""").find(normalized)?.groupValues?.get(1)
    return hourToken?.toIntOrNull()?.takeIf { it in 0..23 }
}

/** Recompute from stored fare, miles, times, cost, rating, postcodes and created hour. */
fun displayedFormulaScore(ride: RideRequest): Int? {
    val cost = ride.cost_per_mile ?: ride.price_per_mile ?: return null
    return computeClientFormulaScore(ride, cost)
}

fun netPoundsPerHour(ride: RideRequest, costPerMile: Double): Double? {
    val fare = ride.price
    val pickupMin = ride.pickup_time_minutes
    val tripMin = ride.trip_time_minutes
    if (fare < 1.0 || pickupMin == null || tripMin == null) return null
    val ret = resolveReturnToZone(ride)
    val minutes = pickupMin + tripMin + ret.minutes.coerceAtLeast(0) + FORMULA_BUFFER_MINUTES
    if (minutes <= 0) return null
    val miles = (ride.pickup_distance_value ?: 0.0).coerceAtLeast(0.0) +
        (ride.trip_distance_value ?: 0.0).coerceAtLeast(0.0) +
        ret.miles.coerceAtLeast(0.0)
    val net = fare - miles * costPerMile.coerceAtLeast(0.0)
    val pph = net * 60.0 / minutes
    return pph.takeIf { it.isFinite() }
}

/** Clock hour for pickup/drop points: local created time, then offer window. */
fun rideScoreHour(ride: RideRequest): Int? =
    parseRideHour(ride.created_at) ?: parseRideHour(ride.start_time_window)

/**
 * Pickup points are subtracted, drop points are added. Each side is 0 when
 * that postcode/hour is not on the sheet — do not drop the other side.
 */
fun postcodeHourAdjustment(ride: RideRequest): Pair<Int, Int> {
    val hour = rideScoreHour(ride) ?: return 0 to 0
    val pick = (lookupPostcodeScoreRow(ride.pickup_address_postcode)?.pointsAtHour(hour) ?: 0) +
        eventHourUplift(ride.pickup_address_postcode, hour)
    val drop = (lookupPostcodeScoreRow(ride.dropoff_address_postcode)?.pointsAtHour(hour) ?: 0) +
        eventHourUplift(ride.dropoff_address_postcode, hour)
    return pick to drop
}

fun computeClientFormulaScore(ride: RideRequest, costPerMile: Double): Int? {
    val pph = netPoundsPerHour(ride, costPerMile) ?: return null
    val hourPts = poundsPerHourToPoints(pph)
    val ratePts = ratingToPoints(ride.rating)
    val (pickPts, dropPts) = postcodeHourAdjustment(ride)
    return hourPts + ratePts - pickPts + dropPts
}

fun formatFormulaBreakdownParts(ride: RideRequest, costPerMile: Double): String? {
    val pph = netPoundsPerHour(ride, costPerMile) ?: return null
    val hourPts = poundsPerHourToPoints(pph)
    val ratePts = ratingToPoints(ride.rating)
    val (pickPts, dropPts) = postcodeHourAdjustment(ride)
    val total = hourPts + ratePts - pickPts + dropPts
    val costBit = "£${String.format(Locale.US, "%.2f", costPerMile)}/mi"
    val pphBit = String.format(Locale.US, "%.2f/h", pph)
    val b = StringBuilder()
    b.append(costBit).append("  ").append(pphBit)
    b.append("  ").append(hourPts).append('+').append(ratePts)
    if (rideScoreHour(ride) != null || pickPts != 0 || dropPts != 0) {
        b.append(" -").append(pickPts).append(" +").append(dropPts)
    }
    b.append(" = ").append(total)
    return b.toString()
}
