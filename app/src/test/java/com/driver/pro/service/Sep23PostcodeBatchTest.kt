package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 23 Sep 2026: WN5→W5, NW41SE drop above street, NWn0, HAS 5BP, Meadowbank NW+8LH.
 */
class Sep23PostcodeBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun extract_wn5_on_london_is_w5_not_wigan() {
        assertTrue(extractOuterLondonPostcodes("London. WN5 5JY").contains("W5"))
        assertFalse(extractOuterLondonPostcodes("London. WN5 5JY").contains("WN5"))
        assertTrue(extractOuterLondonPostcodes("Tesco. London. NWn0 0TL").contains("NW10"))
        assertTrue(extractOuterLondonPostcodes("Pinner. HAS 5BP").contains("HA5"))
        assertTrue(extractOuterLondonPostcodes("NW41SE").contains("NW4"))
        assertTrue(extractOuterLondonPostcodes("NW27").contains("NW2"))
        assertTrue(normalizeOcrOfferText("HAS 5BP").contains("HA5"))
        assertTrue(normalizeOcrOfferText("Costco Wholesale. London. HAS OYJ").contains("HA9"))
        assertTrue(
            extractOuterLondonPostcodes("PureGym London Ealing Broadway.\nWN5 5JY").contains("W5"),
        )
        assertFalse(
            extractOuterLondonPostcodes("PureGym London Ealing Broadway.\nWN5 5JY").contains("WN5"),
        )
        assertFalse(
            "Map label E30 must not become E3",
            extractOuterLondonPostcodes("E30").contains("E3"),
        )
    }

    @Test
    fun id3606_ealing_wn5_is_w5_drop() {
        val text = """
            ALPEK
            2 Uberx
            Exclusive
            £8.29
            4.75
            £0.55 est. holiday entitlement included
            9 min (2.3 mi)
            X
            Bus Stop G (Point Place). Wembley.
            HA9 GDE
            18 mins (3.4 mi)
            PureGym London Ealing Broadway.
            London. WN5 5JY
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(8.29, ride.price, 0.05)
        assertEquals("HA9", ride.pickup_address_postcode)
        assertEquals("W5", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3586_nw41se_above_sunny_gardens_is_nw4_drop() {
        val text = """
            RT
            ry
            RDEMA
            2 uberX
            £758
            4.94
            Exclusive
            6 min (1.7 mi)
            £0.42 est. holiday entitlement included
            t 16 mins (5.0 mi)
            MAPES BURY
            Neasden Shoppping Centre. Brent.
            NW2 7
            NW41SE
            142 Sunny Gardens Road. London.
            X
            Confirm
            Hammesmith
        """.trimIndent()
        val ride = parse(text)
        assertEquals(7.58, ride.price, 0.05)
        assertEquals("NW2", ride.pickup_address_postcode)
        assertEquals("NW4", ride.dropoff_address_postcode)
        assertFalse(ocrLastDropAddressOmitsPostcode(text))
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3586_nw27_jammed_still_nw2_pickup_nw4_drop() {
        val text = """
            R
            AI
            F
            ur
            anic
            BREN
            2 Uberx
            £758
            4.94
            Exclusive
            6 min (1.7 mi)
            £0.42 est. holiday entitlement included
            t 16 mins (5.0 mi)
            FORTUNE
            GREEN
            Neasden Shoppping Centre. Brent.
            NW27
            NW41SE
            142 Sunny Gardens Road. London.
            X
            Confirm
            jte
        """.trimIndent()
        val ride = parse(text)
        assertEquals(7.58, ride.price, 0.05)
        assertEquals("NW2", ride.pickup_address_postcode)
        assertEquals("NW4", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3584_nwn0_otl_is_nw10_drop() {
        val text = """
            A
            BRENTLRARK/
            2 UberX Exclusive
            £5.40
            4.85
            £0.36 est. holiday entitlement included
            6 min (1.6 mi)
            Braemar Avenue. London. NW10 0DD
            10 mins (2.1 mi)
            X
            Tesco. London. NWn0 0TL
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(5.40, ride.price, 0.05)
        assertEquals("NW10", ride.pickup_address_postcode)
        assertEquals("NW10", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3200_has_5bp_is_ha5_drop_with_x() {
        val text = """
            MANDEVILLE
            2 Electric
            A40
            £10.64
            * 5.00
            £0.63 est. holiday entitlement included
            22 min (5.7 mi)
            X
            St Mark's Hospital. Harrow. HAl 30J
            12 mins (3.1 mi)
            94 Central Avenue. Pinner. HAS 5BP
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(10.64, ride.price, 0.05)
        assertEquals("HA1", ride.pickup_address_postcode)
        assertEquals("HA5", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun meadowbank_nw_8lh_is_nw9_pickup_hal_is_ha1_drop() {
        val text = """
            H
            UberX
            ★ 5.00
            Trip Radar
            Brant
            £8.95
            Verified
            11 min (2.7 mi)
            £0.56 est. holiday entitlement included
            30J
            t 17 mins (4.1 mi)
            BRENARk
            58 Meadowbank Road. London. NW
            8LH
            X
            Northwick Park Hospital. Harrow. HAL
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(8.95, ride.price, 0.05)
        assertEquals("NW9", ride.pickup_address_postcode)
        assertEquals("HA1", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3195_occluded_drop_stays_incomplete_not_invented() {
        val text = """
            OYAL
            ge
            m
            2 UberX Exclusive
            £10.53
            4.74
            12 min (2.9 mi)
            HARR-
            £0.69 est. holiday entitlement included
            X
            93 Monks Park. Wembley. HA9 6JW
            Confirm
            Connected for charging only. Tap the USB
            notification to use USB for transferring files.
            RL
            Queen's
        """.trimIndent()
        val ride = parse(text)
        assertEquals(10.53, ride.price, 0.05)
        assertEquals("HA9", ride.pickup_address_postcode)
        assertEquals("", ride.dropoff_address_postcode.orEmpty())
        val err = validateRideBeforeScoring(ride, text)
        assertTrue(err != null && err.contains("OCR incomplete"))
        assertTrue(err!!.contains("trip time"))
        assertTrue(err.contains("drop-off"))
        assertFalse(err.contains("pickup postcode"))
    }

    @Test
    fun kings_dr_still_omits_drop_when_only_pickup_wrap() {
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
        assertEquals("NW9", ride.pickup_address_postcode)
        assertEquals("", ride.dropoff_address_postcode.orEmpty())
        assertTrue(ocrLastDropAddressOmitsPostcode(text))
    }
}
