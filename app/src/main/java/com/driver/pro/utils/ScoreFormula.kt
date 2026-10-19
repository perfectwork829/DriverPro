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
    /** Hours 0..24. Index 24 is midnight (00:00). -1 means that hour is not on the sheet. */
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

private fun hours(vararg fromHour3: Int): IntArray {
    require(fromHour3.size == 22) { "X3..X24 is 22 values" }
    val out = IntArray(25) { UNKNOWN_HOUR_POINTS }
    for (i in fromHour3.indices) {
        out[i + 3] = fromHour3[i]
    }
    out[0] = out[24]
    return out
}

/** Known hour cells when the full X3–X24 row is not on the sheet yet. */
private fun sparseHours(vararg hourToPoints: Pair<Int, Int>): IntArray {
    val out = IntArray(25) { UNKNOWN_HOUR_POINTS }
    for ((hour, pts) in hourToPoints) {
        when (hour) {
            0, 24 -> {
                out[24] = pts
                out[0] = pts
            }
            in 1..23 -> out[hour] = pts
        }
    }
    return out
}

/**
 * Outward-code rows from the client sheet. SG6 is the 17 Oct screenshot
 * (X3..X24 then A=0 B=0). Other cells are hours / A / B the client confirmed
 * on 17–19 Oct (pickup subtracted, drop added).
 */
val POSTCODE_SCORE_TABLE: Map<String, PostcodeScoreRow> = mapOf(
    "SG6" to PostcodeScoreRow(
        hourPoints = hours(4, 5, 3, 4, 5, 6, 5, 4, 3, 3, 3, 3, 3, 5, 5, 5, 6, 6, 6, 6, 6, 3),
        returnMiles = 0.0,
        returnMinutes = 0,
    ),
    "TW6" to PostcodeScoreRow(
        hourPoints = sparseHours(20 to 6, 22 to 6),
        returnMiles = 0.0,
        returnMinutes = 0,
    ),
    "W6" to PostcodeScoreRow(
        hourPoints = sparseHours(21 to 7),
        returnMiles = 0.0,
        returnMinutes = 0,
    ),
    "W11" to PostcodeScoreRow(
        hourPoints = sparseHours(21 to 7),
        returnMiles = 0.0,
        returnMinutes = 0,
    ),
    "TW13" to PostcodeScoreRow(
        hourPoints = sparseHours(21 to 6),
        returnMiles = 0.0,
        returnMinutes = 0,
    ),
    "TN16" to PostcodeScoreRow(
        hourPoints = sparseHours(21 to 0),
        returnMiles = 5.0,
        returnMinutes = 5,
    ),
)

fun lookupPostcodeScoreRow(postcode: String?): PostcodeScoreRow? {
    val outward = ukOutwardCode(postcode) ?: return null
    POSTCODE_SCORE_TABLE[outward]?.let { return it }
    val area = ukPostcodeArea(outward) ?: return null
    return POSTCODE_SCORE_TABLE[area]
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

/**
 * Pickup points are subtracted, drop points are added. Each side is 0 when
 * that postcode/hour is not on the sheet — do not drop the other side.
 */
fun postcodeHourAdjustment(ride: RideRequest): Pair<Int, Int> {
    val hour = parseRideHour(ride.created_at) ?: return 0 to 0
    val pick = lookupPostcodeScoreRow(ride.pickup_address_postcode)?.pointsAtHour(hour) ?: 0
    val drop = lookupPostcodeScoreRow(ride.dropoff_address_postcode)?.pointsAtHour(hour) ?: 0
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
    if (pickPts != 0 || dropPts != 0) {
        b.append(" -").append(pickPts).append(" +").append(dropPts)
    }
    b.append(" = ").append(total)
    return b.toString()
}
