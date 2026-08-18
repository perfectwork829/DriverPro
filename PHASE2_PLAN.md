# DriverPro — Phase 2 Plan

**Goal:** Stabilize the existing DriverPro flow for daily driver use — reliable OCR on Uber Match/Confirm cards, correct scoring API payloads, trustworthy History, and a testable release build path. **Not** a Play Store submission in this phase.

**Agreed deadline (PM):** Full testable build by **Friday, 28 August 2026** (no Play Store required).

**Current build:** **v1.18** — `app/build/outputs/apk/release/app-release.apk`

---

## Delivery order (4 workstreams)

| # | Workstream | Purpose | Status | Versions |
|---|------------|---------|--------|----------|
| 1 | **Clear History + confirm window** | Visible client wins; manual safety when OCR is unsure | ✅ Done | v1.14–v1.16 |
| 2 | **Card detection** | Crop the Uber offer card before OCR (not full screen noise) | ✅ Done | v1.15 |
| 3 | **OCR corpus + accuracy report** | Measurable ≥95% field accuracy gate; regression harness | ✅ Done | v1.17–v1.18 |
| 4 | **Field hardening + release APK / Play prep path** | Fix real client bugs; sideload APK; document Play steps | ✅ Done | v1.17–v1.18 |

---

## 1. Clear History + confirm window

### Requirements
- **Clear History** button on History tab — clears local list and stops server rows repopulating the UI after clear.
- **Confirm window** (Accept / Decline / Skip overlay) when OCR accuracy &lt; 80% or key fields are missing — pauses auto-accept/reject.

### Implementation
| Area | Location |
|------|----------|
| Clear History UI | `HistoryScreen.kt` |
| Server row filter after clear | `StorageManage.kt` — `markHistoryCleared()` / `isRideAfterHistoryClear()` |
| Confirm overlay | `DriverAppAccessibilityService.kt` — `ACTION_SHOW_MANUAL_CONFIRM` |
| Trigger threshold | `ScreenCaptureService.kt` — `LOW_OCR_CONFIDENCE_THRESHOLD = 80` |

### Client test notes
- Clear History is tested on the **History tab** (shows `History (v1.xx)`).
- Confirm window appears on **live offer capture**, not on History.
- Fixed v1.16 bug: toast said “cleared” but list repopulated from server — now server-synced rows are hidden after clear.

---

## 2. Card detection

### Requirements
- Detect the white Uber offer card (bottom sheet) on the driver screen.
- Crop OCR region to the card instead of the full screenshot (map labels, notifications, score overlay noise).

### Implementation
| Area | Location |
|------|----------|
| Luminance-based detector | `OfferCardDetector.kt` |
| Wired into capture pipeline | `ScreenCaptureService.cropOfferCardRegion()` |
| Fallback | Bottom ~⅔ of screen if detection fails |
| Unit tests | `OfferCardDetectorTest.kt` |

### Client test notes
- Card detection runs during **screen capture** — not visible on History alone.
- Short screen recordings of the offer appearing are useful when crop/timing looks wrong.

---

## 3. OCR corpus + accuracy report

### Requirements
- Golden corpus: simulated ML Kit OCR dumps + JSON expectations.
- Field-level accuracy report with **≥95% target**.
- CI-friendly unit tests the team can run before each client APK.

### Implementation
| Area | Location |
|------|----------|
| Corpus data | `app/src/test/resources/ocr_corpus/` |
| Case registry | `OCR_CORPUS_CASE_IDS` in `OcrAccuracyReport.kt` |
| Report runner | `runOcrAccuracyReport()` |
| Corpus tests | `RideOcrCorpusTest.kt` |
| Accuracy gate | `RideOcrAccuracyReportTest.kt` |
| Aug 2026 client batch | `AugFieldBatchTest.kt` (11 regression cases from 13 client errors) |

### Run locally

```bash
./gradlew :app:testReleaseUnitTest --tests com.driver.pro.service.RideOcrCorpusTest
./gradlew :app:testReleaseUnitTest --tests com.driver.pro.service.RideOcrAccuracyReportTest
./gradlew :app:testReleaseUnitTest --tests com.driver.pro.service.AugFieldBatchTest
./gradlew :app:testReleaseUnitTest
```

### Corpus cases (current)

**July regressions**
- `july25_e1w_ec2r`
- `july25_xl_37_41`
- `july25_el_sel_spitalfields`

**Aug field bugs + controls**
- `aug25_fare_17_54` — fare tens-digit recovery
- `aug25_pickup_mi_0_1` — 0.1 mi pickup
- `aug25_trip_mi_157_5` — long Assist trip 157.5 mi
- `aug25_ha0_pickup` — HA0 district allowed
- `aug25_no_pickup_pc` — do not borrow drop postcode
- `aug25_heathrow_ub3` — control (17.5 mi correct)

### Add a new case
1. Add `<id>.txt` + `<id>.json` under `ocr_corpus/`.
2. Register `<id>` in `OCR_CORPUS_CASE_IDS`.
3. Re-run corpus + accuracy tests.

---

## 4. Field hardening + release APK / Play prep

### Aug 2026 client retest (56 offers, 13 errors = 77% correct)

Client provided **paired evidence** (History row + Uber card). All 13 error patterns were categorized and fixed in **v1.18**:

