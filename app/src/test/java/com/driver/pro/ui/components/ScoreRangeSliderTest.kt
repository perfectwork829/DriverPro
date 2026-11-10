package com.driver.pro.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreRangeSliderTest {

    @Test
    fun price_per_mile_snaps_to_ten_pence_and_shows_one_decimal() {
        assertEquals(2.1f, snapSliderValue(2.1234f, 0f, 15f, 0.1f), 0.0001f)
        assertEquals(2.1f, snapSliderValue(2.06f, 0f, 15f, 0.1f), 0.0001f)
        assertEquals("2.1", formatSliderLabel(snapSliderValue(2.06f, 0f, 15f, 0.1f), 1))
        assertEquals("0.5", formatSliderLabel(0.5f, 1))
        assertEquals(149, sliderSteps(0f, 15f, 0.1f))
    }

    @Test
    fun score_still_snaps_to_five_and_shows_whole_numbers() {
        assertEquals(75f, snapSliderValue(73.4f, 0f, 150f, 5f), 0.0001f)
        assertEquals("75", formatSliderLabel(75f, 0))
    }
}

class UnscoredHistoryCardTest {

    private fun ride(price: Double, raw: String) = com.driver.pro.RideRequest(
        id = 1, price = price, rating = 0.0,
        pickup_time_minutes = 6, pickup_distance_value = 1.2, pickup_address_postcode = "SW7",
        trip_time_minutes = 15, trip_distance_value = 3.1, dropoff_address_postcode = null,
        start_time_window = null, end_time_window = null, acceptedOrRejected = 0, raw_text = raw,
    )

    @Test
    fun unscored_order_with_a_fare_is_shown_in_history() {
        assertEquals(true, com.driver.pro.isCustomerVisibleRide(ride(6.77, "OCR debug: missing drop postcode\n...")))
        assertEquals(true, com.driver.pro.isCustomerVisibleRide(ride(6.77, "normal")))
    }

    @Test
    fun empty_read_without_fare_stays_hidden() {
        assertEquals(false, com.driver.pro.isCustomerVisibleRide(ride(0.0, "OCR debug: no fare\n...")))
    }

    @Test
    fun fare_misread_as_rating_stays_hidden() {
        assertEquals(false, com.driver.pro.isCustomerVisibleRide(
            ride(4.83, "OCR debug: OCR low confidence: price looks like passenger rating\n..."),
        ))
    }

    @Test
    fun unscored_card_hidden_when_same_offer_was_scored_moments_later() {
        val unscored = ride(6.77, "OCR debug: OCR incomplete: missing drop-off postcode")
            .copy(id = 1762800000, created_at = "2026-11-09 22:03:58")
        val scored = ride(6.77, "Electric £6.77").copy(id = 5948, created_at = "2026-11-09 22:04:00", final_score = 36)
        val other = ride(9.10, "OCR debug: OCR incomplete: missing drop-off postcode")
            .copy(id = 1762800100, created_at = "2026-11-09 22:10:00")
        val list = com.driver.pro.customerHistoryRides(listOf(unscored, scored, other))
        assertEquals(listOf(5948, 1762800100), list.map { it.id })
    }

    @Test
    fun unscored_card_kept_when_no_scored_twin() {
        val unscored = ride(6.77, "OCR debug: OCR incomplete: missing drop-off postcode")
            .copy(created_at = "2026-11-09 22:03:58")
        val laterSameFare = ride(6.77, "x").copy(id = 9, created_at = "2026-11-09 22:30:00")
        assertEquals(2, com.driver.pro.customerHistoryRides(listOf(unscored, laterSameFare)).size)
    }

    @Test
    fun score_slider_step_one_keeps_every_point() {
        assertEquals(118f, snapSliderValue(117.6f, 0f, 150f, 1f), 0.0001f)
        assertEquals(149, sliderSteps(0f, 150f, 1f))
    }

    @Test
    fun price_rate_line_drops_unread_rating() {
        assertEquals("Price/Rate: 6.77(4.40)", formatHistoryPriceRate(6.77, 4.4))
        assertEquals("Price: 6.77", formatHistoryPriceRate(6.77, 0.0))
    }
}
