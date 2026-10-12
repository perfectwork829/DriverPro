package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 8 Oct 2026: jammed WIF7JT drop (W1F 7JT) on NorthWall Capital cards. */
class Oct8PostcodeBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun jammed_wif7jt_is_w1f() {
        assertTrue(normalizeOcrOfferText("WIF7JT").contains("W1F 7JT"))
        assertTrue(extractOuterLondonPostcodes("NorthWalI Capital. London. WIF7JT").contains("W1F"))
        assertTrue(extractOuterLondonPostcodes("Soho/Westminister Council. WIF 7HL").contains("W1F"))
    }

    @Test
    fun valentine_house_sw6_northwall_w1f() {
        val text = """
            A304
            Ę 2 Comfort Exclusive
            a 1-4 min
            £21.53
            4.64
            Verified
            1l min (1.2 mi)
            1 stop
            £l.87 est. holiday entitlement included
            Valentine House. London. SW6 2EH
            34 mins (4.7 mi)
            QUEEN STOV
            X
            TRINITY
            NorthWalI Capital. London. WIF7JT
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(21.53, ride.price, 0.05)
        assertEquals("SW6", ride.pickup_address_postcode)
        assertEquals("W1F", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun valentine_house_match_card_wif7jt_after_drop_leg() {
        val text = """
            2 Comfort
            £21.51
            4.64
            TCHMERE
            Verified
            £l.85 est. holiday entitlement included
            13 min (1.4 mi)
            l stop
            Valentine House. London. SW6 2EH
            X
            34 mins (4.7 mi)
            NorthWalI Capital. London. WIF7JT
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(21.51, ride.price, 0.05)
        assertEquals("SW6", ride.pickup_address_postcode)
        assertEquals("W1F", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun pelham_sw7_regents_nw1_not_la21_map() {
        assertTrue(lineLooksLikeBareMapDistrictLabel("LA214"))
        assertTrue(!extractOuterLondonPostcodes("LA214").contains("LA21"))
        assertTrue(extractOuterLondonPostcodes("NWI 4NS").contains("NW1"))
        val text = """
            BlNGD.O.N
            4
            w
            2 Comfort
            £17.62
            4.90
            6 min (0.8 mi)
            £l64 est. holiday entitlement included
            MLLBAN
            X
            The Pelham Hotel. London. SW7 2LA
            27 mins (3.6 mi)
            Match
            Regent's University London. London.
            NWI 4NS
            LA214
            A
            WE
        """.trimIndent()
        val ride = parse(text)
        assertEquals(17.62, ride.price, 0.05)
        assertEquals("SW7", ride.pickup_address_postcode)
        assertEquals("NW1", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun pelham_svw7_corndel_ec1iv() {
        assertTrue(normalizeOcrOfferText("SVW7 2").contains("SW7"))
        assertTrue(normalizeOcrOfferText("EC1iv 9AZ").contains("EC1V"))
        assertTrue(extractOuterLondonPostcodes("1-3 Pelham Street. London. SVW7 2").contains("SW7"))
        assertTrue(extractOuterLondonPostcodes("Corndel. London. EC1iv 9AZ").contains("EC1V"))
        val text = """
            2 Comfort
            £18.35
            4.76
            Verified
            £1.51 est. holiday entitlement included
            6 min (0.9 mi)
            Lambeth
            1-3 Pelham Street. London. SVW7 2
            44 mins (5.2 mi)
            Corndel. London. EC1iv 9AZ
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(18.35, ride.price, 0.05)
        assertEquals("SW7", ride.pickup_address_postcode)
        assertEquals("EC1V", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun science_museum_sw7_embankment_sw3_orphan() {
        val text = """
            A303
            NORLDsEND
            CF
            2 Electric
            £6.07
            4.89
            Exclusive
            Verified
            6 min (1.1 mi)
            £0.48 est. holiday entitlement included
            SW3 4LJ
            EHll-R-GH
            Science Museum. London. SW7 2DD
            12 mins (1.8 mi)
            6 Embankment Gardens. London.
            X
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(6.07, ride.price, 0.05)
        assertEquals("SW7", ride.pickup_address_postcode)
        assertEquals("SW3", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun prince_consort_fare_5_03_rating_5_00() {
        val text = """
            ROADWAY
            A3
            2 UberX
            £5.03
            5.00
            2 min (0.2 mi)
            wRLEND
            £0.38 est. holiday entitlement included
            2BB
            a2220
            Prince Consort Road. London. SW7
            12 mins (1.9 mi)
            X
            King's Rd. London. sw1O OEB
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(5.03, ride.price, 0.001)
        assertEquals(5.00, ride.rating, 0.001)
        assertEquals("SW7", ride.pickup_address_postcode)
        assertEquals("SW10", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }
}
