package com.driver.pro.service

import com.driver.pro.RideRequest
import kotlin.math.abs

/** Key fields that must agree across consecutive OCR reads before scoring. */
data class RideParseFingerprint(
    val price: Double,
    val pickupTime: Int,
    val tripTime: Int,
    val pickupPc: String,
    val dropPc: String,
)

fun rideParseFingerprint(ride: RideRequest): RideParseFingerprint? {
    val pickupTime = ride.pickup_time_minutes ?: return null
    val tripTime = ride.trip_time_minutes ?: return null
    if (ride.price < 1.0) return null
    // Postcodes optional for fingerprint — intermittent blank drops were resetting the gate forever.
    val pickupPc = ride.pickup_address_postcode.orEmpty().trim()
    val dropPc = ride.dropoff_address_postcode.orEmpty().trim()
    return RideParseFingerprint(
        price = ride.price,
        pickupTime = pickupTime,
        tripTime = tripTime,
        pickupPc = pickupPc,
        dropPc = dropPc,
    )
}

fun fingerprintsMatch(a: RideParseFingerprint, b: RideParseFingerprint): Boolean {
    if (abs(a.price - b.price) > 0.011) return false
    if (a.pickupTime != b.pickupTime || a.tripTime != b.tripTime) return false
    // Only compare postcodes when both reads have them (avoids flicker resetting the gate).
    if (a.pickupPc.isNotBlank() && b.pickupPc.isNotBlank() && a.pickupPc != b.pickupPc) return false
    if (a.dropPc.isNotBlank() && b.dropPc.isNotBlank() && a.dropPc != b.dropPc) return false
    return true
}

sealed class StabilityGateResult {
    data object Ready : StabilityGateResult()
    data class Waiting(val consecutive: Int, val required: Int) : StabilityGateResult()
    data object Incomplete : StabilityGateResult()
}

/**
 * Require [requiredMatches] consecutive parses with the same fingerprint before scoring.
 */
class OcrStabilityGate(private val requiredMatches: Int = 2) {
    private var lastFingerprint: RideParseFingerprint? = null
    private var consecutiveMatches = 0

    fun reset() {
        lastFingerprint = null
        consecutiveMatches = 0
    }

    fun isReady(): Boolean = consecutiveMatches >= requiredMatches

    fun needsMoreReads(): Boolean = consecutiveMatches in 1 until requiredMatches

    fun record(ride: RideRequest): StabilityGateResult {
        val fp = rideParseFingerprint(ride) ?: run {
            reset()
            return StabilityGateResult.Incomplete
        }
        if (lastFingerprint != null && !fingerprintsMatch(lastFingerprint!!, fp)) {
            consecutiveMatches = 1
            lastFingerprint = fp
            return StabilityGateResult.Waiting(1, requiredMatches)
        }
        consecutiveMatches++
        lastFingerprint = fp
        return if (consecutiveMatches >= requiredMatches) {
            StabilityGateResult.Ready
        } else {
            StabilityGateResult.Waiting(consecutiveMatches, requiredMatches)
        }
    }
}
