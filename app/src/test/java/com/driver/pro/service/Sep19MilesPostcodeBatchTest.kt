package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 19 Sep 2026: HA1 pickup, HA9/NW6 swap, 11.8, 3.1l, 8.4 miles. */
class Sep19MilesPostcodeBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun id3200_hal_30j_is_ha1_pickup() {
        val text = """
            MANDEVILLE
            2 Electric
            A40
            £10.64
            * 5.00
            £0.63 est. holiday entitlement included
            22 min (5.7 mi)
            St Mark's Hospital. Harrow. HAl 30J
            12 mins (3.1 mi)
            94 Central Avenue. Pinner. HA5 5BP
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(10.64, ride.price, 0.05)
        assertEquals("HA1", ride.pickup_address_postcode)
        assertEquals("HA5", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3195_ha9_pickup_nw6_drop_not_swapped() {
        val text = """
            2 UberX Exclusive
            £10.53
            4.74
            12 min (2.9 mi)
            HARROW ROAD
            £0.69 est. holiday entitlement included
            93 Monks Park. Wembley. HA9 6JW
            22 mins (4.6 mi)
            Planet Organic, London, NW6 6
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(10.53, ride.price, 0.05)
        assertEquals("HA9", ride.pickup_address_postcode)
        assertEquals("NW6", ride.dropoff_address_postcode)
        assertEquals(12, ride.pickup_time_minutes)
        assertEquals(22, ride.trip_time_minutes)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3195_dump_confirm_then_drop_still_ha9_then_nw6() {
        val text = """
            ROYAL
            y9
            un
            2 UberX Exclusive
            £10.53
            4.74
            12 min (2.9 mi)
            HARROW ROAD
            £0.69 est. holiday entitlement included
            93 Monks Park. Wembley. HA9 6JW
            Confirm
            22 mins (4.6 mi)
            Planet Organic, London, NW6 6
        """.trimIndent()
        val ride = parse(text)
        assertEquals("HA9", ride.pickup_address_postcode)
        assertEquals("NW6", ride.dropoff_address_postcode)
    }

    @Test
    fun id3264_28_min_11_3_is_11_8() {
        val text = """
            E30
            2 Electric
            £13.64
            4.68
            Exclusive
            5 min (1.5 mi)
            £0.68 est. holiday entitlement included
            28 mins (11.3 mi)
            Runnymede Hall. Uxbridge. UB8 3FG
            Confirm
            31 Lambert Walk. Wembley. HA9 7TR
        """.trimIndent()
        val ride = parse(text)
        assertEquals(13.64, ride.price, 0.05)
        assertEquals("UB8", ride.pickup_address_postcode)
        assertEquals("HA9", ride.dropoff_address_postcode)
        assertEquals(28, ride.trip_time_minutes)
        assertEquals(11.8, ride.trip_distance_value!!, 0.05)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3253_3_1l_mi_is_3_1_not_3_11() {
        val text = """
            2 Electric
            Exclusive
            £4.90
            4.89
            £0.29 est. holiday entitlement included
            8 min (3.1l mi)
            OSD
            466-468 uxbridge rd. London. uB4
            4 mins (1.0 mi)
            41 Fremantle Way. Hayes. UB3 2FX
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(4.90, ride.price, 0.05)
        assertEquals(3.1, ride.pickup_distance_value!!, 0.05)
        assertEquals("UB4", ride.pickup_address_postcode)
        assertEquals("UB3", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3245_20_min_8_4_stays_8_4_not_3_4() {
        val text = """
            M25
            Heathrow Airport NORTH
            2 Electric
            (LHR)
            £14.24
            ★ 4.56
            13 min (5.4 mi)
            FELTAM
            £0.72 est. holiday entitlement inciuded
            6AB
            14 Gloucester Road. Hounslow. TW4
            h 20 mins (8.4 mi)
            Match
            13 Belgrave Mews. Uxbridge. UB8 3AG
        """.trimIndent()
        val ride = parse(text)
        assertEquals(14.24, ride.price, 0.05)
        assertEquals("TW4", ride.pickup_address_postcode)
        assertEquals("UB8", ride.dropoff_address_postcode)
        assertEquals(20, ride.trip_time_minutes)
        assertEquals(8.4, ride.trip_distance_value!!, 0.05)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun parseTripLeg_3_1l_and_8_4_and_11_3() {
        assertEquals(3.1, parseTripLegFromLine("8 min (3.1l mi)")?.miles ?: 0.0, 0.001)
        assertEquals(8.4, parseTripLegFromLine("20 mins (8.4 mi)")?.miles ?: 0.0, 0.001)
        assertEquals(11.8, parseTripLegFromLine("28 mins (11.3 mi)")?.miles ?: 0.0, 0.001)
        assertTrue(extractOuterLondonPostcodes("St Mark's Hospital. Harrow. HAl 30J").contains("HA1"))
    }
}
