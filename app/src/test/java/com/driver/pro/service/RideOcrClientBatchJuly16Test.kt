package com.driver.pro.service

import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Client field reports 2026-07-16. */
class RideOcrClientBatchJuly16Test {

    @Test
    fun truncated_cr7_7_thornton_heath_electric() {
        val text = """
            Electric Exclusive
            £23.69
            Cash payment
            5.00
            14 min (3.3 mi)
            Leander Family Practice. Thornton Heath. CR7 7
            1 hr 8 min (16.9 mi)
            44 Broadway. London. E15 1XH
            Long trip (60+ min)
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("CR7", ride.pickup_address_postcode)
        assertEquals("E15", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun ocr_crz7_recovers_to_cr7() {
        val text = """
            Electric Exclusive
            £23.69
            5.00
            14 min (3.3 mi)
            Leander Family Practice. Thornton Heath. CRZ7
            1 hr 8 min (16.9 mi)
            44 Broadway. London. E15 LXH
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("CR7", ride.pickup_address_postcode)
        assertEquals("E15", ride.dropoff_address_postcode)
    }

    @Test
    fun split_cr7_8ls_inward_on_next_line() {
        val text = """
            UberX
            £10.61
            4.79
            9 min (1.9 mi)
            14 Liverpool Rd. Thornton Heath. CR7
            8LS
            26 mins (5.5 mi)
            Blackshaw Rd. London. SW17 0BZ
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("CR7", ride.pickup_address_postcode)
        assertEquals("SW17", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun split_crz_then_8ls_and_swi7() {
        val text = """
            UberX
            £10.61
            4.79
            9 min (1.9 mi)
            14 Liverpool Rd. Thornton Heath. CRZ
            8LS
            26 mins (5.5 mi)
            Blackshaw Rd. London. SWi7 0BZ
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("CR7", ride.pickup_address_postcode)
        assertEquals("SW17", ride.dropoff_address_postcode)
    }

    @Test
    fun long_trip_cr2_pickup_se1_drop_not_swapped() {
        val text = """
            UberX
            £19.16
            5.00
            5 min (1.2 mi)
            76 Warham Road, South Croydon, CR2 6LB
            1 hr 10 min (11.4 mi)
            Southbank Centre Belvedere Rd, London, SE1 8XX
            Long trip (60+ min)
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("CR2", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
    }

    @Test
    fun croydon_cr0_pickup_se14_drop_not_swapped_rating_4_65() {
        val text = """
            UberX
            £14.29
            ★ 4.65
            7 min (1.4 mi)
            94 Park Lane, Croydon, CR0 1JB
            44 mins (9.5 mi)
            Queens Road Partnership, London, SE14 5HD
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("CR0", ride.pickup_address_postcode)
        assertEquals("SE14", ride.dropoff_address_postcode)
        assertEquals(4.65, ride.rating, 0.001)
    }

    @Test
    fun rating_keeps_two_decimals_4_66() {
        val text = """
            Electric Exclusive
            £8.53
            ★ 4.66
            4 min (0.7 mi)
            142 Pampisford Road, Purley, CR8 2NH
            26 mins (6.0 mi)
            Riverside Dr, Mitcham, CR4 4BR
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.66, ride.rating, 0.001)
        assertEquals("CR8", ride.pickup_address_postcode)
        assertEquals("CR4", ride.dropoff_address_postcode)
    }
}
