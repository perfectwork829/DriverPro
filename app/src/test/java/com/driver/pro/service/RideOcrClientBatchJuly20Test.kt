package com.driver.pro.service

import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Client field reports 2026-07-20. */
class RideOcrClientBatchJuly20Test {

    @Test
    fun rating_4_71_not_rounded_to_4_70() {
        val text = """
            UberX Exclusive
            £4.85
            ★ 4.71
            £0.44 est. holiday entitlement included
            1 min (0.1 mi)
            Harrington Road, London, SW7 3EX
            8 mins (1.1 mi)
            Radnor Walk, London
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.71, ride.rating, 0.001)
        assertEquals(4.85, ride.price, 0.001)
    }

    @Test
    fun hammersmith_w6_pickup_big_ben_sw1a_drop_not_swapped() {
        val text = """
            UberXL Exclusive
            £21.41
            ★ 4.88
            £1.50 est. holiday entitlement included
            15 min (2.0 mi)
            Premier Inn London Hammersmith Hotel, London, W6 8DN
            30 mins (4.9 mi)
            Big Ben, London, SW1A 0AA
            Match
            SW1A
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("W6", ride.pickup_address_postcode)
        assertEquals("SW1A", ride.dropoff_address_postcode)
    }

    @Test
    fun truncated_w4_1_pickup_not_empty() {
        val text = """
            UberX Exclusive
            £14.24
            Cash payment ★ 5.00
            £0.96 est. holiday entitlement included
            13 min (2.6 mi)
            10 Windmill Road, London, W4 1
            31 mins (5.2 mi)
            115 Park Street, London, W1K 7JE
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("W4", ride.pickup_address_postcode)
        assertEquals("W1K", ride.dropoff_address_postcode)
    }

    @Test
    fun trip_leg_parses_1_l_mi_and_nmi() {
        val pickup = parseTripLegFromLine("6 min (1.l mi)")
        assertEquals(6, pickup!!.minutes)
        assertEquals(1.1, pickup.miles, 0.001)

        val drop = parseTripLegFromLine("19 mins (3.2 nmi)")
        assertEquals(19, drop!!.minutes)
        assertEquals(3.2, drop.miles, 0.001)

        val text = """
            UberX Exclusive
            £8.13
            ★ 4.59
            £0.62 est. holiday entitlement included
            6 min (1.l mi)
            Distillery Wharf. London. W6 9HR
            19 mins (3.2 nmi)
            Imperial Wharf. London. SW6 2UB
            Confirm
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(6, ride.pickup_time_minutes)
        assertEquals(19, ride.trip_time_minutes)
        assertEquals(1.1, ride.pickup_distance_value!!, 0.001)
        assertEquals(3.2, ride.trip_distance_value!!, 0.001)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun rating_4_92_on_whole_pound_fare_not_3_50() {
        val text = """
            Electric Exclusive
            £37
            ★ 4.92
            £2.49 est. holiday entitlement included
            8 min (1.1 mi)
            Sydney St, London, SW3 6NJ
            1 hr 39 min (24.1 mi)
            Sandpiper Cl, Greenhithe, DA9 9RU
            Match
            Long trip (60+ min)
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(4.92, ride.rating, 0.001)
        assertEquals(37.0, ride.price, 0.001)
    }

    @Test
    fun call_banner_pol_does_not_become_pickup_postcode() {
        assertTrue(
            "Pol alone must not yield a postcode",
            extractOuterLondonPostcodes("Incoming call\nWojtek Pol Heavy Hulage").none {
                it.startsWith("P") || it.startsWith("PO")
            },
        )
        val text = """
            Incoming call
            Wojtek Pol Heavy Hulage
            Electric Exclusive
            £8.03
            ★ 4.66
            £0.53 est. holiday entitlement included
            7 min (1.2 mi)
            Villa Mamas, London, SW3 3NT
            18 mins (2.7 mi)
            Hyatt Regency London - The Churchill, London, W1H 7BH
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("SW3", ride.pickup_address_postcode)
        assertEquals("W1H", ride.dropoff_address_postcode)
        assertFalse(ride.pickup_address_postcode!!.startsWith("P"))
    }
}
