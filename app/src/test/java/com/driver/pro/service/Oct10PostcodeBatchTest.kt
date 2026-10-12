package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 10 Oct 2026: truncated between-legs drop "KT23 3" on Compass House → Leatherhead. */
class Oct10PostcodeBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun kt23_3_is_truncated_orphan_drop_line() {
        assertTrue(isTruncatedInwardPostcodeOnlyLine("KT23 3"))
        assertTrue(isOrphanDropPostcodeLine("KT23 3"))
        assertFalse(isFullPostcodeOnlyLine("KT23 3"))
        assertTrue(extractOuterLondonPostcodes("KT23 3").contains("KT23"))
    }

    @Test
    fun compass_house_sw18_leatherhead_kt23() {
        val text = """
            T PUTNEY
            1-7 min
            2 Electric
            4.57
            £25.66
            AD
            Verified
            3 min (0.9 mi)
            £l.04 est. holiday entitlement included
            KT23 3
            hdsworth NORTH
            X
            217 Compass House. London. SW18 1
            t 39 mins (19.6 mi)
            42 Middlemead Road. Leatherhead.
            Match
        """.trimIndent()
        assertFalse(ocrLastDropAddressOmitsPostcode(text))
        val ride = parse(text)
        assertEquals(25.66, ride.price, 0.05)
        assertEquals(3, ride.pickup_time_minutes)
        assertEquals(0.9, ride.pickup_distance_value!!, 0.05)
        assertEquals(39, ride.trip_time_minutes)
        assertEquals(19.6, ride.trip_distance_value!!, 0.05)
        assertEquals("SW18", ride.pickup_address_postcode)
        assertEquals("KT23", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }
}
