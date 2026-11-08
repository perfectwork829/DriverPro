# Handoff — I drive smart (driverPRO)

For a **new laptop / new Cursor account**. Copy or clone this whole folder, then open it as the workspace. Cursor should load `AGENTS.md`, `.cursor/rules/driverpro.mdc`, and this file.

**Remote:** `https://github.com/perfectwork829/DriverPro.git`  
**Branch:** `main`

---

## Current version

| What | Value |
|---|---|
| Last **pushed** commit | `723180a` `fix: fixed 10.27 feedback` — **1.66** |
| **Local uncommitted** (this machine, 1 Nov 2026) | **1.69** — customer History hides OCR-debug / raw OCR. Also includes 1.67 Start scroll and 1.68 new Uber layout. |

If you only `git clone` / `git pull`, you get **1.66**. You will **not** have the 1.67 Start-screen fix until you copy the two dirty files or commit/push them from the old laptop.

Uncommitted files (must copy if you do not commit):

- `app/build.gradle.kts` (`versionCode = 67`, `versionName = "1.67"`)
- `app/src/main/java/com/driver/pro/ui/screens/StartScreen.kt` (scrollable column, 24.dp padding, 28.dp spacers)

Also copy this handoff set if they are not on remote yet:

- `AGENTS.md`
- `HANDOFF.md`
- `.cursor/rules/driverpro.mdc`
- `CLIENT_STORE_ACCOUNTS_GUIDE.md` (Play + App Store beginner guide)

---

## What this app is

Android (Kotlin, Jetpack Compose, Gradle 8.13, AGP 8.13.1). Drivers capture Uber Driver Match/Confirm cards (MediaProjection + Accessibility), OCR the offer, score it locally with the client formula, overlay the score, save History, POST to `idrivesmart.co.uk`.

**There is no iPhone app in this repo.** Client wants Play **and** App Store later. Play can use this Android app. App Store needs a **new iOS project** plus a Mac and Apple certificates. Do not fake an IPA.

Sideload APKs are still signed with the **debug keystore** (`app/build.gradle.kts`). Not Play-store signing.

---

## Product rules (do not regress)

1. Start-screen operating cost must change the formula score (persist on load; `persistSessionOperatingCost`).
2. History score = local `displayedFormulaScore`, not the API remainder.
3. Overlay rates = Uber **gross** £/h and £/mi. Formula net is History tape (hidden from UI) and the integer score.
4. Formula (17 Oct 2026 email):  
   `net £/h = [Y − (C+D+A)×cost] × 60 / (E+F+B+4)`  
   then `ceil(£/h / 0.50)` + rating points − pickup hour + drop hour.  
   Buffer **4** minutes. A/B from **drop** KODY row only. London-start only. Do not invent return miles.
5. Hour points: `KodyTable.kt` (2,832 outward codes, X1–X24). Import: `python scripts/import_kody.py` from `C:\Users\Admin\Downloads\KODY.xlsx`.
6. Event uplifts: `kodyEventUplifts` extra on base table. **Empty** until the client sends event postcodes/hours.
7. History title is `History` (no version). Do **not** show `formatFormulaBreakdown`, **Show raw OCR**, **Copy raw OCR**, or OCR-debug cards. Score number and Uber £/h £/mi stay.
8. Licence list: London only (`londonLicenceCities`). Label `London`, not `London (city)`.
9. Start copy:  
   - `In which city do you hold a license?`  
   - `How much does it cost you to drive 1 mile?`  
   Welcome is still `WELCOME {username}`.
10. User-facing: Uber → “driver app”; app name **I drive smart**.

---

## Already done (Oct 2026)

- Blue result overlay bar
- Cost-per-mile actually used in scoring (was stuck at £0.50 at launch)
- Full KODY hour + A/B table
- History formula tape and `History (v1.62)` **hidden** (1.63)
- Licence cities filtered to London, no “(city)” (1.64)
- Licence question sentence case (1.65)
- Cost question sentence case (1.66) — **heading only**; boxes still clipped on 1.66
- **1.67 (local only):** Start screen scroll + reduced padding so £ digit boxes and `050 = £0.50` hint sit on screen above the bottom nav
- **1.68 (local only):** Uber Nov 2026 offer layout — `12,70 GBP`, `18 minut • 7.2 mil`, Dopasowanie / Akceptuj (Polish + English bullet cards)
- **1.69 (local only):** Customer History hides OCR-debug rows and Show/Copy raw OCR

---

## Open / next

1. **Ship 1.67** — commit/push if the user asks, then assemble APK if they ask. Confirm on device that £ boxes show under the cost question.
2. History raw OCR / OCR-debug cards — **hidden** in 1.69.
3. Event-hour overlay UI — not built; hook exists.
4. Google Play / App Store — client guide is `CLIENT_STORE_ACCOUNTS_GUIDE.md`. No production keystore, no AAB uploaded, no Apple app. Wait for Play invite + Apple enrolment. New Play accounts usually need closed testing before production.
5. **iOS** — new project; not this Gradle app.
6. Play policy risk: MediaProjection + Accessibility + overlay on Uber. Client must accept rejection risk (email is enough; no Google waiver PDF).

---

## Key files

| Area | Path |
|---|---|
| Version | `app/build.gradle.kts` |
| Formula | `app/src/main/java/com/driver/pro/utils/ScoreFormula.kt` |
| KODY table | `app/src/main/java/com/driver/pro/utils/KodyTable.kt` |
| KODY import | `scripts/import_kody.py` |
| Overlay rates | `app/src/main/java/com/driver/pro/utils/OfferEarnings.kt` |
| Return A/B | `app/src/main/java/com/driver/pro/utils/ReturnToZone.kt` |
| Capture / OCR | `service/ScreenCaptureService.kt`, `RideOcrParser.kt` |
| Overlay on Uber | `service/DriverAppAccessibilityService.kt` |
| Start / cost | `ui/screens/StartScreen.kt`, `viewmodels/StartScreenViewModel.kt` |
| History | `ui/screens/HistoryScreen.kt`, `ui/components/RideRequestCard.kt` |
| Cities | `utils/LicenceCity.kt` |
| API | `network/ApiClient.kt` |
| Client store guide | `CLIENT_STORE_ACCOUNTS_GUIDE.md` |

Tests: `app/src/test/java/com/driver/pro/utils/ScoreFormulaTest.kt`, `OfferEarningsTest.kt`, `ReturnToZoneTest.kt`, `LicenceCityTest.kt`. Gradle wrapper download can hang; a local dist may exist under `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.13-bin\`.

---

## How to continue on the new machine

1. Install Android Studio / SDK 36, JDK 17, clone the GitHub repo.
2. Copy **uncommitted 1.67 files** from the old laptop (or commit and push from the old laptop first — only if the owner asks).
3. Open the folder in Cursor. Do not ignore `HANDOFF.md`.
4. Do not rebuild iOS. For a client install, `assembleRelease` only when asked; APK lands in `app/build/outputs/apk/release/`.
5. Keep bumping version on client-visible edits.

---

## What not to tell the client

- That an IPA exists
- That History still shows the formula (it does not, from 1.63)
- That cost-per-mile is unused (it is used)
- That Play/App Store listing is already live
