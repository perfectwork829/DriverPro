package com.driver.pro.utils

import com.driver.pro.RideRequest

/**
 * Return-to-zone (A miles, B minutes) for a London-start job that drops outside TfL.
 *
 * Formula uses [Y − (C + D + A)×cost] × 100 / (E + F + B).
 * Intra-London drops stay A=0, B=0 so SW1→SE1 scores do not change.
 * Pickup outside TfL London is not a London job — no return is added.
 *
 * Table is keyed by outward code (SN2) then area (SN). Missing rows fall back
 * to the outbound trip (A=D, B=F). Replace [DEFAULT_RETURN_TO_ZONE] when the
 * admin spreadsheet is imported.
 */
data class ReturnToZone(
    val miles: Double,
    val minutes: Int,
) {
    companion object {
        val NONE = ReturnToZone(0.0, 0)
    }
}

/** Greater London / TfL operating area (inner + outer). Not surrounding towns. */
val TFL_LONDON_POSTCODE_AREAS: Set<String> = setOf(
    "E", "EC", "N", "NW", "SE", "SW", "W", "WC",
    "BR", "CR", "DA", "EN", "HA", "IG", "KT", "RM", "SM", "TW", "UB",
)

fun ukPostcodeArea(raw: String?): String? {
    val token = ukOutwardCode(raw) ?: raw?.trim()?.uppercase()?.takeIf { it.matches(Regex("^[A-Z]{1,2}$")) }
        ?: return null
    return Regex("^([A-Z]{1,2})").find(token)?.groupValues?.get(1)
}

fun ukOutwardCode(raw: String?): String? {
    val token = raw?.trim()?.uppercase()?.split(Regex("\\s+"))?.firstOrNull() ?: return null
    return Regex("^[A-Z]{1,2}\\d{1,2}[A-Z]?").find(token)?.value
}

fun isTflLondonPostcode(raw: String?): Boolean {
    val area = ukPostcodeArea(raw) ?: return false
    return area in TFL_LONDON_POSTCODE_AREAS
}

fun resolveReturnToZone(
    pickupPostcode: String?,
    dropoffPostcode: String?,
    tripMiles: Double?,
    tripMinutes: Int?,
    table: Map<String, ReturnToZone> = DEFAULT_RETURN_TO_ZONE,
): ReturnToZone {
    if (!isTflLondonPostcode(pickupPostcode)) return ReturnToZone.NONE
    if (dropoffPostcode.isNullOrBlank()) return ReturnToZone.NONE
    if (isTflLondonPostcode(dropoffPostcode)) return ReturnToZone.NONE
    val outward = ukOutwardCode(dropoffPostcode)
    val area = ukPostcodeArea(dropoffPostcode)
    val fromTable = outward?.let { table[it] } ?: area?.let { table[it] }
    if (fromTable != null) return fromTable
    return ReturnToZone(
        miles = (tripMiles ?: 0.0).coerceAtLeast(0.0),
        minutes = (tripMinutes ?: 0).coerceAtLeast(0),
    )
}

fun resolveReturnToZone(ride: RideRequest): ReturnToZone = resolveReturnToZone(
    pickupPostcode = ride.pickup_address_postcode,
    dropoffPostcode = ride.dropoff_address_postcode,
    tripMiles = ride.trip_distance_value,
    tripMinutes = ride.trip_time_minutes,
)

/** Driving return to the London zone. Area-level; SN2 is the documented example. */
val DEFAULT_RETURN_TO_ZONE: Map<String, ReturnToZone> = mapOf(
    "SN2" to ReturnToZone(82.0, 110),
    "SN" to ReturnToZone(80.0, 105),
    "BN" to ReturnToZone(55.0, 90),
    "RH" to ReturnToZone(30.0, 55),
    "GU" to ReturnToZone(30.0, 55),
    "RG" to ReturnToZone(40.0, 60),
    "OX" to ReturnToZone(58.0, 90),
    "CB" to ReturnToZone(60.0, 80),
    "SO" to ReturnToZone(80.0, 100),
    "PO" to ReturnToZone(75.0, 100),
    "MK" to ReturnToZone(50.0, 70),
    "LU" to ReturnToZone(34.0, 55),
    "SS" to ReturnToZone(40.0, 70),
    "ME" to ReturnToZone(35.0, 55),
    "AL" to ReturnToZone(25.0, 45),
    "HP" to ReturnToZone(30.0, 50),
    "SG" to ReturnToZone(35.0, 50),
    "CO" to ReturnToZone(65.0, 90),
    "SL" to ReturnToZone(22.0, 40),
    "WD" to ReturnToZone(18.0, 40),
    "CM" to ReturnToZone(35.0, 55),
    "CT" to ReturnToZone(62.0, 85),
    "TN" to ReturnToZone(40.0, 70),
    "BH" to ReturnToZone(100.0, 130),
    "BA" to ReturnToZone(115.0, 140),
    "BS" to ReturnToZone(120.0, 150),
    "CV" to ReturnToZone(95.0, 120),
    "B" to ReturnToZone(120.0, 140),
    "LE" to ReturnToZone(100.0, 120),
    "NG" to ReturnToZone(130.0, 150),
    "PE" to ReturnToZone(85.0, 110),
    "IP" to ReturnToZone(80.0, 110),
    "NR" to ReturnToZone(115.0, 140),
    "GL" to ReturnToZone(100.0, 130),
    "CF" to ReturnToZone(150.0, 180),
    "EX" to ReturnToZone(170.0, 200),
    "PL" to ReturnToZone(195.0, 230),
    "M" to ReturnToZone(200.0, 240),
    "LS" to ReturnToZone(195.0, 230),
)
