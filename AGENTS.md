# I drive smart (driverPRO)

Android driver scoring app. Package `com.driver.pro`. Store name **I drive smart**. Backend `idrivesmart.co.uk`.

Read **`HANDOFF.md`** first. It is the current status for a new Cursor session.

## Hard rules

- **Android only** in this repo. There is no Xcode/iOS target. Do not invent an IPA. Do not tell the client an IPA was built.
- **Do not assemble an APK/AAB unless the user asks.**
- **Do not git commit or push unless the user asks.**
- **Do not over-claim.** Scoring, OCR, overlay, and History are separate. A scoring change is not an OCR fix.
- Cost per mile on the **Start** screen is the operating cost used in the formula. Overlay **£/h** and **£/mi** stay **Uber gross**, not net of cost.
- Score formula (client, 17 Oct 2026):
  1. net £/h = `[Y − (C+D+A)×cost] × 60 / (E+F+B+4)`
  2. band = `ceil(£/h / 0.50)`
  3. add rating points (4.51–5.00 → 10 … 1.00–1.50 → 3)
  4. subtract pickup postcode hour points, add drop postcode hour points
- **A** (return miles) and **B** (return minutes) come from the drop postcode **KODY** row only. Do not guess. Intra-London is 0 unless the sheet has values.
- Buffer is **4 minutes**. Return A/B only when the pickup is still inside the working zone: its own KODY return is 0 (every TfL district, plus SL, SG and WD). A pickup that already has return miles does not add the drop's A/B.
- Hour table source of truth: `KodyTable.kt` imported from `C:\Users\Admin\Downloads\KODY.xlsx` via `scripts/import_kody.py`. Event uplifts (`kodyEventUplifts`) sit on top and are **empty** until an overlay is set.
- History must **not** show the formula tape (`43+10 -8 +8 = 53`), `History (v…)`, **Show raw OCR**, or **Copy raw OCR**. Score number and Uber £/h £/mi stay. Unscored orders (Uber glitch / missing postcode) **do** show, as a plain card: ID, time, price/rating, £/h £/mi — no Pickup/Drop/Score lines (client 9 Nov 2026).
- Licence city list: **London only**. Label is `London`, not `London (city)`.
- Bump `versionCode` / `versionName` in `app/build.gradle.kts` for each client-visible change.

## Layout (tabs)

0. Start / licence + cost per mile  
1. History  
2. Settings (capture, overlay, Shizuku)  
3. Postcode map  

OCR: MediaProjection + Accessibility. Overlay on the Uber Driver offer. Scores POST `/api/ride-request/`.
