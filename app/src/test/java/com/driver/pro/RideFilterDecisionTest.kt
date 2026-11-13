package com.driver.pro

import org.junit.Assert.assertEquals
import org.junit.Test

class RideFilterDecisionTest {

    private val scoreAccept = RangeFilterThresholds(enabled = true, reject = 12f, accept = 42f)
    private val priceRejectLow = RangeFilterThresholds(enabled = true, reject = 54f, accept = 80f)

    @Test
    fun id_6293_score_accept_and_price_reject_takes_no_action() {
        val filters = RideFilterThresholds(score = scoreAccept, price = priceRejectLow)
        assertEquals(
            0,
            decideRideFromFilters(
                localScore = 75,
                price = 22.54,
                rating = 4.82,
                poundsPerMile = 2.19,
                thresholds = filters,
                serverDecision = 1,
            ),
        )
    }

    @Test
    fun score_reject_and_price_accept_takes_no_action() {
        val scoreReject = RangeFilterThresholds(enabled = true, reject = 90f, accept = 120f)
        val priceAccept = RangeFilterThresholds(enabled = true, reject = 10f, accept = 20f)
        val filters = RideFilterThresholds(score = scoreReject, price = priceAccept)
        assertEquals(
            0,
            decideRideFromFilters(
                localScore = 86,
                price = 24.80,
                rating = 4.80,
                poundsPerMile = 2.0,
                thresholds = filters,
                serverDecision = -1,
            ),
        )
    }

    @Test
    fun score_only_still_accepts_above_band() {
        val filters = RideFilterThresholds(score = scoreAccept)
        assertEquals(
            1,
            decideRideFromFilters(
                localScore = 75,
                price = 22.54,
                rating = null,
                poundsPerMile = null,
                thresholds = filters,
                serverDecision = -1,
            ),
        )
    }

    @Test
    fun price_only_still_rejects_below_band() {
        val filters = RideFilterThresholds(price = priceRejectLow)
        assertEquals(
            -1,
            decideRideFromFilters(
                localScore = 75,
                price = 22.54,
                rating = null,
                poundsPerMile = null,
                thresholds = filters,
                serverDecision = 1,
            ),
        )
    }

    @Test
    fun both_accept_still_accepts() {
        val priceAlsoAccept = RangeFilterThresholds(enabled = true, reject = 10f, accept = 20f)
        val filters = RideFilterThresholds(score = scoreAccept, price = priceAlsoAccept)
        assertEquals(
            1,
            decideRideFromFilters(
                localScore = 75,
                price = 22.54,
                rating = null,
                poundsPerMile = null,
                thresholds = filters,
                serverDecision = 0,
            ),
        )
    }

    @Test
    fun combine_accept_and_reject_is_undecided() {
        assertEquals(0, combineFilterDecisions(listOf(1, -1)))
        assertEquals(1, combineFilterDecisions(listOf(1, 0)))
        assertEquals(-1, combineFilterDecisions(listOf(-1, 0)))
        assertEquals(0, combineFilterDecisions(listOf(0, 0)))
        assertEquals(null, combineFilterDecisions(emptyList()))
    }

    @Test
    fun score_middle_and_postcode_accept_accepts() {
        val filters = RideFilterThresholds(
            score = RangeFilterThresholds(enabled = true, reject = 12f, accept = 90f),
            postcode = PostcodeFilterState(
                enabled = true,
                statuses = mapOf("OX26" to "accept"),
            ),
        )
        assertEquals(
            1,
            decideRideFromFilters(
                localScore = 69,
                price = 72.41,
                rating = 4.88,
                poundsPerMile = 1.77,
                thresholds = filters,
                serverDecision = 0,
                pickupPostcode = "SL2",
                dropPostcode = "OX26",
            ),
        )
    }

    @Test
    fun score_reject_and_postcode_accept_takes_no_action() {
        val filters = RideFilterThresholds(
            score = RangeFilterThresholds(enabled = true, reject = 90f, accept = 120f),
            postcode = PostcodeFilterState(
                enabled = true,
                statuses = mapOf("N6" to "accept"),
            ),
        )
        assertEquals(
            0,
            decideRideFromFilters(
                localScore = 86,
                price = 24.80,
                rating = 4.80,
                poundsPerMile = 2.0,
                thresholds = filters,
                serverDecision = -1,
                pickupPostcode = "HA9",
                dropPostcode = "N6",
            ),
        )
    }

    @Test
    fun postcode_accept_and_postcode_reject_on_same_trip_takes_no_action() {
        assertEquals(
            0,
            postcodeFilterDecision(
                pickupPostcode = "HA9",
                dropPostcode = "N6",
                filter = PostcodeFilterState(
                    enabled = true,
                    statuses = mapOf("HA9" to "accept", "N6" to "reject"),
                ),
            ),
        )
    }
}
