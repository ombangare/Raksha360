# Raksha360 — Implementation and Test Verification Report

**Date of Report:** 11 October 2026  
**Project:** Raksha360 (Continuation of Naari Kavach)  
**Execution Environment:** Windows 10/11, Python 3.10+, Node.js v22.14+, PowerShell  

---

## 1. Executive Summary

This update completes the **Raksha360 Real Nagpur Public Data, Colorful Free Map, and Full Feature Verification** protocol. All changes have been applied in place to the existing repository with minimal, additive, test-backed modifications.

### Core Upgrades Completed:
1. **Real Nagpur Public Directory & Helplines:**
   - Seeded 37 public police stations from `reference-data/nagpur-public/nagpur_police_stations.json` into a separate additive `police_station_directory` SQLite table.
   - Added authenticated read-only endpoints `GET /api/v1/police-station-directory` and `GET /api/v1/public-helplines`.
   - Preserved human-reviewed coordinates on reseed while keeping unreviewed coordinates strictly `null`.
   - Updated dashboard's **Stations & Units** page with two strictly separate sections: (A) Nagpur City Police public directory reference with Marathi script and contact details, and (B) Raksha360 synthetic demo response units.
   - Added official public citizen helplines (112, 1091, 1098, 1930, 108, Police Office `0712-2560601`, and Control Room `+91-712-2563232`) with direct dialer links and verification notices.
2. **Clear, Colorful No-Key Map:**
   - Defaulted MapLibre GL JS to **OpenFreeMap Bright** (`https://tiles.openfreemap.org/styles/bright`) with vibrant road cartography and place labels.
   - Added style switchers for **Bright**, **Liberty**, and **Dark** vector map styles.
   - Positioned center at Nagpur (`[79.0882, 21.1458]`), added `Center Nagpur` button, metric scale, pitch navigation controls, and restrained legend.
   - Removed all leftover Google 3D styles and obsolete upsell classes. No Google Maps key, billing, or proprietary map SDK is required.
   - Enforced visible provider attribution: `OpenFreeMap © OpenMapTiles · Data from OpenStreetMap`.
   - Only pinned stations that have human-verified coordinates (`coordinates_verified = true`).
3. **NCRB Crime Statistics Integrity:**
   - Added `GET /api/v1/ncrb-manifest` and dedicated `NCRB crime stats` reference view in the dashboard.
   - Accurately flagged status as `SOURCE_IDENTIFIED_VALUES_NOT_IMPORTED`.
   - Sourced official catalog and PDF report links without guessing or fabricating annual numbers.
4. **Idempotent Synthetic Fixture Importer:**
   - Created `scripts/import_raksha360_fixtures.py` supporting `--demo` (explicit opt-in seeding) and `--clear-demo` (safe deletion of demo records while preserving live user records).
   - Covered all 10 fixture families (`DEMO-MANUAL-SOS-001` through `DEMO-LOCATION-STREAM-010`), reports, dispatches, and device statuses.
   - Ensured zero real SMTP emails, SMS, or dispatch calls are made.
5. **Modernized FastAPI Lifespan:**
   - Converted deprecated `@app.on_event("startup")` and `@app.on_event("shutdown")` to FastAPI `@asynccontextmanager` lifespan, eliminating deprecation warnings.
6. **Automated Test Suite Expansion:**
   - Expanded pytest suite from 12 to **17 passing unit and integration tests** in `backend/tests/test_api.py`.
   - Verified Vite production build (`npm run build` in `police-dashboard/`) successfully compiled in 5.97s.

---

## 2. Inventory of Modified and Created Files

| File | Status | Description of Change |
| :--- | :--- | :--- |
| `backend/app/main.py` | Modified | Added lifespan context manager; added `GET /api/v1/public-helplines` and `GET /api/v1/ncrb-manifest` endpoints; preserved manual station coordinates on reseed. |
| `backend/tests/test_api.py` | Modified | Added tests for public directory provenance, human coordinate preservation, separate demo units, fixture import/cleanup lifecycle, public helplines, and `.invalid` email prevention. |
| `police-dashboard/src/components/MapLibreMap.jsx` | Modified | Integrated OpenFreeMap Bright default, Bright/Liberty/Dark switch buttons, Center Nagpur button, MapLibre attribution, and verified-coordinates filter. |
| `police-dashboard/src/App.jsx` | Modified | Updated Stations page with emergency helplines cards and Section A/B separation; added NcrbPage; added `ncrb` to navigation; updated state sync. |
| `police-dashboard/src/api.js` | Modified | Added `helplines` and `ncrbManifest` API client methods. |
| `police-dashboard/src/styles.css` | Modified | Removed obsolete `.google3d-*` styles; added styles for style-switch buttons, helplines grid, and NCRB catalog cards. |
| `scripts/import_raksha360_fixtures.py` | Created | Idempotent CLI fixture importer with `--demo` and `--clear-demo` flags. |
| `NAGPUR_PUBLIC_DATA_NOTES.md` | Updated | Comprehensive provenance documentation, data classifications, map attribution, and safety boundaries. |
| `IMPLEMENTATION_AND_TEST_REPORT.md` | Updated | This detailed implementation, test verification, and execution report. |

---

## 3. Automated Test Acceptance Verification

All 9 acceptance criteria specified in Section 7 of the master prompt were rigorously tested:

