# Handoff — I drive smart (driverPRO)

For a **new laptop / new Cursor or Claude Code session**. Clone or copy this whole folder, then open it as the workspace. Read `AGENTS.md` (hard rules), `.cursor/rules/driverpro.mdc`, and this file.

**Remote:** `https://github.com/perfectwork829/DriverPro.git`
**Branch:** `main`
**Updated:** 10 Nov 2026

---

## Current version

| What | Value |
|---|---|
| `app/build.gradle.kts` (working copy) | `versionCode = 74`, `versionName = "1.74"` |
| Last **pushed** commit | `e4005d5` `fix: fixed 11.08 feedback`, which is **1.70** (`main` is level with `origin/main`) |
| **Local uncommitted** (10 Nov 2026) | **1.72**: 1.71 slider and History changes, plus return miles on zone-edge pickups (SL2 → OX26) |

A `git clone` or `git pull` gives you **1.70**. You get **1.72** only after the files below are committed and pushed, or copied from the old machine.

### Uncommitted files (commit, or copy to the new machine)

| File | Change |
|---|---|
| `app/build.gradle.kts` | 70 → 72, 1.70 → 1.72 |
| `app/src/main/java/com/driver/pro/ui/components/ScoreRangeSlider.kt` | `labelDecimals` param, `formatSliderLabel`, snapped value rounded to 2 dp |
| `app/src/main/java/com/driver/pro/ui/screens/SettingsScreen.kt` | SCORE slider `valueStep = 1f` (was 5). PRICE PER MILE `valueStep = 0.1f, labelDecimals = 1` |
| `app/src/main/java/com/driver/pro/StorageManage.kt` | `isCustomerVisibleRide`, `customerHistoryRides` |
| `app/src/main/java/com/driver/pro/ui/screens/HistoryScreen.kt` | uses `customerHistoryRides(...)` instead of hiding every OCR-debug row |
| `app/src/main/java/com/driver/pro/ui/components/RideRequestCard.kt` | `formatHistoryPriceRate`. Unscored cards hide the Pickup/Drop/Score lines |
| `app/src/main/java/com/driver/pro/service/ScreenCaptureService.kt` | `saveDebugOcrAttempt` saves `created_at` as `yyyy-MM-dd HH:mm:ss` |
| `app/src/main/java/com/driver/pro/utils/ReturnToZone.kt` | Zone start is a pickup whose own return is 0, so SL2 → OX26 uses A=32, B=44 |
| `AGENTS.md` | History rule updated for unscored cards (client, 9 Nov 2026). Zone-start rule for A/B |
| `HANDOFF.md` | this file |
| `app/src/test/java/com/driver/pro/ui/components/ScoreRangeSliderTest.kt` | **new (untracked)**: `ScoreRangeSliderTest` + `UnscoredHistoryCardTest` |

Nothing has been tried on a phone yet, and no 1.72 APK has been built.

---

## What this app is

Android (Kotlin, Jetpack Compose, Gradle 8.13, AGP 8.13.1, SDK 36, JDK 17). The app captures Uber Driver Match/Confirm cards (MediaProjection + Accessibility), reads the offer with OCR (ML Kit then `RideOcrParser`), scores it locally with the client formula, overlays the score on the Uber card, saves it to History and POSTs `/api/ride-request/` to `idrivesmart.co.uk`.

**Android only. There is no iPhone/Xcode target in this repo and no IPA exists.** An App Store version would need a new iOS project, a Mac and Apple certificates. Never tell the client an IPA was built.

Sideload APKs are signed with the **debug keystore** (`app/build.gradle.kts`), not Play signing. Build an APK/AAB only when the owner asks.

---

## Product rules (do not regress)

1. The Start-screen cost per mile is the operating cost in the formula. It is saved on load (`persistSessionOperatingCost`).
2. Formula (client, 17 Oct 2026):
   - `net £/h = [Y − (C+D+A)×cost] × 60 / (E+F+B+4)`
   - band `ceil(£/h / 0.50)`
   - plus rating points (4.51–5.00 → 10 … 1.00–1.50 → 3)
   - minus pickup postcode hour points, plus drop postcode hour points
