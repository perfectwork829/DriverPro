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
    fun parse_live_overlay_keeps_three_digit_score() {
        val parts = parseLiveOfferOverlay("£78.60/h   109   £10.29/mi")!!
        assertEquals(109, parts.score)
    }

    @Test
    fun parse_live_overlay_keeps_negative_score() {
        val parts = parseLiveOfferOverlay("£59.56/h   -12   £5.01/mi")!!
        assertEquals(-12, parts.score)
        assertEquals("£59.56/h", parts.perHour)
        assertEquals("£5.01/mi", parts.perMile)
        val ride = ride(21.87, 7, 15, 1.0, 3.4)
        val roundTrip = parseLiveOfferOverlay(formatLiveOfferOverlay(ride, -20))!!
        assertEquals(-20, roundTrip.score)
    }

    @Test
    fun history_line_matches_overlay_rates() {
        val line = formatOfferEarningsLine(ride(6.43, 8, 13, 1.0, 1.9))
        assertEquals("£18.37/h  £2.22/mi", line)
    }

    @Test
    fun promote_truncated_10_to_110_on_excellent_short_hop() {
        assertEquals(110, promoteTruncatedRideScore(10, ride(14.83, 2, 7, 0.1, 0.9)))
        assertEquals(110, promoteTruncatedRideScore(10, ride(15.71, 2, 11, 0.1, 2.8)))
        assertEquals(109, promoteTruncatedRideScore(9, ride(14.41, 1, 10, 0.0, 1.4)))
    }

    @Test
    fun promote_keeps_real_three_digit_and_mid_scores() {
        assertEquals(110, promoteTruncatedRideScore(110, ride(29.80, 1, 26, 0.0, 8.0)))
        assertEquals(40, promoteTruncatedRideScore(40, ride(14.83, 2, 7, 0.1, 0.9)))
        assertEquals(10, promoteTruncatedRideScore(10, ride(4.73, 4, 5, 0.5, 0.9)))
    }

    @Test
    fun promote_truncated_50_to_150_on_long_high_fare_lux() {
        assertEquals(150, promoteTruncatedRideScore(50, ride(34.14, 8, 40, 2.3, 32.1)))
        assertEquals(350, promoteTruncatedRideScore(350, ride(34.14, 8, 40, 2.3, 32.1)))
    }

    @Test
    fun formula_score_changes_with_start_screen_cost() {
        val masons = ride(17.14, 1, 13, 0.1, 2.6)
        val atOnePence = computeFormulaScore(masons, 0.01)!!
        val atThirtyPence = computeFormulaScore(masons, 0.30)!!
        val atEightyPence = computeFormulaScore(masons, 0.80)!!
        val atFourPounds = computeFormulaScore(masons, 4.00)!!
        val atSixPounds = computeFormulaScore(masons, 6.00)!!
        assertEquals(122, atOnePence)
        assertEquals(117, atThirtyPence)
        assertEquals(107, atEightyPence)
        assertEquals(45, atFourPounds)
        assertEquals(7, atSixPounds)
        assertTrue(atOnePence > atSixPounds)
    }

    @Test
    fun formula_score_on_long_job_uses_mileage_cost_not_truncated_144() {
        val marsden = ride(25.11, 7, 52, 1.0, 13.2)
        val atFiftyPence = computeFormulaScore(marsden, 0.50)!!
        assertEquals(31, atFiftyPence)
        assertTrue(atFiftyPence < 70)
        assertEquals(42, computeFormulaScore(marsden, 0.01))
        assertEquals(-30, computeFormulaScore(marsden, 3.00))
    }

    @Test
    fun formula_score_goes_negative_when_mileage_cost_exceeds_fare() {
        val job = ride(21.87, 7, 15, 1.0, 3.4)
        val score = computeFormulaScore(job, 6.00)!!
        assertTrue(score < 0)
        val overlay = formatLiveOfferOverlay(job, score)
        val parts = parseLiveOfferOverlay(overlay)!!
        assertEquals(score, parts.score)
        assertTrue((parts.score ?: 0) < 0)
    }
}
