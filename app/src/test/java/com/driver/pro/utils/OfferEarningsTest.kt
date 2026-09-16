package com.driver.pro.utils

import com.driver.pro.RideRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferEarningsTest {

    private fun ride(
        price: Double,
        pickupMin: Int?,
        tripMin: Int?,
        pickupMi: Double?,
        tripMi: Double?,
    ) = RideRequest(
        id = 1,
        price = price,
        rating = 4.8,
        pickup_time_minutes = pickupMin,
        pickup_distance_value = pickupMi,
        pickup_address_postcode = "NW10",
        trip_time_minutes = tripMin,
        trip_distance_value = tripMi,
        dropoff_address_postcode = "W2",
        start_time_window = null,
        end_time_window = null,
        acceptedOrRejected = 0,
        final_score = 40,
    )

    @Test
    fun pounds_per_hour_uses_pickup_plus_trip_time() {
        // £15.79 over 7 + 47 = 54 min → 15.79 / 0.9 = 17.54
        val e = computeOfferEarnings(15.79, 7, 47, 1.9, 8.1)
        assertEquals(17.54, e.poundsPerHour!!, 0.02)
    }

    @Test
    fun pounds_per_mile_uses_pickup_plus_trip_miles() {
        // £15.79 / (1.9 + 8.1) = 1.579
        val e = computeOfferEarnings(15.79, 7, 47, 1.9, 8.1)
        assertEquals(1.58, e.poundsPerMile!!, 0.02)
    }

    @Test
    fun trip_only_when_pickup_metrics_missing() {
        val e = computeOfferEarnings(12.00, null, 20, null, 4.0)
        assertEquals(36.00, e.poundsPerHour!!, 0.02)
        assertEquals(3.00, e.poundsPerMile!!, 0.02)
    }

    @Test
    fun blank_when_fare_or_metrics_missing() {
        val noFare = computeOfferEarnings(0.0, 5, 10, 1.0, 2.0)
        assertNull(noFare.poundsPerHour)
        assertNull(noFare.poundsPerMile)
        val noTime = computeOfferEarnings(10.0, null, null, 1.0, 2.0)
        assertNull(noTime.poundsPerHour)
        assertEquals(10.0 / 3.0, noTime.poundsPerMile!!, 0.02)
    }

    @Test
    fun overlay_message_is_two_lines() {
        val msg = formatScoreOverlayMessage(42, ride(15.79, 7, 47, 1.9, 8.1))
        assertTrue(msg.startsWith("Score: 42"))
        assertTrue(msg.contains("£17.54/h"))
        assertTrue(msg.contains("£1.58/mi"))
        assertTrue('\n' in msg)
    }

    @Test
    fun overlay_keeps_suffix_after_rates() {
        val msg = formatScoreOverlayMessage(
            31,
            ride(8.16, 7, 20, 1.5, 3.8),
            suffix = "Accepted (finding button…)",
        )
        assertTrue(msg.contains("£/h") || msg.contains("/h"))
        assertTrue(msg.endsWith("Accepted (finding button…)"))
    }

    @Test
    fun history_line_matches_overlay_rates() {
        val line = formatOfferEarningsLine(ride(6.43, 8, 13, 1.0, 1.9))
        // 6.43 / 21 min * 60 = 18.37; 6.43 / 2.9 mi = 2.22
        assertEquals("£18.37/h  £2.22/mi", line)
    }
}
