package com.driver.pro.service

import com.driver.pro.RideRequest
import com.driver.pro.network.validateRideBeforeScoring
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import kotlin.math.abs

/** Phase 2 OCR corpus case id → resource basename under `ocr_corpus/`. */
val OCR_CORPUS_CASE_IDS: List<String> = listOf(
    // July 2025 regression
    "july25_e1w_ec2r",
    "july25_xl_37_41",
    "july25_el_sel_spitalfields",
    // Aug 2025 field bugs + controls
    "aug25_fare_17_54",
    "aug25_pickup_mi_0_1",
    "aug25_trip_mi_157_5",
    "aug25_ha0_pickup",
    "aug25_no_pickup_pc",
    "aug25_heathrow_ub3",
)

data class CorpusFieldResult(
    val caseId: String,
    val field: String,
    val expected: String,
    val actual: String,
    val passed: Boolean,
)

data class OcrAccuracyReport(
    val totalChecks: Int,
    val passedChecks: Int,
    val accuracyPercent: Double,
    val failures: List<CorpusFieldResult>,
) {
    val metTarget: Boolean get() = accuracyPercent >= OCR_ACCURACY_TARGET_PERCENT
}

const val OCR_ACCURACY_TARGET_PERCENT = 95.0

fun readCorpusResource(path: String): String {
    val stream = OcrCorpusRunner::class.java.classLoader!!.getResourceAsStream(path)
        ?: error("Missing corpus resource: $path")
    return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}

fun parseCorpusRide(caseId: String): RideRequest {
    val text = readCorpusResource("ocr_corpus/$caseId.txt")
    return fillMissingTripMetrics(text, parseRideInfo(text, null))
}

/** Run golden corpus expectations and return field-level accuracy. */
fun runOcrAccuracyReport(caseIds: List<String> = OCR_CORPUS_CASE_IDS): OcrAccuracyReport {
    val failures = mutableListOf<CorpusFieldResult>()
    var total = 0
    var passed = 0

    fun check(caseId: String, field: String, expected: String, actual: String, ok: Boolean) {
        total++
        if (ok) passed++ else failures.add(CorpusFieldResult(caseId, field, expected, actual, false))
    }

    for (caseId in caseIds) {
        val text = readCorpusResource("ocr_corpus/$caseId.txt")
        val expected = JSONObject(readCorpusResource("ocr_corpus/$caseId.json"))
        val ride = fillMissingTripMetrics(text, parseRideInfo(text, null))

        if (expected.has("pickup_postcode")) {
            val exp = expected.getString("pickup_postcode")
            val act = ride.pickup_address_postcode.orEmpty()
            check(caseId, "pickup_postcode", exp, act, exp == act)
        }
        if (expected.has("drop_postcode")) {
            val exp = expected.getString("drop_postcode")
            val act = ride.dropoff_address_postcode.orEmpty()
            check(caseId, "drop_postcode", exp, act, exp == act)
        }
        if (expected.has("price")) {
            val exp = expected.getDouble("price")
            val act = ride.price
            check(caseId, "price", exp.toString(), act.toString(), abs(exp - act) <= 0.05)
        }
        if (expected.has("rating")) {
            val exp = expected.getDouble("rating")
            val act = ride.rating
            check(caseId, "rating", exp.toString(), act.toString(), abs(exp - act) <= 0.05)
        }
        if (expected.has("pickup_miles")) {
            val exp = expected.getDouble("pickup_miles")
            val act = ride.pickup_distance_value ?: -1.0
            check(caseId, "pickup_miles", exp.toString(), act.toString(), abs(exp - act) <= 0.1)
        }
        if (expected.has("trip_miles")) {
            val exp = expected.getDouble("trip_miles")
            val act = ride.trip_distance_value ?: -1.0
            check(caseId, "trip_miles", exp.toString(), act.toString(), abs(exp - act) <= 0.1)
        }
        if (expected.optBoolean("must_score", false)) {
            val err = validateRideBeforeScoring(ride, text)
            check(caseId, "must_score", "null", err ?: "null", err == null)
        }
        if (expected.optBoolean("must_not_score", false)) {
            val err = validateRideBeforeScoring(ride, text)
            check(caseId, "must_not_score", "error", err ?: "null", err != null)
        }
    }

    val pct = if (total == 0) 100.0 else passed * 100.0 / total
    return OcrAccuracyReport(total, passed, pct, failures)
}

/** Marker for corpus harness package. */
private object OcrCorpusRunner
