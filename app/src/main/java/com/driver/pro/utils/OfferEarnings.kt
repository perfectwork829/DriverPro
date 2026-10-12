package com.driver.pro.utils

import com.driver.pro.RideRequest
import java.util.Locale

/**
 * Live-offer earnings rates from the Uber card we already parse.
 *
 * £/h = fare / (pickup minutes + trip minutes) × 60
 * £/mi = fare / (pickup miles + trip miles)
 *
 * Missing pickup uses trip-only so a scored offer still shows a rate.
 */
data class OfferEarnings(
    val poundsPerHour: Double?,
    val poundsPerMile: Double?,
)

fun computeOfferEarnings(
    price: Double,
    pickupMinutes: Int?,
    tripMinutes: Int?,
    pickupMiles: Double?,
    tripMiles: Double?,
): OfferEarnings {
    if (price < 1.0) return OfferEarnings(null, null)

    val totalMinutes = (pickupMinutes ?: 0) + (tripMinutes ?: 0)
    val perHour = if (totalMinutes > 0) {
        val raw = price / (totalMinutes / 60.0)
        raw.takeIf { it.isFinite() && it in 0.5..500.0 }
    } else {
        null
    }

    val pickupMi = pickupMiles?.takeIf { it > 0.0 } ?: 0.0
    val tripMi = tripMiles?.takeIf { it > 0.0 } ?: 0.0
    val totalMiles = pickupMi + tripMi
    val perMile = if (totalMiles >= 0.05) {
        val raw = price / totalMiles
        raw.takeIf { it.isFinite() && it in 0.05..100.0 }
    } else {
        null
    }

    return OfferEarnings(perHour, perMile)
}

fun computeOfferEarnings(ride: RideRequest): OfferEarnings = computeOfferEarnings(
    price = ride.price,
    pickupMinutes = ride.pickup_time_minutes,
    tripMinutes = ride.trip_time_minutes,
    pickupMiles = ride.pickup_distance_value,
    tripMiles = ride.trip_distance_value,
)

fun formatPoundsRate(value: Double): String =
    "£${String.format(Locale.UK, "%.2f", value)}"

fun formatPerHourLabel(earnings: OfferEarnings): String? =
    earnings.poundsPerHour?.let { "${formatPoundsRate(it)}/h" }

fun formatPerMileLabel(earnings: OfferEarnings): String? =
    earnings.poundsPerMile?.let { "${formatPoundsRate(it)}/mi" }

/** Compact overlay/history line, e.g. `£24.50/h  £1.82/mi`. */
fun formatOfferEarningsLine(earnings: OfferEarnings): String? {
    val parts = mutableListOf<String>()
    formatPerHourLabel(earnings)?.let { parts.add(it) }
    formatPerMileLabel(earnings)?.let { parts.add(it) }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ")
}

fun formatOfferEarningsLine(ride: RideRequest): String? =
    formatOfferEarningsLine(computeOfferEarnings(ride))

/** App does not cap the product score; this is only a sanity bound for OCR/JSON leftovers. */
const val MAX_REPORTED_RIDE_SCORE = 999

/**
 * Server sometimes stores 110 as 10, 109 as 9, and 150 as 50 (hundreds digit dropped).
 * Restore it when the offer cannot plausibly be a sub-100 score.
 */
fun promoteTruncatedRideScore(score: Int?, ride: RideRequest): Int? {
    if (score == null) return null
    if (score in 100..MAX_REPORTED_RIDE_SCORE) return score
    if (score !in 1..99) return score
    val e = computeOfferEarnings(ride)
    val perHour = e.poundsPerHour ?: 0.0
    val perMile = e.poundsPerMile ?: 0.0
    val fare = ride.price
    val tripMi = ride.trip_distance_value ?: 0.0
    val tripMin = ride.trip_time_minutes ?: 0
    // Short high-rate hops: 9/10 instead of 109/110.
    if (score in 1..25 && perHour >= 45.0 && perMile >= 2.5) return 100 + score
    // Lux / long high-fare: 50 instead of 150 (RH10→BN11 £34 / 32 mi).
    if (score in 26..99 && fare >= 20.0 && (tripMi >= 12.0 || tripMin >= 25)) {
        return 100 + score
    }
    return score
}

/** Live banner: £/h left, score in the middle (no label), £/mi right. */
fun formatLiveOfferOverlay(ride: RideRequest, score: Int?): String {
    val earnings = computeOfferEarnings(ride)
    return listOfNotNull(
        formatPerHourLabel(earnings),
        score?.toString(),
        formatPerMileLabel(earnings),
    ).joinToString("   ")
}

/** @deprecated Use [formatLiveOfferOverlay]; kept so call sites compile during the rename. */
fun formatScoreOverlayMessage(
    score: Int,
    ride: RideRequest,
    suffix: String = "",
): String {
    val line = formatLiveOfferOverlay(ride, score)
    return if (suffix.isBlank()) line else "$line\n$suffix"
}

/** Rates-only banner when postcode/score fields are missing. */
fun formatRatesOnlyOverlayMessage(ride: RideRequest): String? {
    val line = formatLiveOfferOverlay(ride, score = null)
    return line.takeIf { it.isNotBlank() }
}

data class LiveOfferOverlayParts(
    val perHour: String?,
    val score: Int?,
    val perMile: String?,
)

/** Parse the live banner line produced by [formatLiveOfferOverlay]. */
fun parseLiveOfferOverlay(message: String): LiveOfferOverlayParts? {
    val t = message.trim()
    if (t.isEmpty()) return null
    val hour = Regex("""£[\d.]+/h""").find(t)?.value
    val mile = Regex("""£[\d.]+/mi""").find(t)?.value
    val withoutRates = t
        .replace(hour.orEmpty(), " ")
        .replace(mile.orEmpty(), " ")
        .trim()
    val score = Regex("""\b(\d{1,4})\b""").findAll(withoutRates)
        .mapNotNull { it.groupValues[1].toIntOrNull() }
        .filter { it in 0..MAX_REPORTED_RIDE_SCORE }
        .maxOrNull()
    if (hour == null && mile == null && score == null) return null
    return LiveOfferOverlayParts(hour, score, mile)
}
