# Play Store prep path (DriverPro)

Current test builds (v1.17+) ship as **signed release APK** using the **debug keystore** so the client can sideload without a production key. This is intentional for field testing — not for Play submission.

## Test APK (client field builds)

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Verify version on device: **History tab** shows `History (v1.17)`.

## OCR quality gate (Phase 2)

```bash
./gradlew :app:testReleaseUnitTest --tests com.driver.pro.service.RideOcrCorpusTest
./gradlew :app:testReleaseUnitTest --tests com.driver.pro.service.RideOcrAccuracyReportTest
```

Target: **≥95%** field-level accuracy (`OCR_ACCURACY_TARGET_PERCENT` in `OcrAccuracyReport.kt`).  
Corpus cases live in `app/src/test/resources/ocr_corpus/`.

## Before Play Console upload

1. **Production signing**
   - Generate a release keystore (store securely; back up).
   - Replace `signingConfigs.release` in `app/build.gradle.kts` with production `storeFile`, passwords, and alias.
   - Never commit keystore or passwords to git.

2. **Build AAB (required for Play)**

   ```bash
   ./gradlew :app:bundleRelease
   ```

   Output: `app/build/outputs/bundle/release/app-release.aab`

3. **Versioning**
   - Bump `versionCode` (integer, monotonic) and `versionName` in `app/build.gradle.kts` for each upload.

4. **Play Console checklist**
   - App content: accessibility / screen capture disclosure (MediaProjection + Accessibility Service).
   - Data safety form (network API to idrivesmart.co.uk, Firebase Analytics/Crashlytics).
   - Target audience and permissions justification (overlay, accessibility, foreground service).
   - Internal testing track first → closed testing with client devices.

5. **Optional hardening before store**
   - Enable R8/minify in `release { isMinifyEnabled = true }` after smoke-testing signed AAB.
   - Remove duplicate manifest permissions flagged at build time.
   - Replace debug-signed release with Play App Signing upload key.

## v1.18 field-test notes (Aug 2026 client batch — 56 offers, 13 errors)

Parser fixes from 15 paired client cases:

| Issue | Fix |
|-------|-----|
| £17.19 → £1.71 | Tens-digit fare recovery from low £ OCR + ignore holiday add-on £ |
| W11 / NW10 pickup missing | Leg-zone postcode recovery + final zone pass |
| Map label drop PC (N1, CR0) | Skip bare map district tokens without address |
| Borrowed drop PC (NW9, HA0) | Clear drop when Uber omits postcode on drop line |
| HA0 → HA9 swap | Final zone pass when pickup==drop |
| 8.9 → 3.9 trip miles | 3.x→8.x upscale; keep 8.9 (don't downgrade 8.8→3.8 range bug) |
| 3.8 → 3.3 trip miles | Decimal 8→3 recovery on 12–20 min legs |
| 0.1 → 1.0 pickup | Stop treating 0.1 mi as 0.0 mi→1.0 mi |
| Score overlay on offer | Moved to bottom-left (was top, covering fare) |

Regression gate: `AugFieldBatchTest` (11 client cases) + corpus ≥95%.

Features from Phase 2 delivery order:

1. Clear History + confirm window (v1.14–v1.16)
2. Card detection (`OfferCardDetector`)
3. OCR corpus + 95% accuracy gate (this release)
4. Release APK path (this document)
