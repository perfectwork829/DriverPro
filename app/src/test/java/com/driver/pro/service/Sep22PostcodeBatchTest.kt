package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 22 Sep 2026: £9.24 Confirm W11 3BU → SW1W 0EN (ID-3465).
 * OCR dump uses WIl / SWIW OEN; previously scored as SW1W (ID-2802).
 */
class Sep22PostcodeBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    private val grosvenorDump = """
        Bush
        itt 2 Electric
        op
        Kensington
        £9.24
        4.57
        Exclusive
        £0.65 est. holiday entitlement included
        4 min (0.6 mi)
        Southbank International School.
        London. WIl 3BU
        d 23 mins (3.4 mi)
        6 Lower Grosvenor Place. London.
        SWIW OEN
        X
        Confirm
        Ga
        N
    """.trimIndent()

    @Test
    fun extract_swiw_oen_is_sw1w() {
        assertTrue(
            "SWIW OEN should be SW1W 0EN",
            extractOuterLondonPostcodes("SWIW OEN").contains("SW1W"),
        )
        assertTrue(
            extractOuterLondonPostcodes("6 Lower Grosvenor Place. London.\nSWIW OEN")
                .contains("SW1W"),
        )
        assertTrue(
            extractOuterLondonPostcodes("6 Lower Grosvenor Place. London. SWIW OEN")
                .contains("SW1W"),
        )
        assertTrue(normalizeOcrOfferText("SWIW OEN").contains("SW1W"))
    }

    @Test
    fun swiw_oen_line_is_not_treated_as_omitted_drop() {
        assertFalse(ocrLastDropAddressOmitsPostcode(grosvenorDump))
    }

    @Test
    fun id3465_grosvenor_swiw_oen_is_sw1w_drop() {
        val ride = parse(grosvenorDump)
        assertEquals(9.24, ride.price, 0.05)
        assertEquals("W11", ride.pickup_address_postcode)
        assertEquals("SW1W", ride.dropoff_address_postcode)
        assertEquals(0.6, ride.pickup_distance_value!!, 0.05)
        assertEquals(3.4, ride.trip_distance_value!!, 0.05)
        assertEquals(4, ride.pickup_time_minutes)
        assertEquals(23, ride.trip_time_minutes)
        assertNull(validateRideBeforeScoring(ride, grosvenorDump))
    }
}
