package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.charset.StandardCharsets

/**
 * Golden OCR corpus: each case is `ocr_corpus/<id>.txt` + `ocr_corpus/<id>.json`.
 * Register new ids in [CASE_IDS] (and add the two resource files).
 *
 * JSON fields (assert only those present):
 * pickup_postcode, drop_postcode, price, rating, pickup_miles, trip_miles,
 * must_score, must_not_score
 */
@RunWith(Parameterized::class)
class RideOcrCorpusTest(private val caseId: String) {

    @Test
    fun corpus_case() {
        val text = readResource("ocr_corpus/$caseId.txt")
        val expected = JSONObject(readResource("ocr_corpus/$caseId.json"))
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))

        if (expected.has("pickup_postcode")) {
            assertEquals(
                "[$caseId] pickup",
                expected.getString("pickup_postcode"),
                ride.pickup_address_postcode.orEmpty(),
            )
        }
        if (expected.has("drop_postcode")) {
            assertEquals(
                "[$caseId] drop",
                expected.getString("drop_postcode"),
                ride.dropoff_address_postcode.orEmpty(),
            )
        }
        if (expected.has("price")) {
            assertEquals("[$caseId] price", expected.getDouble("price"), ride.price, 0.05)
        }
        if (expected.has("rating")) {
            assertEquals("[$caseId] rating", expected.getDouble("rating"), ride.rating, 0.05)
        }
        if (expected.has("pickup_miles")) {
            assertEquals(
                "[$caseId] pickup mi",
                expected.getDouble("pickup_miles"),
                ride.pickup_distance_value!!,
                0.1,
            )
        }
        if (expected.has("trip_miles")) {
            assertEquals(
                "[$caseId] trip mi",
                expected.getDouble("trip_miles"),
                ride.trip_distance_value!!,
                0.1,
            )
        }
        if (expected.optBoolean("must_score", false)) {
            assertNull(
                "[$caseId] should score: ${validateRideBeforeScoring(ride, text)}",
                validateRideBeforeScoring(ride, text),
            )
        }
        if (expected.optBoolean("must_not_score", false)) {
            assertTrue(
                "[$caseId] should not score",
                validateRideBeforeScoring(ride, text) != null,
            )
        }
    }

    companion object {
        /** Golden corpus — see also [OCR_CORPUS_CASE_IDS]. */
        private val CASE_IDS = OCR_CORPUS_CASE_IDS

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<String>> = CASE_IDS.map { arrayOf(it) }

        private fun readResource(path: String): String {
            val stream = RideOcrCorpusTest::class.java.classLoader!!.getResourceAsStream(path)
                ?: error("Missing corpus resource: $path")
            return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }
    }
}

class AccessibilityRideHintsTest {
    @Test
    fun merge_fills_blank_postcodes_and_recovers_fare() {
        val ocr = RideRequest(
            id = 0,
            price = 3.74,
            rating = 3.50,
            pickup_time_minutes = 11,
            pickup_distance_value = 2.3,
            pickup_address_postcode = null,
            trip_time_minutes = 65,
            trip_distance_value = 11.2,
            dropoff_address_postcode = null,
            start_time_window = null,
            end_time_window = null,
            acceptedOrRejected = 0,
            type = "",
        )
        val hints = extractAccessibilityRideHints(
            """
            UberXL
            £37.41
            ★ 4.93
            11 min (2.3 mi)
            Croydon, CR0 3LW
            1 hr 5 min (11.2 mi)
            London, EC2M 7PY
            Long trip (60+ min)
            Match
            """.trimIndent(),
        )
        val merged = mergeAccessibilityIntoRide(ocr, hints)
        assertEquals(37.41, merged.price, 0.05)
        assertEquals(4.93, merged.rating, 0.05)
        assertEquals("CR0", merged.pickup_address_postcode)
        assertEquals("EC2M", merged.dropoff_address_postcode)
        assertEquals("match", merged.type)
    }

    @Test
    fun overlay_lists_missing_fields() {
        val ride = RideRequest(
            id = 0, price = 6.43, rating = 5.0,
            pickup_time_minutes = 8, pickup_distance_value = 1.0,
            pickup_address_postcode = "E1",
            trip_time_minutes = 13, trip_distance_value = 1.9,
            dropoff_address_postcode = null,
            start_time_window = null, end_time_window = null,
            acceptedOrRejected = 0, type = "match",
        )
        val missing = listMissingRideFields(ride, "dummy")
        assertTrue(missing.any { it.contains("drop", ignoreCase = true) })
        assertTrue(formatIncompleteOverlayMessage("OCR incomplete", missing).contains("drop"))
    }
}
