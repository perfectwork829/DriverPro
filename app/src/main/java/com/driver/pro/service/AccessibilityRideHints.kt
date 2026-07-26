package com.driver.pro.service

import com.driver.pro.RideRequest

/**
 * Parse Uber Driver offer fields from Accessibility node text (when exposed).
 * Used to fill gaps left by screenshot OCR — never overwrites a strong OCR value
 * except for clear fare recovery (£3.74 vs £37.41).
 */
data class AccessibilityRideHints(
    val price: Double? = null,
    val rating: Double? = null,
    val pickupMinutes: Int? = null,
    val pickupMiles: Double? = null,
    val tripMinutes: Int? = null,
    val tripMiles: Double? = null,
    val pickupPostcode: String? = null,
    val dropPostcode: String? = null,
    val offerType: String? = null,
)

/** Flatten node strings into one OCR-like blob for reuse of existing parsers. */
fun accessibilityNodesToText(nodes: List<String>): String {
    return nodes
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("\n")
}

fun extractAccessibilityRideHints(rawText: String): AccessibilityRideHints {
    if (rawText.isBlank()) return AccessibilityRideHints()
    val text = fixWrongLetterToNumber(rawText)
    val legs = extractTripLegPairsInOrder(text)
    val pcs = extractOuterLondonPostcodes(text)
    val price = extractBestPrice(text).takeIf { it >= 3.0 }
    val rating = pickBestPassengerRating(emptyList(), text)
    val type = when {
        Regex("""\bConfirm\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "confirm"
        Regex("""\bMatch\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "match"
        Regex("""\bAccept\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "confirm"
        else -> null
    }
    return AccessibilityRideHints(
        price = price,
        rating = rating,
        pickupMinutes = legs.getOrNull(0)?.first,
        pickupMiles = legs.getOrNull(0)?.second,
        tripMinutes = legs.getOrNull(1)?.first,
        tripMiles = legs.getOrNull(1)?.second,
        pickupPostcode = pcs.getOrNull(0),
        dropPostcode = pcs.getOrNull(1) ?: pcs.getOrNull(0)?.takeIf { pcs.size == 1 },
        offerType = type,
    )
}

/**
 * Merge Accessibility hints into an OCR-parsed ride.
 * Prefer OCR when present; fill blanks from a11y; recover truncated fares from a11y.
 */
fun mergeAccessibilityIntoRide(ocrRide: RideRequest, hints: AccessibilityRideHints): RideRequest {
    if (hints == AccessibilityRideHints()) return ocrRide

    var price = ocrRide.price
    if (hints.price != null) {
        when {
            price < 1.0 -> price = hints.price
            // £37.41 OCR'd as £3.74 — trust larger a11y £ fare on long trips.
            price in 3.0..4.99 && hints.price >= price * 5 && hints.price in 15.0..80.0 ->
                price = hints.price
        }
    }

    var rating = ocrRide.rating
    if (hints.rating != null && (rating < 4.0 || rating == 3.50) && hints.rating in 4.0..5.05) {
        rating = hints.rating
    }

    val pickupMin = ocrRide.pickup_time_minutes ?: hints.pickupMinutes
    val pickupMi = ocrRide.pickup_distance_value ?: hints.pickupMiles
    val tripMin = ocrRide.trip_time_minutes ?: hints.tripMinutes
    val tripMi = ocrRide.trip_distance_value ?: hints.tripMiles
    val pickupPc = ocrRide.pickup_address_postcode?.takeIf { it.isNotBlank() }
        ?: hints.pickupPostcode
    val dropPc = ocrRide.dropoff_address_postcode?.takeIf { it.isNotBlank() }
        ?: hints.dropPostcode
    val type = ocrRide.type.takeIf { it.isNotBlank() } ?: hints.offerType.orEmpty()

    return ocrRide.copy(
        price = price,
        rating = rating,
        pickup_time_minutes = pickupMin,
        pickup_distance_value = pickupMi,
        pickup_address_postcode = pickupPc,
        trip_time_minutes = tripMin,
        trip_distance_value = tripMi,
        dropoff_address_postcode = dropPc,
        type = type,
    )
}

/** Human-readable missing-field list for overlays (same rules as validateRideForScoring). */
fun listMissingRideFields(ride: RideRequest, ocrText: String? = null): List<String> {
    val missing = mutableListOf<String>()
    if (ride.price < 1.0) missing.add("fare")
    if (ride.rating <= 0.0) missing.add("rating")
    if (ride.pickup_time_minutes == null) missing.add("pickup time")
    if (ride.trip_time_minutes == null) missing.add("trip time")
    if (ride.pickup_distance_value == null) missing.add("pickup distance")
    if (ride.trip_distance_value == null) missing.add("trip distance")
    if (ride.pickup_address_postcode.isNullOrBlank()) {
        val omit = !ocrText.isNullOrBlank() && ocrHasPickupAddressWithoutPostcode(ocrText)
        if (!omit) missing.add("pickup postcode")
    }
    if (ride.dropoff_address_postcode.isNullOrBlank()) {
        val omit = !ocrText.isNullOrBlank() && ocrHasDropAddressStreetWithoutPostcode(ocrText)
        if (!omit) missing.add("drop-off postcode")
    }
    if (ride.type.isBlank()) missing.add("Confirm/Match")
    return missing
}

fun formatIncompleteOverlayMessage(reason: String, missing: List<String>): String {
    if (missing.isEmpty()) {
        return if (reason.startsWith("No score:", ignoreCase = true)) reason else "No score: $reason"
    }
    val fields = missing.joinToString(" · ")
    return "No score — check: $fields"
}
