package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 8 Oct 2026: 12 min (1.5 mi) OCR'd as (5 mi). */
class Oct8MilesBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun parseOcrMiles_1_5_not_5_on_12_min() {
        assertEquals(1.5, parseOcrMiles("5", 12)!!, 0.001)
        assertEquals(1.5, parseOcrMiles("5.0", 12)!!, 0.001)
        assertEquals(1.5, parseTripLegFromLine("12 mins (5 mi)")?.miles ?: 0.0, 0.001)
        assertEquals(5.2, parseOcrMiles("5.2", 44)!!, 0.001)
        assertEquals(5.0, parseOcrMiles("5", 20)!!, 0.001)
    }

    @Test
    fun oakley_gardens_1_5_mi_trip_not_5() {
        val text = """
            A30E
            2 Electric
            KING'S ROAD
            £4.69
            4.72
            Exclusive
            Verified
            £0.39 est. holiday entitlement included
            3 min (0.5 mi)
            X
            28 Oakley Gardens. London. Sw3 5QG
            12 mins (5 mi)
            Rutland Gardens. London. SW7 1BX
            Confirm
            N
            CSnham Comm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(4.69, ride.price, 0.05)
        assertEquals(3, ride.pickup_time_minutes)
        assertEquals(0.5, ride.pickup_distance_value!!, 0.05)
        assertEquals(12, ride.trip_time_minutes)
        assertEquals(1.5, ride.trip_distance_value!!, 0.05)
        assertEquals("SW3", ride.pickup_address_postcode)
        assertEquals("SW7", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseOcrMiles_1_2_not_12_on_15_min() {
        assertEquals(1.2, parseOcrMiles("12", 15)!!, 0.001)
        assertEquals(1.2, parseOcrMiles("L2", 15)!!, 0.001)
        assertEquals(1.2, parseTripLegFromLine("15 mins (L2 mi)")?.miles ?: 0.0, 0.001)
        assertEquals(11.2, parseOcrMiles("11.2", 65)!!, 0.001)
        assertEquals(11.0, parseOcrMiles("1.1", 24)!!, 0.001)
    }

    @Test
    fun lilibet_1_2_mi_trip_not_12() {
        val text = """
            12111
            tor
            2 Electric
            Trip Radar
            £5.34
            4.78
            3 min (0.3 mi)
            £0.38 est. holiday entitlement included
            Lilibet S. London. WIJ 6QB
            15 mins (L2 mi)
            Odeon. London. WC2H 7LQ
            50 Kalò Di Ciro
            Salvo
            Match
            X
        """.trimIndent()
        val ride = parse(text)
        assertEquals(5.34, ride.price, 0.05)
        assertEquals(3, ride.pickup_time_minutes)
        assertEquals(0.3, ride.pickup_distance_value!!, 0.05)
        assertEquals(15, ride.trip_time_minutes)
        assertEquals(1.2, ride.trip_distance_value!!, 0.05)
        assertEquals("W1J", ride.pickup_address_postcode)
        assertEquals("WC2H", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseOcrMiles_1_9_not_19_on_19_min() {
        assertEquals(1.9, parseOcrMiles("19", 19)!!, 0.001)
        assertEquals(1.9, parseOcrMiles("19", 25)!!, 0.001)
        assertEquals(1.9, parseTripLegFromLine("19 mins (19 mi)")?.miles ?: 0.0, 0.001)
        assertEquals(1.9, parseTripLegFromLine("25 mins (19 mi)")?.miles ?: 0.0, 0.001)
        assertEquals(11.0, parseOcrMiles("1.1", 24)!!, 0.001)
        assertEquals(2.4, parseOcrMiles("2.4", 25)!!, 0.001)
    }

    @Test
    fun valiant_house_1_9_mi_trip_not_19() {
        val text = """
            A304
            ed
            2 UberX
            £7.39
            4.70
            Verified
            6 min (0.6 mi)
            QUEENSwr
            £0.65 est. holiday entitlement included
            South Kensington Underground
            Metro Station. London. SW7 2NB
            19 mins (19 mi)
            Valiant House. London. SWIl 3LU
            Match
            X
        """.trimIndent()
        val ride = parse(text)
        assertEquals(7.39, ride.price, 0.05)
        assertEquals(6, ride.pickup_time_minutes)
        assertEquals(0.6, ride.pickup_distance_value!!, 0.05)
        assertEquals(19, ride.trip_time_minutes)
        assertEquals(1.9, ride.trip_distance_value!!, 0.05)
        assertEquals("SW7", ride.pickup_address_postcode)
        assertEquals("SW11", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun hari_sw1x_theatre_sw1y_1_9_not_19() {
        assertTrue(normalizeOcrOfferText("SsWIY").contains("SW1Y"))
        assertTrue(extractOuterLondonPostcodes("His Majesty's Theatre. London. SsWIY\n4QL").contains("SW1Y"))
        val text = """
            sTR
            2 Comfort Exclusive
            £10.31
            4.90
            £0.89 est. holiday entitlement included
            l min (0.l mi)
            The Hari. London. SWIX 8HQ
            25 mins (19 mi)
            X
            His Majesty's Theatre. London. SsWIY
            4QL
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(10.31, ride.price, 0.05)
        assertEquals(1, ride.pickup_time_minutes)
        assertEquals(0.1, ride.pickup_distance_value!!, 0.05)
        assertEquals(25, ride.trip_time_minutes)
        assertEquals(1.9, ride.trip_distance_value!!, 0.05)
        assertEquals("SW1X", ride.pickup_address_postcode)
        assertEquals("SW1Y", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseOcrMiles_1_1_not_11_on_13_min() {
        assertEquals(1.1, parseOcrMiles("1l", 13)!!, 0.001)
        assertEquals(1.1, parseOcrMiles("11", 13)!!, 0.001)
        assertEquals(1.1, parseTripLegFromLine("13 mins (1l mi)")?.miles ?: 0.0, 0.001)
        assertEquals(11.0, parseOcrMiles("1.1", 24)!!, 0.001)
    }

    @Test
    fun hay_hill_1_1_mi_trip_not_11() {
        val text = """
            S
            ANE
            2 comfort
            £6.56
            4.77
            £0.51 est. holiday entitlement included
            3 min (0.4 mi)
            Portuguese Embassy. London. SWIX
            8PP
            13 mins (1l mi)
            X
            15 Hay Hill. London. W1J 8
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(6.56, ride.price, 0.05)
        assertEquals(3, ride.pickup_time_minutes)
        assertEquals(0.4, ride.pickup_distance_value!!, 0.05)
        assertEquals(13, ride.trip_time_minutes)
        assertEquals(1.1, ride.trip_distance_value!!, 0.05)
        assertEquals("SW1X", ride.pickup_address_postcode)
        assertEquals("W1J", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }
}
