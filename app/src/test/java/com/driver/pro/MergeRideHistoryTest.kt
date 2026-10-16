package com.driver.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeRideHistoryTest {

    private fun ride(
        id: Int,
        price: Double,
        createdAt: String,
        rawText: String = "",
        score: Int? = 31,
    ) = RideRequest(
        id = id,
        price = price,
        rating = 4.5,
        pickup_time_minutes = 5,
        pickup_distance_value = 1.0,
        pickup_address_postcode = "NW10",
        trip_time_minutes = 14,
        trip_distance_value = 3.5,
        dropoff_address_postcode = "HA0",
        start_time_window = null,
        end_time_window = null,
        acceptedOrRejected = 0,
        final_score = score,
        raw_text = rawText,
        created_at = createdAt,
    )

    @Test
    fun keepsLocalScoredRidesWhenApiReturnsOlderRows() {
        val local = listOf(ride(id = 0, price = 6.49, createdAt = "2026-08-20 12:32:00"))
        val api = listOf(ride(id = 99, price = 10.0, createdAt = "2026-08-11 10:00:00"))
        val merged = mergeRideHistory(local, api)
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.price == 6.49 && it.final_score == 31 })
        assertTrue(merged.any { it.id == 99 })
    }

    @Test
    fun localDebugAndScoredSurviveNonEmptyApi() {
        val local = listOf(
            ride(id = 0, price = 1.0, createdAt = "2026-08-20 12:00:00", rawText = "OCR debug missing"),
            ride(id = 0, price = 6.49, createdAt = "2026-08-20 12:32:00"),
        )
        val api = listOf(ride(id = 50, price = 9.0, createdAt = "2026-08-19 09:00:00"))
        val merged = mergeRideHistory(local, api)
        assertEquals(3, merged.size)
    }

    @Test
    fun dedupesSameServerIdPreferringApiButKeepsLocalOcrText() {
        val local = listOf(
            ride(id = 50, price = 9.0, createdAt = "2026-08-19 09:00:00", rawText = "local ocr"),
        )
        val api = listOf(
            ride(id = 50, price = 9.0, createdAt = "2026-08-19 09:00:00", rawText = ""),
        )
        val merged = mergeRideHistory(local, api)
        assertEquals(1, merged.size)
        assertEquals("local ocr", merged[0].raw_text)
    }

    @Test
    fun keepsLocalFullScoreWhenApiDropsHundredsDigit() {
        val local = listOf(ride(id = 5204, price = 17.14, createdAt = "2026-10-12 14:06:10", score = 110))
        val api = listOf(ride(id = 5204, price = 17.14, createdAt = "2026-10-12 14:06:10", score = 10))
        val merged = mergeRideHistory(local, api)
        assertEquals(1, merged.size)
        assertEquals(110, merged[0].final_score)
    }

    @Test
    fun keepsLocalFormulaScoreWhenApiReturnsRemainderOnLongJob() {
        val local = listOf(ride(id = 5219, price = 25.11, createdAt = "2026-10-12 15:11:23", score = 18))
        val api = listOf(ride(id = 5219, price = 25.11, createdAt = "2026-10-12 15:11:23", score = 44))
        val merged = mergeRideHistory(local, api)
        assertEquals(18, merged[0].final_score)
    }

    @Test
    fun keepsLocalOperatingCostWhenApiOmitsIt() {
        val local = listOf(
            ride(id = 5462, price = 12.98, createdAt = "2026-10-15 22:19:00", score = 80)
                .copy(cost_per_mile = 0.05, price_per_mile = 0.05),
        )
        val api = listOf(ride(id = 5462, price = 12.98, createdAt = "2026-10-15 22:19:00", score = 80))
        val merged = mergeRideHistory(local, api)
        assertEquals(0.05, merged[0].cost_per_mile!!, 0.001)
    }

    @Test
    fun preferDisplayedScoreRestoresTruncatedApiWhenLocalIsRemainder() {
        assertEquals(110, preferDisplayedScore(10, 110))
        assertEquals(110, preferDisplayedScore(110, 10))
        assertEquals(18, preferDisplayedScore(18, 44))
    }
}
