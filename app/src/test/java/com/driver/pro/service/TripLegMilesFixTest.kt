package com.driver.pro.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TripLegMilesFixTest {

    @Test
    fun longAssistTrip_upscales_15_75_to_157_5() {
        val line = "2 hr 42 min (15.75 mi)"
        val leg = parseTripLegFromLine(line)
        requireNotNull(leg)
        assertEquals(162, leg.minutes)
        assertEquals(157.5, leg.miles, 0.1)
    }

    @Test
    fun aug25AssistCorpus_tripMiles() {
        val text = readCorpusResource("ocr_corpus/aug25_trip_mi_157_5.txt")
        val legs = extractTripLegPairsInOrder(text)
        assertEquals(2, legs.size)
        assertEquals(157.5, legs[1].second, 0.1)
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))
        assertEquals(157.5, ride.trip_distance_value!!, 0.1)
    }
}
