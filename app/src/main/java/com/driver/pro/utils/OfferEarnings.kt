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

/**
 * Live banner: £/h on the left, score in the middle (no "Score" label), £/mi on the right.
 * Omit [score] when the job was not sent for a server score.
 */
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
    val score = Regex("""^\d{1,3}$""").find(withoutRates)?.value?.toIntOrNull()
    if (hour == null && mile == null && score == null) return null
    return LiveOfferOverlayParts(hour, score, mile)
}
