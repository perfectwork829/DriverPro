package com.driver.pro.service

import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Client field reports 2026-07-17. */
class RideOcrClientBatchJuly17Test {

    // ID-1390: ★ 4.80 merged by OCR with the holiday line → rating fell back to 3.50.
    @Test
    fun rating_4_80_survives_merged_holiday_line() {
        val text = """
            UberX Exclusive
            £7.14
            ★ 4.80 Verified £0.43 est. holiday entitlement included
            5 min (1.2 mi)
            Park Plaza Park Royal, London, W3 0TA
            17 mins (4.5 mi)
            Boxpark, Wembley, HA9 0JT
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.80, ride.rating, 0.001)
        assertEquals(7.14, ride.price, 0.001)
        assertEquals("W3", ride.pickup_address_postcode)
        assertEquals("HA9", ride.dropoff_address_postcode)
    }

    @Test
    fun rating_line_merged_with_holiday_parses_star_value() {
        assertEquals(
            4.80,
            parseRatingFromLine("★ 4.80 Verified £0.43 est. holiday entitlement included")!!,
            0.001,
        )
        assertEquals(
            4.69,
            parseRatingFromLine("* 4.69 Verified £0.57 est. holiday entitlement included")!!,
            0.001,
        )
        assertEquals(4.45, parseRatingFromLine("4.45 Verified £0.42 est. holiday")!!, 0.001)
    }

    // ID-1374: map word "NOR..." became fake pickup outward "N0R" beating NW10.
    @Test
    fun fake_n0r_from_map_text_rejected_pickup_is_nw10() {
        assertFalse(isValidUkOutward("N0R"))
        assertTrue(extractOuterLondonPostcodes("NOR").isEmpty())

        val text = """
            NOR
            UberX
            £7.47
            ★ 4.69 Verified
            £0.57 est. holiday entitlement included
            5 min (0.9 mi)
            Harlesden Station, London, NW10 7AA
            21 mins (3.0 mi)
            Westfield London, London, W12 7GF
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("NW10", ride.pickup_address_postcode)
        assertEquals("W12", ride.dropoff_address_postcode)
    }

    // ID-1406: map label "CHILDS HILL" became fake outward "HI11" beating NW1 (wrapped "NW1"/"7ST").
    @Test
    fun fake_hi11_from_hill_label_rejected_pickup_is_nw1() {
        assertFalse(isValidUkOutward("HI11"))
        assertTrue(extractOuterLondonPostcodes("CHILDS HILL").isEmpty())
        assertTrue(extractOuterLondonPostcodes("Primrose Hill").isEmpty())

        val text = """
            CHILDS HILL
            UberX Exclusive
            £4.70
            ★ 4.79
            £0.40 est. holiday entitlement included
            2 min (0.1 mi)
            22 Prince Albert Road, London, NW1
            7ST
            10 mins (1.7 mi)
            Finchley Road Station (Stop CH), London, NW3 5HS
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("NW1", ride.pickup_address_postcode)
        assertEquals("NW3", ride.dropoff_address_postcode)
    }

    // ID-1405: pickup NW1 8AG must win; drop's WC1N must not be copied into pickup.
    @Test
    fun camden_guitars_nw1_pickup_not_wc1n() {
        val text = """
            Electric Exclusive
            £9.22
            ★ 4.87
            £0.64 est. holiday entitlement included
            8 min (1.5 mi)
            Camden Guitars, London, NW1 8AG
            19 mins (2.7 mi)
            Goodenough Club Hotel, London, WC1N 2AD
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("NW1", ride.pickup_address_postcode)
        assertEquals("WC1N", ride.dropoff_address_postcode)
    }

