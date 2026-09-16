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

/** Compact overlay/history line, e.g. `£24.50/h  £1.82/mi`. */
fun formatOfferEarningsLine(earnings: OfferEarnings): String? {
    val parts = mutableListOf<String>()
    earnings.poundsPerHour?.let { parts.add("${formatPoundsRate(it)}/h") }
    earnings.poundsPerMile?.let { parts.add("${formatPoundsRate(it)}/mi") }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ")
}

fun formatOfferEarningsLine(ride: RideRequest): String? =
    formatOfferEarningsLine(computeOfferEarnings(ride))

/**
 * Left-side live overlay. Second line is £/h and £/mi so the score stays readable
 * and does not sit over Match/Confirm.
 */
fun formatScoreOverlayMessage(
    score: Int,
    ride: RideRequest,
    suffix: String = "",
): String {
    val earnings = formatOfferEarningsLine(ride)
    val head = buildString {
        append("Score: $score")
        if (earnings != null) {
            append('\n')
            append(earnings)
        }
        if (suffix.isNotBlank()) {
            append(" — ")
            append(suffix)
        }
    }
    return head
}
