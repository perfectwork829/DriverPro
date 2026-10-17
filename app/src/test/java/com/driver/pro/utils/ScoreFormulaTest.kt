package com.driver.pro.utils

import com.driver.pro.RideRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreFormulaTest {

    private fun ride(
        price: Double,
        pickupMin: Int,
        tripMin: Int,
        pickupMi: Double,
        tripMi: Double,
        rating: Double = 4.83,
        pickupPc: String = "TW6",
        dropPc: String = "SG6",
        createdAt: String = "2026-10-16 22:54:00",
    ) = RideRequest(
        id = 5471,
        price = price,
        rating = rating,
        pickup_time_minutes = pickupMin,
        pickup_distance_value = pickupMi,
        pickup_address_postcode = pickupPc,
        trip_time_minutes = tripMin,
        trip_distance_value = tripMi,
        dropoff_address_postcode = dropPc,
        start_time_window = null,
        end_time_window = null,
        acceptedOrRejected = 0,
        final_score = 0,
        created_at = createdAt,
    )

    @Test
    fun pounds_per_hour_bands_match_client_sheet() {
        assertEquals(38, poundsPerHourToPoints(18.51))
        assertEquals(38, poundsPerHourToPoints(19.00))
        assertEquals(39, poundsPerHourToPoints(19.01))
        assertEquals(39, poundsPerHourToPoints(19.21))
        assertEquals(39, poundsPerHourToPoints(19.50))
        assertEquals(40, poundsPerHourToPoints(19.51))
        assertEquals(40, poundsPerHourToPoints(20.00))
        assertEquals(1, poundsPerHourToPoints(0.50))
        assertEquals(2, poundsPerHourToPoints(0.51))
    }

    @Test
    fun rating_bands_match_client_sheet() {
        assertEquals(10, ratingToPoints(5.00))
        assertEquals(10, ratingToPoints(4.83))
        assertEquals(10, ratingToPoints(4.51))
        assertEquals(9, ratingToPoints(4.50))
        assertEquals(9, ratingToPoints(4.01))
        assertEquals(8, ratingToPoints(4.00))
        assertEquals(3, ratingToPoints(1.00))
        assertEquals(0, ratingToPoints(0.99))
    }

    @Test
    fun sg6_hour_22_is_six_and_return_is_zero() {
        val row = lookupPostcodeScoreRow("SG6 2AB")!!
        assertEquals(6, row.pointsAtHour(22))
        assertEquals(3, row.pointsAtHour(24))
        assertEquals(3, row.pointsAtHour(0))
        assertEquals(0.0, row.returnMiles, 0.001)
        assertEquals(0, row.returnMinutes)
        assertEquals(ReturnToZone.NONE.miles, resolveReturnToZone("TW6", "SG6", 48.8, 74).miles, 0.001)
        assertEquals(0, resolveReturnToZone("TW6", "SG6", 48.8, 74).minutes)
    }

    @Test
    fun client_walkthrough_tw6_sg6_is_49() {
        val job = ride(50.63, 3, 74, 0.6, 48.8)
        assertEquals(19.21, netPoundsPerHour(job, 0.50)!!, 0.005)
        assertEquals(39, poundsPerHourToPoints(19.21))
        assertEquals(10, ratingToPoints(4.83))
        assertEquals(6 to 6, postcodeHourAdjustment(job))
        assertEquals(49, computeFormulaScore(job, 0.50))
        assertEquals(
            "£0.50/mi  19.21/h  39+10 -6 +6 = 49",
            formatFormulaBreakdown(job.copy(cost_per_mile = 0.50)),
        )
    }

    @Test
    fun history_card_with_old_stored_44_displays_49() {
        val stored = ride(50.63, 3, 74, 0.6, 48.8).copy(
            final_score = 44,
            cost_per_mile = 0.50,
            price_per_mile = 0.50,
        )
        assertEquals(49, displayedFormulaScore(stored))
        assertEquals(22, parseRideHour("2026-10-16 22:54:00"))
        assertEquals(22, parseRideHour("16 Oct 2026, 22:54"))
    }
}
