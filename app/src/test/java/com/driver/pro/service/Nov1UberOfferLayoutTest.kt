package com.driver.pro.service

import com.driver.pro.network.validateRideBeforeScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Nov1UberOfferLayoutTest {

    private val polishMatch = """
        UberX
        12,70 GBP
        Wliczone uprawnienie do wolnego: ok. 0,90
        18 minut • 7.2 mil
        11 Beech Road, Westerham, TN16 3JQ
        ★ 4,96  Zweryfikowano
        17 minut • 6.7 mil
        Bromley Common, Bromley, BR2 8HA
        Dopasowanie
    """.trimIndent()

    private val polishAccept = """
        UberX Tylko dla Ciebie
        6,68 GBP
        Wliczone uprawnienie do wolnego: ok. 0,40
        2 minut • 0.3 mil
        London
        ★ 4,66
        11 minut • 3.9 mil
        East Croydon Railway Station, Croydon, CR0 1LF
        Akceptuj
    """.trimIndent()

    @Test
    fun tripLeg_parses_polish_bullet_and_english_bullet() {
        val polish = parseTripLegFromLine("18 minut • 7.2 mil")
        assertEquals(18, polish?.minutes)
        assertEquals(7.2, polish?.miles ?: 0.0, 0.001)

        val english = parseTripLegFromLine("11 mins • 3.9 miles")
        assertEquals(11, english?.minutes)
        assertEquals(3.9, english?.miles ?: 0.0, 0.001)
    }

    @Test
    fun isRideOfferCard_accepts_polish_and_english_new_layout() {
        assertTrue(isRideOfferCard(polishMatch))
        assertTrue(isRideOfferCard(polishAccept))
        assertTrue(
            isRideOfferCard(
                """
                UberX
                £8.10
                4 min • 0.5 mi
                SW1A 1AA
                12 min • 2.1 mi
                SE1 2AA
                Accept
                """.trimIndent(),
            ),
        )
        assertFalse(isRideOfferCard("map only Bromley A232"))
    }

    @Test
    fun parseRideInfo_polish_match_card_scores() {
        val ride = fillMissingTripMetrics(polishMatch, parseRideInfo(polishMatch, null))
        assertEquals(12.70, ride.price, 0.01)
        assertEquals(4.96, ride.rating, 0.01)
        assertEquals(18, ride.pickup_time_minutes)
        assertEquals(7.2, ride.pickup_distance_value ?: 0.0, 0.05)
        assertEquals(17, ride.trip_time_minutes)
        assertEquals(6.7, ride.trip_distance_value ?: 0.0, 0.05)
        assertEquals("TN16", ride.pickup_address_postcode)
        assertEquals("BR2", ride.dropoff_address_postcode)
        assertEquals("match", ride.type)
        assertNull(validateRideBeforeScoring(ride, polishMatch))
    }

    @Test
    fun parseRideInfo_polish_accept_without_pickup_postcode_does_not_invent() {
        val ride = fillMissingTripMetrics(polishAccept, parseRideInfo(polishAccept, null))
        assertEquals(6.68, ride.price, 0.01)
        assertEquals(4.66, ride.rating, 0.01)
        assertEquals(2, ride.pickup_time_minutes)
        assertEquals(0.3, ride.pickup_distance_value ?: 0.0, 0.05)
        assertEquals(11, ride.trip_time_minutes)
        assertEquals(3.9, ride.trip_distance_value ?: 0.0, 0.05)
        assertEquals("", ride.pickup_address_postcode.orEmpty())
        assertEquals("CR0", ride.dropoff_address_postcode)
        assertEquals("confirm", ride.type)
        assertNotNull(validateRideBeforeScoring(ride, polishAccept))
    }

    @Test
    fun holiday_entitlement_polish_is_not_the_fare() {
        assertTrue(isAddonFareLine("Wliczone uprawnienie do wolnego: ok. 0,90"))
        val ride = parseRideInfo(polishMatch, null)
        assertEquals(12.70, ride.price, 0.01)
    }
}
