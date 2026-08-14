# OCR golden corpus (Phase 2)

Each case: `<id>.txt` (simulated ML Kit OCR dump) + `<id>.json` (expected fields).

## Run tests
./gradlew :app:testReleaseUnitTest --tests com.driver.pro.service.RideOcrCorpusTest
./gradlew :app:testReleaseUnitTest --tests com.driver.pro.service.RideOcrAccuracyReportTest

## Accuracy target
95% field-level pass rate across all JSON expectations (see OcrAccuracyReport.kt).

## Add a case
1. Add `<id>.txt` and `<id>.json` in this folder.
2. Register `<id>` in `OCR_CORPUS_CASE_IDS` (OcrAccuracyReport.kt).

JSON fields (all optional except id):
- pickup_postcode, drop_postcode (use "" when pickup must stay empty)
- price, rating, pickup_miles, trip_miles
- must_score, must_not_score
