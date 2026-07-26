package com.driver.pro.service

import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Client field reports 2026-07-25. */
class RideOcrClientBatchJuly25Test {

    @Test
    fun e1w_ec2r_not_swapped_with_map_e15() {
        val text = """
            Electric Exclusive
            £5.42
            ★ 5.00
            £0.22 est. holiday entitlement included
            11 min (1.2 mi)
            Vicinity, London, E1W 1LD
            10 mins (1.5 mi)
            Clayton Hotel London Wall, London, EC2R 7NJ
            E15
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("E1W", ride.pickup_address_postcode)
        assertEquals("EC2R", ride.dropoff_address_postcode)
        assertTrue(extractOuterLondonPostcodes(text).none { it == "E15" })
    }

    @Test
    fun xl_37_41_not_3_74_and_cr0_ec2m() {
        val text = """
            UberXL
            £3.74
            ★ 4.93
            11 min (2.3 mi)
            23 Addington Road, Croydon, CR0 3LW
            1 hr 5 min (11.2 mi)
            London Liverpool Street Train Station, London, EC2M 7PY
            Long trip (60+ min)
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(37.41, ride.price!!, 0.01)
        assertEquals(4.93, ride.rating, 0.001)
        assertEquals("CR0", ride.pickup_address_postcode)
        assertEquals("EC2M", ride.dropoff_address_postcode)
    }

    @Test
    fun el_6qr_sel_9sp_spitalfields_london_bridge() {
        val text = """
            Electric
            £6.43
            Cash payment ★ 5.00
            £0.41 est. holiday entitlement included
            8 min (lỘ mi)
            Pizza Pilgrims Spitalfields. London. El
            6QR
            13 mins (19 mi)
            London Bridge. London. SEl 9SP
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("E1", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
        assertEquals(1.0, ride.pickup_distance_value!!, 0.05)
        assertEquals(1.9, ride.trip_distance_value!!, 0.05)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun glare_pickup_not_filled_with_drop_w1c() {
        val text = """
            Electric Exclusive
            £8.01
            ★ 4.82 Verified
            5 min (0.6 mi)
            Jasmine News, London,
            23 mins (2.6 mi)
            Samsonite, London, W1C 2DW
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("W1C", ride.dropoff_address_postcode)
        assertTrue(
            ride.pickup_address_postcode.isNullOrBlank() ||
                ride.pickup_address_postcode == "EC1M",
        )
        assertTrue(ride.pickup_address_postcode != "W1C")
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun rating_4_90_not_3_50_from_trip_miles() {
        val text = """
            UberX
            £12.74
            ★ 4.90
            £1.03 est. holiday entitlement included
            5 min (0.6 mi)
            Bishopsgate Police Station, London, EC2M 4NP
            21 mins (3.5 mi)
            Old Ford Rd., London, E3 5JJ
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.90, ride.rating, 0.001)
        assertEquals(12.74, ride.price!!, 0.01)
    }

    @Test
    fun ecin_swie_split_inward_and_leading_decimal_miles() {
        val text = """
            Electric
            ★ 5.00
            £12.86
            7 min (.3 mi)
            £1.04 est. holiday entitlement included
            Bounce. London. ECIN 2TD
            20 mins (2.7 mi)
            6LB
            Buckingham Palace. London. SWIE
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("EC1N", ride.pickup_address_postcode)
        assertEquals("SW1E", ride.dropoff_address_postcode)
        assertEquals(0.3, ride.pickup_distance_value!!, 0.05)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun street_only_drop_cavalry_square_ok() {
        val text = """
            UberX Exclusive
            £15.08
            ★ 4.84 Verified
            £1.24 est. holiday entitlement included
            1 min (0.1 mi)
            48 Cavalry Square. London
            46 Hatton Garden. London. ECIN 8EX
            30 mins (4.5 mi)
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("EC1N", ride.pickup_address_postcode)
        assertTrue(ocrHasDropAddressStreetWithoutPostcode(text))
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun e1_7ex_split_before_address_not_missing() {
        val text = """
            UberXL
            £7.85
            ★ 4.88
            £0.65 est. holiday entitlement included
            5 min (0.5 mi)
            7EX
            Travelodge London City. London. El
            6 mins (0.9 mi)
            Whitechapel Station. London. El 1BY
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("E1", ride.pickup_address_postcode)
        assertEquals("E1", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }
}
