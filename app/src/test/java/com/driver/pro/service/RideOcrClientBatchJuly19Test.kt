package com.driver.pro.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Client field reports 2026-07-19. */
class RideOcrClientBatchJuly19Test {

    @Test
    fun rating_4_44_not_default_3_50() {
        val text = """
            UberXL
            £9.82
            ★ 4.44
            £0.84 est. holiday entitlement included
            9 min (2.5 mi)
            12 Enmore Road, London, SE25 5NQ
            5 mins (1.2 mi)
            93 Alexandra Road, Croydon, CR0 6EZ
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.44, ride.rating, 0.001)
        assertEquals(9.82, ride.price, 0.001)
    }

    @Test
    fun rating_on_same_line_as_fare() {
        assertEquals(4.44, parseRatingFromLine("£9.82 4.44")!!, 0.001)
        assertEquals(4.63, parseRatingFromLine("£6.84 ★ 4.63")!!, 0.001)
        assertEquals(4.61, parseRatingFromLine("★ 4.61 Verified")!!, 0.001)
    }

    @Test
    fun rating_4_63_priority_card() {
        val text = """
            UberX Priority
            Exclusive
            £6.84
            ★ 4.63
            £0.57 est. holiday entitlement included
            +£1.16 included for priority
            4 min (1.4 mi)
            48 Buxton Lane, Caterham, CR3 5HE
            5 mins (1.4 mi)
            Straw Cl, Caterham, CR3 5FL
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.63, ride.rating, 0.001)
    }

    @Test
    fun pickup_distance_1_0_mi_not_zero() {
        val text = """
            UberX Exclusive
            £6.73
            ★ 4.71
            £0.50 est. holiday entitlement included
            4 min (1.0 mi)
            The Moon Under Water (Wetherspoon), London, SW16 4AU
            9 mins (2.9 mi)
            Croydon, CR0 1GE
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(1.0, ride.pickup_distance_value!!, 0.001)
        assertEquals(4, ride.pickup_time_minutes)
    }

    @Test
    fun pickup_distance_recovers_zero_to_one_mile() {
        assertEquals(1.0, parseOcrMiles("0.0", 4)!!, 0.001)
        assertEquals(1.0, parseOcrMiles("1.0", 4)!!, 0.001)
    }

    @Test
    fun price_8_01_not_3_01() {
        val text = """
            Electric Exclusive
            £3.01
            ★ 4.83 Verified
            £0.58 est. holiday entitlement included
            6 min (1.3 mi)
            238 Northborough Road, London, SW16 4BA
            12 mins (3.4 mi)
            15 Gassiot Road, London, SW17 8LB
            Match
            8.01
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(8.01, ride.price, 0.001)
    }

    @Test
    fun price_4_67_not_map_20() {
        val text = """
            Electric
            £4.67
            ★ 4.86
            £0.37 est. holiday entitlement included
            4 min (0.8 mi)
            Brigstock Road, Thornton Heath, CR7 8RB
            6 mins (1.4 mi)
            9 Wellington Road, Croydon, CR0 2SH
            Match
            20
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.67, ride.price, 0.001)
        assertNotEquals(20.0, ride.price, 0.001)
    }

    @Test
    fun comfort_card_pickup_1min_not_35min() {
        val text = """
            Comfort Exclusive
            £22.16
            ★ 4.76 Verified
            £1.83 est. holiday entitlement included
            1 min (0.1 mi)
            12a Norbury Avenue, Thornton Heath, CR7 8AP
            35 mins (9.2 mi)
            London E1 8AB
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(1, ride.pickup_time_minutes)
        assertEquals(35, ride.trip_time_minutes)
        assertEquals(0.1, ride.pickup_distance_value!!, 0.001)
        assertEquals(9.2, ride.trip_distance_value!!, 0.001)
        assertEquals("CR7", ride.pickup_address_postcode)
        assertEquals("E1", ride.dropoff_address_postcode)
    }

    @Test
    fun verified_rating_4_92_not_3_50() {
        val text = """
            UberX Exclusive
            £4.82
            ★ 4.92 Verified
            £0.39 est. holiday entitlement included
            5 min (1.3 mi)
            Burntwood Lane, Caterham, CR3 5YX
            3 mins (0.9 mi)
            14 Foxon Lane Gardens, Caterham, CR3 5SN
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.92, ride.rating, 0.001)
    }
}
