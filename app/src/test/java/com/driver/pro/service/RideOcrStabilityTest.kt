package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import com.driver.pro.network.validateRidePlausibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideOcrStabilityTest {

    @Test
    fun stabilityGate_requires_two_matching_reads() {
        val gate = OcrStabilityGate(requiredMatches = 2)
        val ride = sampleRide()
        assertTrue(gate.record(ride) is StabilityGateResult.Waiting)
        assertTrue(gate.record(ride) is StabilityGateResult.Ready)
    }

    @Test
    fun stabilityGate_resets_on_mismatch() {
        val gate = OcrStabilityGate(requiredMatches = 2)
        val ride = sampleRide()
        gate.record(ride)
        val other = ride.copy(dropoff_address_postcode = "HP2")
        assertTrue(gate.record(other) is StabilityGateResult.Waiting)
        assertTrue(gate.record(ride) is StabilityGateResult.Waiting)
    }

    @Test
    fun filterLinesExcludingMapMargins_drops_m25_at_screen_edge() {
        val lines = listOf(
            OcrLine("£7.79", 100, 200),
            OcrLine("7 min (2.7 mi)", 150, 200),
            OcrLine("Kings Langley, London, WD4 8LF", 180, 200),
            OcrLine("10 mins (3.1 mi)", 210, 200),
            OcrLine("M25", 220, 20),
            OcrLine("Confirm", 260, 200),
        )
        val filtered = filterLinesExcludingMapMargins(lines, frameWidth = 400)
        assertTrue(filtered.none { it.text == "M25" })
        assertTrue(filtered.any { it.text.contains("WD4") })
    }

    @Test
    fun validateRidePlausibility_rejects_rating_shaped_price() {
        val ride = sampleRide().copy(price = 4.93, rating = 4.93)
        assertNotNull(validateRidePlausibility(ride))
    }

    @Test
    fun validateRideBeforeScoring_passes_plausible_ride() {
        assertNull(validateRideBeforeScoring(sampleRide(), sampleOcr()))
    }

    private fun sampleRide() = RideRequest(
        id = 0,
        price = 4.68,
        rating = 4.93,
        pickup_time_minutes = 11,
        pickup_distance_value = 3.8,
        pickup_address_postcode = "HP2",
        trip_time_minutes = 8,
        trip_distance_value = 2.9,
        dropoff_address_postcode = "HP1",
        start_time_window = "",
        end_time_window = "",
        acceptedOrRejected = 0,
        type = "confirm",
        accuracy = 95,
    )

    private fun sampleOcr() = """
        £4.68
        4.93
        11 min (3.8 mi)
        Address, Hemel Hempstead, HP2 4AB
        8 mins (2.9 mi)
        Address, Hemel Hempstead, HP1 1AA
        Confirm
    """.trimIndent()
}
