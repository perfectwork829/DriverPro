package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 29 Sep 2026: BA4-ROAD map, stacked W2/SE5, SwiV ILH drop. */
class Sep29PostcodeBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun extract_skips_bath_road_map_label() {
        assertFalse(extractOuterLondonPostcodes("-BA4-ROAD-").contains("BA4"))
        assertTrue(lineLooksLikeMapRoadChrome("-BA4-ROAD-"))
        assertTrue(extractOuterLondonPostcodes("Radisson Red Heathrow. Pick-up\npoint. UB7 0DU").contains("UB7"))
        assertTrue(extractOuterLondonPostcodes("London. SwiV ILH").contains("SW1V"))
        assertTrue(normalizeOcrOfferText("SwiV ILH").contains("SW1V"))
    }

    @Test
    fun id3983_radisson_heathrow_ub7_not_ba4() {
        val text = """
            5
            n-
            2 Electric Exclusive
            £10.60
            4.75
            7 min (3.2 mi)
            -BA4-ROAD-
            £0.72 est. holiday entitlement included
            Radisson Red Heathrow. Pick-up
            point. UB7 0DU
            10 mins (3.9 mi)
            X
            3 Gordon Road. West Drayton. UB7
            8AH
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(10.60, ride.price, 0.05)
        assertEquals("UB7", ride.pickup_address_postcode)
        assertEquals("UB7", ride.dropoff_address_postcode)
        assertFalse(ride.pickup_address_postcode == "BA4")
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id4087_inhabit_w2_pickup_burgess_park_se5_drop() {
        val text = """
            mith
            op'
            2 Electric Exclusive
            £20.94 9
            4.78
            Verified
            10 min (1.6 mi)
            £l.65 est. holiday entitlement included
            45 mins (6.8 mi)
            MGarder
            Burgess Park. SE5 OJD
            Ebury Square
            Inhabit. Queen's Gardens. London. W2
            3BA
            Confirm
            X
        """.trimIndent()
        val ride = parse(text)
        assertEquals(20.94, ride.price, 0.05)
        assertEquals("W2", ride.pickup_address_postcode)
        assertEquals("SE5", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun riu_victoria_sw1v_drop_from_swiv_ilh() {
        val text = """
            RED Trip Radar 2
            2 Comfort
            £16.17
            * 4.88
            Verified
            6 min (1.0 mi)
            £1.33 est. holiday entitlement included
            Dorian. London. Wll 2AT
            t 27 mins (4.1 mi)
            Hotel Riu Plaza London Victoria.
            London. SwiV ILH
            Ebury Square
            Gardens
            Match
            TRINITY /
            X
        """.trimIndent()
        val ride = parse(text)
        assertEquals(16.17, ride.price, 0.05)
        assertEquals("W11", ride.pickup_address_postcode)
        assertEquals("SW1V", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun hillingdon_hospital_london_only_pickup_does_not_score() {
        val text = """
            na
            2 UberX
            £8.26
            ★ 4.63
            Verified
            £0.48 est. holiday entitlement included
            4 min (1.3 mi)
            Hillingdon Hospital HB Bus Stop.
            London
            14 mins (5.6 mi)
            62 Amanda Ct. Slough. SL3 7TE
            X
            Match
            H
        """.trimIndent()
        val ride = parse(text)
        assertEquals(8.26, ride.price, 0.05)
        assertTrue(ride.pickup_address_postcode.isNullOrBlank())
        assertEquals("SL3", ride.dropoff_address_postcode)
        val err = validateRideBeforeScoring(ride, text)
        assertNotNull(err)
        assertTrue(err!!.contains("pickup postcode"))
    }
}
