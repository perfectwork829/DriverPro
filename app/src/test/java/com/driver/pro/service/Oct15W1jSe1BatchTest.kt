package com.driver.pro.service

import com.driver.pro.utils.computeFormulaScore
import com.driver.pro.utils.resolveReturnToZone
import com.driver.pro.utils.ReturnToZone
import org.junit.Assert.assertEquals
import org.junit.Test

/** 15 Oct 2026: Exec Piccadilly W1J → SE1 £21.84. Email 05/02/2026 adds +4 minutes. */
class Oct15W1jSe1BatchTest {

    private fun parse(text: String) =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun piccadilly_w1j_to_se1_parses_and_scores_104_at_50p() {
        val text = """
            ILL
            2 Exec
            Lambeth
            £21.84 o
            4.55
            Verified
            4 min (0.6 mi)
            £1.98 est. holiday entitlement included
            18 mins (3.9 mi)
            London. SEl 2BU
            WELLS
            K
            Piccadilly Circus. London. WlJ 9HN
            GRANGE
            Match
            X
            G
        """.trimIndent()
        val ride = parse(text)
        assertEquals(21.84, ride.price, 0.01)
        assertEquals(4, ride.pickup_time_minutes)
        assertEquals(0.6, ride.pickup_distance_value!!, 0.05)
        assertEquals(18, ride.trip_time_minutes)
        assertEquals(3.9, ride.trip_distance_value!!, 0.05)
        assertEquals("W1J", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
        assertEquals(ReturnToZone.NONE, resolveReturnToZone(ride))
        assertEquals(104, computeFormulaScore(ride, 0.50))
        assertEquals(116, computeFormulaScore(ride, 0.0))
    }

    @Test
    fun masons_sw1y_to_se1_parses_and_scores_121_at_50p() {
        val text = """
            London
            2 ExecExclusive
            £17.14 9
            4.91
            £1.58 est. holiday entitlement included
            l min (0.1 mi)
            13 Masons Yd. London. SWIY 6BU
            13 mins (2.6 mi)
            X
            One Blackfriars St George. London.
            SEl 9GD
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(17.14, ride.price, 0.01)
        assertEquals(1, ride.pickup_time_minutes)
        assertEquals(0.1, ride.pickup_distance_value!!, 0.05)
        assertEquals(13, ride.trip_time_minutes)
        assertEquals(2.6, ride.trip_distance_value!!, 0.05)
        assertEquals("SW1Y", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
        assertEquals(ReturnToZone.NONE, resolveReturnToZone(ride))
        assertEquals(121, computeFormulaScore(ride, 0.50))
    }

    @Test
    fun heathrow_t5_tw6_to_ub7_parses_and_scores_80_at_5p() {
        val text = """
            2 Electric Exclusive
            £12.98
            4.90
            Verified
            £l.18 est. holiday entitlement included
            4 min (0.6 mi)
            X
            Terminal 5. Terminal Parking 5. Level 1.
            Row R. TW6 2FF
            d 14 mins (3.1 mi)
            Park Inn by Radisson. Heathrow. UB7
            ODU
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(12.98, ride.price, 0.01)
        assertEquals(4, ride.pickup_time_minutes)
        assertEquals(0.6, ride.pickup_distance_value!!, 0.05)
        assertEquals(14, ride.trip_time_minutes)
        assertEquals(3.1, ride.trip_distance_value!!, 0.05)
        assertEquals("TW6", ride.pickup_address_postcode)
        assertEquals("UB7", ride.dropoff_address_postcode)
        assertEquals(ReturnToZone.NONE, resolveReturnToZone(ride))
        assertEquals(70, computeFormulaScore(ride, 0.50))
        assertEquals(80, computeFormulaScore(ride, 0.05))
    }

    @Test
    fun heathrow_t5_tw6_to_sw6_parses_61_min() {
        val text = """
            2 UberX Exclusive
            £25.43
            5.00
            £l.62 est. holiday entitlement included
            4 min (0.6 mi)
            Terminal5. Terminal Parking 5. Level 1.
            Row R. TW6 2FF
            X
            lhr l min (18.3 mi)
            17 Jaeger House. London. sW6 2GF
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(25.43, ride.price, 0.01)
        assertEquals(4, ride.pickup_time_minutes)
        assertEquals(0.6, ride.pickup_distance_value!!, 0.05)
        assertEquals(61, ride.trip_time_minutes)
        assertEquals(18.3, ride.trip_distance_value!!, 0.05)
        assertEquals("TW6", ride.pickup_address_postcode)
        assertEquals("SW6", ride.dropoff_address_postcode)
        assertEquals(ReturnToZone.NONE, resolveReturnToZone(ride))
        assertEquals(32, computeFormulaScore(ride, 0.50))
        assertEquals(49, computeFormulaScore(ride, 0.05))
    }

    @Test
    fun heathrow_t5_tw6_to_sw6_noisy_dump_still_sw6_not_ealing() {
        val text = """
            2 UberX Exclusive
            / £25.43
            5.00
            4 min (0.6 mi)
            £1.62 est. holiday entitlement included
            Row R. TW6 2FF
            w.ELLS
            lhr l min (18.3 mi)
            Ealing
            Terminal 5. Terminal Parking 5. Level 1.
            X
            Confirm
            17 Jaeger House. London. sW6 2GF
            es
            E
        """.trimIndent()
        val ride = parse(text)
        assertEquals(25.43, ride.price, 0.01)
        assertEquals(4, ride.pickup_time_minutes)
        assertEquals(0.6, ride.pickup_distance_value!!, 0.05)
        assertEquals(61, ride.trip_time_minutes)
        assertEquals(18.3, ride.trip_distance_value!!, 0.05)
        assertEquals("TW6", ride.pickup_address_postcode)
        assertEquals("SW6", ride.dropoff_address_postcode)
        assertEquals(49, computeFormulaScore(ride, 0.05))
    }

    @Test
    fun heathrow_t5_tw6_to_se15_parses_95_min() {
        val text = """
            2 Comfort Electric Exclusive
            £45.43
            4.80
            £3.35 est. holiday entitlement included
            4 min (0.6 mi)
            Terminal 5. Terminal 5. TW6 2FF
            lhr 35 min (25.4 mi)
            Peckham Rooms Hotel by Belvilla.
            London. SE15 2PT
            X
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(45.43, ride.price, 0.01)
        assertEquals(4, ride.pickup_time_minutes)
        assertEquals(0.6, ride.pickup_distance_value!!, 0.05)
        assertEquals(95, ride.trip_time_minutes)
        assertEquals(25.4, ride.trip_distance_value!!, 0.05)
        assertEquals("TW6", ride.pickup_address_postcode)
        assertEquals("SE15", ride.dropoff_address_postcode)
        assertEquals(ReturnToZone.NONE, resolveReturnToZone(ride))
        assertEquals(43, computeFormulaScore(ride, 0.50))
    }
}
