package com.driver.pro.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Client field reports 2026-07-21. */
class RideOcrClientBatchJuly21Test {

    @Test
    fun rating_4_71_upgrades_from_truncated_4_7() {
        // OCR often drops the last digit; full form may still appear nearby.
        assertEquals(4.71, upgradeTruncatedRating(4.7, "★ 4.7\n4.71"), 0.001)
        assertEquals(4.71, upgradeTruncatedRating(4.70, "fare\n★ 4.71"), 0.001)

        val text = """
            UberX
            £8.78
            ★ 4.7
            4.71
            £0.51 est. holiday entitlement included
            14 min (3.6 mi)
            7 Landseer Close, Edgware, HA8 5SB
            12 mins (3.8 mi)
            Alsultan Market, London, NW10 1QG
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.71, ride.rating, 0.001)
    }

    @Test
    fun rating_4_48_verified_not_3_50() {
        val text = """
            UberX Exclusive
            £10.19
            ★ 4.48 Verified
            £0.64 est. holiday entitlement included
            12 min (2.7 mi)
            34 Cannon Hill, London, NW6 1JS
            18 mins (3.3 mi)
            London, W1U 8HA
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.48, ride.rating, 0.001)
    }

    @Test
    fun rating_recovers_when_star_on_separate_garbled_line() {
        val text = """
            UberX Exclusive
            £10.19
            4.48 Verified
            £0.64 est. holiday entitlement included
            12 min (2.7 mi)
            34 Cannon Hill, London, NW6 1JS
            18 mins (3.3 mi)
            London, W1U 8HA
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.48, ride.rating, 0.001)
    }

    @Test
    fun same_district_nw4_split_inward_pickup_not_empty() {
        val text = """
            Comfort Exclusive
            £5.68
            ★ 4.68
            £0.48 est. holiday entitlement included
            5 min (1.4 mi)
            5-3 Alwyn Gardens, London, NW4
            4XW
            4 mins (0.8 mi)
            Hendon Central Underground Metro Station, London, NW4 3AS
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("NW4", ride.pickup_address_postcode)
        assertEquals("NW4", ride.dropoff_address_postcode)
    }

    @Test
    fun join_split_postcode_nw4_4xw() {
        val joined = joinSplitPostcodeLines("London, NW4\n4XW\nConfirm")
        assertTrue(joined.contains("NW4 4XW"))
        assertTrue(extractOuterLondonPostcodes("London, NW4\n4XW").contains("NW4"))
    }

    @Test
    fun sw1e_split_6lb_drop_not_empty() {
        val text = """
            Electric Exclusive
            £14.42
            ★ 5.00 Verified
            £0.85 est. holiday entitlement included
            10 min (2.9 mi)
            6 Normanby Road, London, NW10 1BX
            40 mins (7.2 mi)
            Buckingham Palace, London, SW1E
            6LB
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("NW10", ride.pickup_address_postcode)
        assertEquals("SW1E", ride.dropoff_address_postcode)
    }

    @Test
    fun join_split_postcode_sw1e_6lb() {
        val joined = joinSplitPostcodeLines("Buckingham Palace, London, SW1E\n6LB")
        assertTrue(joined.contains("SW1E 6LB"))
    }

    @Test
    fun nw11_not_truncated_to_nw1() {
        val text = """
            UberX
            £12.50
            ★ 4.85 Verified
            £0.70 est. holiday entitlement included
            8 min (2.2 mi)
            713 Finchley Road, London, NW11 8AT
            30 mins (8.7 mi)
            University of West London, London, W5 5RF
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("NW11", ride.pickup_address_postcode)
        assertEquals("W5", ride.dropoff_address_postcode)
    }

    @Test
    fun nw11_recovers_when_ocr_wraps_as_nw1_plus_1_8at() {
        val joined = joinSplitPostcodeLines("713 Finchley Road, London, NW1\n1 8AT")
        assertTrue("Expected NW11 in $joined", joined.contains("NW11"))
        val text = """
            UberX
            £12.50
            ★ 4.85 Verified
            8 min (2.2 mi)
            713 Finchley Road, London, NW1
            1 8AT
            30 mins (8.7 mi)
            University of West London, London, W5 5RF
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("NW11", ride.pickup_address_postcode)
    }

    @Test
    fun longer_outward_nw11_beats_nw1_in_list() {
        val pcs = extractOuterLondonPostcodes("London, NW1\nLondon, NW11 8AT")
        assertTrue(pcs.contains("NW11"))
        assertTrue("NW1 should be dropped when NW11 present, got $pcs", !pcs.contains("NW1"))
    }

    @Test
    fun rating_4_67_and_nw11_drop_not_empty() {
        val text = """
            Electric Exclusive
            £5.71
            ★ 4.67
            £0.28 est. holiday entitlement included
            10 min (4.3 mi)
            28 Ossulton Way, London, N2 0DS
            6 mins (1.6 mi)
            Golders Green London Underground Station, London, NW11 7RN
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.67, ride.rating, 0.001)
        assertEquals("N2", ride.pickup_address_postcode)
        assertEquals("NW11", ride.dropoff_address_postcode)
    }

    @Test
    fun nw11_drop_recovers_when_split_across_lines() {
        val text = """
            Electric Exclusive
            £5.71
            ★ 4.67
            10 min (4.3 mi)
            28 Ossulton Way, London, N2 0DS
            6 mins (1.6 mi)
            Golders Green London Underground Station, London, NW11
            7RN
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("NW11", ride.dropoff_address_postcode)
    }
}
