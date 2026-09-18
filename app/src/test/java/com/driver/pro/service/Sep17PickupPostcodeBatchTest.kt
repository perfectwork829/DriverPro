package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 17 Sep 2026 client retest: missing/wrong pickup outwards + W14 3.2 mi. */
class Sep17PickupPostcodeBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    @Test
    fun id3034_ruby_zoe_wll_30g_pickup_w11() {
        val text = """
            2 UberX
            £8.97
            5.00
            Green
            Exclusive
            Verified
            30G
            £0.78 est. holiday entitlement included
            4 min (0.5 mi)
            Ruby-Zoe Hotel London. London. Wll
            20 mins (2.6 mi)
            50 Prebend Gardens. London. W6 OXU
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(8.97, ride.price, 0.05)
        assertEquals("W11", ride.pickup_address_postcode)
        assertEquals("W6", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3025_sw6_inq_travelodge_not_blank_pickup() {
        val text = """
            2 Electric Exclusive
            £ll.97
            ★ 5.00
            19 min (2.6 mi)
            Queen's CI
            Gardenst imited
            £l.01 est. holiday entitlement included
            SW6 INQ
            Travelodge London Fulham. Fulham.
            l stop
            19 mins (2.2 mi)
            Westfield Mall. London. W12 7GF
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(11.97, ride.price, 0.08)
        assertEquals("SW6", ride.pickup_address_postcode)
        assertEquals("W12", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3012_nw0_3du_is_nw10() {
        val text = """
            2 Comfort
            £12.62
            Cash payment * 5.00
            20 min (2.2 mi)
            £0.91 est. holiday entitlement included
            Verified
            2 Crediton Road. London. NW0 3DU
            29 mins (4.5 mi)
            Match
            Wembley Stadium. London. HA9 Ows
        """.trimIndent()
        val ride = parse(text)
        assertEquals(12.62, ride.price, 0.05)
        assertEquals("NW10", ride.pickup_address_postcode)
        assertEquals("HA9", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3005_qpark_w23_and_wiw5() {
        val text = """
            2 UberX
            £9.65
            ★ 4.77
            8 min (1.3 mi)
            £0.71 est. holiday entitlement included
            Q-Park Queensway. London. W23
            18 mins (2.5 mi)
            106 Hallam Street. London. Wiw 5
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(9.65, ride.price, 0.05)
        assertEquals("W2", ride.pickup_address_postcode)
        assertEquals("W1W", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id3002_ruby_zoe_wil_30g_to_w12() {
        val text = """
            2 Comfort Exclusive
            £8.07
            4.82
            4 min (0.5 mi)
            £0.73 est. holiday entitlement included
            30G
            Ruby-Zoe Hotel London. London. Wil
            14 mins (1.9 mi)
            White City House. London. W12 7FR
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(8.07, ride.price, 0.05)
        assertEquals("W11", ride.pickup_address_postcode)
        assertEquals("W12", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id2994_w2_pickup_not_se5_swap() {
        val text = """
            2 Electric Exclusive
            £20.949
            4.78
            Verified
            10 min (1.6 mi)
            3BA
            £l.65 est. holiday entitlement included
            45 mins (6.8 mi)
            Burgess Park. SE5 OJD
            Inhabit. Queen's Gardens. London. W2 io
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(20.94, ride.price, 0.08)
        assertEquals("W2", ride.pickup_address_postcode)
        assertEquals("SE5", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id2973_w14_keeps_3_2_miles() {
        val text = """
            2 UberX
            £13.02 9
            t 4.65
            9 min (1.2 mi)
            £l.l4 est. holiday entitlement included
            l stop
            27 mins (3.2 mi)
            Holland Inn Hotel. London. WI4 8HL
            67 Holland Road. London. W14 8
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(13.02, ride.price, 0.05)
        assertEquals("W14", ride.pickup_address_postcode)
        assertEquals("W14", ride.dropoff_address_postcode)
        assertEquals(3.2, ride.trip_distance_value!!, 0.15)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id2969_ladbroke_wl_3nw_is_w11_not_w1() {
        val text = """
            2 Uberx
            ★ 4.92
            £14.88
            3 min (0.3 mi)
            3NW
            £1.31 est. holiday entitlement included
            The Ladbroke Arms. London. Wl
            38 mins (4.7 mi)
            Chapter Kings Cross. London. N1 9JP
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(14.88, ride.price, 0.05)
        assertEquals("W11", ride.pickup_address_postcode)
        assertEquals("N1", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id2964_wi2_blb_is_w12_not_w1() {
        val text = """
            2 Uberx Exclusive
            £1719
            * 4.72
            10 min (0.8 mi)
            £1.49 est. holiday entitlement included
            38 mins (5.2 mi)
            Shepherd's Bush London Overground
            Station. London. WI2 BLB
            Clapham Junction Railway Station
            (CLJ). London. SW1l 1SP
            Confirm
        """.trimIndent()
        val ride = parse(text)
        assertEquals(17.19, ride.price, 0.08)
        assertEquals("W12", ride.pickup_address_postcode)
        assertEquals("SW11", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun id2957_elgin_wil_ipy_pickup_w11() {
        val text = """
            2 UberX
            £14.76
            5.00
            4 min (0.7 mi)
            £l16 est. holiday entitlement included
            The Elgin. London. WIl IPY
            37 mins (4.8 mi)
            4JH
            Page8 - Page Hotels. London. WC2N
            Match
        """.trimIndent()
        val ride = parse(text)
        assertEquals(14.76, ride.price, 0.05)
        assertEquals("W11", ride.pickup_address_postcode)
        assertEquals("WC2N", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }
}
