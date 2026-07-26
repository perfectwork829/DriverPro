package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import com.driver.pro.network.validateRideForScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideOcrParsingTest {

    @Test
    fun tripLeg_parses_pickup_and_drop_with_miles() {
        assertEquals(2, parseTripLegFromLine("2 min (0.3 mi)")?.minutes)
        assertEquals(26, parseTripLegFromLine("26 mins (5.0 mi)")?.minutes)
        assertEquals(8, parseTripLegFromLine("8 min (1.1 mi)")?.minutes)
        assertEquals(20, parseTripLegFromLine("20 mins (3.0 mi)")?.minutes)
    }

    @Test
    fun tripLeg_parses_one_hour_one_minute() {
        assertEquals(61, parseTripLegFromLine("1 hr 1 min (17.7 mi)")?.minutes)
    }

    @Test
    fun tripLeg_parses_ocr_noisy_hour_and_miles_digits() {
        val parsed = parseTripLegFromLine("l hr 8 min (l4.2 mi)")
        assertEquals(68, parsed?.minutes)
        assertEquals(14.2, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun tripLeg_fixes_71_miles_to_7_1_for_urban_trip_time() {
        val parsed = parseTripLegFromLine("54 mins (71.0 mi)")
        assertEquals(54, parsed?.minutes)
        assertEquals(7.1, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun parseOcrMiles_fixes_7l1_pattern() {
        assertEquals(7.1, parseOcrMiles("7l.1", 54)!!, 0.001)
    }

    @Test
    fun parseOcrMiles_scales_71_point_0_for_54_min_trip() {
        assertEquals(7.1, parseOcrMiles("71.0", 54)!!, 0.001)
    }

    @Test
    fun postcodes_prefer_sw7_over_w7_when_both_valid() {
        val pcs = extractOuterLondonPostcodes("Millennium Hotel, London, SW7 4LH")
        assertTrue("Expected SW7, got $pcs", pcs.contains("SW7"))
    }

    @Test
    fun postcodes_prefer_w8_not_sw8_when_w8_on_screen() {
        val pcs = extractOuterLondonPostcodes("Jacuzzi, London, W8 4SG")
        assertTrue("Expected W8, got $pcs", pcs.contains("W8"))
        assertTrue("Should not prefer SW8 over W8, got $pcs", pcs.first() == "W8")
    }

    @Test
    fun postcodes_recover_sl4_from_s14_ocr() {
        val pcs = extractOuterLondonPostcodes("Saint Marys Lane, Windsor, S14 4SE")
        assertTrue("Expected SL4 from S14 OCR, got $pcs", pcs.contains("SL4"))
    }

    @Test
    fun postcodes_recover_n15_from_n0_ocr() {
        val pcs = extractOuterLondonPostcodes("Penrith Rd, London, N0 5QY")
        assertTrue("Expected N15 from N0 OCR, got $pcs", pcs.contains("N15"))
    }

    @Test
    fun tripLeg_fixes_3_2_miles_to_8_2_on_long_trip() {
        val parsed = parseTripLegFromLine("55 mins (3.2 mi)")
        assertEquals(55, parsed?.minutes)
        assertEquals(8.2, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun parseRideInfo_keeps_same_postcode_when_pickup_and_drop_share_address() {
        val text = """
            £12.01
            4.69
            17 min (3.2 mi)
            Penrith Rd, London, N15 5QY
            34 mins (3.7 mi)
            Penrith Rd, London, N15 5QY
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("N15", ride.pickup_address_postcode)
        assertEquals("N15", ride.dropoff_address_postcode)
    }

    @Test
    fun postcodes_recover_sw7_from_w7_ocr_line() {
        val pcs = extractOuterLondonPostcodes("Millennium Hotel, London, W7 4LH")
        assertTrue("Expected SW7 from W7 OCR, got $pcs", pcs.contains("SW7"))
    }

    @Test
    fun extractTime_requires_miles_parenthesis_first() {
        val text = """
            £13.86
            4.72
            2 min (0.3 mi)
            SW1P 3JA
            26 mins (5.0 mi)
            SW18 2RT
        """.trimIndent()
        val times = extractTime(text)
        assertEquals(listOf("2", "26"), times.take(2))
    }

    @Test
    fun fare_without_decimal_point_normalized() {
        assertEquals(7.43, parseFareFromLine("£743")!!, 0.001)
        assertEquals(7.43, parseFareFromLine("£7.43")!!, 0.001)
        assertEquals(11.56, parseFareFromLine("£11.56")!!, 0.001)
        assertEquals(46.0, parseFareFromLine("£46")!!, 0.001)
    }

    @Test
    fun parseRideInfo_comfort_reserve_fortySix_pounds() {
        val text = """
            Comfort Reserve
            £46
            ★ 4.85
            8 min (2.1 mi)
            2 Park Hill Drive, Cobham, KT11 2FN
            1 hr 11 min (22.2 mi)
            Reform Social & Grill, London, W1U 2BE
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(46.0, ride.price, 0.001)
        assertEquals("KT11", ride.pickup_address_postcode)
        assertEquals("W1U", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_baker_st_w1u_not_w1k_map_label() {
        val text = """
            £4.62
            ★ 5.00
            3 min (0.4 mi)
            W1K
            96-98 Baker St, London, W1U 6TJ
            8 mins (1.1 mi)
            127 Mount Street, London, W1K 3NT
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("W1U", ride.pickup_address_postcode)
        assertEquals("W1K", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_bayswater_w2_not_sw1w_map_label() {
        val text = """
            Electric
            £7.04
            5.00
            7 min (1.0 mi)
            SW1W
            Bayswater Road, London, W2 2NT
            18 mins (2.7 mi)
            Victoria Coach Station, London, SW1W 9TP
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("W2", ride.pickup_address_postcode)
        assertEquals("SW1W", ride.dropoff_address_postcode)
    }

    @Test
    fun extract_postcodes_w1u_and_w1k_from_addresses() {
        val pickupPcs = extractOuterLondonPostcodes("96-98 Baker St, London, W1U 6TJ")
        val dropPcs = extractOuterLondonPostcodes("127 Mount Street, London, W1K 3NT")
        assertTrue("Expected W1U, got $pickupPcs", pickupPcs.contains("W1U"))
        assertTrue("Expected W1K, got $dropPcs", dropPcs.contains("W1K"))
    }

    @Test
    fun extract_postcodes_w2_and_sw1w_from_addresses() {
        val pickupPcs = extractOuterLondonPostcodes("Bayswater Road, London, W2 2NT")
        val dropPcs = extractOuterLondonPostcodes("Victoria Coach Station, London, SW1W 9TP")
        assertTrue("Expected W2, got $pickupPcs", pickupPcs.contains("W2"))
        assertTrue("Expected SW1W, got $dropPcs", dropPcs.contains("SW1W"))
    }

    @Test
    fun structured_lines_w1u_when_postcode_split_to_line_after_drop_leg() {
        val lines = listOf(
            OcrLine("£4.62", 10),
            OcrLine("★ 5.00", 20),
            OcrLine("3 min (0.4 mi)", 80),
            OcrLine("96-98 Baker St, London", 100),
            OcrLine("8 mins (1.1 mi)", 120),
            OcrLine("W1U 6TJ", 130),
            OcrLine("127 Mount Street, London, W1K 3NT", 150),
        )
        val fullText = lines.joinToString("\n") { it.text }
        val parsed = parseStructuredFromLines(lines, fullText)!!
        assertEquals("W1U", parsed.pickupPostcode)
        assertEquals("W1K", parsed.dropPostcode)
    }

    @Test
    fun structured_lines_w2_when_postcode_split_after_drop_leg() {
        val lines = listOf(
            OcrLine("£7.04", 10),
            OcrLine("5.00", 20),
            OcrLine("7 min (1.0 mi)", 80),
            OcrLine("Bayswater Road, London", 100),
            OcrLine("18 mins (2.7 mi)", 120),
            OcrLine("W2 2NT", 130),
            OcrLine("Victoria Coach Station, London, SW1W 9TP", 150),
        )
        val fullText = lines.joinToString("\n") { it.text }
        val parsed = parseStructuredFromLines(lines, fullText)!!
        assertEquals("W2", parsed.pickupPostcode)
        assertEquals("SW1W", parsed.dropPostcode)
    }

    @Test
    fun structured_lines_map_sw1w_only_in_pickup_zone_gives_wrong_pickup() {
        val lines = listOf(
            OcrLine("£7.04", 10),
            OcrLine("5.00", 20),
            OcrLine("7 min (1.0 mi)", 80),
            OcrLine("SW1W", 90),
            OcrLine("Bayswater Road, London", 110),
            OcrLine("18 mins (2.7 mi)", 140),
            OcrLine("Victoria Coach Station, London, SW1W 9TP", 170),
        )
        val parsed = parseStructuredFromLines(lines, lines.joinToString("\n") { it.text })!!
        assertEquals("", parsed.pickupPostcode)
        assertEquals("SW1W", parsed.dropPostcode)
    }

    @Test
    fun structured_lines_map_w1k_only_when_pickup_address_has_no_postcode_in_zone() {
        val lines = listOf(
            OcrLine("£4.62", 10),
            OcrLine("★ 5.00", 20),
            OcrLine("3 min (0.4 mi)", 80),
            OcrLine("W1K", 90),
            OcrLine("96-98 Baker St, London", 110),
            OcrLine("8 mins (1.1 mi)", 140),
            OcrLine("127 Mount Street, London, W1K 3NT", 170),
        )
        val parsed = parseStructuredFromLines(lines, lines.joinToString("\n") { it.text })!!
        assertEquals("", parsed.pickupPostcode)
        assertEquals("W1K", parsed.dropPostcode)
    }

    @Test
    fun structured_lines_map_w1k_does_not_steal_pickup_from_w1u_address() {
        val lines = listOf(
            OcrLine("£4.62", 10),
            OcrLine("★ 5.00", 20),
            OcrLine("3 min (0.4 mi)", 80),
            OcrLine("W1K", 90),
            OcrLine("96-98 Baker St, London, W1U 6TJ", 110),
            OcrLine("8 mins (1.1 mi)", 140),
            OcrLine("127 Mount Street, London, W1K 3NT", 170),
        )
        val parsed = parseStructuredFromLines(lines, lines.joinToString("\n") { it.text })!!
        assertEquals("W1U", parsed.pickupPostcode)
        assertEquals("W1K", parsed.dropPostcode)
    }

    @Test
    fun parseRideInfo_w2_tw6_with_combined_address_lines() {
        val text = """
            Electric
            £22.39
            Cash payment
            4.80
            £1.48 est. holiday entitlement included
            6 min (0.8 mi)
            London, W2 2QS
            45 mins (17.4 mi)
            EGYPTAIR, Hounslow, TW6 2GW
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(6, ride.pickup_time_minutes)
        assertEquals(45, ride.trip_time_minutes)
        assertEquals(0.8, ride.pickup_distance_value!!, 0.001)
        assertEquals(17.4, ride.trip_distance_value!!, 0.001)
        assertEquals("W2", ride.pickup_address_postcode)
        assertEquals("TW6", ride.dropoff_address_postcode)
        assertNull(validateRideForScoring(ride))
    }

    @Test
    fun parseRideInfo_electric_cash_w2_to_tw6_heathrow() {
        val text = """
            Electric
            £22.39
            Cash payment
            4.80
            £1.48 est. holiday entitlement included
            6 min (0.8 mi)
            W2 2QS
            London
            45 mins (17.4 mi)
            EGYPTAIR
            TW6 2GW
            Hounslow
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(22.39, ride.price, 0.001)
        assertEquals(4.80, ride.rating, 0.001)
        assertEquals(6, ride.pickup_time_minutes)
        assertEquals(45, ride.trip_time_minutes)
        assertEquals(0.8, ride.pickup_distance_value!!, 0.001)
        assertEquals(17.4, ride.trip_distance_value!!, 0.001)
        assertEquals("W2", ride.pickup_address_postcode)
        assertEquals("TW6", ride.dropoff_address_postcode)
        assertEquals("match", ride.type)
        assertNull(validateRideForScoring(ride))
    }

    @Test
    fun pickFare_ignores_holiday_entitlement_line_on_electric_card() {
        val lines = listOf(
            OcrLine("Electric", 0),
            OcrLine("£22.39", 20),
            OcrLine("Cash payment", 35),
            OcrLine("4.80", 50),
            OcrLine("£1.48 est. holiday entitlement included", 70),
            OcrLine("6 min (0.8 mi)", 100),
        )
        val fare = pickFareFromHeaderLines(lines, lines.joinToString("\n") { it.text })
        assertEquals(22.39, fare!!, 0.001)
    }

    @Test
    fun parseRideInfo_pickup_kt11_not_map_label_kt22() {
        val text = """
            Electric
            Exclusive
            £8.34
            4.85
            2 min (0.2 mi)
            KT22
            Cooper Cobham - BMW, London, KT11 1JG
            13 mins (4.0 mi)
            Spicers Fld, Leatherhead, KT22 0UT
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("KT11", ride.pickup_address_postcode)
        assertEquals("KT22", ride.dropoff_address_postcode)
    }

    @Test
    fun rating_inline_with_star() {
        assertEquals(4.72, parseRatingFromLine("★ 4.72")!!, 0.001)
        assertEquals(4.16, parseRatingFromLine("4.16")!!, 0.001)
    }

    @Test
    fun structured_lines_pickup_before_drop() {
        val lines = listOf(
            OcrLine("£13.54", 10),
            OcrLine("4.30", 40),
            OcrLine("3 min (0.6 mi)", 80),
            OcrLine("Park Plaza London Waterloo, SE1 7DP", 110),
            OcrLine("25 mins (4.4 mi)", 140),
            OcrLine("Jerningham Court, SE14 5NU", 170),
        )
        val parsed = parseStructuredFromLines(lines, lines.joinToString("\n") { it.text })!!
        assertEquals(3, parsed.pickupMinutes)
        assertEquals(25, parsed.tripMinutes)
        assertEquals("SE1", parsed.pickupPostcode)
        assertEquals("SE14", parsed.dropPostcode)
    }

    @Test
    fun structured_lines_does_not_steal_drop_postcode_into_pickup() {
        val lines = listOf(
            OcrLine("£22.87", 10),
            OcrLine("3.46", 40),
            OcrLine("6 min (1.2 mi)", 80),
            OcrLine("Gleneagle Rd, London", 110),
            OcrLine("1 hr 1 min (7.6 mi)", 140),
            OcrLine("London", 170),
            OcrLine("SW16 6GF", 200),
        )
        val parsed = parseStructuredFromLines(lines, lines.joinToString("\n") { it.text })!!
        assertEquals(6, parsed.pickupMinutes)
        assertEquals(61, parsed.tripMinutes)
        assertEquals("", parsed.pickupPostcode)
        assertEquals("SW16", parsed.dropPostcode)
    }

    @Test
    fun structured_lines_anchor_postcodes_to_leg_positions() {
        val lines = listOf(
            OcrLine("Map label: London WC2B 5AA", 0),
            OcrLine("£24.11", 20),
            OcrLine("4.88", 40),
            OcrLine("8 min (1.0 mi)", 80),
            OcrLine("103 Kingsway, London, WC2B 6JR", 110),
            OcrLine("59 mins (7.8 mi)", 140),
            OcrLine("Royal Victoria Dock, London, E16 1XL", 170),
        )
        val parsed = parseStructuredFromLines(lines, lines.joinToString("\n") { it.text })!!
        assertEquals("WC2B", parsed.pickupPostcode)
        assertEquals("E16", parsed.dropPostcode)
    }

    @Test
    fun extractBestPrice_handles_missing_decimal() {
        val price = extractBestPrice("Electric\n£743\n5.00\n3 min (0.4 mi)")
        assertTrue(price in 7.0..7.5)
    }

    @Test
    fun pickFare_does_not_use_passenger_rating_as_price() {
        val lines = listOf(
            OcrLine("Electric", 0),
            OcrLine("£4.82", 20),
            OcrLine("5.00", 50),
            OcrLine("£0.39 est. holiday entitlement included", 70),
            OcrLine("3 min (0.4 mi)", 100),
        )
        val fare = pickFareFromHeaderLines(lines, lines.joinToString("\n") { it.text })
        assertEquals(4.82, fare!!, 0.001)
    }

    @Test
    fun extractBestPrice_prefers_fare_over_rating_decimal() {
        val price = extractBestPrice("Electric\n£4.82\n5.00\n3 min (0.4 mi)")
        assertEquals(4.82, price, 0.001)
    }

    @Test
    fun fare_ignores_priority_included_addon_lines() {
        val lines = listOf(
            OcrLine("Electric Priority", 0),
            OcrLine("£11.89", 20),
            OcrLine("★ 4.92", 40),
            OcrLine("+ £1.16 included for priority", 60),
            OcrLine("5 min (0.6 mi)", 90),
        )
        val fare = pickFareFromHeaderLines(lines, lines.joinToString("\n") { it.text })
        assertEquals(11.89, fare!!, 0.001)
    }

    @Test
    fun postcodes_extract_guildford_gu_from_full_address() {
        val text = """
            7 Park Road, Guildford, GU1 4PH
            Royal Surrey County Hospital, Guildford, GU2 7XX
        """.trimIndent()
        val pcs = extractOuterLondonPostcodes(text)
        assertTrue("Expected GU1, got $pcs", pcs.contains("GU1"))
        assertTrue("Expected GU2, got $pcs", pcs.contains("GU2"))
    }

    @Test
    fun postcodes_extract_redhill_rh() {
        val pcs = extractOuterLondonPostcodes("6 Montfort Rise, Redhill, RH1 5DU")
        assertTrue(pcs.contains("RH1"))
    }

    @Test
    fun postcodes_extract_mk10_truncated_inward_and_mk6_full() {
        val text = """
            £8.64
            4.70
            17 min (9.4 mi)
            22 Maritime Way, Milton Keynes, MK10 7
            11 mins (4.7 mi)
            Milton Keynes Hospital, Milton Keynes, MK6 5LD
            Match
        """.trimIndent()
        val pcs = extractOuterLondonPostcodes(text)
        assertTrue("Expected MK10, got $pcs", pcs.contains("MK10"))
        assertTrue("Expected MK6, got $pcs", pcs.contains("MK6"))
        assertTrue("Pickup MK10 should appear before drop MK6, got $pcs", pcs.indexOf("MK10") < pcs.indexOf("MK6"))
    }

    @Test
    fun postcodes_truncated_inward_does_not_steal_mk6_from_full_postcode() {
        val pcs = extractOuterLondonPostcodes("Milton Keynes Hospital, MK6 5LD")
        assertEquals(listOf("MK6"), pcs)
    }

    @Test
    fun postcodes_parse_cr0_when_ocr_reads_letter_o_instead_of_zero() {
        val text = """
            1 min (0.2 mi)
            Bensham Lane, Croydon, CRO 2RS
            5 mins (0.9 mi)
            The Crescent Primary School, Croydon, CR0 2HN
        """.trimIndent()
        val pcs = extractOuterLondonPostcodes(text)
        assertTrue("Expected CR0 from CRO OCR variant, got $pcs", pcs.contains("CR0"))
    }

    @Test
    fun postcodes_parse_e16_when_ocr_reads_el6() {
        val pcs = extractOuterLondonPostcodes("UEL SportsDock, London, EL6 2RD")
        assertTrue("Expected E16 from EL6 OCR variant, got $pcs", pcs.contains("E16"))
    }

    @Test
    fun fare_prefers_trip_price_over_rating_without_pound_sign() {
        val lines = listOf(
            OcrLine("Comfort", 0),
            OcrLine("32.32", 20),
            OcrLine("★ 4.44", 40),
            OcrLine("£3.07 est. holiday entitlement included", 60),
            OcrLine("10 min (0.7 mi)", 90),
        )
        val fare = pickFareFromHeaderLines(lines, lines.joinToString("\n") { it.text })
        assertEquals(32.32, fare!!, 0.001)
    }

    @Test
    fun reconcile_duplicated_pickup_drop_from_two_leg_lines() {
        val ocr = """
            £21.85
            4.70
            9 min (0.8 mi)
            40-42 Parker St, London, WC2B 5PQ
            44 mins (4.3 mi)
            Globe Primary School, London, E2 0JH
            Match
        """.trimIndent()
        val parsed = parseRideInfo(ocr, null)
        val ride = reconcileDuplicatedPickupDrop(parsed, ocr)
        assertEquals(9, ride.pickup_time_minutes)
        assertEquals(44, ride.trip_time_minutes)
        assertEquals(0.8, ride.pickup_distance_value!!, 0.001)
        assertEquals(4.3, ride.trip_distance_value!!, 0.001)
    }

    @Test
    fun parseRideInfo_leaves_pickup_postcode_blank_when_only_drop_has_code() {
        val text = """
            £48.85
            4.63
            7 min (0.6 mi)
            Cambridge Circus D Bus Stop, London
            59 mins (7.7 mi)
            Tooting Broadway, London, SW17 0SU
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("", ride.pickup_address_postcode)
        assertEquals("SW17", ride.dropoff_address_postcode)
        assertEquals(95, ride.accuracy)
    }

    @Test
    fun parseRideInfo_reads_pickup_postcode_when_only_pickup_has_code() {
        val text = """
            £5.91
            4.49
            7 min (1.1 mi)
            3 Halefield Road, London, N17 9XR
            10 mins (1.1 mi)
            Tottenham Delivery Office, London
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("N17", ride.pickup_address_postcode)
        assertEquals("", ride.dropoff_address_postcode)
        val err = validateRideForScoring(ride)
        assertNotNull(err)
        assertTrue(err!!.contains("drop-off postcode"))
        assertTrue(!err.contains("pickup postcode"))
    }

    @Test
    fun parseRideInfo_reads_n18_pickup_when_drop_address_has_no_postcode() {
        val text = """
            £5.76
            4.82
            4 min (0.7 mi)
            77 Somerset Road, London, N18 1HH
            11 mins (1.5 mi)
            Eley Trading Estate Nobel Rd,
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("N18", ride.pickup_address_postcode)
        assertEquals("", ride.dropoff_address_postcode)
        assertTrue(
            validateRideForScoring(ride)?.contains("drop-off postcode") == true,
        )
    }

    @Test
    fun parseRideInfo_assigns_lone_postcode_after_drop_leg_to_drop() {
        val text = """
            £7.67
            ★ 4.71
            4 min (0.4 mi)
            15 mins (1.6 mi)
            N1 7GQ
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("", ride.pickup_address_postcode)
        assertEquals("N1", ride.dropoff_address_postcode)
    }

    @Test
    fun pickBestPassengerRating_prefers_star_line_over_noise() {
        val lines = listOf(
            OcrLine("Electric", 0),
            OcrLine("£7.26", 20),
            OcrLine("3.5", 40),
            OcrLine("★ 4.41", 55),
        )
        assertEquals(4.41, pickBestPassengerRating(lines, lines.joinToString("\n") { it.text })!!, 0.001)
    }

    @Test
    fun tripLeg_keeps_27_7_miles_on_72_min_long_trip() {
        val parsed = parseTripLegFromLine("1 hr 12 min (27.7 mi)")
        assertEquals(72, parsed?.minutes)
        assertEquals(27.7, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun tripLeg_fixes_4_0_miles_to_0_4_on_short_pickup() {
        val parsed = parseTripLegFromLine("3 min (4.0 mi)")
        assertEquals(3, parsed?.minutes)
        assertEquals(0.4, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun parseOcrMiles_keeps_0_3_on_short_pickup() {
        // Do not force 0.3→0.8; short local pickups commonly really are ~0.3 mi.
        assertEquals(0.3, parseOcrMiles("0.3", 6)!!, 0.001)
    }

    @Test
    fun postcodes_recover_sw1w_from_al0n_ocr() {
        val pcs = extractOuterLondonPostcodes("Salon Sloane, London, AL0N 8NP")
        assertTrue("Expected SW1W from AL0N OCR, got $pcs", pcs.contains("SW1W"))
    }

    @Test
    fun parseRideInfo_comfort_exclusive_27_7_mile_trip() {
        val text = """
            Comfort Exclusive
            £45.36
            4.59
            2 min (0.2 mi)
            33 Grosvenor Place, London, SW1X 7HN
            1 hr 12 min (27.7 mi)
            Queens Hill, Ascot, SL5
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(72, ride.trip_time_minutes)
        assertEquals(27.7, ride.trip_distance_value!!, 0.001)
        assertEquals("SW1X", ride.pickup_address_postcode)
        assertEquals("SL5", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_salon_sloane_sw1w_from_al0n_ocr() {
        val text = """
            £12.02
            4.88
            4 min (0.5 mi)
            Salon Sloane, London, AL0N 8NP
            35 mins (4.4 mi)
            The Derby London City, Curio Collection by Hilton, Greater London, EC3R 5AA
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("SW1W", ride.pickup_address_postcode)
        assertEquals("EC3R", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_sw1p_pickup_not_w1d_from_drop() {
        val text = """
            £8.19
            5.00
            3 min (0.4 mi)
            Grange Wellington Hotel, London, SW1P 2PA
            16 mins (1.8 mi)
            16 Denman St, London, W1D 7DY
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("SW1P", ride.pickup_address_postcode)
        assertEquals("W1D", ride.dropoff_address_postcode)
        assertEquals(0.4, ride.pickup_distance_value!!, 0.001)
    }

    @Test
    fun parseRideInfo_sw1p_pickup_when_w1d_map_label_in_pickup_zone() {
        val text = """
            £8.19
            5.00
            3 min (0.4 mi)
            W1D
            Grange Wellington Hotel, London, SW1P 2PA
            16 mins (1.8 mi)
            16 Denman St, London, W1D 7DY
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("SW1P", ride.pickup_address_postcode)
        assertEquals("W1D", ride.dropoff_address_postcode)
    }

    @Test
    fun tripLeg_keeps_0_3_miles_on_short_drop() {
        val parsed = parseTripLegFromLine("6 mins (0.3 mi)")
        assertEquals(6, parsed?.minutes)
        assertEquals(0.3, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun tripLeg_fixes_2_78_miles_to_27_8_on_long_trip() {
        val parsed = parseTripLegFromLine("55 mins (2.78 mi)")
        assertEquals(55, parsed?.minutes)
        assertEquals(27.8, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun parseRideInfo_air_street_w1j_not_w6_map_label() {
        val text = """
            £15.41
            4.52
            4 min (0.4 mi)
            W6
            20 Air Street, London, W1J 0AB
            31 mins (5.2 mi)
            Harbour Club Chelsea, London, SW6 2RW
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("W1J", ride.pickup_address_postcode)
        assertEquals("SW6", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_chelsea_sw6_from_n0_ocr() {
        val text = """
            £15.41
            4.52
            4 min (0.4 mi)
            20 Air Street, London, W1J 0AB
            31 mins (5.2 mi)
            Harbour Club Chelsea, London, N0 2RW
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("W1J", ride.pickup_address_postcode)
        assertEquals("SW6", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_addlestone_27_8_mile_trip() {
        val text = """
            UberX
            £33.38
            4.94
            3 min (0.5 mi)
            1 Saint James's Square, London, SW1Y 4JH
            55 mins (2.78 mi)
            67 Liberty Hall Road, Addlestone, KT15 1SS
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(27.8, ride.trip_distance_value!!, 0.001)
        assertEquals("SW1Y", ride.pickup_address_postcode)
        assertEquals("KT15", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_rupert_street_w1d_not_ec3m_map_label() {
        val text = """
            Electric
            £11.31
            4.85
            2 min (0.2 mi)
            EC3M
            Rupert Street, Coventry Street, W1D 7AB
            25 mins (2.9 mi)
            Fenchurch St, London, EC3M 5AD
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("W1D", ride.pickup_address_postcode)
        assertEquals("EC3M", ride.dropoff_address_postcode)
        assertNull(validateRideForScoring(ride))
    }

    @Test
    fun parseRideInfo_fenchurch_ec3m_from_n6_ocr() {
        val text = """
            Electric
            £11.31
            4.85
            2 min (0.2 mi)
            Rupert Street, Coventry Street, W1D 7AB
            25 mins (2.9 mi)
            Fenchurch St, London, N6 5AD
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("W1D", ride.pickup_address_postcode)
        assertEquals("EC3M", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_sw6_pickup_when_drop_leg_ocr_before_pickup_address() {
        val lines = listOf(
            OcrLine("£12.98", 0),
            OcrLine("★ 4.53", 20),
            OcrLine("7 min (1.2 mi)", 40),
            OcrLine("W13", 50),
            OcrLine("29 mins (7.5 mi)", 60),
            OcrLine("22 Purser's Cross Rd, London, SW6 4QX", 80),
            OcrLine("35 Woodstock Avenue, London, W13 9UQ", 100),
        )
        val parsed = parseStructuredFromLines(lines, lines.joinToString("\n") { it.text })!!
        assertEquals("SW6", parsed.pickupPostcode)
        assertEquals("W13", parsed.dropPostcode)
    }

    @Test
    fun parseRideInfo_electric_exclusive_fare_without_pound_sign() {
        val text = """
            Electric
            Exclusive
            11.91
            ★ 4.70
            5 min (0.7 mi)
            The Atlas, London, SW6 1RX
            39 mins (4.2 mi)
            Doppo, London, W1D 4PW
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(11.91, ride.price, 0.001)
        assertEquals(4.70, ride.rating, 0.001)
    }

    @Test
    fun parseRideInfo_uberx_fare_not_rating_when_no_pound() {
        val text = """
            UberX
            11.47
            ★ 4.75
            £0.93 est. holiday entitlement included
            4 min (0.5 mi)
            123 Old Brompton Road, London, SW7 3RP
            32 mins (3.7 mi)
            Onewelbeck, London, W1G 0AR
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(11.47, ride.price, 0.001)
    }

    @Test
    fun parseOcrMiles_fixes_8_8_to_3_8_directly() {
        assertEquals(3.8, parseOcrMiles("8.8", 39)!!, 0.001)
    }

    @Test
    fun tripLeg_fixes_8_8_miles_to_3_8_on_medium_trip() {
        val parsed = parseTripLegFromLine("39 mins (8.8 mi)")
        assertEquals(39, parsed?.minutes)
        assertEquals(3.8, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun tripLeg_keeps_0_7_miles_on_four_min_pickup() {
        val parsed = parseTripLegFromLine("4 min (0.7 mi)")
        assertEquals(4, parsed?.minutes)
        assertEquals(0.7, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun tripLeg_fixes_0_7_miles_to_0_2_on_short_pickup() {
        val parsed = parseTripLegFromLine("2 min (0.7 mi)")
        assertEquals(2, parsed?.minutes)
        assertEquals(0.2, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun tripLeg_keeps_0_7_miles_on_three_min_pickup() {
        val parsed = parseTripLegFromLine("3 min (0.7 mi)")
        assertEquals(3, parsed?.minutes)
        assertEquals(0.7, parsed?.miles ?: 0.0, 0.001)
    }

    @Test
    fun postcodes_n0_inward_4_is_n1_not_n15() {
        val pcs = extractOuterLondonPostcodes("Lady Mildmay, London, N0 4PR")
        assertTrue("Expected N1 from N0 4PR OCR, got $pcs", pcs.contains("N1"))
        assertTrue("Should not invent N15, got $pcs", !pcs.contains("N15"))
    }

    @Test
    fun postcodes_gu21_does_not_emit_gu2_substring() {
        val pcs = extractOuterLondonPostcodes("Kent PLC, Surrey, GU21 5BJ")
        assertTrue("Expected GU21, got $pcs", pcs.contains("GU21"))
        assertTrue("GU2 must not match inside GU21, got $pcs", !pcs.contains("GU2"))
    }

    @Test
    fun postcodes_ignore_station_abbrev_in_parentheses() {
        val pcs = extractOuterLondonPostcodes("Woking Railway Station (WOK), London, GU22 7AE")
        assertTrue("Expected GU22, got $pcs", pcs.contains("GU22"))
        assertTrue("Station code WOK must not be a postcode, got $pcs", !pcs.contains("WOK"))
    }

    @Test
    fun parseRideInfo_guildford_gu1_pickup_not_gu2_drop() {
        val text = """
            UberX
            Exclusive
            £11.25
            ★ 4.54
            6 min (2.2 mi)
            Ashuka Tandoori, Guildford, GU1 2RE
            15 mins (4.3 mi)
            Royal Surrey Hospital, Guildford, GU2 7XX
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("GU1", ride.pickup_address_postcode)
        assertEquals("GU2", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_gu4_pickup_not_wok_station_code() {
        val text = """
            Comfort
            Exclusive
            £16.87
            ★ 4.82
            6 min (1.9 mi)
            16 Baldwin Crescent, Guildford, GU4 7XW
            19 mins (6.8 mi)
            Woking Railway Station (WOK), London, GU22 7AE
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("GU4", ride.pickup_address_postcode)
        assertEquals("GU22", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_gu1_pickup_when_both_zones_share_guildford() {
        val text = """
            UberX
            Exclusive
            £6.21
            ★ 4.73
            1 min (0 mi)
            London Road, Guildford, GU1 1XS
            11 mins (3.5 mi)
            Royal Surrey Hospital, Guildford, GU2 7XX
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("GU1", ride.pickup_address_postcode)
        assertEquals("GU2", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_gu1_pickup_not_gu21_from_drop() {
        val text = """
            UberX
            £13.82
            ★ 4.90
            5 min (1.5 mi)
            Guildford Harbour Hotel, Guildford, GU1 3DA
            21 mins (7.0 mi)
            Kent PLC, Surrey, GU21 5BJ
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("GU1", ride.pickup_address_postcode)
        assertEquals("GU21", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_kt5_pickup_kt3_drop_truncated_inward() {
        val text = """
            UberX
            £9.04
            ★ 4.53
            9 min (2.0 mi)
            Tolworth Rise South, Surbiton, KT5 9
            9 mins (2.5 mi)
            Green Lane Nursery, New Malden, KT3
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("KT5", ride.pickup_address_postcode)
        assertEquals("KT3", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_gu1_when_drop_leg_ocr_before_pickup_address() {
        val lines = listOf(
            OcrLine("£11.25", 0),
            OcrLine("★ 4.54", 20),
            OcrLine("6 min (2.2 mi)", 40),
            OcrLine("15 mins (4.3 mi)", 60),
            OcrLine("Ashuka Tandoori, Guildford, GU1 2RE", 80),
            OcrLine("Royal Surrey Hospital, Guildford, GU2 7XX", 100),
        )
        val parsed = parseStructuredFromLines(lines, lines.joinToString("\n") { it.text })!!
        assertEquals("GU1", parsed.pickupPostcode)
        assertEquals("GU2", parsed.dropPostcode)
    }

    @Test
    fun postcodes_e1_waterloo_recovers_se1() {
        val pcs = extractOuterLondonPostcodes("The Sidings Waterloo, London, E1 7BH")
        assertTrue("Expected SE1 from E1 Waterloo OCR, got $pcs", pcs.contains("SE1"))
        assertTrue("E1 should not win over SE1 on Waterloo line, got $pcs", pcs.first() == "SE1")
    }

    @Test
    fun postcodes_e16_docklands_stays_e16_not_se16() {
        val pcs = extractOuterLondonPostcodes("Berwick Rd, London, E16 3DS")
        assertTrue("Expected E16, got $pcs", pcs.contains("E16"))
        assertTrue("Must not map Docklands E16 to SE16, got $pcs", !pcs.contains("SE16"))
    }

    @Test
    fun postcodes_al2_mawbey_house_recovers_se1() {
        val pcs = extractOuterLondonPostcodes("Mawbey House, London, AL2")
        assertTrue("Expected SE1 from AL2 OCR, got $pcs", pcs.contains("SE1"))
    }

    @Test
    fun parseRideInfo_waterloo_se1_drop_not_e1() {
        val text = """
            UberX Priority
            Exclusive
            £10.50
            ★ 4.90
            4 min (0.5 mi)
            London, SE16 4DG
            25 mins (3.1 mi)
            The Sidings Waterloo, London, SE1 7BH
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("SE16", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_abbeyfield_0_7_pickup_miles() {
        val text = """
            UberX Priority
            Exclusive
            £12.89
            ★ 4.60
            4 min (0.7 mi)
            Abbeyfield Road, London, SE16 2DX
            30 mins (7.3 mi)
            3 Alwold Cres, London, SE12 9AF
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(0.7, ride.pickup_distance_value!!, 0.001)
        assertEquals("SE16", ride.pickup_address_postcode)
        assertEquals("SE12", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_mawbey_se1_pickup_not_al2() {
        val text = """
            £14.07
            ★ 4.55
            7 min (1.4 mi)
            Mawbey House, London, SE1
            28 mins (7.2 mi)
            Berwick Rd, London, E16 3DS
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("SE1", ride.pickup_address_postcode)
        assertEquals("E16", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_se16_wc1e_not_identical_postcodes() {
        val text = """
            UberX
            Exclusive
            £14.75
            ★ 4.89
            3 min (0.4 mi)
            6 Argyle Way, London, SE16 3JQ
            44 mins (5.2 mi)
            Slade school of art, London, WC1E
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("SE16", ride.pickup_address_postcode)
        assertEquals("WC1E", ride.dropoff_address_postcode)
        assertEquals(3, ride.pickup_time_minutes)
        assertEquals(44, ride.trip_time_minutes)
        assertEquals(0.4, ride.pickup_distance_value!!, 0.001)
        assertEquals(5.2, ride.trip_distance_value!!, 0.001)
        assertNull(validateRideForScoring(ride))
    }

    @Test
    fun postcodes_al0_en3_6ed_recovers_en3() {
        val pcs = extractOuterLondonPostcodes("88 Newbury Avenue, Enfield, AL0 6ED")
        assertTrue("Expected EN3 from AL0 6ED Enfield OCR, got $pcs", pcs.contains("EN3"))
        assertTrue("AL0 must not remain, got $pcs", !pcs.contains("AL0"))
    }

    @Test
    fun postcodes_n0_enfield_4bh_recovers_en1() {
        val pcs = extractOuterLondonPostcodes("99 Canning Square, Enfield, N0 4BH")
        assertTrue("Expected EN1 from N0 4BH Enfield OCR, got $pcs", pcs.contains("EN1"))
    }

    @Test
    fun postcodes_al0_enfield_4nf_recovers_en1() {
        val pcs = extractOuterLondonPostcodes("110 Worcesters Avenue, Enfield, AL0 4NF")
        assertTrue("Expected EN1 from AL0 4NF Enfield OCR, got $pcs", pcs.contains("EN1"))
    }

    @Test
    fun postcodes_n1r_8ed_recovers_n17() {
        val pcs = extractOuterLondonPostcodes("13 Durban Road, London, N1R 8ED")
        assertTrue("Expected N17 from N1R 8ED OCR, got $pcs", pcs.contains("N17"))
    }

    @Test
    fun postcodes_waltham_cross_n0_recovers_en8() {
        val pcs = extractOuterLondonPostcodes("77 Eleanor Road, Waltham Cross, N0")
        assertTrue("Expected EN8 from N0 Waltham Cross OCR, got $pcs", pcs.contains("EN8"))
    }

    @Test
    fun parseRideInfo_en3_pickup_nw1_kings_cross_drop() {
        val text = """
            £35.71
            4.78
            4 min (0.6 mi)
            King's Cross Station Bus Stop, Camden Town, NW1 2
            36 mins (11.3 mi)
            10 Marrilyne Avenue, Enfield, EN3 6EG
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("NW1", ride.pickup_address_postcode)
        assertEquals("EN3", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_en3_pickup_en8_drop_enfield_cards() {
        val text = """
            UberX
            Exclusive
            £5.88
            ★ 5.00
            9 min (2.5 mi)
            88 Newbury Avenue, Enfield, AL0 6ED
            6 mins (1.3 mi)
            78 amazon dig1, London, EN3 7RL
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("EN3", ride.pickup_address_postcode)
        assertEquals("EN3", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_baker_street_en1_drop_not_n0() {
        val text = """
            Electric
            £5.41
            ★ 4.70
            10 min (2.7 mi)
            304 Baker Street, Enfield, EN1 3BD
            5 mins (1.2 mi)
            99 Canning Square, Enfield, N0 4BH
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("EN1", ride.pickup_address_postcode)
        assertEquals("EN1", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_durban_n17_pickup() {
        val text = """
            £6.29
            ★ 4.29
            5 min (1.4 mi)
            13 Durban Road, London, N1R 8ED
            13 mins (2.9 mi)
            Gorleston Rd, London, N15 5QR
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("N17", ride.pickup_address_postcode)
        assertEquals("N15", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_forty_hall_en8_drop() {
        val text = """
            UberX
            £6.89
            ★ 5.00
            7 min (1.4 mi)
            Forty Hall & Estate, Enfield, EN2 9HA
            11 mins (3.0 mi)
            77 Eleanor Road, Waltham Cross, N0
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("EN2", ride.pickup_address_postcode)
        assertEquals("EN8", ride.dropoff_address_postcode)
    }

    @Test
    fun fare_ignores_misread_priority_addon_22_55() {
        val lines = listOf(
            OcrLine("UberX Priority", 0),
            OcrLine("Exclusive", 10),
            OcrLine("£17.91", 20),
            OcrLine("★ 4.74", 40),
            OcrLine("£1.30 est. holiday entitlement included", 50),
            OcrLine("+£22.55 included for priority", 60),
            OcrLine("10 min (1.8 mi)", 90),
        )
        val full = lines.joinToString("\n") { it.text }
        val fare = pickFareFromHeaderLines(lines, full)
        assertEquals(17.91, fare!!, 0.001)
        assertEquals(17.91, extractBestPrice(full), 0.001)
    }

    @Test
    fun parseOcrMiles_fixes_1_1_to_11_for_24_min_trip() {
        assertEquals(11.0, parseOcrMiles("1.1", 24)!!, 0.001)
    }

    @Test
    fun parseRideInfo_gerrards_cross_11_8_mile_drop() {
        val text = """
            UberX Exclusive
            £14.28
            ★ 4.66
            9 min (4.9 mi)
            45 Station Road, Gerrards Cross, SL9 8ER
            24 mins (1.1 mi)
            Lamar Café, Greenford, UB6 7JD
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(24, ride.trip_time_minutes)
        assertEquals(11.0, ride.trip_distance_value!!, 0.5)
    }

    @Test
    fun parseStructuredFromLines_ub6_ha2_by_visual_top_when_lines_reordered() {
        val lines = listOf(
            OcrLine("UberX", 0),
            OcrLine("£5.48", 50),
            OcrLine("4.60", 70),
            OcrLine("10 min (3.0 mi)", 100),
            OcrLine("10 mins (2.3 mi)", 200),
            OcrLine("2 Park Drive, Harrow, HA2 7LT", 350),
            OcrLine("621 Whitton Avenue West, Greenford, UB6 0DZ", 250),
        )
        val full = lines.joinToString("\n") { it.text }
        val structured = parseStructuredFromLines(lines, full)
        assertEquals("UB6", structured?.pickupPostcode)
        assertEquals("HA2", structured?.dropPostcode)
    }

    @Test
    fun parseRideInfo_whitton_ub6_pickup_harrow_ha2_drop() {
        val text = """
            UberX
            £5.48
            4.60
            10 min (3.0 mi)
            621 Whitton Avenue West, Greenford, UB6 0DZ
            10 mins (2.3 mi)
            2 Park Drive, Harrow, HA2 7LT
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("UB6", ride.pickup_address_postcode)
        assertEquals("HA2", ride.dropoff_address_postcode)
    }

    @Test
    fun postcodes_recover_ub1_from_n0_southall() {
        val pcs = extractOuterLondonPostcodes("Jalebi Junction, Southall, N0 1LN")
        assertTrue("Expected UB1 from N0 OCR on Southall line, got $pcs", pcs.contains("UB1"))
        assertTrue("Must not emit N0, got $pcs", !pcs.contains("N0"))
    }

    @Test
    fun parseRideInfo_southall_ub1_drop_not_n0() {
        val text = """
            UberX
            £4.63
            ★ 4.86
            9 min (1.8 mi)
            11 Rose Gardens, Southall, UB1 1XL
            8 mins (1.6 mi)
            Jalebi Junction, Southall, N0 1LN
            Match
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals("UB1", ride.pickup_address_postcode)
        assertEquals("UB1", ride.dropoff_address_postcode)
    }

    @Test
    fun parseStructuredFromLines_kings_langley_no_drop_postcode() {
        val lines = listOf(
            OcrLine("UberX Priority", 0),
            OcrLine("Exclusive", 20),
            OcrLine("£7.79", 40),
            OcrLine("4.88 Verified", 60),
            OcrLine("+£1.16 included for priority", 80),
            OcrLine("7 min (2.7 mi)", 120),
            OcrLine("Kings Langley Railway Station (KGL), London, WD4 8LF", 160),
            OcrLine("10 mins (3.1 mi)", 200),
            OcrLine("Confirm", 240),
        )
        val full = lines.joinToString("\n") { it.text }
        val structured = parseStructuredFromLines(lines, full)
        assertEquals(7.79, structured?.price!!, 0.001)
        assertEquals("WD4", structured.pickupPostcode)
        assertTrue(structured.dropPostcode.isBlank())
    }

    @Test
    fun parseRideInfo_kings_langley_wd4_without_visible_drop_address() {
        val text = """
            UberX Priority
            Exclusive
            £7.79
            4.88 Verified
            £0.56 est. holiday entitlement included
            +£1.16 included for priority
            7 min (2.7 mi)
            Kings Langley Railway Station (KGL), London, WD4 8LF
            10 mins (3.1 mi)
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(7.79, ride.price, 0.001)
        assertEquals(7, ride.pickup_time_minutes)
        assertEquals(10, ride.trip_time_minutes)    
        assertEquals(2.7, ride.pickup_distance_value!!, 0.001)
        assertEquals(3.1, ride.trip_distance_value!!, 0.001)
        assertEquals("WD4", ride.pickup_address_postcode)
        assertTrue(
            ride.dropoff_address_postcode.isNullOrBlank(),
        )
        assertTrue(
            validateRideForScoring(ride, text)?.contains("drop-off postcode") == true ||
                validateRideForScoring(ride, text)?.contains("drop-off address not on screen") == true,
        )
    }

    @Test
    fun postcodes_keep_real_wd25_on_full_address() {
        val pcs = extractOuterLondonPostcodes("12 High Street, Watford, WD25 7XX")
        assertTrue("Expected WD25 on real address, got $pcs", pcs.contains("WD25"))
    }

    @Test
    fun postcodes_ignore_m25_motorway_not_wd25() {
        assertTrue(
            "M25 must not become a postcode, got ${extractOuterLondonPostcodes("M25")}",
            extractOuterLondonPostcodes("M25").isEmpty(),
        )
        val fromMisread = extractOuterLondonPostcodes("WD25")
        val fromMap = extractOuterLondonPostcodes("""
            10 mins (3.1 mi)
            M25
            Confirm
        """.trimIndent())
        assertTrue(
            "WD25 must not be parsed from motorway/map labels, misread=$fromMisread map=$fromMap",
            !fromMisread.contains("WD25") && !fromMap.contains("WD25"),
        )
    }

    @Test
    fun parseFareFromLine_handles_euro_and_e_misread_as_pound() {
        assertEquals(9.84, parseFareFromLine("E9.84")!!, 0.001)
        assertEquals(8.94, parseFareFromLine("€8.94")!!, 0.001)
    }

    @Test
    fun parseRideInfo_comfort_exclusive_marble_arch_fare() {
        val text = """
            Comfort
            Exclusive
            £9.84
            4.75
            £0.87 est. holiday entitlement included
            2 min (0.2 mi)
            London Marriott Hotel Marble Arch, London, W1H 5DN
            13 mins (1.5 mi)
            Five Guys Burgers and Fries
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(9.84, ride.price, 0.001)
        assertEquals(4.75, ride.rating, 0.01)
    }

    @Test
    fun parseRideInfo_uberx_cash_payment_fare() {
        val text = """
            UberX
            Exclusive
            £8.94
            Cash payment
            5.00
            £0.75 est. holiday entitlement included
            2 min (0.2 mi)
            London Marriott Hotel Marble Arch, London, W1H 5DN
            15 mins (2.0 mi)
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(8.94, ride.price, 0.001)
    }

    @Test
    fun parseRideInfo_comfort_6_94_fare_without_pound_prefix() {
        val text = """
            Comfort
            Exclusive
            6.94
            4.73
            1 min (0.1 mi)
            Duke of Kendal, London, W2 2AF
            14 mins (2.0 mi)
            Jumeirah Carlton Tower, London, SW1X 9PY
            Confirm
        """.trimIndent()
        val ride = parseRideInfo(text, null)
        assertEquals(6.94, ride.price, 0.001)
    }

    @Test
    fun filterLinesToOfferCardZone_keeps_fare_above_trip_legs() {
        val lines = listOf(
            OcrLine("Comfort", 40, 100),
            OcrLine("Exclusive", 45, 200),
            OcrLine("£9.84", 50, 100),
            OcrLine("4.75", 90, 100),
            OcrLine("2 min (0.2 mi)", 300, 100),
            OcrLine("London Marriott Hotel Marble Arch, London, W1H 5DN", 330, 100),
            OcrLine("13 mins (1.5 mi)", 400, 100),
            OcrLine("Confirm", 500, 100),
        )
        val filtered = filterLinesToOfferCardZone(lines)
        assertTrue(filtered.any { it.text.contains("£9.84") })
    }

    @Test
    fun parseRideInfo_scheduled_trip_drop_before_last_leg_bromley_se20() {
        // Reserved/scheduled layout: clock times, drop address ABOVE the final "N mins (X mi)" line.
        val text = """
            2 UberX
            £715
            * 4.72
            Yesterday
            14:42
            3 min (o.4 mi)
            £0.51 est. holiday entitlement included
            25A East St, Bromley, BR1 1QE
            15:38
            Specsavers, London, SE20 7PF
            13 mins (4.0 mi)
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(7.15, ride.price, 0.001)
        assertEquals("BR1", ride.pickup_address_postcode)
        assertEquals("SE20", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_uberx_clapham_fare_l_as_one_in_price() {
        val text = """
            UberX
            £1l.98
            * 4.65 O Verified
            Claphamcommon A8
            1l min (1.5 mi)
            £0.95 est. holiday entitlement included
            Travelodge London Clapham Junction, London, SW1l 2PD
            28 mins (4.1 mi)
            Larry's cut unisex salon, London, SE5 0EN
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(11.98, ride.price, 0.001)
        assertEquals("SW11", ride.pickup_address_postcode)
        assertEquals("SE5", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_electric_alma_rd_no_pickup_postcode_on_card() {
        val text = """
            Electric
            £16.18
            * 4.57
            5 min (0.8 mi)
            Alma Rd, London
            32 mins (5.4 mi)
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(16.18, ride.price, 0.001)
        assertTrue(ride.pickup_address_postcode.isNullOrBlank())
        val err = validateRideBeforeScoring(ride, text)
        assertTrue(err == null || !err.contains("pickup postcode"))
    }

    @Test
    fun validateRideForScoring_allows_missing_pickup_pc_when_street_only_on_card() {
        val ocr = """
            Electric
            £16.18
            5 min (0.8 mi)
            Alma Rd, London
            32 mins (5.4 mi)
            Match
        """.trimIndent()
        val ride = RideRequest(
            id = 0,
            price = 16.18,
            rating = 4.57,
            pickup_time_minutes = 5,
            pickup_distance_value = 0.8,
            pickup_address_postcode = "",
            trip_time_minutes = 32,
            trip_distance_value = 5.4,
            dropoff_address_postcode = "SE5",
            start_time_window = "",
            end_time_window = "",
            acceptedOrRejected = 0,
            type = "match",
        )
        assertNull(validateRideForScoring(ride, ocr))
    }

    @Test
    fun parseRideInfo_noisy_electric_alma_rd_client_capture() {
        val text = """
            14shek
            Electric
            A
            * 4.57
            Mertor
            £16.18 9
            5 min (O.8 mi)
            TCHMSGP.€0
            Alma Rd. London
            32 mins (5.4 mi)
            £1.38 est. holiday entitlement included
            Clapham common
            IGGE'S MARSH
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(16.18, ride.price, 0.001)
        assertTrue(ride.pickup_address_postcode.isNullOrBlank())
        val err = validateRideBeforeScoring(ride, text)
        assertTrue(
            "Should not block on missing pickup postcode, got: $err",
            err == null || !err.contains("pickup postcode"),
        )
    }

    @Test
    fun parseRideInfo_noisy_uberx_clapham_client_capture() {
        val text = """
            KeisiñeerE
            LATC
            UberX
            £1l.98
            * 4.65 O Verified
            Claphamcommon A8
            1l min (1.5 mi)
            £0.95 est. holiday entitlement included
            Travelodge London Clapham
            London
            Junction. London. SW1l 2PD
            28 mins (4.1 mi)
            OEN
            A232
            Lambeth
            Larry's cut unisex salon. London. SE5
            Match
            Cambe
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(11.98, ride.price, 0.001)
        assertEquals("SW11", ride.pickup_address_postcode)
        assertEquals("SE5", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_noisy_capture_bromley_se20_mangled_postcode() {
        // Real on-device OCR: overlay/WhatsApp noise, "BRI1QE" (BR1 1QE mangled), periods for commas.
        val text = """
            00:39 6
            et +48 609 717 213
            Reading offer. (1/2)
            8. Wpisz ten kod
            9. Na koncu kliknij Start
            00:36
            2 UberX
            £715
            Today
            . 4.72
            Yesterday
            14:42
            3 min (o.4 mi)
            15:38
            6:20
            £0.51 est. holiday entitlement included
            25A East St. Bromley. BRI1QE
            Specsavers. London. SE20 7PF
            13 mins (4.0 mi)
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(7.15, ride.price, 0.001)
        assertEquals("BR1", ride.pickup_address_postcode)
        assertEquals("SE20", ride.dropoff_address_postcode)
    }

    @Test
    fun parseRideInfo_client_ivy_soho_w1f_w2() {
        val text = """
            2 UberX
            10.87
            Exclusive
            8JB
            4 min (0.3 mi)
            £0.92 est. holiday entitlement included
            The Ivy Soho Brasserie. London. WF
            15 mins (2.1 mi)
            155 Sussex Gardens. London. w2 2RU
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(10.87, ride.price, 0.001)
        assertEquals("W1F", ride.pickup_address_postcode)
        assertEquals("W2", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_client_courthouse_w1f_w14() {
        val text = """
            2 UberX Exclusive
            £18.80
            4.68 Verified
            5 min (0.3 mi)
            Courthouse Hotel. London. WIF 7HL
            32 mins (5.0 mi)
            10 Fielding Road. London. WI4 OLL
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(18.80, ride.price, 0.001)
        assertEquals("W1F", ride.pickup_address_postcode)
        assertEquals("W14", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_client_langham_w1w_n1() {
        val text = """
            2 UberX
            £16.17
            4.59
            3 min (0.2 mi)
            Verified
            London. Wiw 6BU
            Grange Langham Court Hotel.
            29 mins (4.5 mi)
            London. N1 4SD
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(16.17, ride.price, 0.001)
        assertEquals("W1W", ride.pickup_address_postcode)
        assertEquals("N1", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_client_selfridges_w1u_se1() {
        val text = """
            Electric
            £11.76
            4.38
            5 min (0.7 mi)
            lLG
            Selfridges. London. Duke Street. WIU
            19 mins (3.1 mi)
            SEl 7LS
            Novotel London Waterloo. London.
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(11.76, ride.price, 0.001)
        assertEquals("W1U", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_client_wellington_w1h_w1d() {
        val text = """
            2 Comfort Exclusive
            £16.28
            4.83
            2 min (0.2 mi)
            Duke of Wellington. London. WIH 2HQ
            22 mins (2.1 mi)
            Archer Street. London. WD 7AP
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(16.28, ride.price, 0.001)
        assertEquals("W1H", ride.pickup_address_postcode)
        assertEquals("W1D", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_client_bombay_palace_barnet_legs() {
        val text = """
            2 Comfort
            £26.79
            3min O2 m)
            n 44mins (10.9 mi)
            Bombay Palace, London, W2 2AA
            Eversleigh Rd, Barnet, EN5 1ND
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(26.79, ride.price, 0.001)
        assertEquals(3, ride.pickup_time_minutes)
        assertEquals(44, ride.trip_time_minutes)
        assertEquals("W2", ride.pickup_address_postcode)
        assertEquals("EN5", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_client_electric_keats_enfield_orphan_4sf() {
        val text = """
            2 Electric
            £9.17
            4.74 O Verified
            £0.59 est. holiday entitlement included
            14 min (3.5 mi)
            ENDLEBURY
            Swaythling Cl. London. N18 20G
            10 mins (3.2 mi)
            Keats Cl. Enfield.
            MARSHES
            Match
            4SF
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(9.17, ride.price, 0.001)
        assertEquals("N18", ride.pickup_address_postcode)
        assertEquals("EN3", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_client_comfort_garbled_legs_ig8_e1_missing_from_ocr() {
        // Map noise — addresses often missing; legs must still parse so later frames can score.
        val text = """
            2 Comfort
            £22.54
            4.87
            6min l6 m)
            29 mins (94 mi)
            £178 est holiday entitlementincluded
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(22.54, ride.price, 0.001)
        assertEquals(6, ride.pickup_time_minutes)
        assertEquals(29, ride.trip_time_minutes)
        assertEquals(1.6, ride.pickup_distance_value!!, 0.05)
        assertEquals(9.4, ride.trip_distance_value!!, 0.05)
    }

    @Test
    fun postcodes_extract_n17_9fq_and_n17_ord() {
        val pcs = extractOuterLondonPostcodes(
            "North Lodge. Tottenham. N17 9FQ\nLondon. N17 ORD",
        )
        assertTrue("Expected N17, got $pcs", pcs.contains("N17"))
        assertTrue("Expected N17 twice or once, got $pcs", pcs.isNotEmpty())
    }

    @Test
    fun parseRideInfo_client_electric_n17_same_district_drop_ord() {
        val text = """
            2 Electric
            £14.22
            * 4.73
            24 min (5.3 mi)
            £1.06 est. holiday entitlement included
            North Lodge. Tottenham. N17 9FQ
            12 mins (2.7 mi)
            1-2 min
            London. N17 ORD
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(14.22, ride.price, 0.001)
        assertEquals("N17", ride.pickup_address_postcode)
        assertEquals("N17", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseRideInfo_kings_langley_drop_not_wd25_from_map_m25() {
        val text = """
            UberX Priority
            Exclusive
            £7.79
            4.88 Verified
            7 min (2.7 mi)
            Kings Langley Railway Station (KGL), London, WD4 8LF
            10 mins (3.1 mi)
            M25
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("WD4", ride.pickup_address_postcode)
        assertTrue(
            "Drop must not be WD25 from M25 map label, got ${ride.dropoff_address_postcode}",
            ride.dropoff_address_postcode.isNullOrBlank() || ride.dropoff_address_postcode != "WD25",
        )
    }
}
