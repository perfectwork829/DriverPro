package com.driver.pro.utils

import com.driver.pro.network.PricePerMileResponse
import com.driver.pro.network.rowUsedForScoring
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

    @Test
    fun scoring_uses_oldest_server_row_not_a_newer_copy() {
        val rows = listOf(
            PricePerMileResponse(id = 10, user = 1, price_per_mile = 5.55),
            PricePerMileResponse(id = 2, user = 1, price_per_mile = 0.50),
        )
        assertEquals(2, rows.rowUsedForScoring()!!.id)
        assertEquals(0.50, rows.rowUsedForScoring()!!.price_per_mile, 0.001)
    }

    @Test
    fun launch_uses_api_cost_when_local_is_still_default_50p() {
        assertEquals(0.10, operatingCostForSession(0.50, 0.10), 0.001)
    }

    @Test
    fun launch_keeps_local_cost_when_api_is_still_stale_50p() {
        assertEquals(0.10, operatingCostForSession(0.10, 0.50), 0.001)
    }

    @Test
    fun launch_follows_api_when_both_are_custom() {
        assertEquals(0.20, operatingCostForSession(0.35, 0.20), 0.001)
    }
}
