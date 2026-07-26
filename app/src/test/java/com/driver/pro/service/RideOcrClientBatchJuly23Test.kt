package com.driver.pro.service

import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Client field reports 2026-07-23. */
class RideOcrClientBatchJuly23Test {

    @Test
    fun se10_not_e10_on_naval_college() {
        val text = """
            Comfort Exclusive
            £11.65
            ★ 4.72
            £0.87 est. holiday entitlement included
            9 min (1.5 mi)
            36 Fairlawn Park, London, SE26 5RU
            24 mins (5.0 mi)
            Old Royal Naval College, London, E10 9NN
            Confirm
        """.trimIndent()
        // OCR dropped the leading S on SE10
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("SE26", ride.pickup_address_postcode)
        assertEquals("SE10", ride.dropoff_address_postcode)
    }

    @Test
    fun se10_preferred_when_both_e10_and_se10_inferred() {
        val pcs = extractOuterLondonPostcodes("Old Royal Naval College, London, E10 9NN")
        assertTrue("Expected SE10, got $pcs", pcs.contains("SE10"))
        assertTrue("E10 must not win over SE10, got $pcs", !pcs.contains("E10") || pcs.first() == "SE10")
    }

    @Test
    fun rating_4_71_not_4_70() {
        val text = """
            Exec
            £28.13
            ★ 4.71
            £2.22 est. holiday entitlement included
            13 min (2.7 mi)
            58B Blythe Vale, London, SE6 4NR
            54 mins (9.2 mi)
            Gilray House, London, EC1V 2NL
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.71, ride.rating, 0.001)
    }

    @Test
    fun pickup_1_2_not_2_0_when_ocr_has_1_2() {
        val text = """
            Comfort Exclusive
            £8
            ★ 4.75
            £0.59 est. holiday entitlement included
            7 min (2.0 mi)
            1.2 mi
            Bob Wines, London, SE19 2AS
            15 mins (3.4 mi)
            William Rose Butchers, London, SE22 8HD
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(1.2, ride.pickup_distance_value!!, 0.001)
    }

    @Test
    fun exclusive_comma_decimal_1_2_mi() {
        val leg = parseTripLegFromLine("7 min (1,2 mi)")
        assertEquals(7, leg!!.minutes)
        assertEquals(1.2, leg.miles, 0.001)

        val text = """
            Comfort Exclusive
            £28.29
            ★ 4.79 Verified
            £1.58 est. holiday entitlement included
            7 min (1,2 mi)
            2 Lawrie Park Crescent, London, SE26 6HD
            59 mins (23.4 mi)
            easyJet, Crawley, RH6 0NP
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(1.2, ride.pickup_distance_value!!, 0.001)
        assertEquals("SE26", ride.pickup_address_postcode)
        assertEquals("RH6", ride.dropoff_address_postcode)
    }

    @Test
    fun match_card_se26_rh6_1_2_mi() {
        val text = """
            Comfort
            £28.29
            ★ 4.79 Verified
            £1.58 est. holiday entitlement included
            7 min (1.2 mi)
            2 Lawrie Park Crescent, London, SE26 6HD
            59 mins (23.4 mi)
            easyJet, Crawley, RH6 0NP
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(1.2, ride.pickup_distance_value!!, 0.001)
        assertEquals(23.4, ride.trip_distance_value!!, 0.001)
        assertEquals("SE26", ride.pickup_address_postcode)
        assertEquals("RH6", ride.dropoff_address_postcode)
    }

    @Test
    fun garbled_pickup_miles_still_keeps_trip_distance() {
        val text = """
            Comfort
            £7.82
            ★ 4.83 Verified
            £0.57 est. holiday entitlement included
            6 min (lỘ mi)
            30 Cortland House. London. SE20 8
            15 mins (3.6 mi)
            Dental Beauty - Dulwich. London. SE22 8SW
            Match
            GIPSY HILL
            LOWER SYDENHAM
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(fixWrongLetterToNumber(text), null))
        assertEquals(15, ride.trip_time_minutes)
        assertEquals(3.6, ride.trip_distance_value!!, 0.001)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun br3_pickup_cr0_drop_not_swapped() {
        val text = """
            Exec Exclusive
            £15.13
            ★ 4.78
            £1.27 est. holiday entitlement included
            11 min (2.2 mi)
            65 Ernest Grove, Beckenham, BR3 3HY
            16 mins (3.9 mi)
            East Croydon Railway Station, Croydon, CR0 1LF
            Confirm
            CR0
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("BR3", ride.pickup_address_postcode)
        assertEquals("CR0", ride.dropoff_address_postcode)
    }

    @Test
    fun br3_cr0_document_order_when_both_addresses_after_legs() {
        // Both addresses below the drop leg in OCR; card Y order matches pickup-then-drop.
        val lines = listOf(
            OcrLine("Exec Exclusive", 0),
            OcrLine("£15.13", 50),
            OcrLine("★ 4.78", 70),
            OcrLine("11 min (2.2 mi)", 100),
            OcrLine("16 mins (3.9 mi)", 120),
            OcrLine("65 Ernest Grove, Beckenham, BR3 3HY", 250),
            OcrLine("East Croydon Railway Station, Croydon, CR0 1LF", 400),
        )
        val full = lines.joinToString("\n") { it.text }
        val structured = parseStructuredFromLines(lines, full)
        assertEquals("BR3", structured?.pickupPostcode)
        assertEquals("CR0", structured?.dropPostcode)
    }

    @Test
    fun br3_pickup_not_stolen_by_map_cr0_between_legs() {
        val text = """
            Exec Exclusive
            £15.13
            ★ 4.78
            11 min (2.2 mi)
            CR0
            16 mins (3.9 mi)
            65 Ernest Grove, Beckenham, BR3 3HY
            East Croydon Railway Station, Croydon, CR0 1LF
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("BR3", ride.pickup_address_postcode)
        assertEquals("CR0", ride.dropoff_address_postcode)
    }
}
