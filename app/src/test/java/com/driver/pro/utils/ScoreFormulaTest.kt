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
            1 to 2, 2 to 3,
            3 to 4, 4 to 5, 5 to 3, 6 to 4, 7 to 5, 8 to 6, 9 to 6, 10 to 5,
            11 to 4, 12 to 3, 13 to 3, 14 to 3, 15 to 3, 16 to 5, 17 to 5, 18 to 5,
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
    fun tape_shows_hour_terms_even_when_table_has_no_cell() {
        val job = ride(50.63, 3, 74, 0.6, 48.8, pickupPc = "SE1", dropPc = "SE15")
            .copy(cost_per_mile = 0.50)
        assertEquals(9 to 7, postcodeHourAdjustment(job))
        assertEquals(
            "£0.50/mi  19.21/h  39+10 -9 +7 = 47",
            formatFormulaBreakdown(job),
        )
    }

    @Test
    fun known_pickup_hour_is_not_dropped_when_drop_hour_is_missing() {
        val job = ride(50.63, 3, 74, 0.6, 48.8, pickupPc = "TW6", dropPc = "SE15")
        assertEquals(6 to 7, postcodeHourAdjustment(job))
        assertEquals(50, computeFormulaScore(job, 0.50))
    }

    @Test
    fun id_5519_w11_tn16_includes_return_and_hour_points() {
        val card = RideRequest(
            id = 5519,
            price = 54.99,
            rating = 4.60,
            pickup_time_minutes = 5,
            pickup_distance_value = 0.6,
            pickup_address_postcode = "W11",
            trip_time_minutes = 117,
            trip_distance_value = 23.9,
            dropoff_address_postcode = "TN16",
            start_time_window = null,
            end_time_window = null,
            acceptedOrRejected = 0,
            final_score = 61,
            created_at = "18 Oct 2026, 21:04",
            cost_per_mile = 0.10,
            price_per_mile = 0.10,
        )
        assertEquals(5.0, resolveReturnToZone(card).miles, 0.001)
        assertEquals(5, resolveReturnToZone(card).minutes)
        assertEquals(21, parseRideHour(card.created_at))
        assertEquals(7 to 0, postcodeHourAdjustment(card))
        assertEquals(23.84, netPoundsPerHour(card, 0.10)!!, 0.005)
        assertEquals(48, poundsPerHourToPoints(23.84))
        assertEquals(51, displayedFormulaScore(card))
        assertEquals(
            "£0.10/mi  23.84/h  48+10 -7 +0 = 51",
            formatFormulaBreakdown(card),
        )
    }

    @Test
    fun id_5530_w12_sw8_applies_pickup_7_and_drop_8() {
        val card = RideRequest(
            id = 5530,
            price = 22.34,
            rating = 4.60,
            pickup_time_minutes = 10,
            pickup_distance_value = 1.2,
            pickup_address_postcode = "W12",
            trip_time_minutes = 42,
            trip_distance_value = 6.6,
            dropoff_address_postcode = "SW8",
            start_time_window = null,
            end_time_window = null,
            acceptedOrRejected = 0,
            final_score = 57,
            created_at = "19 Oct 2026, 21:38",
            cost_per_mile = 0.10,
            price_per_mile = 0.10,
        )
        assertEquals(7 to 8, postcodeHourAdjustment(card))
        assertEquals(23.10, netPoundsPerHour(card, 0.10)!!, 0.005)
        assertEquals(58, displayedFormulaScore(card))
        assertEquals(
            "£0.10/mi  23.10/h  47+10 -7 +8 = 58",
            formatFormulaBreakdown(card),
        )
    }

    @Test
    fun id_5531_tw6_sg6_stays_78() {
        val card = ride(50.63, 3, 74, 0.6, 48.8).copy(
            id = 5531,
            created_at = "19 Oct 2026, 21:46",
            cost_per_mile = 0.10,
            price_per_mile = 0.10,
        )
        assertEquals(6 to 6, postcodeHourAdjustment(card))
        assertEquals(33.84, netPoundsPerHour(card, 0.10)!!, 0.005)
        assertEquals(78, displayedFormulaScore(card))
        assertEquals(
            "£0.10/mi  33.84/h  68+10 -6 +6 = 78",
            formatFormulaBreakdown(card),
        )
    }

    @Test
    fun id_5535_tw6_w14_adds_drop_hour_points() {
        val card = RideRequest(
            id = 5535,
            price = 19.52,
            rating = 4.59,
            pickup_time_minutes = 4,
            pickup_distance_value = 0.6,
            pickup_address_postcode = "TW6",
            trip_time_minutes = 43,
            trip_distance_value = 16.4,
            dropoff_address_postcode = "W14",
            start_time_window = null,
            end_time_window = null,
            acceptedOrRejected = 0,
            final_score = 46,
            created_at = "19 Oct 2026, 21:50",
            cost_per_mile = 0.10,
            price_per_mile = 0.10,
        )
        assertEquals(6 to 7, postcodeHourAdjustment(card))
        assertEquals(20.96, netPoundsPerHour(card, 0.10)!!, 0.005)
        assertEquals(53, displayedFormulaScore(card))
        assertEquals(
            "£0.10/mi  20.96/h  42+10 -6 +7 = 53",
            formatFormulaBreakdown(card),
        )
    }

    @Test
    fun morning_kody_hour_9_fills_the_four_zero_jobs() {
        val at951 = "20 Oct 2026, 09:51"
        fun job(pick: String, drop: String) = ride(
            50.63, 3, 74, 0.6, 48.8,
            pickupPc = pick,
            dropPc = drop,
            createdAt = at951,
        )
        assertEquals(10, lookupPostcodeScoreRow("SW6")!!.pointsAtHour(9))
        assertEquals(6, lookupPostcodeScoreRow("SW18")!!.pointsAtHour(9))
        assertEquals(6, lookupPostcodeScoreRow("HA9")!!.pointsAtHour(9))
        assertEquals(10, lookupPostcodeScoreRow("SE1")!!.pointsAtHour(9))
        assertEquals(6, lookupPostcodeScoreRow("NW10")!!.pointsAtHour(9))
        assertEquals(10, lookupPostcodeScoreRow("W2")!!.pointsAtHour(9))
        assertEquals(10 to 6, postcodeHourAdjustment(job("SW6", "SW18")))
        assertEquals(6 to 10, postcodeHourAdjustment(job("HA9", "SE1")))
        assertEquals(6 to 10, postcodeHourAdjustment(job("NW10", "W2")))
        assertEquals(6 to 6, postcodeHourAdjustment(job("NW10", "NW10")))
    }

    @Test
    fun event_uplift_adds_on_top_of_kody_base() {
        val job = ride(
            50.63, 3, 74, 0.6, 48.8,
            pickupPc = "W11",
            dropPc = "TN16",
            createdAt = "2026-10-16 21:04:00",
        )
        val extras = IntArray(24)
        extras[20] = 2
        kodyEventUplifts = mapOf("W11" to extras)
        try {
            assertEquals(9 to 0, postcodeHourAdjustment(job))
        } finally {
            kodyEventUplifts = emptyMap()
        }
    }
}
