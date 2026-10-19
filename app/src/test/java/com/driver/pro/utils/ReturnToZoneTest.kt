package com.driver.pro.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnToZoneTest {

    @Test
    fun tfl_london_includes_inner_and_outer() {
        assertTrue(isTflLondonPostcode("SW1Y"))
        assertTrue(isTflLondonPostcode("SE1"))
        assertTrue(isTflLondonPostcode("TW13"))
        assertTrue(isTflLondonPostcode("NW10"))
        assertTrue(isTflLondonPostcode("KT1"))
        assertFalse(isTflLondonPostcode("SN2"))
        assertFalse(isTflLondonPostcode("BN11"))
        assertFalse(isTflLondonPostcode("RH6"))
        assertFalse(isTflLondonPostcode("RH10"))
        assertFalse(isTflLondonPostcode("OX4"))
    }

    @Test
    fun outward_and_area_from_full_or_district() {
        assertEquals("SW1Y", ukOutwardCode("SW1Y 6BU"))
        assertEquals("SW", ukPostcodeArea("SW1Y 6BU"))
        assertEquals("SN2", ukOutwardCode("SN2"))
        assertEquals("SN", ukPostcodeArea("SN2 1AA"))
    }

    @Test
    fun unknown_drop_has_no_return_until_sheet_row_exists() {
        val ret = resolveReturnToZone(
            pickupPostcode = "E1",
            dropoffPostcode = "ZZ9",
            tripMiles = 40.0,
            tripMinutes = 70,
            table = emptyMap(),
        )
        assertEquals(ReturnToZone.NONE, ret)
    }

    @Test
    fun rh6_has_no_return_until_sheet_row_exists() {
        val ret = resolveReturnToZone("TW6", "RH6", 39.3, 51)
        assertEquals(ReturnToZone.NONE, ret)
    }

    @Test
    fun sg6_return_is_zero_from_client_sheet() {
        val ret = resolveReturnToZone("TW6", "SG6", 48.8, 74)
        assertEquals(0.0, ret.miles, 0.001)
        assertEquals(0, ret.minutes)
    }

    @Test
    fun tn16_return_is_five_miles_and_five_minutes() {
        val ret = resolveReturnToZone("W11", "TN16", 23.9, 117)
        assertEquals(5.0, ret.miles, 0.001)
        assertEquals(5, ret.minutes)
        assertFalse(isTflLondonPostcode("TN16"))
        assertTrue(isTflLondonPostcode("W11"))
    }
}
