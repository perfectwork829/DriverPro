package com.driver.pro.network

import com.driver.pro.RideRequest
import com.driver.pro.service.isPlausibleMilesForMinutes
import com.driver.pro.service.ocrHasDropAddressAfterTripLeg
import com.driver.pro.service.ocrHasDropAddressStreetWithoutPostcode
import com.driver.pro.service.ocrHasPickupAddressWithoutPostcode
import com.google.gson.Gson

private val rideGson = Gson()

/**
 * Ride fields as a JSON object (for logging, tests, and Postman reference).
 */
fun rideRequestToJson(ride: RideRequest): String = rideGson.toJson(ride)

/**
 * HTTP body for `POST /api/ride-request/`.
 *
 * The idrivesmart backend parses the request body twice (`json.loads` on a JSON string that
 * contains the ride object). The outer body must therefore be a **JSON string**, not a raw
 * `{...}` object — otherwise the server raises:
 * "the JSON object must be str, bytes or bytearray, not dict".
 *
 * Postman: set body to **raw → Text** or raw JSON whose entire value is one quoted string, e.g.
 * `"{\"price\":9.75,\"rating\":4.86,\"type\":\"confirm\",...}"`
 * (or paste the output of this function for a real ride).
 */
fun rideRequestHttpBody(ride: RideRequest): String = rideGson.toJson(rideRequestToJson(ride))

/** Log ride object + exact HTTP body sent to POST /api/ride-request/. */
fun logRideRequestPayload(ride: RideRequest, httpBody: String) {
    logLongPayload("ride-request-payload", "rideJson=", rideRequestToJson(ride))
    logLongPayload("ride-request-payload", "httpBody=", httpBody)
}

/** Log a potentially long string across multiple logcat lines (Android ~4k limit). */
fun logLongPayload(tag: String, label: String, payload: String) {
    val maxChunk = 3500
    if (payload.length <= maxChunk) {
        android.util.Log.i(tag, "$label$payload")
        return
    }
    var offset = 0
    var part = 0
    while (offset < payload.length) {
        val end = minOf(offset + maxChunk, payload.length)
        android.util.Log.i(tag, "$label[part $part] ${payload.substring(offset, end)}")
        offset = end
        part++
    }
}

/**
 * Client-side checks before calling the scoring API.
 * Returns a user-visible message, or null if the payload is complete enough to send.
 */
fun validateRideForScoring(ride: RideRequest, ocrText: String? = null): String? {
    val pickupPc = ride.pickup_address_postcode.orEmpty().trim()
    val dropPc = ride.dropoff_address_postcode.orEmpty().trim()
    if (pickupPc.isNotBlank() && pickupPc == dropPc) {
        val sameTime = ride.pickup_time_minutes != null &&
            ride.trip_time_minutes != null &&
            ride.pickup_time_minutes == ride.trip_time_minutes
        val sameDist = ride.pickup_distance_value != null &&
            ride.trip_distance_value != null &&
            ride.pickup_distance_value == ride.trip_distance_value
        if (sameTime && sameDist) {
            return "OCR incomplete: pickup and drop look identical"
        }
        // Same outward with different trip metrics = valid local/same-district trip — allow scoring.
        // (Previously blocked when distinct()==1 even for real N17→N17 offers.)
    }

    val missing = mutableListOf<String>()
    if (ride.price < 1.0) {
        missing.add("fare (price)")
    }
    if (ride.rating <= 0.0) {
        missing.add("passenger rating")
    }
    if (ride.pickup_time_minutes == null) {
        missing.add("pickup time")
    }
    if (ride.trip_time_minutes == null) {
        missing.add("trip time")
    }
    if (ride.pickup_distance_value == null) {
        missing.add("pickup distance")
    }
    if (ride.trip_distance_value == null) {
        missing.add("trip distance")
    }
    if (ride.pickup_address_postcode.isNullOrBlank()) {
        // Uber Electric / some Match cards show street + "London" only — no pickup outward on screen.
        val uberOmitsPickupPc = !ocrText.isNullOrBlank() && ocrHasPickupAddressWithoutPostcode(ocrText)
        if (!uberOmitsPickupPc) {
            missing.add("pickup postcode")
        }
    }
    if (ride.dropoff_address_postcode.isNullOrBlank()) {
        // Street visible but postcode OCR'd as orphan/map noise (e.g. "Keats Cl. Enfield." + "4SF").
        val streetOnlyDrop = !ocrText.isNullOrBlank() && ocrHasDropAddressStreetWithoutPostcode(ocrText)
        if (!streetOnlyDrop) {
            missing.add("drop-off postcode")
        }
    }
    if (ride.type.isBlank()) {
        missing.add("offer type (Confirm/Match label not read)")
    }
    return when {
        missing.isEmpty() -> null
        missing.size == 1 -> "OCR incomplete: missing ${missing[0]}"
        else -> "OCR incomplete: missing ${missing.joinToString(", ")}"
    }
}

/** Impossible travel speed only — used to reject teleport-level OCR errors, not borderline noise. */
private fun isImpossibleSpeed(miles: Double, minutes: Int): Boolean {
    if (minutes <= 0) return false
    val mph = miles / (minutes / 60.0)
    return mph > 90.0
}

/**
 * Sanity checks on complete payloads — only rejects clearly impossible OCR, never plausible offers.
 *
 * Deliberately conservative: earlier heuristics ("price looks like rating", tight speed bounds)
 * blocked real low fares (£4.60) and normal trips. The backend does the real scoring; we only
 * stop teleport-level distance errors and price==rating duplication here.
 */
fun validateRidePlausibility(ride: RideRequest): String? {
    // Only when the price is *exactly* the rating (OCR duplicated the number) — not just "close".
    if (ride.price in 4.5..5.05 && kotlin.math.abs(ride.price - ride.rating) < 0.001) {
        return "OCR low confidence: price looks like passenger rating"
    }
    val pickupMin = ride.pickup_time_minutes
    val tripMin = ride.trip_time_minutes
    val pickupMi = ride.pickup_distance_value
    val tripMi = ride.trip_distance_value
    if (pickupMin != null && pickupMi != null && isImpossibleSpeed(pickupMi, pickupMin)) {
        return "OCR low confidence: pickup distance unlikely for time"
    }
    if (tripMin != null && tripMi != null && isImpossibleSpeed(tripMi, tripMin)) {
        return "OCR low confidence: trip distance unlikely for time"
    }
    return null
}

/** Full validation pipeline: completeness + plausibility. */
fun validateRideBeforeScoring(ride: RideRequest, ocrText: String? = null): String? {
    return validateRideForScoring(ride, ocrText) ?: validateRidePlausibility(ride)
}
