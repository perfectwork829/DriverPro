package com.driver.pro.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PricePerMileFormatTest {

    @Test
    fun apiDecimal_to_digit_boxes() {
        assertEquals("050", formatApiPriceToDigitInput("0.50"))
        assertEquals("050", formatApiPriceToDigitInput("0.5"))
        assertEquals("050", formatApiPriceToDigitInput("0.500"))
    }

    @Test
    fun digit_boxes_to_api_pounds() {
        assertEquals(0.5f, digitInputToApiPrice("050"), 0.001f)
        assertEquals(0.55, digitInputToApiPounds("055"), 0.001)
        assertEquals(5.55, digitInputToApiPounds("555"), 0.001)
    }

    @Test
    fun request_decimal_uses_dot_even_if_default_locale_uses_comma() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("0.55", formatApiPriceForRequest(0.55))
            assertEquals("5.55", formatApiPriceForRequest(5.55))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
