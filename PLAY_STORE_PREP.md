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

## v1.17 field-test notes (Aug hardening)

Parser fixes from client recordings/screenshots:

| Issue | Fix |
|-------|-----|
| HA0 pickup rejected | Allow HA + CR for district `0` |
| £17.54 → £1.75 | Tens-digit fare recovery |
| 0.1 mi → 0.11 mi | Short-leg decimal noise |
| 157.5 mi → 15.75 mi | Long Assist trip upscale + no erroneous scale-down |
| Borrowed drop postcode on pickup | Clear pickup when address has no PC |

Features from Phase 2 delivery order:

1. Clear History + confirm window (v1.14–v1.16)
2. Card detection (`OfferCardDetector`)
3. OCR corpus + 95% accuracy gate (this release)
4. Release APK path (this document)
