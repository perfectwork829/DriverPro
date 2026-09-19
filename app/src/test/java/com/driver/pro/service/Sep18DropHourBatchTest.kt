package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 18 Sep 2026: omitted drop PC, HAO lHX, and "1 hr (10.5 mi)". */
class Sep18DropHourBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun id3092_oxford_st_does_not_borrow_pickup_w8() {
        val text = """
            2 uberX
            £8.34
            * 5.00
            10 min (1.3 mi)
            £0.54 est. holiday entitlement included
            22 mins (2.9 mi)
            Green Par
            Currys PC World. London. W8 5ED
            Oxford St. London
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(8.34, ride.price, 0.05)
        assertEquals("W8", ride.pickup_address_postcode)
        assertEquals("", ride.dropoff_address_postcode.orEmpty())
        assertEquals(2.9, ride.trip_distance_value!!, 0.1)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3126_hao_lhx_is_ha0_1hx() {
        val text = """
            2 UberX
            £9.85
            4.55
            Verified
            £0.72 est. holiday entitlement included
            5 min (0.9 mi)
            Westwood Sports Pub & Kitchen.
            London. W12 7HB
            15 mins (4.9 mi)
            257 Water Road. Wembley. HAO lHX
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(9.85, ride.price, 0.05)
        assertEquals("W12", ride.pickup_address_postcode)
        assertEquals("HA0", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun confirm_27_84_lhr_is_60_min_and_10_5_mi() {
        val text = """
            Wandsworth
            2 uberX
            £27.84
            4.85
            Exclusive
            £2.23 est. holiday entitlement included
            4 min (0.7 mi)
            Dorsett Shepherd's Bush. London.
            W12 8QE
            lhr (10.5 mi)
            Dorsett Canary Wharf London.
            London. El4 9TP
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(27.84, ride.price, 0.05)
        assertEquals("W12", ride.pickup_address_postcode)
        assertEquals("E14", ride.dropoff_address_postcode)
        assertEquals(4, ride.pickup_time_minutes)
        assertEquals(60, ride.trip_time_minutes)
        assertEquals(10.5, ride.trip_distance_value!!, 0.15)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseTripLeg_hour_only_with_miles() {
        val parsed = parseTripLegFromLine("lhr (10.5 mi)")
        assertEquals(60, parsed?.minutes)
        assertEquals(10.5, parsed?.miles ?: 0.0, 0.001)
        val spaced = parseTripLegFromLine("1 hr (10.5 mi)")
        assertEquals(60, spaced?.minutes)
        assertEquals(10.5, spaced?.miles ?: 0.0, 0.001)
        val withMins = parseTripLegFromLine("1 hr 12 min (27.7 mi)")
        assertEquals(72, withMins?.minutes)
        assertEquals(27.7, withMins?.miles ?: 0.0, 0.001)
        assertEquals(7, parseTripLegFromLine("7 min (1.8 mi)")?.minutes)
        assertTrue(extractOuterLondonPostcodes("Queens Hill, Ascot, SL5").contains("SL5"))
        val mapNoise = extractOuterLondonPostcodes(
            "NI\nlin\n78 Birchen Grove. London. NW9 8SA",
        )
        assertTrue("Expected NW9, got $mapNoise", mapNoise.contains("NW9"))
        assertTrue("Map NI+lin must not become N1, got $mapNoise", "N1" !in mapNoise)
    }
}
