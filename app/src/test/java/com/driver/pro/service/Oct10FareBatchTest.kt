package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 10 Oct 2026: Tesco Thornton Heath £4.73 fare dropped; holiday line £0.43 remains. */
class Oct10FareBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun holiday_0_43_recovers_4_73() {
        assertEquals(4.73, recoverFareFromHolidayEntitlement("£0.43 est. holiday entitlement included")!!, 0.01)
        assertEquals(
            4.73,
            extractBestPrice(
                """
                5.00
                4 min (0.5 mi)
                £0.43 est. holiday entitlement included
                Match
                """.trimIndent(),
            ),
            0.01,
        )
    }

    @Test
    fun tesco_thornton_heath_recovers_missing_pound_fare() {
        val text = """
            A23
            nGSTOGKEDAD
            5.00
            4 min (0.5 mi)
            £0.43 est. holiday entitlement included
            Tesco. Thornton Heath. CR7 8RX
            5 mins (0.9 mi)
            8PQ
            SelhurstF
            Stadiur
            Cranbrook Rd. Thornton Heath. CR7
            andleraTK
            X
            Match
            6s
        """.trimIndent()
        val ride = parse(text)
        assertEquals(4.73, ride.price, 0.05)
        assertEquals(5.00, ride.rating, 0.05)
        assertEquals(4, ride.pickup_time_minutes)
        assertEquals(0.5, ride.pickup_distance_value!!, 0.05)
        assertEquals(5, ride.trip_time_minutes)
        assertEquals(0.9, ride.trip_distance_value!!, 0.05)
        assertEquals("CR7", ride.pickup_address_postcode)
        assertEquals("CR7", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun mill_st_and_collingham_keep_correct_miles() {
        val mill = parse(
            """
            A4202
            NRLINON
            ARCAD
            2 Comfort
            £14.83
            4.62
            £0.58 est. holiday entitlement included
            2 min (0.1 mi)
            13 Masons Yd. London. SW1Y GBU
            7 mins (0.9 mi)
            3-5 Mill St. London. wis 2AU
            X
            Match
            """.trimIndent(),
        )
        assertEquals(14.83, mill.price, 0.05)
        assertEquals(0.9, mill.trip_distance_value!!, 0.05)
        assertEquals("SW1Y", mill.pickup_address_postcode)
        assertEquals("W1S", mill.dropoff_address_postcode)

        val collingham = parse(
            """
            2 Exec
            £15.71
            4.63
            Exclusive
            £l.02 est. holiday entitlement included
            2 min (0.l mi)
            13 Masons Yd. London. SWIY 6BU
            11 mins (2.8 mi)
            OHL
            X
            20 Collingham Gardens. London. SW5
            Confirm
            """.trimIndent(),
        )
        assertEquals(15.71, collingham.price, 0.05)
        assertEquals(2.8, collingham.trip_distance_value!!, 0.05)
        assertEquals("SW1Y", collingham.pickup_address_postcode)
        assertEquals("SW5", collingham.dropoff_address_postcode)
    }
}
