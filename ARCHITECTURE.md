# I drive smart (driverPRO) — Architecture overview

**Package:** `com.driver.pro`  
**Store name:** I drive smart  
**Backend:** `https://idrivesmart.co.uk/`  
**Platform:** Android only (Kotlin, Jetpack Compose). No iOS / IPA in this repo.

Read `HANDOFF.md` and `AGENTS.md` for current version, product rules, and session constraints. This document is a structural map for developers.

**Local build at time of writing:** versionName `1.69`, versionCode `69` (`app/build.gradle.kts`). Confirm against git / HANDOFF before assuming what is pushed.

---

## 1. Overall architecture

Client-heavy Android app: drivers capture Uber Driver Match/Confirm cards, OCR the offer, score it **locally** with the client formula, overlay the score on the Uber UI, save History, and sync with the backend.

```text
┌─────────────────────────────────────────────────────────────┐
│  UI (Jetpack Compose)                                        │
│  Login → Main tabs: Start | History | Settings | Postcode    │
└───────────────┬───────────────────────────────┬─────────────┘
                │                               │
        ViewModels / Session              SharedPreferences
                │                         (+ Encrypted prefs)
                ▼                               │
┌───────────────────────────────┐               │
│  Backend: idrivesmart.co.uk   │◄──────────────┘
│  JWT (SimpleJWT-style)        │
│  Ktor + Retrofit dual clients │
└───────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Capture / score pipeline (foreground)                       │
│  MediaProjection → ScreenCaptureService                      │
│       → ML Kit OCR → RideOcrParser                           │
│       → local ScoreFormula (+ KodyTable A/B + hour points)   │
│       → POST /api/ride-request/                              │
│       → Accessibility overlay / optional Shizuku tap         │
└─────────────────────────────────────────────────────────────┘
```

### Stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Navigation Compose |
| Build | Gradle 8.13, AGP 8.13.1, minSdk 26, targetSdk 36 |
| OCR | Google ML Kit Text Recognition (on-device) |
| Overlay / automation | Accessibility Service + optional Shizuku |
| Crash / analytics | Firebase Crashlytics + Analytics |
| HTTP | **Two** clients: Ktor (OkHttp) and Retrofit + OkHttp |

### Product rules that affect architecture (do not regress)

1. Start-screen **cost per mile** drives the **net** formula score (`persistSessionOperatingCost`).
2. Overlay **£/h** and **£/mi** remain Uber **gross**, not net of cost.
3. History score = local `displayedFormulaScore`, not the API remainder.
4. Formula (17 Oct 2026):  
   `net £/h = [Y − (C+D+A)×cost] × 60 / (E+F+B+4)`  
   then `ceil(£/h / 0.50)` + rating points − pickup hour + drop hour.  
   Buffer **4** minutes. **A/B** from drop postcode **KODY** row only. London-start only.
5. History must **not** show formula tape, version subtitle, or raw OCR / OCR-debug cards.
6. Licence list: **London only**, label `London` (not `London (city)`).

### Runtime capture flow

Settings starts MediaProjection → `ScreenCaptureService` screenshots → crop offer card → ML Kit OCR → parse/validate → `computeFormulaScore` → POST score → save History → Accessibility shows overlay (gross rates + integer score).

---

## 2. Main modules and responsibilities

