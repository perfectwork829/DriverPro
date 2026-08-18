package com.driver.pro.service

import org.junit.Assert.assertEquals
import org.junit.Test

/** Aug 2026 client field batch (56 offers, 13 errors) — regression gate for v1.18. */
class AugFieldBatchTest {

    private fun parse(text: String) =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun id2500_fare_17_19_not_1_71() {
        val text = """
            UberX
            Exclusive
            £1.71
            ★ 4.72
            £1.49 est. holiday entitlement included
            10 min (0.8 mi)
            Shepherd's Bush London Overground Station, London, W12 8LB
            38 mins (5.2 mi)
            Clapham Junction Railway Station (CLJ), London, SW11 1SP
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(17.1, ride.price, 0.15)
        assertEquals("W12", ride.pickup_address_postcode)
        assertEquals("SW11", ride.dropoff_address_postcode)
    }

    @Test
    fun id2488_w11_pickup_not_blank() {
        val text = """
            UberX
            Exclusive
            £6.29
            4.60
            5 min (0.8 mi)
            The Castle, London, W11 1LU
            13 mins (2.2 mi)
            Completedworks, London, NW1 5DA
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals("W11", ride.pickup_address_postcode)
        assertEquals("NW1", ride.dropoff_address_postcode)
    }

    @Test
    fun id2553_trip_miles_8_9_not_3_9() {
        val text = """
            Comfort
            Exclusive
            £19.72
            4.23
            13 min (3.7 mi)
            Baronet House, London, NW10 7GL
            41 mins (3.9 mi)
            Lavender Hill Magistrates' Court, London, SW11 1JU
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(8.9, ride.trip_distance_value!!, 0.1)
    }

    @Test
    fun id2551_drop_w6_not_map_n1() {
        val text = """
            UberX
            Exclusive
            £13.05
            4.63
            12 min (3.3 mi)
            N1
            Aspire Court, London, HA0 1TX
            30 mins (6.6 mi)
            Charing Cross Hospital, London, W6 8RF
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals("HA0", ride.pickup_address_postcode)
        assertEquals("W6", ride.dropoff_address_postcode)
    }

    @Test
    fun id2543_drop_wc2_not_map_cr0() {
        val text = """
            Electric
            Exclusive
            £17.76
            4.91
            14 min (4.9 mi)
            4 Bibsworth Road, London, N3 3RW
            CR0
            47 mins (8.5 mi)
            The National Gallery, London, WC2N 5DN
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals("N3", ride.pickup_address_postcode)
        assertEquals("WC2N", ride.dropoff_address_postcode)
    }

    @Test
    fun id2542_trip_miles_3_8_not_3_3() {
        val text = """
            UberX
            Exclusive
            £7.20
            4.86
            7 min (1.8 mi)
            78 Birchen Grove, London, NW9 8SA
            15 mins (3.3 mi)
            17 Swan Drive, London, NW9 5DE
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(3.8, ride.trip_distance_value!!, 0.1)
    }

    @Test
    fun id2529_pickup_miles_0_1_not_1_0() {
        val text = """
            UberX Priority
            Exclusive
            £5.91
            5.00
            +£1.16 included for priority
            2 min (1.0 mi)
            2 drury wy. n circular rd., London, NW10 0TH
            12 mins (3.0 mi)
            St Pauls Ave, London, NW2 5TD
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(0.1, ride.pickup_distance_value!!, 0.05)
    }

    @Test
    fun id2521_drop_blank_when_no_postcode_on_card() {
        val text = """
            UberX Priority
            Match
            £12.01
            4.60
            Cash payment
            +£1.27 included for priority
            18 min (5.7 mi)
            105-8 Lanacre Avenue, London, NW9 5AN
            14 mins (3.4 mi)
            Kings Dr, Wembley
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals("NW9", ride.pickup_address_postcode)
        assertEquals("", ride.dropoff_address_postcode.orEmpty())
    }

    @Test
    fun id2512_pickup_ha0_not_ha9() {
        val text = """
            UberX
            Match
            £7.10
            4.93
            11 min (3.1 mi)
            Allium House, Harrow, HA0 1BD
            11 mins (2.6 mi)
            Costco Wembley, London, HA9 0YJ
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals("HA0", ride.pickup_address_postcode)
        assertEquals("HA9", ride.dropoff_address_postcode)
    }

    @Test
    fun id2501_drop_blank_great_portland_st() {
        val text = """
            UberX Priority
            Match
            £17.56
            4.52
            16 min (4.1 mi)
            Braunston House, London, HA0 1RP
            30 mins (8.1 mi)
            Great Portland St, London
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals("HA0", ride.pickup_address_postcode)
        assertEquals("", ride.dropoff_address_postcode.orEmpty())
    }

    @Test
    fun id2556_truncated_sw1h_drop() {
        val text = """
            UberX
            Exclusive
            £15.79
            4.50
            7 min (1.9 mi)
            Dog Ln, London, NW10 1PP
            47 mins (8.1 mi)
            22-28 Broadway, London, SW1H 0
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals("NW10", ride.pickup_address_postcode)
        assertEquals("SW1H", ride.dropoff_address_postcode)
    }
}
