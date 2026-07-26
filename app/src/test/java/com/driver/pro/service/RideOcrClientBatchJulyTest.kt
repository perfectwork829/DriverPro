package com.driver.pro.service

import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests from client field reports (Jul 2026 batch).
 */
class RideOcrClientBatchJulyTest {

    @Test
    fun truncated_sl3_sl2_stoke_poges() {
        val text = """
            UberX
            £10.18
            5.00
            13 min (5.1 mi)
            Fulmer Common Road. Slough. SL3 6
            5 mins (1.8 mi)
            Khalsa Secondary Academy. Stoke Poges. SL2 4
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(10.18, ride.price, 0.001)
        assertEquals("SL3", ride.pickup_address_postcode)
        assertEquals("SL2", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun concatenated_sl41lh_windsor_legoland() {
        val text = """
            Electric
            £6.41
            Cash payment
            4.57
            13 min (4.9 mi)
            SL41LH
            Macdonald Windsor Hotel. Windsor.
            12 mins (3.5 mi)
            Windsor. SL4 4AY
            LEGOLAND Woodland Village.
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(6.41, ride.price, 0.001)
        assertEquals("SL4", ride.pickup_address_postcode)
        assertEquals("SL4", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun pickup_miles_keeps_0_3_not_forced_to_0_8() {
        val text = """
            UberX
            £8.15
            4.76 Verified
            4 min (0.3 mi)
            Kingston Road, Staines-upon-Thames, TW18 4LT
            20 mins (7.0 mi)
            Virgin Atlantic, Hounslow, TW6 2GW
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(0.3, ride.pickup_distance_value!!, 0.001)
    }

    @Test
    fun price_prefers_pound_fare_not_inflated_30() {
        val text = """
            UberX
            £14.55
            5.00 Verified
            £0.99 est. holiday entitlement included
            9 min (2.4 mi)
            5 Caledonia Road, Staines, TW19 7
            22 mins (7.3 mi)
            Isleworth Crown Court, London, TW7 5LP
            Match
            30.00
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(14.55, ride.price, 0.001)
        assertTrue(ride.price < 20.0)
    }

    @Test
    fun truncated_al2_colney_street_not_blank() {
        val text = """
            UberXL
            £16.44
            4.43 Verified
            15 min (7.9 mi)
            Ventura Park, Old Parkbury Lane, Colney Street, AL2 2
            16 mins (6.7 mi)
            McDonald's (High Street), Watford, WD17 2BJ
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("AL2", ride.pickup_address_postcode)
        assertEquals("WD17", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun harry_potter_wd25_pickup_al1_drop_not_swapped() {
        val text = """
            Comfort
            £14.62
            4.43 Verified
            8 min (2.7 mi)
            Harry Potter Studios, Pick-up point, WD25 7GD
            20 mins (7.4 mi)
            St Albans City, St. Albans, AL1 5
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("WD25", ride.pickup_address_postcode)
        assertEquals("AL1", ride.dropoff_address_postcode)
    }

    @Test
    fun watford_wd19_london_only_drop_allowed() {
        val text = """
            Electric
            £20.33
            5.00
            8 min (2.6 mi)
            63 Eastbury Rd. Watford. WDI9
            50 mins (18.5 mi)
            London
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("WD19", ride.pickup_address_postcode)
        assertTrue(ride.dropoff_address_postcode.isNullOrBlank())
        val err = validateRideBeforeScoring(ride, text)
        assertTrue("Should not block on drop postcode when destination is London-only, got $err",
            err == null || !err.contains("drop-off postcode"))
    }

    @Test
    fun st_thomas_sel_7eh_orphan_pickup_line() {
        val text = """
            Electric
            £12.43
            4.68
            10 min (1.3 mi)
            St Thomas' Hospital. Pick-up point.
            SEl 7EH
            15 mins (1.8 mi)
            Waleran Flats. London. SEl 5XB
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(12.43, ride.price, 0.001)
        assertEquals("SE1", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }

    @Test
    fun novotel_waterloo_sel_7ls_pickup() {
        val text = """
            Comfort
            £8.73
            4.59
            6 min (0.8 mi)
            Novotel London Waterloo. London.
            SEl 7LS
            7 mins (1.0 mi)
            London Waterloo Train Station. London. SEl 8SW
            Match
        """.trimIndent()
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals("SE1", ride.pickup_address_postcode)
        assertEquals("SE1", ride.dropoff_address_postcode)
        assertNull(validateRideBeforeScoring(ride, text))
    }
}