| # | Acceptance Criterion | Verification Command / Method | Status | Details / Evidence |
| :---: | :--- | :--- | :---: | :--- |
| **1** | Python syntax compilation | `python -m py_compile backend/app/main.py` | **PASS** | Exited with returncode 0; clean AST compilation. |
| **2** | FastAPI backend test suite | `python -m pytest -q backend/tests` | **PASS** | **17 passed** in 3.06s. Deprecation warnings resolved. |
| **3** | Public station directory (37 rows, provenance, null coords, idempotency) | `test_public_police_station_directory_is_authenticated_provenanced_and_idempotent` | **PASS** | Verified 37 rows, 401 unauthenticated, null lat/lon, stable IDs on repeat seed. |
| **4** | Preserves human-approved coordinates | `test_public_directory_preserves_manually_approved_coordinates_and_avoids_operational_inference` | **PASS** | Manually set coordinate on `NGP-AJNI` was preserved on reseed; no operational status inferred. |
| **5** | Separate public directory vs demo units | `test_official_directory_and_demo_units_in_separate_endpoints` | **PASS** | Verified `police-station-directory` has `isOfficialPublicDirectory=True, isDemo=False`; `stations` has `isDemo=True, status=DEMO_ONLY`. |
| **6** | Explicit idempotent fixture seed & cleanup | `test_synthetic_fixture_importer_and_clear_lifecycle` | **PASS** | Seeded 10 incidents, 6 reports, 4 dispatches; live event remained `is_demo=0`; `--clear-demo` deleted only demo rows. |
| **7** | No consent / `.invalid` domain never sent | `test_invalid_email_domain_and_unconfigured_smtp_never_sent`, `test_offline_monitor_does_not_email_without_explicit_consent` | **PASS** | Zero SMTP calls initiated when unconsented, when SMTP unconfigured, or when `.invalid` domain is used. |
| **8** | Offline marker popup language & reconnection | `test_missed_heartbeat_creates_last_known_location_incident_and_sends_consented_email` | **PASS** | Incident location marked `LAST KNOWN LOCATION · NOT LIVE`; reconnect clears offline state. |
| **9** | Production dashboard build | `cd police-dashboard; npm run build` | **PASS** | Vite built production bundle in 5.97s (`dist/` generated with zero errors). |

---

## 4. Test Execution Logs

### Pytest Backend Suite:
```powershell
PS C:\Users\omban\Downloads\Raksha360> python -m pytest -q backend/tests
.................                                                        [100%]
17 passed, 1 warning in 3.06s
```

### Fixtures Importer CLI:
```powershell
PS C:\Users\omban\Downloads\Raksha360> python scripts/import_raksha360_fixtures.py --demo
Connecting to database at: C:\Users\omban\Downloads\Raksha360\backend\data\raksha360.sqlite3
Importing synthetic demonstration fixtures from reference-data/nagpur-public/...
Import completed successfully:
  - incidents: 10 records seeded/updated
  - reports: 6 records seeded/updated
  - dispatches: 4 records seeded/updated
  - device_statuses: 3 records seeded/updated
  - emergency_contacts: 2 records seeded/updated
  - email_deliveries: 2 records seeded/updated
Notice: All fixtures marked is_demo=1. No emails, SMS, or dispatch calls made.
```

### Vite Production Build:
```powershell
PS C:\Users\omban\Downloads\Raksha360\police-dashboard> npm run build
> raksha360-police-control-room@1.0.0 build
> vite build

vite v6.4.4 building for production...
transforming...
✓ 1585 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                     0.58 kB │ gzip:   0.35 kB
dist/assets/index-BDPH5BKv.css    115.53 kB │ gzip:  20.32 kB
dist/assets/index-vZElGzMO.js   1,383.74 kB │ gzip: 382.56 kB │ map: 3,601.68 kB
✓ built in 5.97s
```

---

## 5. Operational Boundaries & Physical Limitations

1. **Android Gradle Wrapper & Toolchain:**
   The workspace archive does not contain Gradle wrapper shell launch scripts (`gradlew`, `gradlew.bat`) or the binary `gradle-wrapper.jar`. This development environment has no installed Android SDK or Gradle CLI on PATH. Consequently, Android builds must be synced and built using **Android Studio** on a system with Android SDK installed.
2. **Physical Device Verification:**
   Microphone permissions, battery optimization exemptions, background location tracking, and real Vosk speech recognition require testing on a physical Android handset with consenting participants before operational deployment.
3. **No External Dispatch Connection:**
   The backend's external police gateway status remains `NOT_CONFIGURED`. Dispatch tasks entered in the police dashboard are internal, auditable control-room coordination records only.
4. **Hosted Vector Tile Availability:**
   OpenFreeMap is a public, community-accessible vector tile service. While highly responsive and completely free (no API keys required), production emergency infrastructure should deploy managed or self-hosted tile infrastructure with SLA guarantees.

---

## 6. Commands to Run Raksha360

### Backend (FastAPI on Port 4000):
```powershell
# In terminal 1 (project root)
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r backend\requirements.txt
python -m pip install pytest httpx
python -m uvicorn backend.app.main:app --host 0.0.0.0 --port 4000 --reload
```

### Police Control Room Dashboard (React on Port 5173):
```powershell
# In terminal 2
cd police-dashboard
npm install
npm run dev
```

### Run Tests:
```powershell
# Backend test suite
python -m py_compile backend\app\main.py
python -m pytest -q backend\tests

# Seed synthetic fixtures (explicit opt-in)
python scripts\import_raksha360_fixtures.py --demo

# Clear synthetic fixtures (retaining live user events)
python scripts\import_raksha360_fixtures.py --clear-demo

# Build dashboard production bundle
cd police-dashboard
npm run build
```

### Android Build (Android Studio):
1. Open the project root `c:\Users\omban\Downloads\Raksha360` in Android Studio.
2. Let Gradle sync project dependencies.
3. If Voice SOS models are needed, run `.\scripts\prepare-voice-model.ps1` in PowerShell.
4. Build and install the APK via **Build → Make Project** or **Run 'app'** to a connected test device.