    // ID-1409: pickup NW1 0AU must win; drop's SE1 must not be duplicated into pickup.
    @Test
    fun bayham_street_nw1_pickup_not_se1() {
        val text = """
            UberXL
            £21.91
            Cash payment ★ 5.00
            £1.93 est. holiday entitlement included
            10 min (1.7 mi)
            150 Bayham Street, London, NW1 0AU
            33 mins (3.8 mi)
            London Waterloo Train Station,
            London, SE1 8SW
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("NW1", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
    }

    @Test
    fun single_drop_postcode_line_does_not_fill_pickup() {
        // Only one SE1 mention on one line: pickup must stay blank, not copy the drop code.
        val text = """
            UberXL
            £21.91
            5.00
            10 min (1.7 mi)
            150 Bayham Street, London
            33 mins (3.8 mi)
            London Waterloo Train Station, London, SE1 8SW
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertNotEquals("SE1", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
    }

    // ID-1413: £11.44 unreadable over the map — the ★ 4.48 rating must not become the price.
    @Test
    fun rating_4_48_must_not_leak_into_price() {
        val text = """
            Electric Exclusive
            ★ 4.48
            £0.85 est. holiday entitlement included
            7 min (1.3 mi)
            1 Steele's Mews S, London, NW3 4SJ
            27 mins (3.4 mi)
            Centre Point Dental, London, WC2H 0LA
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.48, ride.rating, 0.001)
        assertTrue("price must be unread, got ${ride.price}", ride.price < 1.0)
        val error = validateRideBeforeScoring(ride)
        assertNotNull(error)
    }

    @Test
    fun price_still_read_when_fare_line_present_with_4_48_rating() {
        val text = """
            Electric Exclusive
            £11.44
            ★ 4.48
            £0.85 est. holiday entitlement included
            7 min (1.3 mi)
            1 Steele's Mews S, London, NW3 4SJ
            27 mins (3.4 mi)
            Centre Point Dental, London, WC2H 0LA
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(11.44, ride.price, 0.001)
        assertEquals(4.48, ride.rating, 0.001)
    }

    // Missing trip time+distance: pickup leg mangled to "7 min .4 m)".
    @Test
    fun trip_leg_parses_min_dot_digit_m_paren_lost() {
        val leg = parseTripLegFromLine("7 min .4 m)")
        assertNotNull(leg)
        assertEquals(7, leg!!.minutes)
        assertEquals(0.4, leg.miles, 0.001)

        val text = """
            UberX Exclusive
            £7.42
            ★ 4.45
            £0.42 est. holiday entitlement included
            7 min .4 m)
            614 Western Avenue. London. W3 0TE
            15 mins (5.0 mi)
            Rubicon. Wembley. HA9 0YJ
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(7, ride.pickup_time_minutes)
        assertEquals(15, ride.trip_time_minutes)
        assertEquals(5.0, ride.trip_distance_value!!, 0.001)
        assertEquals(7.42, ride.price, 0.001)
    }

    // Missing trip distance: "8 mins (0.8 mni)".
    @Test
    fun trip_leg_parses_mni_misread() {
        val leg = parseTripLegFromLine("8 mins (0.8 mni)")
        assertNotNull(leg)
        assertEquals(8, leg!!.minutes)
        assertEquals(0.8, leg.miles, 0.001)

        val text = """
            Comfort Exclusive
            £5.76
            ★ 4.86
            £0.56 est. holiday entitlement included
            2 min (0.2 mi)
            Arlington Road. London. NW1 7AH
            8 mins (0.8 mni)
            29 Grafton Road. London. NW5 3DX
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(2, ride.pickup_time_minutes)
        assertEquals(8, ride.trip_time_minutes)
        assertEquals(0.8, ride.trip_distance_value!!, 0.001)
    }

    // Missing trip time: "50 nmins (7.4 mi)".
    @Test
    fun trip_leg_parses_nmins_misread() {
        val leg = parseTripLegFromLine("50 nmins (7.4 mi)")
        assertNotNull(leg)
        assertEquals(50, leg!!.minutes)
        assertEquals(7.4, leg.miles, 0.001)

        val text = """
            Electric Exclusive
            £22.45
            ★ 4.50 Verified
            £1.83 est. holiday entitlement included
            6 min (0.8 mi)
            Watch House, London, W1G 8UE
            50 nmins (7.4 mi)
            London, NW9 4DF
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(6, ride.pickup_time_minutes)
        assertEquals(50, ride.trip_time_minutes)
        assertEquals(7.4, ride.trip_distance_value!!, 0.001)
        assertEquals(22.45, ride.price, 0.001)
    }

    @Test
    fun district_zero_only_valid_for_croydon() {
        assertTrue(isValidUkOutward("CR0"))
        assertFalse(isValidUkOutward("N0R"))
        assertFalse(isValidUkOutward("W0"))
        assertFalse(isValidUkOutward("SE0"))
        assertFalse(isValidUkOutward("HI11"))
        assertFalse(isValidUkOutward("XX1"))
        assertTrue(isValidUkOutward("NW10"))
        assertTrue(isValidUkOutward("WC1N"))
        assertTrue(isValidUkOutward("HA9"))
    }
}