3. Buffer is **4** minutes. **A/B** come from the **drop** KODY row only, and only when the pickup is still inside the working zone (that pickup row's own return is 0: every TfL district, plus SL, SG and WD). A pickup that already has return miles is not a zone start. Do not invent return miles. Intra-zone is 0 unless the sheet has values.
4. Hour table: `KodyTable.kt`, imported from `C:\Users\Admin\Downloads\KODY.xlsx` via `python scripts/import_kody.py`. Event uplifts (`kodyEventUplifts`) are **empty** until the client sends event postcodes and hours.
5. The overlay **£/h** and **£/mi** are Uber **gross**, not net of cost.
6. The History score is the local `displayedFormulaScore`, not the API value. The server drops the hundreds digit (118 → 18).
7. **Accept/reject decision (1.74):** made locally by `decideRideFromFilters(...)` using every enabled Settings filter (Score, Price, Rating, Price per mile). Each filter votes Accept / Reject / neither. If any votes Accept and another votes Reject, the app takes **no action**. One filter alone keeps the old Reject < X / Accept > Y behaviour. With no local filter voting it falls back to the server decision, but a server "accept" with a local score under 70 is ignored. Thresholds are cached in `app_prefs` by `SettingsScreen` (`saveRideFilterThresholds`).
8. **History:**
   - Title is `History`, with no version number.
   - Never show the formula tape (`43+10 -8 +8 = 53`), **Show raw OCR** or **Copy raw OCR**.
   - Scored cards show ID, time, price/rating, Pickup, Drop, Score + decision, and Uber £/h £/mi.
   - **Unscored orders** (Uber glitch, missing postcode) show as a plain card: ID, time, price/rating, £/h £/mi. They have no Pickup/Drop/Score lines and are not marked invalid (client, 9 Nov 2026).
   - Hidden: rows with no fare, rows where the "price" was really the passenger rating, and an unscored row when the same fare was scored within 5 minutes.
9. **Settings sliders:** Score moves in steps of **1**. Price per mile moves in **£0.10** steps, range 0–15, label to 1 decimal.
10. Licence list: **London only** (`londonLicenceCities`). Label `London`, not `London (city)`.
11. Start copy: `In which city do you hold a license?` / `How much does it cost you to drive 1 mile?`. The welcome stays `WELCOME {username}`.
12. In user-facing text Uber is called the “driver app”, and the app name is **I drive smart**.
13. Bump `versionCode` / `versionName` for each client-visible change.

---

## Fixed since the last handoff (1.66 → 1.72)

| Version | Commit | What |
|---|---|---|
| 1.67 | `531f799` | Start screen scrolls with less padding, so the £ digit boxes and the `050 = £0.50` hint sit above the bottom nav |
| 1.68 | `b54e0ec` | Start screen cleanup |
| 1.69 | `65fcfbf` (+ `5761fba` gradle fix) | New Uber Nov 2026 offer layout (`12,70 GBP`, `18 minut • 7.2 mil`, Polish/English bullet cards). History hides OCR-debug rows and Show/Copy raw OCR. Added `ARCHITECTURE.md`, `AGENTS.md`, `HANDOFF.md` |
| 1.70 | `e4005d5` | **Good orders were auto-rejected.** The server compared the truncated score (118 → 18), so the decision is now made locally (rule 7). The parser accepts `mi` as well as `mil`/`miles`. `Nov8ClientBatchTest` covers the client's 8 Nov samples |
| 1.71 | uncommitted | Score slider steps of 1 (the client said 5 was wrong). Price per mile back to 0.1 steps. Unscored orders back in History as plain cards. Review fixes: no duplicate unscored/scored card for one offer, a reliable timestamp on unscored rows, and a price misread from the rating no longer shown |
| 1.72 | uncommitted | Zone-edge pickups (SL, SG, WD — sheet return 0) now include the drop's return miles and minutes. ID-6068 SL2 → OX26 was 149 with the return left out; with A=32, B=44 at £0.30/mi the score is 69. A start that already has return miles (SN2) still adds nothing |

Note: the client's "old bugs came back" report on 8 Nov came from a phone still running **1.68 or older**. Make sure the client installs the newest APK before re-testing.

---

## Still open / unfinished

1. **Ship 1.72.** Commit/push and build the APK only when the owner asks, then test on a phone:
   - Score slider moves by 1
   - Price per mile shows e.g. £2.1
   - Unscored cards appear without Pickup/Drop/Score
   - SL2 → OX26 includes the 32 miles / 44 minutes return (score 69 at £0.30/mi, not 149)
2. **Not verified:** whether the server keeps price-per-mile settings as decimals. If it stores whole numbers, £2.1 comes back as £2 after reload. Check `getUserSettings` after saving 2.1.
3. **Event-hour overlay UI** is not built. The `kodyEventUplifts` hook exists and is empty.
4. **Google Play / App Store:**
   - There is a client guide in `CLIENT_STORE_ACCOUNTS_GUIDE.md`.
   - No production keystore, no AAB uploaded, no iOS app.
   - New Play accounts usually need closed testing first.
   - Play policy risk: MediaProjection + Accessibility + overlay on Uber.
5. **Tracked files that look sensitive or temporary:** `driver_key`, `driver_key1`, `keystore`, `tmp_id2488.txt` and `tmp_parse.kts` are in git. The owner has not decided whether to remove them from the repo or rotate keys. Do not delete without asking.

## Known bugs / client feedback not done

- **Slough job (8 Nov):** minutes/distance wrong, so the points were wrong. The OCR text itself says "4 mi" where the real trip was 2.1, so it can't be fixed from the text alone. Needs a clearer screenshot or recording.
- **New Uber layout on the client's iPhone (8 Nov):** Uber appears to be A/B testing. This app only runs on Android. Parser support for the Nov layout is in 1.69, but each new variant needs samples.
- **Electric £15.49 drop shown as "London" (8 Nov):** wrong drop postcode. Not confirmed fixed. `RideOcrParser.kt` (around line 498–505) can still copy the pickup postcode into drop when drop is blank.
- **Client video** `C:\Users\Admin\Downloads\VID-20261109-WA0016.mp4` (9 Nov) has not been reviewed.
- **Postcode map W / SW screens showing "—":** never clarified with the client.

---

## Key files

| Area | Path |
|---|---|
| Version | `app/build.gradle.kts` |
| Formula | `app/src/main/java/com/driver/pro/utils/ScoreFormula.kt` |
| KODY table / import | `utils/KodyTable.kt`, `scripts/import_kody.py` |
| Overlay rates | `utils/OfferEarnings.kt` |
| Return A/B | `utils/ReturnToZone.kt` |
| Capture / OCR | `service/ScreenCaptureService.kt`, `service/RideOcrParser.kt` |
| Overlay on Uber | `service/DriverAppAccessibilityService.kt` |
| Accept/reject + History filters | `StorageManage.kt` (`decideRideFromScore`, `customerHistoryRides`) |
| Settings / sliders | `ui/screens/SettingsScreen.kt`, `ui/components/ScoreRangeSlider.kt` |
| Start / cost | `ui/screens/StartScreen.kt`, `viewmodels/StartScreenViewModel.kt` |
| History | `ui/screens/HistoryScreen.kt`, `ui/components/RideRequestCard.kt` |
| Cities | `utils/LicenceCity.kt` |
| API | `network/ApiClient.kt` |
| Architecture notes | `ARCHITECTURE.md` |

Tests are under `app/src/test/java/com/driver/pro/` (`utils/`, `service/` client OCR batches, `network/`, `ui/components/`). Run them with:

```bash
./gradlew.bat :app:testDebugUnitTest --offline
```

On Windows, run Gradle from Git Bash and redirect output to a log; PowerShell treats Gradle's stderr as an error. Gradle wrapper downloads can hang, but a local dist may exist under `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.13-bin\`.

---

## How to continue on the new machine

1. Install Android Studio / SDK 36 and JDK 17, then clone the repo.
2. If 1.72 isn't pushed yet, copy the uncommitted files listed above from the old machine. Alternatively, the owner can commit and push from the old machine.
3. Run the unit tests and expect them all to pass.
4. Don't build iOS. For a client install, run `assembleRelease` only when asked; the APK is written to `app/build/outputs/apk/release/`.
5. Keep bumping the version on client-visible edits.

---

## What not to tell the client

- That an IPA or iPhone build exists
- That History still shows the formula (it hasn't since 1.63)
- That cost per mile is unused (it is used)
- That a Play/App Store listing is live
- That an OCR fix is a scoring fix, or the reverse
