package com.driver.pro.service

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 accuracy gate: corpus field checks must meet [OCR_ACCURACY_TARGET_PERCENT].
 * Run: ./gradlew :app:testReleaseUnitTest --tests com.driver.pro.service.RideOcrAccuracyReportTest
 */
class RideOcrAccuracyReportTest {

    @Test
    fun corpus_meets_accuracy_target() {
        val report = runOcrAccuracyReport()
        val summary = buildString {
            appendLine("OCR corpus accuracy: ${"%.1f".format(report.accuracyPercent)}%")
            appendLine("Passed ${report.passedChecks}/${report.totalChecks} field checks")
            appendLine("Target: ${OCR_ACCURACY_TARGET_PERCENT}%")
            if (report.failures.isNotEmpty()) {
                appendLine("Failures:")
                report.failures.forEach { f ->
                    appendLine("  ${f.caseId}.${f.field}: expected=${f.expected} actual=${f.actual}")
                }
            }
        }
        println(summary)
        assertTrue(
            summary,
            report.metTarget,
        )
    }
}
