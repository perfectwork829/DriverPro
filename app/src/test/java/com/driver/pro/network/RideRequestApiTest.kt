package com.driver.pro.network

import com.driver.pro.RideRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRequestApiTest {

    @Test
    fun rideRequestToJson_isSingleObject() {
        val ride = sampleRide()
        val json = rideRequestToJson(ride)
        assertTrue(json.trimStart().startsWith("{"))
        assertTrue(json.contains("\"price\""))
        assertTrue(json.contains("\"type\":\"confirm\""))
    }

    @Test
    fun rideRequestHttpBody_isJsonEncodedString_forServer() {
        val body = rideRequestHttpBody(sampleRide())
        assertTrue(body.trimStart().startsWith("\""))
        assertTrue(body.trimEnd().endsWith("\""))
        assertTrue(body.contains("\\\"price\\\"") || body.contains("\"price\""))
    }

    @Test
    fun validateRideForScoring_reportsMissingFields() {
        val incomplete = sampleRide().copy(
            price = 0.0,
            pickup_address_postcode = "",
            type = "",
        )
        val err = validateRideForScoring(incomplete)
        assertNotNull(err)
        assertTrue(err!!.contains("fare"))
        assertTrue(err.contains("pickup postcode"))
        assertTrue(err.contains("Confirm/Match"))
    }

    @Test
    fun validateRideForScoring_passesCompleteRide() {
        assertNull(validateRideForScoring(sampleRide()))
    }

    @Test
    fun validateRideForScoring_reportsIdenticalPickupAndDrop() {
        val duplicated = sampleRide().copy(
            pickup_address_postcode = "N1",
            dropoff_address_postcode = "N1",
            pickup_time_minutes = 15,
            trip_time_minutes = 15,
            pickup_distance_value = 1.6,
            trip_distance_value = 1.6,
        )
        assertEquals(
            "OCR incomplete: pickup and drop look identical",
            validateRideForScoring(duplicated),
        )
    }

    @Test
    fun validateRideForScoring_allowsSamePostcodeWhenTripMetricsDiffer() {
        val sameAddressReturn = sampleRide().copy(
            pickup_address_postcode = "N15",
            dropoff_address_postcode = "N15",
            pickup_time_minutes = 17,
            trip_time_minutes = 34,
            pickup_distance_value = 3.2,
            trip_distance_value = 3.7,
        )
        val ocr = """
            17 min (3.2 mi)
            Penrith Rd, London, N15 5QY
            34 mins (3.7 mi)
            Penrith Rd, London, N15 5QY
            Match
        """.trimIndent()
        assertNull(validateRideForScoring(sameAddressReturn, ocr))
    }

    @Test
    fun validateRideForScoring_allowsSamePostcodeWhenTwoDistinctPostcodesOnScreen() {
        // Photo-of-screen OCR mis-assigned drop = pickup, but two real addresses exist → must not block.
        val ocr = """
            UberX
            £4.60
            4.49 Verified
            3 min (0.2 mi)
            Elie Saab, London, W1J 6QQ
            9 mins (1.2 mi)
            16A Motcomb Street, London, SW1X 8LB
            Match
        """.trimIndent()
        val ride = sampleRide().copy(
            price = 4.60,
            rating = 4.49,
            pickup_time_minutes = 3,
            trip_time_minutes = 9,
            pickup_distance_value = 0.2,
            trip_distance_value = 1.2,
            pickup_address_postcode = "SW1X",
            dropoff_address_postcode = "SW1X",
            type = "match",
        )
        assertNull(validateRideForScoring(ride, ocr))
    }

    @Test
    fun validateRidePlausibility_allowsLowFareNearRatingBand() {
        val lowFare = sampleRide().copy(price = 4.60, rating = 4.49)
        assertNull(validateRidePlausibility(lowFare))
    }

    private fun sampleRide() = RideRequest(
        id = 0,
        price = 12.5,
        rating = 4.8,
        pickup_time_minutes = 5,
        pickup_distance_value = 0.4,
        pickup_address_postcode = "KT6",
        trip_time_minutes = 10,
        trip_distance_value = 2.0,
        dropoff_address_postcode = "KT1",
        start_time_window = "10-16",
        end_time_window = "10-26",
        acceptedOrRejected = 0,
        type = "confirm",
        accuracy = 95,
        ocr_image_uri = "",
    )
}
