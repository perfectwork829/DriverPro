package com.driver.pro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryClearFilterTest {

    private fun ride(createdAt: String) = RideRequest(
        id = 1,
        price = 10.0,
        rating = 4.5,
        pickup_time_minutes = 5,
        pickup_distance_value = 1.0,
        pickup_address_postcode = "W2",
        trip_time_minutes = 20,
        trip_distance_value = 3.0,
        dropoff_address_postcode = "SE1",
        start_time_window = null,
        end_time_window = null,
        acceptedOrRejected = 0,
        created_at = createdAt,
    )

    @Test
    fun hidesRidesBeforeClearTimestamp() {
        val clearedAt = parseRideCreatedAtMs("2026-08-11 18:00:00")!!
        assertFalse(isRideAfterHistoryClear(ride("2026-08-11 17:30:50"), clearedAt))
        assertTrue(isRideAfterHistoryClear(ride("2026-08-11 18:30:00"), clearedAt))
    }

    @Test
    fun hidesRidesWithMissingTimestampAfterClear() {
        assertFalse(isRideAfterHistoryClear(ride(""), 1_700_000_000_000L))
        assertTrue(isRideAfterHistoryClear(ride(""), 0L))
    }
}