| Error type | Example IDs | Fix summary |
|------------|-------------|-------------|
| Fare tens-digit | 2500 | £17.19 read as £1.71 — recover ×10 from low £ OCR; ignore holiday/priority add-on £ |
| Missing pickup postcode | 2488, 2494, 2524 | W11 / NW10 — leg-zone anchoring + final recovery pass |
| Missing drop postcode | 2556 | Truncated SW1H on card |
| Wrong drop postcode (map) | 2551, 2543 | N1 vs W6, CR0 vs WC2N — skip bare map district labels |
| Borrowed drop postcode | 2521, 2501 | NW9 / HA0 copied from pickup when Uber omits drop PC |
| Pickup postcode swap | 2512 | HA0 read as HA9 from drop line |
| Trip miles 8→3 | 2553 | 8.9 → 3.9 on long leg |
| Trip miles 8→3 (short) | 2542, 2522 | 3.8 → 3.3 |
| Pickup miles | 2529 | 0.1 → 1.0 on 2 min leg |
| Score overlay | Client feedback | Moved score toast to bottom-left (was covering fare) |

**Expected after v1.18:** ~90%+ on similar recordings (exact live rate depends on OCR quality per frame).

### Release APK path

```bash
./gradlew :app:assembleRelease
```

| Item | Detail |
|------|--------|
| Output | `app/build/outputs/apk/release/app-release.apk` |
| Signing | Debug keystore (installable test APK — **not** production Play key) |
| Version check | History tab → `History (v1.18)` |
| Config | `app/build.gradle.kts` — `versionCode` / `versionName` |

### Play prep path (document only — not executed in Phase 2)

See [`PLAY_STORE_PREP.md`](PLAY_STORE_PREP.md):
- Production keystore + AAB build (`bundleRelease`)
- Play Console disclosures (MediaProjection, Accessibility, overlay)
- Internal testing track before production

---

## Version history (Phase 2)

| Version | Focus |
|---------|--------|
| **v1.14** | Clear History button; confirm window (Accept / Decline / Skip) |
| **v1.15** | Offer card detection + crop |
| **v1.16** | Clear History fix — server rows hidden after clear |
| **v1.17** | OCR corpus seeded; ≥95% accuracy gate; Aug parser fixes (fare, HA0, 157.5 mi, borrowed PC) |
| **v1.18** | Client batch fixes (13 errors); score overlay moved; `AugFieldBatchTest`; all 265 unit tests green |

---

## Client test protocol

### What to send when something is wrong
For each wrong job, send **two things together**:
1. **DriverPro History / result** for that trip (ID, fare, postcodes, miles, score/error)
2. **Uber offer screenshot** (or paused frame from recording) for the **same job**

Optional: one line describing what was wrong.

### What we do **not** require
- Every Uber vehicle category (XL, LUX, EXEC, etc.)
- Renting multiple vehicles
- New recordings before client is back in UK/London (existing recordings + pairs are enough for OCR fixes)
- Second phone for recordings is helpful but not mandatory — screenshots work

### What still needs live UK testing
- **Auto-accept / auto-reject** (cannot be fully validated from recordings alone)
- Confirm window timing on real device
- Card detection under glare / notifications

---

## Success criteria (Phase 2 complete)

| Criterion | Target | Status |
|-----------|--------|--------|
| Clear History works and stays cleared | Client-visible | ✅ |
| Confirm window on low-confidence OCR | &lt; 80% accuracy | ✅ |
| Card detection crops offer region | Unit tests + field | ✅ |
| OCR corpus accuracy gate | ≥ 95% field checks | ✅ (100% on current corpus) |
| Full unit test suite | All pass | ✅ (265 tests) |
| Client field batch regressions | 11/11 cases | ✅ |
| Release APK deliverable | Sideload test build | ✅ v1.18 |
| Play prep documented | Not submitted | ✅ `PLAY_STORE_PREP.md` |

---

## Out of scope (Phase 2)

- Play Store submission
- New product features (Trip Radar redesign, new scoring rules, etc.)
- Backend API changes (`idrivesmart.co.uk` contract fixes are separate ops work)
- Covering every Uber offer type / vehicle category
- 100% OCR accuracy on every frame (goal: **good enough for daily driver use**)

---

## Recommended next steps (post–Phase 2)

1. **Client retest v1.18** on recordings + live offers when back in UK.
2. Send any remaining wrong jobs as **pairs** (History + Uber card).
3. If error rate is acceptable → **Phase 3 decision:** Play Store internal track vs. continued sideload.
4. Optional: expand corpus with new client pairs; each fix becomes a permanent regression case.

---

## Key files reference

| Area | Path |
|------|------|
| OCR parsing | `RideOcrParser.kt`, `ScreenCaptureService.kt` |
| Card detection | `OfferCardDetector.kt` |
| Confirm overlay | `DriverAppAccessibilityService.kt` |
| Clear History | `HistoryScreen.kt`, `StorageManage.kt` |
| Accuracy report | `OcrAccuracyReport.kt` |
| Corpus tests | `RideOcrCorpusTest.kt`, `RideOcrAccuracyReportTest.kt`, `AugFieldBatchTest.kt` |
| Corpus data | `app/src/test/resources/ocr_corpus/` |
| Play prep | `PLAY_STORE_PREP.md` |
