package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 25 Aug 2026 client retest dumps from WhatsApp (Martyna).
 * Ground truth is the Uber card; OCR text is the noisy dump she pasted.
 */
class Aug25RetestBatchTest {

    private fun parse(text: String): RideRequest =
        fillMissingTripMetrics(text, parseRideInfo(text, null))

    private fun dump(label: String, ride: RideRequest) {
        println(
            "$label price=${ride.price} pickup=${ride.pickup_address_postcode} " +
                "drop=${ride.dropoff_address_postcode} " +
                "pMi=${ride.pickup_distance_value} tMi=${ride.trip_distance_value} " +
                "pMin=${ride.pickup_time_minutes} tMin=${ride.trip_time_minutes}",
        )
    }

    /** Clean screenshot of the 4 Aug Spitalfields Electric card. */
    @Test
    fun screenshot_6_43_e1_6qr_se1_9sp() {
        val text = """
            Electric
            £6.43
            Cash payment ★ 5.00
            £0.41 est. holiday entitlement included
            8 min (1.0 mi)
            Pizza Pilgrims Spitalfields, London, E1
            6QR
            13 mins (1.9 mi)
            London Bridge, London, SE1 9SP
            Match
        """.trimIndent()
        val ride = parse(text)
        dump("6.43-clean", ride)
        assertEquals(6.43, ride.price, 0.05)
        assertEquals("E1", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
        assertEquals(1.0, ride.pickup_distance_value!!, 0.05)
        assertEquals(1.9, ride.trip_distance_value!!, 0.05)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun fare_15_79_trip_8_1_not_3_1() {
        val text = """
            2 UberX Exclusive
            £15.79
            4.50
            Kensington
            7 min (1.9 mi)
            £l.01 est. holiday entitlement included
            Dog Ln. London. NW1O 1PP
            47 mins (8.1 mi)
            22-28 Broadway. London. SWIH 0
            Confirm
        """.trimIndent()
        val ride = parse(text)
        dump("15.79", ride)
        assertEquals(15.79, ride.price, 0.05)
        assertEquals("NW10", ride.pickup_address_postcode)
        assertEquals("SW1H", ride.dropoff_address_postcode)
        assertEquals(8.1, ride.trip_distance_value!!, 0.15)
    }

    @Test
    fun fare_20_09_trip_8_1_not_3_1() {
        val text = """
            2 Comfort Exclusive
            £20.09
            Cash payment
            1l min (3.3 mi)
            4.93
            £1.39 est. holiday entitlement included
            Next. London. NW21LS
            H 47 mins (8.l mi)
            2AE
            A501
            Clement House. LSE. London. WC2A
            Confirm
        """.trimIndent()
        val ride = parse(text)
        dump("20.09", ride)
        assertEquals(20.09, ride.price, 0.05)
        assertEquals("NW2", ride.pickup_address_postcode)
        assertEquals("WC2A", ride.dropoff_address_postcode)
        assertEquals(8.1, ride.trip_distance_value!!, 0.15)
    }

    @Test
    fun fare_17_76_trip_8_5_not_3_5() {
        val text = """
            2 Electric
            £17.76
            Exclusive
            4.91
            A240
            £1.01 est. holiday entitlement included
            14 min (4.9 mi)
            4 Bibsworth Road. London. N3 3RW
            d 47 mins (8.5 mi)
            Confirm
            The National Gallery. London. Wc2N
            5DN
        """.trimIndent()
        val ride = parse(text)
        dump("17.76", ride)
        assertEquals(17.76, ride.price, 0.05)
        assertEquals("N3", ride.pickup_address_postcode)
        assertEquals("WC2N", ride.dropoff_address_postcode)
        assertEquals(8.5, ride.trip_distance_value!!, 0.15)
    }

    @Test
    fun fare_7_20_pickup_nw9_not_map_n1() {
        val text = """
            ANE
            rent
            NI
            lin
            BREN
            2 UberX Exclusive
            £7.20
            4.86
            7 min (1.8 mi)
            MAPESBURY
            DUDDEN HILL
            £0 45 est. holiday entitlement included
            Confirm
            78 Birchen Grove. London. NW9 8SA
            15 mins (3.8 mi)
            17 Swan Drive. London. NW9 5DE
        """.trimIndent()
        val ride = parse(text)
        dump("7.20", ride)
        assertEquals(7.20, ride.price, 0.05)
        assertEquals("NW9", ride.pickup_address_postcode)
        assertEquals("NW9", ride.dropoff_address_postcode)
        assertTrue(ride.pickup_address_postcode != "N1")
    }

    @Test
    fun fare_13_61_trip_8_1() {
        val text = """
            2 Uberx
            £13.61
            New requests
            4.53
            Verifięd
            £0.78 est. holiday entitlement included
            14 min (3.5 mi)
            34 Wilson Drive. Wembley. HA9 9TX
            27 mins (8.1 mi)
            Dental Art Implant Clinic - East
            Finchley. London. N2 8AG
            Match
        """.trimIndent()
        val ride = parse(text)
        dump("13.61", ride)
        assertEquals(13.61, ride.price, 0.05)
        assertEquals("HA9", ride.pickup_address_postcode)
        assertEquals("N2", ride.dropoff_address_postcode)
        assertEquals(8.1, ride.trip_distance_value!!, 0.15)
    }

    @Test
    fun fare_17_81_trip_8_7() {
        val text = """
            2 Comfort Exclusive
            £17.81
            4.97
            7 min (1.5 mi)
            £1.31 est. holiday entitlement included
            OLQ
            CHURG
            Wembley Stadium. Carey Way. HA9
            h 44 mins (8.7 mi)
            Confirm
            The Chelsea FC Megastore. London.
            SW6 IHS
        """.trimIndent()
        val ride = parse(text)
        dump("17.81", ride)
        assertEquals(17.81, ride.price, 0.05)
        assertEquals("HA9", ride.pickup_address_postcode)
        assertEquals("SW6", ride.dropoff_address_postcode)
        assertEquals(8.7, ride.trip_distance_value!!, 0.15)
    }

    @Test
    fun fare_8_16_pickup_ha9_from_has_oyj() {
        val text = """
            PARK ROYAL
            2 Uberx Exclusive
            £8.16
            4.67
            Verified
            £0.56 est. holiday entitlement included
            7 min (1.5 mi)
            Costco Wholesale. London. HAS OYJ
            20 mins (3.8 mi)
            London. NW10 6FJ
            Confirm
        """.trimIndent()
        val ride = parse(text)
        dump("8.16-HAS", ride)
        assertEquals(8.16, ride.price, 0.05)
        assertEquals("HA9", ride.pickup_address_postcode)
        assertEquals("NW10", ride.dropoff_address_postcode)
    }

    @Test
    fun fare_8_32_keep_short_trip_miles() {
        val text = """
            2 Electric
            £8.32
            4.65
            1l min (3.9 mi)
            Hampstead He
            £0.43 est. holiday entitlement included
            4 Rundell Crescent. London. NW4 3BP
            14 mins (3.9 mi)
            Match
            Vue Cinema London - North Finchley.
            London. N12 0GL
        """.trimIndent()
        val ride = parse(text)
        dump("8.32", ride)
        assertEquals(8.32, ride.price, 0.05)
        assertEquals("NW4", ride.pickup_address_postcode)
        assertEquals("N12", ride.dropoff_address_postcode)
        assertEquals(3.9, ride.trip_distance_value!!, 0.15)
    }

    @Test
    fun fare_17_56_trip_8_1_blank_drop() {
        val text = """
            2 UberX Priority
            £17.56
            4.52
            Verified
            £1.06 est. holiday entitlement included
            +£2.46 included for priority
            16 min (4.l mi)
            Braunston House. London. HAO 1RP
            30 mins (8.1 mi)
            Great Portland St. London
            Match
        """.trimIndent()
        val ride = parse(text)
        dump("17.56", ride)
        assertEquals(17.56, ride.price, 0.05)
        assertEquals("HA0", ride.pickup_address_postcode)
        assertEquals("", ride.dropoff_address_postcode.orEmpty())
        assertEquals(8.1, ride.trip_distance_value!!, 0.15)
    }

    @Test
    fun fare_8_29_pickup_ha9_not_w5() {
        val text = """
            2 Uberx
            ARK ROYAL
            4.75
            Exclusive
            £8.29
            £0.55 est. holiday entitlement included
            9 min (2.3 mi)
            Bus Stop G (Point Place). Wembley.
            HA9 GDE
            t 18 mins (3.4 mi)
            PureGym London Ealing Broadway.
            London. W5 5JY
            Confirm
        """.trimIndent()
        val ride = parse(text)
        dump("8.29", ride)
        assertEquals(8.29, ride.price, 0.05)
        assertEquals("HA9", ride.pickup_address_postcode)
        assertEquals("W5", ride.dropoff_address_postcode)
    }

    @Test
    fun fare_15_53_drop_sw1w_from_sw1ww() {
        val text = """
            2 Electric
            £15.53
            4.78
            14 min (2.1 mi)
            £1.22 est. holiday entitlement included
            Le Cochonnet. London. W9 1LU
            26 mins (3.7 mi)
            Match
            Sloane Square. London. SW1Ww 8BB
        """.trimIndent()
        val ride = parse(text)
        dump("15.53", ride)
        assertEquals(15.53, ride.price, 0.05)
        assertEquals("W9", ride.pickup_address_postcode)
        assertEquals("SW1W", ride.dropoff_address_postcode)
        assertEquals(3.7, ride.trip_distance_value!!, 0.15)
    }

    @Test
    fun fare_17_09_not_17_0_trip_3_1_not_8_1() {
        val text = """
            2 Comfort
            £1709 o
            * 4.12
            Hyde Park
            £1.58 est. holiday entitlement included
            8 min (1.3 mi)
            Queensway Computer Market.
            London. W2 4QP
            d 26 mins (3.1 mi)
            London Euston Station (EUS). London.
            NWI 2RT
            Match
        """.trimIndent()
        val ride = parse(text)
        dump("17.09", ride)
        assertEquals(17.09, ride.price, 0.08)
        assertEquals("W2", ride.pickup_address_postcode)
        assertEquals("NW1", ride.dropoff_address_postcode)
        assertEquals(3.1, ride.trip_distance_value!!, 0.15)
    }

    @Test
    fun fare_13_02_trip_8_2_not_3_2() {
        val text = """
            2 UberX
            £13.02 9
            t 4.65
            9 min (1.2 mi)
            £l.l4 est. holiday entitlement included
            Holland Inn Hotel. London. WI4 8HL
            27 mins (3.2 mi)
            67 Holland Road. London. W14 8
            Match
        """.trimIndent()
        val ride = parse(text)
        dump("13.02", ride)
        assertEquals(13.02, ride.price, 0.05)
        assertEquals("W14", ride.pickup_address_postcode)
        assertEquals("W14", ride.dropoff_address_postcode)
        assertEquals(8.2, ride.trip_distance_value!!, 0.15)
    }

    @Test
    fun fare_17_19_not_17_1() {
        val text = """
            2 Uberx Exclusive
            £1719
            *4.72
            10 min (0.8 mi)
            £1.49 est. holiday entitlement included
            38 mins (5.2 mi)
            Shepherd's Bush London Overground
            Station. London. W12 8LB
            Clapham Junction Railway Station
            (CLJ). London. SW1l 1SP
            Confirm
        """.trimIndent()
        val ride = parse(text)
        dump("17.19", ride)
        assertEquals(17.19, ride.price, 0.08)
        assertEquals("W12", ride.pickup_address_postcode)
        assertEquals("SW11", ride.dropoff_address_postcode)
    }

    @Test
    fun fare_4_87_pickup_w11_from_w111() {
        val text = """
            2 uberX
            Exclusive
            £4.87
            4.60
            5 min (0.8 mi)
            £0.39 est. holiday entitlement included
            Buns From Home. London. W111
            8 mins (1.4 mi)
            8 Conlan St. London
            Confirm
        """.trimIndent()
        val ride = parse(text)
        dump("4.87", ride)
        assertEquals(4.87, ride.price, 0.05)
        assertEquals("W11", ride.pickup_address_postcode)
    }
}
