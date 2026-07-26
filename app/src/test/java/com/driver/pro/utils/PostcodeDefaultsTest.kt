package com.driver.pro.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostcodeDefaultsTest {

    @Test
    fun defaultOutwardCodesForRegion_twoLetter() {
        val codes = defaultOutwardCodesForRegion("NN")
        assertEquals(40, codes.size)
        assertEquals("NN1", codes.first().full_code)
        assertEquals("NN", codes.first().area)
    }

    @Test
    fun isUkMapRegionId_rejectsFlaMapStyleIds() {
        assertTrue(isUkMapRegionId("AB"))
        assertFalse(isUkMapRegionId("st57"))
        assertFalse(isUkMapRegionId(""))
    }
}
