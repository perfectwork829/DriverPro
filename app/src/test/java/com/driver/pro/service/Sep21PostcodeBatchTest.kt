package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 21 Sep 2026: SWIH0 drop, HA9/TW6 pickup-location swap, Kings Dr omit, WJ, NWI above card. */
class Sep21PostcodeBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun id3406_swih0_is_sw1h_drop() {
        val text = """
            ROYAL
            £15.79
            2 uberX Exclusive
            4.50
            HARROT
            Kensingtoń
            7 min (1.9 mi)
            £1.01 est. holiday entitlement included
            Dog Ln. London. NWIO 1PP
            47 mins (8.1 mi)
            22-28 Broadway. London. SWIH0
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(15.79, ride.price, 0.05)
        assertEquals("NW10", ride.pickup_address_postcode)
        assertEquals("SW1H", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3397_wembley_pickup_location_not_swapped_with_tw6() {
        val text = """
            NORWOOD
            2 UberX Exclusive
            £18.46
            4.94
            Ealing
            Verified
            1l min (2.4 mi)
            43 mins (15.3 mi)
            £0.94 est. holiday entitlement included
            ACTON
            Delta. Hounslow. TW6 2GW
            CENTRA-
            SSE Wembley Arena Pickup Location.
            Lakeside Way. HA9 OBU
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(18.46, ride.price, 0.05)
        assertEquals("HA9", ride.pickup_address_postcode)
        assertEquals("TW6", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3373_kings_dr_does_not_borrow_pickup_nw9() {
        val text = """
            2 uberX Priority
            £12.01
            4.60
            +£1.27 included for priority
            £0.75 est. holiday entitlement included
            18 min (5.7 mi)
            14 mins (3.4 mi)
            MAPESB
            105-8 Lanacre Avenue. London. NW9
            5AN
            Kings Dr. Wembley
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(12.01, ride.price, 0.05)
        assertEquals("NW9", ride.pickup_address_postcode)
        assertEquals("", ride.dropoff_address_postcode.orEmpty())
        val err = validateRideBeforeScoring(ride, text)
        assertNotNull(err)
        assertTrue(err!!.contains("drop-off postcode"))
    }

    @Test
    fun id3445_wj_5al_is_w1j_drop() {
        val text = """
            Hammersmith
            2 UberX
            £15.16
            4.91
            £l14 est. holiday entitlement included
            7 min (1.5 mi)
            O'Donoghue's. London. W12 8HJ
            27 mins (4.9 mi)
            40 Berkeley Square. London. WJ 5AL
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(15.16, ride.price, 0.05)
        assertEquals("W12", ride.pickup_address_postcode)
        assertEquals("W1J", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3439_nwi_above_card_is_nw1_euston_drop() {
        val text = """
            2 Electric
            Exclusive
            £13
            4.82
            NWI 2RT
            3 min (0.5 mi)
            £l13 est. holiday entitlement included
            Hyde Park
            56A Portland Rd. London. W1l 4LQ
            29 mins (4.0 mi)
            London Euston Station (EUS). London.
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(13.00, ride.price, 0.05)
        assertEquals("W11", ride.pickup_address_postcode)
        assertEquals("NW1", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun extract_swih0_and_wj_and_nwi() {
        assertTrue(extractOuterLondonPostcodes("22-28 Broadway. London. SWIH0").contains("SW1H"))
        assertTrue(extractOuterLondonPostcodes("40 Berkeley Square. London. WJ 5AL").contains("W1J"))
        assertTrue(extractOuterLondonPostcodes("NWI 2RT").contains("NW1"))
    }
}
