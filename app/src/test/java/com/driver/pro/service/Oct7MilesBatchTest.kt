package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 7 Oct 2026: 2.4 mi / 2.5 mi urban legs must not become 24 / 25. */
class Oct7MilesBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun parseOcrMiles_keeps_2_4_on_25_min_pickup() {
        assertEquals(2.4, parseOcrMiles("2.4", 25)!!, 0.001)
        assertEquals(2.4, parseOcrMiles("24", 25)!!, 0.001)
        assertEquals(2.4, parseTripLegFromLine("25 min (2.4 mi)")?.miles ?: 0.0, 0.001)
        assertEquals(2.4, parseTripLegFromLine("25 min (24 mi)")?.miles ?: 0.0, 0.001)
    }

    @Test
    fun parseOcrMiles_keeps_2_5_on_24_min_trip() {
        assertEquals(2.5, parseOcrMiles("2.5", 24)!!, 0.001)
        assertEquals(2.5, parseOcrMiles("25", 24)!!, 0.001)
        assertEquals(2.5, parseTripLegFromLine("24 mins (2.5 mi)")?.miles ?: 0.0, 0.001)
        assertEquals(2.5, parseTripLegFromLine("24 mins (25 mi)")?.miles ?: 0.0, 0.001)
    }

    @Test
    fun still_upscales_1_1_to_11_and_2_78_to_27_8() {
        assertEquals(11.0, parseOcrMiles("1.1", 24)!!, 0.001)
        assertEquals(27.8, parseOcrMiles("2.78", 55)!!, 0.001)
    }

    @Test
    fun id4230_barnes_2_4_mi_pickup_not_24() {
        val text = """
            2 Electric
            £8.04
            * 4.83
            25 min (2.4 mi)
            Bishop's Pa
            £0.26 est. holiday entitlement included
            X
            Barnes Station. London. SW13 OHT
            7 mins (14 mi)
            Match
            119 Lonsdale Road. London. SW13 9DA
            PA
        """.trimIndent()
        val ride = parse(text)
        assertEquals(8.04, ride.price, 0.05)
        assertEquals(25, ride.pickup_time_minutes)
        assertEquals(2.4, ride.pickup_distance_value!!, 0.05)
        assertEquals(7, ride.trip_time_minutes)
        assertEquals(1.4, ride.trip_distance_value!!, 0.05)
        assertEquals("SW13", ride.pickup_address_postcode)
        assertEquals("SW13", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id4220_kingfisher_2_5_mi_trip_not_25() {
        val text = """
            PALACE
            RIVERSIDE
            2 Electric
            £8.32
            * 4.81
            sANE END
            2 min (0.2 mi)
            £0.73 est. holiday entitlement included
            Pappa Ciccia. Fulham. SW6 5RQ
            24 mins (2.5 mi)
            X
            Kingfisher House. London. SW18 1TX
            Match
            A32L5
        """.trimIndent()
        val ride = parse(text)
        assertEquals(8.32, ride.price, 0.05)
        assertEquals(2, ride.pickup_time_minutes)
        assertEquals(0.2, ride.pickup_distance_value!!, 0.05)
        assertEquals(24, ride.trip_time_minutes)
        assertEquals(2.5, ride.trip_distance_value!!, 0.05)
        assertEquals("SW6", ride.pickup_address_postcode)
        assertEquals("SW18", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }
}
