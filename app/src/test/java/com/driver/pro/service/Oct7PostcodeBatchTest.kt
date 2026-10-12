package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 7 Oct 2026: SWG/SW1l/WE/SVWI0 letter-as-digit postcodes on Match/Confirm cards. */
class Oct7PostcodeBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun normalize_letter_as_digit_postcodes() {
        assertTrue(normalizeOcrOfferText("SWG 7AH").contains("SW6 7AH"))
        assertTrue(normalizeOcrOfferText("SW1l 3J").contains("SW11"))
        assertTrue(normalizeOcrOfferText("London. WE 7LR").contains("W6 7LR"))
        assertTrue(normalizeOcrOfferText("SVWI0 OXG").contains("SW10"))
        assertTrue(extractOuterLondonPostcodes("30 Bishops Road. London. SWG 7AH").contains("SW6"))
        assertTrue(extractOuterLondonPostcodes("SW1l 3J").contains("SW11"))
        assertTrue(extractOuterLondonPostcodes("London. WE 7LR").contains("W6"))
        assertTrue(extractOuterLondonPostcodes("London. London. SVWI0 OXG").contains("SW10"))
    }

    @Test
    fun bishops_road_swg_pickup_sw18_drop() {
        val text = """
            205.
            PALCE
            RIVRSIDE
            2 Exec
            Exclusive
            £14.37
            4.62
            £1.40 est. holiday entitlement included
            l min (0.1 mi)
            X
            30 Bishops Road. London. SWG 7AH
            22 mins (2.4 mi)
            13 Enterprise Way. London. SW18 IFZ
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(14.37, ride.price, 0.05)
        assertEquals("SW6", ride.pickup_address_postcode)
        assertEquals("SW18", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun chatfield_sw1l_orphan_pickup_cr4_drop() {
        val text = """
            XANDRA
            Electric
            £14.97
            * 4.80
            Trip Radar 2
            £l.17 est. holiday entitlement included
            7 min (0.6 mi)
            SW1l 3J
            Chatfield medical Centre. London.
            X
            37 mins (6.9 mi)
            72 Rawnsley Avenue. Mitcham. CR4
            4BX
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(14.97, ride.price, 0.05)
        assertEquals("SW11", ride.pickup_address_postcode)
        assertEquals("CR4", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun orlando_we_drop_not_missing_w6() {
        val text = """
            k
            CEROAD
            2 Comfort
            £12.92
            4.87
            A304
            Verified
            7 min (0.8 mi)
            £1.20 est. holiday entitlement included
            X
            Stamford Bridge. London. SW6 lHS
            18 mins (2.5 mi)
            Hotel Orlando Bed & Breakfast.
            London. WE 7LR
            Match
            AFC Wimbledon
        """.trimIndent()
        val ride = parse(text)
        assertEquals(12.92, ride.price, 0.05)
        assertEquals("SW6", ride.pickup_address_postcode)
        assertEquals("W6", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun chelsea_harbour_svwi0_pickup_w1k_drop() {
        val text = """
            2 uberX Priority
            £15.41
            4.83
            Verified
            £l.27 est. holiday entitlement included
            +£1.60 included for priority
            9 min (1.2 mi)
            The Chelsea Harbour Hotel & Spa
            London. London. SVWI0 OXG
            27 mins (3.7 mi)
            X
            Grosvenor House A Jw Marriott Hotel.
            London. WIK 7TN
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(15.41, ride.price, 0.05)
        assertEquals("SW10", ride.pickup_address_postcode)
        assertEquals("W1K", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }
}