| Area | Path | Responsibility |
|---|---|---|
| App shell | `MainActivity.kt`, `DriverPROApp.kt`, `ui/navigation/AppNavigation.kt` | Entry, auth gate, tab host; MediaProjection result → capture service |
| Start | `ui/screens/StartScreen.kt`, `viewmodels/StartScreenViewModel.kt` | Licence city (London), operating cost/mile, sync with API |
| History | `ui/screens/HistoryScreen.kt`, `ui/components/RideRequestCard.kt`, `StorageManage.kt` | Local + API merge; customer UI hides OCR-debug / raw OCR |
| Settings | `ui/screens/SettingsScreen.kt`, `viewmodels/SettingsViewModel.kt` | Capture on/off, overlay thresholds, Shizuku gate |
| Postcode map | `ui/screens/PostcodeScreen.kt`, `PostcodeDetailScreen.kt` | WebView map + user postcode enable/status via API |
| Capture | `service/ScreenCaptureService.kt` | MediaProjection loop, card crop, OCR, score, POST, History save |
| OCR parse | `service/RideOcrParser.kt`, `OfferCardDetector.kt`, test corpus under `app/src/test` | Field extraction from Uber Match/Confirm layouts |
| Overlay / a11y | `service/DriverAppAccessibilityService.kt`, legacy `UberAccessibilityService` | Live score banner, gestures, Accessibility text hints |
| Scoring | `utils/ScoreFormula.kt`, `KodyTable.kt`, `ReturnToZone.kt`, `OfferEarnings.kt` | Client formula; embedded KODY hour/A/B table |
| Network | `network/ApiClient.kt`, `RideRequestApi.kt`, `TokenAuth.kt`, `SessionManager.kt` | Auth, ride score/history, cities, postcodes, settings |
| Repos | `repository/UserRepository.kt`, `SettingsRepository.kt` | Thin API wrappers (see issues: `UserRepository` is broken) |
| Tooling | `scripts/import_kody.py` | Regenerates `KodyTable.kt` from `KODY.xlsx` |

### Tab layout

0. **Start** — licence + cost per mile  
1. **History** — scored rides  
2. **Settings** — capture, overlay, Shizuku  
3. **Postcode map** — regions / user postcodes  

### Key formula / data files

| Concern | File |
|---|---|
| Version | `app/build.gradle.kts` |
| Score formula | `app/src/main/java/com/driver/pro/utils/ScoreFormula.kt` |
| KODY table | `app/src/main/java/com/driver/pro/utils/KodyTable.kt` |
| Overlay rates (gross) | `app/src/main/java/com/driver/pro/utils/OfferEarnings.kt` |
| Return A/B | `app/src/main/java/com/driver/pro/utils/ReturnToZone.kt` |
| Event uplifts hook | `kodyEventUplifts` in `ScoreFormula.kt` (**empty** until client supplies events) |

---

## 3. Important API integrations and persistence

### Backend: `https://idrivesmart.co.uk/`

Two HTTP stacks talk to the same host.

#### Ktor (OkHttp engine, IPv4-preferring DNS)

Used for auth, scoring, history, and older settings helpers:

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/token/` | Login |
| POST | `/api/token/refresh/` | Refresh JWT |
| GET | `/api/user/` | Profile |
| POST | `/api/ride-request/` | Score / log ride (**body must be a JSON-encoded string**, not a raw object) |
| GET | `/api/recent-ride-request/` | History sync |
| GET/POST | `/api/get-setting/`, `/api/update-setting/` | Legacy accept/reject scores |
| POST | `/api/saveNode/`, `/api/saveNotificationInfo/` | Debug / telemetry-style posts |

#### Retrofit + OkHttp (`ApiClient` / `ApiService`)

Used for Start / Settings / Postcode screens:

- Cities / user cities  
- Price-per-mile CRUD  
- User settings (score / price / rate / price_per_mile bands)  
- Postcodes / user postcodes / postcode-enable  
- Duplicate login / `getUser` paths also exist here  

#### Other integrations (not a DB)

- **Firebase** Analytics + Crashlytics (`app/google-services.json`, project `driverpro-e2c30`)
- **ML Kit** on-device OCR (no cloud OCR in the hot path)
- **Shizuku** for privileged taps when granted
- **WebView** postcode map: `https://idrivesmart.co.uk/postcode-map/`
- Auth web pages: signup / password reset on the same domain
- `generativeai` is declared in Gradle but **unused** in main source

### Local persistence (no Room / SQLite)

| Store | Contents |
|---|---|
| `SharedPreferences` `app_prefs` | JWT + refresh (legacy), user id/name, operating cost/mile, ride History JSON (max ~120), history-cleared timestamp |
| `EncryptedSharedPreferences` `user_prefs` | Access/refresh for Retrofit `SessionManager` (also mirrored into `app_prefs`) |
| Embedded `KodyTable.kt` | ~2,832 outward codes × hour points + return miles/minutes (A/B) |

