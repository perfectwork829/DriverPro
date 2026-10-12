package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.rideRequestToJson
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 10 Oct 2026: Dear Darling £14.41 / 10 min (1.4 mi) OCR'd as (14 mi) scored as a 14-mile trip. */
class Oct10MilesBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun parseOcrMiles_1_4_not_14_on_10_min() {
        assertEquals(1.4, parseOcrMiles("14", 10)!!, 0.001)
        assertEquals(1.4, parseTripLegFromLine("10 mins (14 mi)")?.miles ?: 0.0, 0.001)
    }

    @Test
    fun dear_darling_1_4_mi_not_14() {
        val text = """
            1202
            BURLINGTỞ
            ARCAD
            2 Exec Exclusive
            £14.4lO
            4.43
            Verified
            I min (0 mi)
            £1.37 est. holiday entitlement included
            Dear Darling. London. SWIY 6JB
            10 mins (14 mi)
            i Cafe
            48 Margaret St. London. wiw 8SE
            Confirm
            X
            50 Kale
            Eli
        """.trimIndent()
        val ride = parse(text)
        assertEquals(14.41, ride.price, 0.05)
        assertEquals(1, ride.pickup_time_minutes)
        assertEquals(0.0, ride.pickup_distance_value!!, 0.05)
        assertEquals(10, ride.trip_time_minutes)
        assertEquals(1.4, ride.trip_distance_value!!, 0.05)
        assertEquals("SW1Y", ride.pickup_address_postcode)
        assertEquals("W1W", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))

        val forScoring = rewriteOcrMilesToParsed(text, ride)
        assertTrue(forScoring.contains("(1.4 mi)"))
        assertFalse(forScoring.contains("(14 mi)"))
        assertTrue(rideRequestToJson(ride.copy(raw_text = forScoring)).contains("1.4"))
        assertFalse(
            Regex("""trip_distance_value":\s*14([.}]|,)""").containsMatchIn(
                rideRequestToJson(ride.copy(raw_text = forScoring)),
            ),
        )
    }
}
