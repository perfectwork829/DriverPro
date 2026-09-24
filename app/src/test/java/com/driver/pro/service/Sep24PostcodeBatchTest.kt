package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 24 Sep 2026: WC2B drop as WwC2B+6TP, W11 1HE as Wll1HE, WIF without London. */
class Sep24PostcodeBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun extract_wwc2b_wll1he_wif_without_london() {
        assertTrue(extractOuterLondonPostcodes("Wil 2ER").contains("W11"))
        assertTrue(extractOuterLondonPostcodes("The Lincoln Suites. London. WwC2B\n6TP").contains("WC2B"))
        assertTrue(extractOuterLondonPostcodes("Ria's. London. Wll1HE").contains("W11"))
        assertTrue(extractOuterLondonPostcodes("Soho/Westminister Council. WIF 7HL").contains("W1F"))
        assertTrue(normalizeOcrOfferText("WwC2B").contains("WC2B"))
        assertTrue(normalizeOcrOfferText("Wll1HE").contains("W11"))
        assertTrue(normalizeOcrOfferText("WIF 7HL").contains("W1F"))
    }

    @Test
    fun id3857_lincoln_suites_wc2b_drop() {
        val text = """
            mith
            op
            2 Comfort
            £15.77
            * 4.70
            Trip Radar
            £l26 est. holiday entitlement included
            3 min (0.6 mi)
            180 Kensington Park Road. London.
            Wil 2ER
            d 38 mins (5.l mi)
            The Lincoln Suites. London. WwC2B
            6TP
            X
            Merton
            Match
            at
            Comr
        """.trimIndent()
        val ride = parse(text)
        assertEquals(15.77, ride.price, 0.05)
        assertEquals("W11", ride.pickup_address_postcode)
        assertEquals("WC2B", ride.dropoff_address_postcode)
        assertEquals(3, ride.pickup_time_minutes)
        assertEquals(38, ride.trip_time_minutes)
        assertEquals(0.6, ride.pickup_distance_value!!, 0.05)
        assertEquals(5.1, ride.trip_distance_value!!, 0.1)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id_comfort_rias_w11_pickup_w1f_drop() {
        val text = """
            it
            M
            op
            CAMPDEN
            Ke
            2 Comfort
            4.92
            Trip Radar
            £16.16
            6 min (1.0 mi)
            Hvde Park
            £1.35 est. holiday entitlement included
            Ria's. London. Wll1HE
            27 mins (3.7 mi)
            Match
            BejkeTe
            X
            Soho/Westminister Council. WIF 7HL
            Gre
        """.trimIndent()
        val ride = parse(text)
        assertEquals(16.16, ride.price, 0.05)
        assertEquals("W11", ride.pickup_address_postcode)
        assertEquals("W1F", ride.dropoff_address_postcode)
        assertEquals(6, ride.pickup_time_minutes)
        assertEquals(27, ride.trip_time_minutes)
        assertEquals(1.0, ride.pickup_distance_value!!, 0.05)
        assertEquals(3.7, ride.trip_distance_value!!, 0.1)
        assertNull(validateRideBeforeScoring(ride, text))
    }
}
