package com.driver.pro.utils

import com.driver.pro.RideRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val e = computeOfferEarnings(15.79, 7, 47, 1.9, 8.1)
        assertEquals(17.54, e.poundsPerHour!!, 0.02)
    }

    @Test
    fun pounds_per_mile_uses_pickup_plus_trip_miles() {
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
    fun overlay_is_hour_score_mile_without_score_label() {
        val msg = formatLiveOfferOverlay(ride(15.79, 7, 47, 1.9, 8.1), 42)
        assertEquals("£17.54/h   42   £1.58/mi", msg)
        assertFalse(msg.contains("Score", ignoreCase = true))
    }

    @Test
    fun overlay_without_score_is_rates_only() {
        val msg = formatRatesOnlyOverlayMessage(ride(15.79, 7, 47, 1.9, 8.1))
        assertEquals("£17.54/h   £1.58/mi", msg)
        assertFalse(msg!!.contains("Score", ignoreCase = true))
        assertFalse(msg.contains("42"))
        val parts = parseLiveOfferOverlay(msg)!!
        assertEquals("£17.54/h", parts.perHour)
        assertNull(parts.score)
        assertEquals("£1.58/mi", parts.perMile)
    }

    @Test
    fun parse_live_overlay_reads_hour_score_mile() {
        val parts = parseLiveOfferOverlay("£27.86/h   50   £4.64/mi")!!
        assertEquals("£27.86/h", parts.perHour)
        assertEquals(50, parts.score)
        assertEquals("£4.64/mi", parts.perMile)
    }

    @Test
    fun history_line_matches_overlay_rates() {
        val line = formatOfferEarningsLine(ride(6.43, 8, 13, 1.0, 1.9))
        assertEquals("£18.37/h  £2.22/mi", line)
    }
}
