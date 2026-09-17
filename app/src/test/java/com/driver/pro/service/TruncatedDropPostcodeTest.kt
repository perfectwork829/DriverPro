package com.driver.pro.service

import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Incomplete inward codes on the Uber card (SW15 6, W14 8, …) must still yield the outward. */
class TruncatedDropPostcodeTest {

    private fun parse(text: String) =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun w14_8_truncated_inward() {
        val text = """
            UberX
            £13.02
            4.65
            9 min (1.2 mi)
            Holland Inn Hotel, London, W14 8HL
            27 mins (8.2 mi)
            67 Holland Road, London, W14 8
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals("W14", ride.pickup_address_postcode)
        assertEquals("W14", ride.dropoff_address_postcode)
    }

    @Test
    fun e17_8_jammed_like_sw156() {
        val text = """
            UberX
            £9.50
            4.80
            6 min (1.1 mi)
            10 High Street, London, E17 7AA
            18 mins (4.2 mi)
            2 Forest Road, London, E178
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals("E17", ride.pickup_address_postcode)
        assertEquals("E17", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun nw10_6_spaced_truncated() {
        val text = """
            UberX
            £8.16
            4.67
            7 min (1.5 mi)
            Costco Wholesale, London, HA9 0YJ
            20 mins (3.8 mi)
            London, NW10 6
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals("HA9", ride.pickup_address_postcode)
        assertEquals("NW10", ride.dropoff_address_postcode)
    }

    @Test
    fun mk10_7_jammed_like_sw156() {
        val text = """
            UberX
            £12.40
            4.80
            6 min (1.4 mi)
            1 High Street, London, NW10 1AA
            22 mins (5.1 mi)
            9 Station Road, Milton Keynes, MK107
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals("NW10", ride.pickup_address_postcode)
        assertEquals("MK10", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun w14_8_jammed_w148() {
        val text = """
            UberX
            £13.02
            4.65
            9 min (1.2 mi)
            Holland Inn Hotel, London, W14 8HL
            27 mins (8.2 mi)
            67 Holland Road, London, W148
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals("W14", ride.pickup_address_postcode)
        assertEquals("W14", ride.dropoff_address_postcode)
    }

    @Test
    fun jammed_sw156_is_truncated_inward_for_sw15() {
        assertTrue(lineHasTruncatedInwardStrict("5 Cedar Mews. London. SW156", "SW15"))
        assertTrue(lineHasTruncatedInwardStrict("London, SW15 6", "SW15"))
        assertFalse(lineHasTruncatedInwardStrict("London, NW10 1PP", "NW1"))
    }
}
