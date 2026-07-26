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
    }
}
