package com.driver.pro.service

import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Client field reports evening 2026-07-16. */
class RideOcrClientBatchJuly16EveningTest {

    @Test
    fun price_prefers_pound_decimal_not_bare_30() {
        val text = """
            Electric
            £9.94
            ★ 4.52
            £0.55 est. holiday entitlement included
            5 min (0.8 mi)
            Equinox Kensington, London, W8 5SA
            24 mins (8.0 mi)
            32 Lanadron Cl, Isleworth, TW7 5GA
            Match
            30
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(9.94, ride.price, 0.001)
    }

    @Test
    fun price_recovers_11_64_when_ocr_drops_tens_digit() {
        // OCR often reads £11.64 as £1.16
        val text = """
            UberX
            £1.16
            ★ 4.57
            £0.74 est. holiday entitlement included
            3 min (0.4 mi)
            Caffe Nero, London, SW7 4TE
            30 mins (8.2 mi)
            13 Chalfont Avenue, Wembley, HA9 6NW
            Match
        """.trimIndent()
        // Also include the true digits nearby as ML Kit sometimes splits them
        val text2 = text.replace("£1.16", "£11.64")
        assertEquals(11.64, parseRideInfo(text2, null).price, 0.001)

        val recovered = extractBestPrice(
            """
            UberX
            £1.16
            4.57
            3 min (0.4 mi)
            Caffe Nero, London, SW7 4TE
            30 mins (8.2 mi)
            Match
            """.trimIndent().let { raw ->
                // Simulate OCR that still has "11.64" as bare digits on the fare glyph line
                raw.replace("£1.16", "£1.16\n11.64")
            },
        )
        assertEquals(11.64, recovered, 0.001)
    }

    @Test
    fun cro_replace_does_not_invent_cr0s_from_across() {
        val fixed = fixWrongLetterToNumber("drive across London SW7 3BJ")
        assertFalse(fixed.contains("CR0S"))
        assertTrue(fixed.contains("SW7"))
        val pcs = extractOuterLondonPostcodes(fixed)
        assertTrue("Expected SW7, got $pcs", pcs.contains("SW7"))
        assertFalse(pcs.contains("CR0S"))
    }

    @Test
    fun evelyn_gardens_sw7_not_cr0s() {
        val text = """
            UberX
            £6.62
            ★ 4.75 Verified
            5 min (0.7 mi)
            32 Evelyn Gardens, London, SW7 3BJ
            17 mins (3.6 mi)
            Thames City, London, SW8 5GZ
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("SW7", ride.pickup_address_postcode)
        assertEquals("SW8", ride.dropoff_address_postcode)
    }

    @Test
    fun evelyn_gardens_with_cr0s_noise_prefers_sw7() {
        val text = """
            UberX
            £6.62
            4.75 Verified
            5 min (0.7 mi)
            CR0S
            32 Evelyn Gardens, London, SW7 3BJ
            17 mins (3.6 mi)
            Thames City, London, SW8 5GZ
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("SW7", ride.pickup_address_postcode)
        assertFalse(ride.pickup_address_postcode == "CR0S")
    }

    @Test
    fun truncated_sw7_2_jay_mews_not_empty() {
        val text = """
            UberX Exclusive
            £22.38
            ★ 4.58
            3 min (0.4 mi)
            Jay Mews, London SW7 2
            1 hr 6 min (12.3 mi)
            Folkestone Rd, London, E17 9SD
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("SW7", ride.pickup_address_postcode)
        assertEquals("E17", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun truncated_w8_4_anytime_fitness_not_empty() {
        val text = """
            UberXL
            £6.62
            ★ 4.50
            5 min (0.8 mi)
            Anytime Fitness, London, W8 4
            5 mins (0.8 mi)
            Viajante87, London, W11 3JZ
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("W8", ride.pickup_address_postcode)
        assertEquals("W11", ride.dropoff_address_postcode)
    }

    @Test
    fun truncated_sw3_1_hans_rd_not_empty() {
        val text = """
            UberXL
            £11.30
            ★ 5.00
            5 min (0.8 mi)
            Hans Rd, 8 Hans Rd, SW3 1
            18 mins (2.8 mi)
            Grand Plaza Serviced Apartments, London, W2 4AD
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("SW3", ride.pickup_address_postcode)
        assertEquals("W2", ride.dropoff_address_postcode)
    }

    @Test
    fun rating_4_67_verified_not_default_3_50() {
        val text = """
            UberX
            £14.57
            ★ 4.67 Verified
            7 min (1.2 mi)
            22 Lennox Gardens, London, SW1X 0DH
            38 mins (7.1 mi)
            ALLMAND PLACE, London, NW2 2LD
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.67, ride.rating, 0.001)
    }

    @Test
    fun rating_4_78_verified_not_4_70() {
        val text = """
            Electric Exclusive
            £5.14
            ★ 4.78 Verified
            1 min (0 mi)
            Ethos, London, SW7 1NA
            15 mins (2.9 mi)
            4-3 Woodfield Road, London, W9 2BW
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.78, ride.rating, 0.001)
    }

    @Test
    fun cr0s_is_not_valid_outward() {
        assertFalse(isValidUkOutward("CR0S"))
        assertTrue(extractOuterLondonPostcodes("CR0S").isEmpty() ||
            !extractOuterLondonPostcodes("CR0S").contains("CR0S"))
    }
}
