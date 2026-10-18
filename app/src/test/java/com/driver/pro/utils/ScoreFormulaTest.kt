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
    fun sg6_sheet_hours_match_x3_to_x24_screenshot() {
        val row = lookupPostcodeScoreRow("SG6")!!
        val byHour = mapOf(
            3 to 4, 4 to 5, 5 to 3, 6 to 4, 7 to 5, 8 to 6, 9 to 5, 10 to 4,
            11 to 3, 12 to 3, 13 to 3, 14 to 3, 15 to 3, 16 to 5, 17 to 5, 18 to 5,
            19 to 6, 20 to 6, 21 to 6, 22 to 6, 23 to 6, 24 to 3,
        )
        byHour.forEach { (hour, points) ->
            assertEquals("SG6 X$hour", points, row.pointsAtHour(hour))
        }
        assertEquals(0.0, row.returnMiles, 0.0)
        assertEquals(0, row.returnMinutes)
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

    @Test
    fun id_5471_history_fields_are_49() {
        val card = RideRequest(
            id = 5471,
            price = 50.63,
            rating = 4.83,
            pickup_time_minutes = 3,
            pickup_distance_value = 0.6,
            pickup_address_postcode = "TW6",
            trip_time_minutes = 74,
            trip_distance_value = 48.8,
            dropoff_address_postcode = "SG6",
            start_time_window = null,
            end_time_window = null,
            acceptedOrRejected = 0,
            final_score = 44,
            created_at = "2026-10-16 22:54:00",
            cost_per_mile = 0.50,
            price_per_mile = 0.50,
        )
        assertEquals(0, resolveReturnToZone(card).minutes)
        assertEquals(19.21, netPoundsPerHour(card, 0.50)!!, 0.005)
        assertEquals(49, displayedFormulaScore(card))
        assertEquals(49, computeFormulaScore(card, 0.50))
        assertEquals(
            "£0.50/mi  19.21/h  39+10 -6 +6 = 49",
            formatFormulaBreakdown(card),
        )
    }

    @Test
    fun id_5484_tw6_sg6_at_2054_applies_cancelling_hour_points() {
        val card = ride(50.63, 3, 74, 0.6, 48.8).copy(
            id = 5484,
            cost_per_mile = 0.35,
            price_per_mile = 0.35,
            created_at = "17 Oct 2026, 20:54",
        )
        assertEquals(20, parseRideHour(card.created_at))
        assertEquals(6 to 6, postcodeHourAdjustment(card))
        assertEquals(24.70, netPoundsPerHour(card, 0.35)!!, 0.005)
        assertEquals(60, displayedFormulaScore(card))
        assertEquals(
            "£0.35/mi  24.70/h  50+10 -6 +6 = 60",
            formatFormulaBreakdown(card),
        )
    }

    @Test
    fun id_5495_w6_tw13_is_50_not_51() {
        val card = RideRequest(
            id = 5495,
            price = 19.56,
            rating = 4.91,
            pickup_time_minutes = 8,
            pickup_distance_value = 1.5,
            pickup_address_postcode = "W6",
            trip_time_minutes = 30,
            trip_distance_value = 13.8,
            dropoff_address_postcode = "TW13",
            start_time_window = null,
            end_time_window = null,
            acceptedOrRejected = 0,
            final_score = 51,
            created_at = "17 Oct 2026, 21:24",
            cost_per_mile = 0.35,
            price_per_mile = 0.35,
        )
        assertEquals(21, parseRideHour(card.created_at))
        assertEquals(7, lookupPostcodeScoreRow("W6")!!.pointsAtHour(21))
        assertEquals(6, lookupPostcodeScoreRow("TW13")!!.pointsAtHour(21))
        assertEquals(7 to 6, postcodeHourAdjustment(card))
        assertEquals(20.29, netPoundsPerHour(card, 0.35)!!, 0.005)
        assertEquals(50, displayedFormulaScore(card))
        assertEquals(
            "£0.35/mi  20.29/h  41+10 -7 +6 = 50",
            formatFormulaBreakdown(card),
        )
    }

    @Test
    fun known_pickup_hour_is_not_dropped_when_drop_hour_is_missing() {
        val job = ride(50.63, 3, 74, 0.6, 48.8, pickupPc = "TW6", dropPc = "SE15")
        assertEquals(6 to 0, postcodeHourAdjustment(job))
        assertEquals(43, computeFormulaScore(job, 0.50))
    }
}