History = merge of local OCR saves + `recent-ride-request`, with clear-history filtering (`mergeRideHistory`, `markHistoryCleared`, `isRideAfterHistoryClear` in `StorageManage.kt`). That is the only on-device “database.”

---

## 4. Project structure and technical issues

### Package / folder layout

```text
driverPRO/
├── AGENTS.md, HANDOFF.md, ARCHITECTURE.md   # developer docs
├── app/
│   ├── build.gradle.kts                     # versionCode / versionName
│   ├── google-services.json
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/driver/pro/
│       │   │   ├── MainActivity.kt, DriverPROApp.kt, StorageManage.kt
│       │   │   ├── ui/          # screens, components, theme, navigation
│       │   │   ├── service/     # capture, OCR, accessibility
│       │   │   ├── network/     # API clients + auth
│       │   │   ├── utils/       # formula, KODY, earnings, Shizuku
│       │   │   ├── viewmodels/
│       │   │   └── repository/
│       │   └── res/
│       └── test/                # OCR corpus + formula unit tests
├── scripts/import_kody.py
└── gradle/libs.versions.toml
```

Single Gradle module: `:app`.

### Technical issues / risks (priority order)

1. **Dual network stacks + dual token stores**  
   Ktor uses `app_prefs` JWT; Retrofit uses encrypted `SessionManager` (mirrored into `app_prefs`). Refresh paths differ. Easy source of “logged-in UI but capture/history 401” bugs.

2. **`UserRepository` infinite recursion**  
   `login` / `getUser` call themselves. `MainViewModel.login()` would stack-overflow if used; real login goes through `LoginScreen` + Ktor/Retrofit directly.

3. **Monolith services**  
   `ScreenCaptureService` and `RideOcrParser` are ~100–130 KB each. Hard to maintain; Uber layout churn (e.g. Nov 2026 Polish/English cards) lives here.

4. **Dead / incomplete code**  
   - `RideService.kt` — stub polling to `YOUR_SERVER.com`, not in the manifest  
   - `DriverAppNotificationListener` — permission declared, service **not** registered  
   - Unused `generativeai` dependency  
   - Nested `android { }` inside `android { }` in `app/build.gradle.kts` (packagingOptions) — fragile Gradle config  

5. **Release / store readiness**  
   - Release still signed with the **debug keystore**  
   - `isMinifyEnabled = false`  
   - Play policy risk: MediaProjection + Accessibility + overlay on Uber  
   - No production keystore / AAB upload path yet; iOS is a separate future project  

6. **Manifest / permissions noise**  
   Duplicate `INTERNET` and `FOREGROUND_SERVICE_MEDIA_PROJECTION`; notification-listener permission without a registered listener.

7. **Security / privacy**  
   JWT also stored in plain `app_prefs`; ride History (fares, postcodes, raw OCR) in plaintext prefs; Retrofit HTTP logging at BODY level; Firebase config in repo (normal for Android client apps).

8. **Docs drift**  
   `HANDOFF.md` may lag local `versionName` / uncommitted OCR–History work. Always check `app/build.gradle.kts` and `git status`.

9. **Event uplifts**  
   `kodyEventUplifts` hook exists but is empty; no UI yet.

10. **Backend contract fragility**  
    Double-encoded JSON body for `/api/ride-request/` and score-digit truncation workarounds (`preferDisplayedScore`, `readScoredRideScore`) show the client compensating for server quirks.

### What not to over-claim

Scoring ≠ OCR ≠ overlay chrome. A scoring change is not an OCR fix. Do not tell clients an IPA exists, that Play/App Store is already live, or that cost-per-mile is unused (it is used).

---

## Quick pointers for a new developer

1. Read `HANDOFF.md` + `AGENTS.md` first.  
2. Confirm version in `app/build.gradle.kts`.  
3. Do **not** assemble APK/AAB, commit, or push unless asked.  
4. Formula / KODY / History UI rules above are client-visible and easy to regress.  
5. For OCR regressions, prefer corpus tests under `app/src/test` over ad-hoc claims.
