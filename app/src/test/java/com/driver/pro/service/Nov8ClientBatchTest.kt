package com.driver.pro.service

import com.driver.pro.ScoreThresholds
import com.driver.pro.decideRideFromScore
import com.driver.pro.mergeRideHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Client batch 8 Nov 2026: good offers auto-rejected; Slough drop minutes; drop "London" only. */
class Nov8ClientBatchTest {

    private val execSw3W14 = """
        2 Exec Exclusive
        £17.05
        Cash payment t 4.83
        £1.34 est. holiday entitlement included
        I min (0.2 mi)
        2 Elystan St. London. SW3 3Ns
        13 mins (2.5 mi)
        London. WI4 8QN
        X
        Confirm
    """.trimIndent()

    private val xlSw3Sw1e = """
        R
        KGSTRO
        2 UberXL Exclusive
        £22.30
        4.58
        Ebury Square
        Gardens
        5 min (0.8 mi)
        £1.48 est. holiday entitlement included
        X
        Albert Bridge Road. London. SW3 5
        13 mins (2.4 mi)
        70 Buckingham Gate. London. SWIE
        6AL
        Confirm
    """.trimIndent()

    private val sloughSl3 = """
        2 berx
        £8.22
        * 4.71 O Verified
        8 min (4.O mi)
        OAD
        £0.52 est. holiday entitlement included
        X
        42 Rayners Close. Slough. SL3 0LH
        8 mins (4 mi)
        57 Cheviot Road. Slough. SL3 8UE
        Match
    """.trimIndent()

    private val electricSe1London = """
        2 Electric Exclusive
        Ebury Square
        £15.49
        4.86
        £L18 est. holiday entitlement included
        3 min (0.5 mi)
        Waterloo Road. London. SEl 8SQ
        28 mins (6.0 mi)
        London
        X
        Confirm
    """.trimIndent()

    private fun parse(t: String) = fillMissingTripMetrics(t, parseRideInfo(t, null))

    @Test
    fun exec_card_parses_sw3_to_w14() {
        val r = parse(execSw3W14)
        assertEquals(17.05, r.price, 0.01)
        assertEquals(4.83, r.rating, 0.01)
        assertEquals(1, r.pickup_time_minutes)
        assertEquals("SW3", r.pickup_address_postcode)
        assertEquals(13, r.trip_time_minutes)
        assertEquals(2.5, r.trip_distance_value ?: 0.0, 0.05)
        assertEquals("W14", r.dropoff_address_postcode)
    }

    @Test
    fun uberxl_card_parses_swie_as_sw1e() {
        val r = parse(xlSw3Sw1e)
        assertEquals(22.30, r.price, 0.01)
        assertEquals(5, r.pickup_time_minutes)
        assertEquals(13, r.trip_time_minutes)
        assertEquals(2.4, r.trip_distance_value ?: 0.0, 0.05)
        assertEquals("SW1E", r.dropoff_address_postcode)
    }

    @Test
    fun slough_drop_minutes_are_8_not_68() {
        val r = parse(sloughSl3)
        assertEquals(8, r.trip_time_minutes)
        assertEquals("SL3", r.dropoff_address_postcode)
    }

    @Test
    fun drop_with_only_london_does_not_copy_pickup_postcode() {
        val r = parse(electricSe1London)
        assertEquals("SE1", r.pickup_address_postcode)
        assertEquals(28, r.trip_time_minutes)
        assertTrue(r.dropoff_address_postcode.isNullOrBlank())
    }

    @Test
    fun local_score_above_accept_is_accepted_even_if_server_rejects() {
        val band = ScoreThresholds(enabled = true, reject = 45, accept = 75)
        assertEquals(1, decideRideFromScore(118, band, serverDecision = -1))
        assertEquals(1, decideRideFromScore(127, band, serverDecision = -1))
        assertEquals(-1, decideRideFromScore(12, band, serverDecision = 0))
        assertEquals(0, decideRideFromScore(57, band, serverDecision = -1))
        assertEquals(0, decideRideFromScore(75, band, serverDecision = 1)) // label is "Accept > 75"
        assertEquals(0, decideRideFromScore(-5, band, serverDecision = -1))
    }

    @Test
    fun score_filter_off_falls_back_to_server_decision() {
        val off = ScoreThresholds(enabled = false, reject = 45, accept = 75)
        assertEquals(-1, decideRideFromScore(118, off, serverDecision = -1))
        assertEquals(0, decideRideFromScore(50, null, serverDecision = 1))
    }

    @Test
    fun history_merge_keeps_on_device_decision() {
        val local = parse(execSw3W14).copy(id = 5725, final_score = 118, acceptedOrRejected = 1)
        val api = local.copy(final_score = 18, acceptedOrRejected = -1)
        val merged = mergeRideHistory(listOf(local), listOf(api)).single()
        assertEquals(1, merged.acceptedOrRejected)
        assertEquals(118, merged.final_score)
    }
}
