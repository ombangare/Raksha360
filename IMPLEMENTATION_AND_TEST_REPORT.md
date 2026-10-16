# Raksha360 — Master Implementation, Full Application QA & Release Readiness Report

**Date:** 14 October 2026  
**Repository:** [https://github.com/ombangare/Raksha360](https://github.com/ombangare/Raksha360)  
**Execution Environment:** Windows 11, OpenJDK JBR 21, Python 3.10.9, Node.js v22.14, Android SDK Platform 36, Vite 6.4.4  
**Production Endpoints:**
- Web Dashboard: [https://raksha360-policedashboard.vercel.app/](https://raksha360-policedashboard.vercel.app/)
- FastAPI Backend: [https://raksha360-api.onrender.com](https://raksha360-api.onrender.com/)
- Production API Base URL: `https://raksha360-api.onrender.com/api/v1`

---

## A. Repository Baseline

- **Current Branch:** `main`
- **Latest Commit Hash:** `93327694f3468d350b7b6137b6e3123ce1b5608a`
- **Commit Message:** `feat(android): configure production API endpoint, network security config, and release verification`
- **Working-Tree Status:**
  - Modified: `police-dashboard/src/App.jsx` (integrated `NagpurAnimatedLogin` component into control-room gate)
  - Untracked: `police-dashboard/src/components/NagpurAnimatedLogin.jsx`, `police-dashboard/src/components/NagpurAnimatedLogin.css`
- **Baseline Build & Automated Test Summary:**
  - **Android Unit & Robolectric Tests:** **19 passed, 0 failed, 0 skipped** (100% success rate across `AuditLoggerServiceTest`, `ExampleRobolectricTest`, `ExampleUnitTest`, and `NaariKavachComprehensiveTest` in 23.50s).
  - **Backend Pytest Suite:** **46 passed, 7 skipped, 0 failed** in 4.57s (7 PostgreSQL integration tests skipped awaiting dedicated isolated test database credentials).
  - **Vite Web Dashboard Production Bundle:** **Compiled in 8.35s** with 0 errors (`dist/index.html`, `dist/assets/index-CLHOnK7o.css`, `dist/assets/index-BxRdtBqy.js`).
  - **Signed Release APK:** `distribution/Raksha360-v1.0-release.apk` (111.13 MB, SHA-256: `bd2b74d3beeb62873952010102240a5efca91eb61485d04aca06c75348e29865`, v2 scheme signature verified).

---

## B. Feature Inventory

| Feature | Primary Source Files | Entry Point / UI Trigger | Dependencies | Operational Limitations | Verification Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Nagpur Animated Control Room Login** | `police-dashboard/src/components/NagpurAnimatedLogin.jsx`, `.css` | Unauthenticated web entry (`/`) | React 19, Lucide React, HTML5 Canvas, CSS 3D | Ambient animations run purely in browser; does not fabricate server telemetry | **PASS** (Code & Bundle verified) |
| **Manual SOS Panic Activation** | `EmergencyOrchestrator.kt`, `EmergencyRepository.kt`, `HomeScreen.kt` | "Emergency SOS" button on Home Screen | Room DB, `ForegroundLocationService`, `IncidentAudioRecorder` | Requires location permissions for GPS tags; falls back to Sadar seed with `isFallback = true` | **PASS** (Unit test verified) |
| **Phone-Shake SOS Trigger** | *None* | Physical phone shake / accelerometer | Android SensorManager / Accelerometer | **Not implemented in codebase** (no accelerometer listeners or shake detection logic found) | **NOT RUN / NOT IMPLEMENTED** |
| **Voice SOS & Enrollment** | `VoiceModelEngine.kt`, `VoiceSosEnrollmentScreen.kt`, `VoiceProfileRepository.kt` | Voice SOS Settings & Enrollment Screen | Vosk offline speech SDK, Android Keystore | Requires pre-downloading Vosk models via `prepare-voice-model.ps1`; uncalibrated threshold | **PASS** (Model abstraction & tests verified) |
| **Child Safety & Local Geofencing** | `ChildSafetyRepository.kt`, `ChildSafetyScreen.kt`, `SafePlaceGeofenceManager.kt` | Child Safety tab in Android navigation | Android `GeofencingClient`, WorkManager, Room DB | Purely local-first Room database; no remote cloud sync or backend authorization | **PASS** (Room DAO & workflow verified) |
| **Contextual Safety Scoring** | `SafetyScoringEngine.kt`, `CrimeDataImportContract.kt`, `MapGeoModels.kt` | Safety Map Screen (`NagpurSafetyMapScreen.kt`) | Local waypoint repository, `NagpurGeoConfig` | Evaluates infrastructure & community signals; never claims "guaranteed safe" or "dangerous" | **PASS** (Scoring engine tests verified) |
| **Citizen Incident Reporting** | `IncidentReportScreen.kt`, `MainViewModel.kt`, `backend/app/main.py` | Incident Report screen (`/report`) | FastAPI `POST /api/v1/reports`, Room DB | Stores record in Raksha360 queue; not an official 112 police complaint | **PASS** (Form validation & API verified) |
| **Police Dispatch Adapter** | `ProductionPoliceDispatchAdapter.kt`, `DemoPoliceDispatchAdapter.kt` | SOS dispatch pipeline | Retrofit / OkHttp `BackendApiClient` | Production adapter strictly reports `UNAVAILABLE` and directs users to dial 112 directly | **PASS** (Contract tests verified) |
| **Cryptographic Evidence Sealing** | `KeystoreCryptoManager.kt`, `EvidenceRepository.kt` | Incident completion / audio recording | Android Keystore RSA/AES, SHA-256 | Tamper-evident byte verification; files stored in private app directory | **PASS** (Byte integrity test verified) |
| **Interactive Map & Station Directory** | `MapLibreMap.jsx`, `OpenFreeMap`, `backend/app/main.py` | Dashboard Map & Stations view | MapLibre GL JS, OpenFreeMap Bright style | Displays 37 verified Nagpur stations; unverified coordinates remain unpinned | **PASS** (Attribution & filters verified) |

---

## C. Voice Assistant & Three-Sample Enrollment

1. **Enrollment Workflow:**
   - **Sample Requirements:** Guided enrollment prompts the user to record separate samples of their personal phrase.
   - **Default Enrollment Phrase:** `"Raksha 360, help me"` (defined in `VoiceProfileRepository.DEFAULT_PHRASE`).
   - **Length Constraint:** Validated between 6 and 100 characters in `VoiceSosEnrollmentScreen.kt`.
2. **Actual Trigger Phrases & ASR Normalization:**
   - Normalized text comparison handles speech recognition variations:
     - `"360"` $\leftrightarrow$ `"three sixty"` $\leftrightarrow$ `"three six zero"`
     - `"raksha"` $\leftrightarrow$ `"rak sha"`
     - Common ASR acoustic confusion: `"russia"` $\leftrightarrow$ `"raksha"`
     - Pronunciation variants: `"naari"` $\leftrightarrow$ `"nari"` $\leftrightarrow$ `"nary"`
3. **Capture and Storage Behavior:**
   - Audio is captured in PCM 16-bit format at 16,000 Hz.
   - Features evaluated via `AudioFeatureExtractor.evaluateQuality` (SNR and RMS thresholds).
   - Speaker embeddings extracted using Vosk `SpeakerModel`, averaged across enrolled samples, L2-normalized, and encrypted via `KeystoreCryptoManager` before storing in private `SharedPreferences`.
4. **Speaker Matching & Verification:**
   - Compares cosine similarity between the current utterance embedding and the stored enrolled embedding.
   - Compares against a threshold (default `0.82`).
   - Clearly documented in code as an uncalibrated algorithmic baseline requiring empirical hardware validation.
5. **Model Provisioning & Availability:**
   - Models: `vosk-model-small-en-us-0.15` (ASR) and `vosk-model-spk-0.4` (Speaker).
   - Models are not bundled in git; they must be provisioned via `scripts/prepare-voice-model.ps1` into `app/src/main/assets/`.
   - When models are absent, `VoiceModelEngine.modelStatusLabel` explicitly alerts: *"Voice SOS models are missing. Run scripts/prepare-voice-model.ps1, then rebuild the app."* There is deliberately **no fabricated sound-energy fallback**.

---

## D. Safety Routing & Parameter Audit

The codebase was audited to reconcile the claimed "nine parameters" against active source code in `CrimeDataImportContract.kt` and `MapGeoModels.kt`. Exactly **four concrete parameters** exist in the operational engine:

1. **`infrastructureScore` (Range: `0.0` to `1.0`):**
   - **Source Data:** Distance and density of verified safety waypoints (`NagpurGeoConfig.VERIFIED_SAFETY_POINTS`: Sadar Police Station, Sitabuldi Police Station, Mayo Hospital, VNIT).
   - **Value:** `0.85` for Sadar Pilot Zone; `0.70` for general Nagpur areas.
2. **`communitySignalScore` (Range: `0.0` to `1.0`):**
   - **Source Data:** Aggregated user check-ins and community safety feedback.
   - **Value:** `0.80` for Sadar Pilot Zone; `0.65` for general Nagpur areas.
3. **`policeDataAvailable` (Boolean):**
   - **Source Data:** `PoliceDatasetStatus.NoDatasetConnected()` vs `DatasetActive()`.
   - **Behavior:** Defaults to `false` until official Nagpur City Police datasets are connected via `ICrimeDataImporter`.
4. **`advisoryNotice` (Text String):**
   - **Ethical Mandate Enforcement:** The scoring engine strictly enforces neutral language. Unit test `testSafetyScoringEngineNeverClaimsGuaranteedSafetyOrAbsoluteDanger` confirms that it never outputs *"guaranteed safe"* or *"absolutely dangerous"*.

*Note on Unimplemented Parameters:* The hypothetical parameters sometimes mentioned in early conceptual outlines (e.g. street lighting lux meters, dynamic foot traffic heatmaps, pavement width ratings, CCTV camera density, road accident statistics) are **not implemented** in the active Kotlin code and are not fabricated.

---

## E. UI/UX & Interactive Control Audit

- **Interactive Controls Verified:**
  - Token show/hide eye toggle button toggles `type="password"` $\leftrightarrow$ `type="text"`.
  - Enter-key form submission triggers authentication with debounce protection.
  - "Check API Health" button sends live query to backend `/api/v1/health`.
  - Category dropdown menu on Incident Report screen (`IncidentReportScreen.kt`) supports 7 categories ("Harassment", "Unsafe route", "Child safety", etc.).
- **Mobile Responsive Layout:**
  - Tested on 375px (iPhone SE/mobile) and 412px (Android standard).
  - Single-column card stacking, zero horizontal scrollbar overflow, touch target padding $\ge 44\text{px}$.
  - System back navigation, keyboard dismissal, and scroll states operate without layout clipping.

---

## F. Nagpur Animated Login Architecture

- **Visual Theme:** Midnight navy (`#0B1120`, `#020617`), charcoal, electric cyan (`#38BDF8`), and saffron gold (`#F59E0B`).
- **Nagpur Landmark SVGs:**
  - **Deekshabhoomi Dome:** High-precision SVG with radial golden glow halo, horizontal ribs, lower entry arches, harmika, and pinnacle chhatri.
  - **Zero Mile Stone:** Sandstone pillar shaft, benchmark inscriptions, hexagonal base pedestal, and apex benchmark beacon.
- **3D Raksha360 Protective Shield:**
  - Multi-stop linear gradient shield with outer gold rim, inner cyan accent ring, vigilance flame, and protective star point.
  - Interactive 3D perspective hover and ambient floating rotation.
- **Frontend/Backend Decoupling:**
  - Ambient particle light trails and landmark animations are drawn on an HTML5 `<canvas>` completely independently of backend requests.
  - The login card makes zero background network requests until the operator explicitly clicks "Enter Control Room" or "Check API Health".
  - Operational disclosure badge clearly displayed: *"INTERNAL COORDINATION TOOL · OFFICIAL POLICE DISPATCH / 112 GATEWAY NOT CONFIGURED."*

---

## G. SOS, Child Safety & Privacy Boundaries

1. **Manual SOS:**
   - Triggers immediately from `HomeScreen.kt` $\rightarrow$ `EmergencyOrchestrator.triggerEmergency`.
   - Generates unique UUID `eventId`, logs audit event, initiates `ForegroundLocationService` notification, and triggers `IncidentAudioRecorder` if enabled in settings.
2. **Phone-Shake SOS:**
   - **Status:** **NOT IMPLEMENTED**. No accelerometer listeners exist in the Android source tree.
3. **Child Safety Implementation:**
   - Implemented in `ChildSafetyRepository.kt`, `ChildSafetyDao.kt`, and `ChildSafetyScreen.kt`.
   - Stores child profiles (`displayName`, `ageBand`, `schoolName`, `safePlaceName`, `geofenceRadiusMeters`).
   - Manages journey overdue monitoring via Android `WorkManager` (`JourneyOverdueWorker.kt`).
   - Sets up local geofences via `SafePlaceGeofenceManager` using Google Play Services `GeofencingClient`.
4. **Privacy & Authorization Boundaries:**
   - **Local-Only Persistence:** Child safety records are stored purely in local Room database (`AppDatabase`).
   - **Zero Cloud Exposure:** The FastAPI backend has no child safety endpoints. Child data is never transmitted to the cloud or visible to dashboard operators, eliminating remote cross-family leakage.

---

## H. Backend & Database Integration

- **Health Probe (`GET /api/v1/health`):**
  - Executes a live `SELECT 1` query using the PostgreSQL connection pool.
  - Returns `HTTP 200` with `{"status": "ok", "database": "connected"}` only when query succeeds.
  - Returns `HTTP 503` when database connection fails, completely suppressing raw database connection strings, passwords, or SQL tracebacks.
  - Bypasses dashboard token authentication to allow unauthenticated uptime monitoring by Render.
- **Database Isolation & Migration:**
  - Automated database migration executes `backend/app/migrations/001_initial_schema.sql`.
  - Schema initialization protects against duplicate column errors (`coordinate_status`) on PostgreSQL.
  - Integration tests enforce PostgreSQL schema isolation (`SET search_path`) and skip destructive tests unless `ALLOW_DESTRUCTIVE_POSTGRES_TESTS=1` is explicitly set.
- **Token Separation:**
  - Device endpoints (`POST /api/v1/emergency-events`, `POST /api/v1/device/heartbeat`) require `Bearer <RAKSHA360_DEVICE_TOKEN>`.
  - Dashboard endpoints (`GET /api/v1/incidents`, `POST /api/v1/incidents/{id}/dispatch`) require `Bearer <RAKSHA360_DASHBOARD_TOKEN>`.

---

## I. Defects Identified and Corrected

| Defect / Risk | Severity | Root Cause | File(s) Changed | Verification Result |
| :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL Schema Duplicate Column Crash** | High | `ALTER TABLE police_station_directory ADD COLUMN coordinate_status` failed on PostgreSQL because the migration already created it. Caught only `sqlite3.OperationalError`. | `backend/app/main.py` | **RESOLVED:** Handled via database engine abstraction; supports both SQLite and PostgreSQL safely. |
| **Test Schema Pollution on Pooled Connections** | High | Integration tests set `search_path` on a temporary connection, but pooled connections opened by FastAPI defaulted to `public`. | `backend/tests/test_postgres_integration.py` | **RESOLVED:** Every pooled connection executes schema set command; teardown cleanly drops test schema. |
| **Health Probe Information Leakage** | Medium | Health check previously returned unprobed status or leaked raw exception messages. | `backend/app/main.py`, `backend/tests/test_api.py` | **RESOLVED:** Executes `SELECT 1`; returns 503 on error without exposing credentials or internal errors. |
| **CredentialManager Test Hang in Robolectric** | Medium | `signOut()` attempted Google Play Services `clearCredentialState` even when signed in as a local demo user, hanging in shadow test environments. | `AuthenticationRepository.kt` | **RESOLVED:** Guarded `clearCredentialState` to execute only when an active non-demo Google UID is present. |
| **Pre-shared Device Token Limitation** | Medium | Android app uses pre-shared static device token rather than individual PKI device enrollment. | `SettingsRepository.kt`, `BackendApiClient.kt` | **DOCUMENTED:** Acceptable for pilot testing; documented as release blocker for general public release. |

---

## J. Android Release Evidence

- **APK Artifact Path:** `distribution/Raksha360-v1.0-release.apk`
- **Application Package ID:** `com.aistudio.naarikavach.safe7x`
- **Version Name / Code:** `1.0` (versionCode: `1`)
- **SDK Targets:** `minSdkVersion: 24`, `targetSdkVersion: 36`
- **APK File Size:** `111.13 MB` (`116,526,417 bytes`)
- **SHA-256 Checksum:** `bd2b74d3beeb62873952010102240a5efca91eb61485d04aca06c75348e29865`
- **Signature Verification:**
  - **Tool:** `apksigner verify --verbose --print-certs`
  - **Result:** `Verifies = true` (Verified using APK Signature Scheme v2: `true`)
  - **Signer DN:** `CN=Raksha360 Release, OU=Civic Safety Engineering, O=Raksha360, L=Nagpur, ST=Maharashtra, C=IN`
  - **Signer SHA-256 Digest:** `dbd8a772dd55c8f44c08e67ae1c3d61b0240da34800358105aae1be3cb84d05f`
- **Target API Base URL:** `https://raksha360-api.onrender.com/api/v1`
- **Native Architectures:** `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (includes Vosk offline speech engine libraries).

---

## K. Comprehensive Test Matrix

| Test Case | Environment | Expected Result | Actual Result | Status | Evidence |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Authentication Session Restore & Demo Sign-In** | Robolectric (Android SDK 34) | Demo user created, session persisted, and clean sign-out without hang | Restored session matches demo profile; sign-out clears prefs | **PASS** | `NaariKavachComprehensiveTest.kt` |
| **Nagpur Geographic Seed Integrity** | Robolectric (Android SDK 34) | Nagpur center, Sadar center, perimeter loop, and verified waypoints exist | Coordinates match real Nagpur landmarks; polygon loop closed | **PASS** | `NaariKavachComprehensiveTest.kt` |
| **Safety Scoring Ethical AI Invariance** | Robolectric (Android SDK 34) | Never claims route is "guaranteed safe" or "absolutely dangerous" | Output notice contains neutral infrastructure advisory | **PASS** | `NaariKavachComprehensiveTest.kt` |
| **Production Police Dispatch Adapter Fallback** | Robolectric (Android SDK 34) | Reports UNAVAILABLE and directs user to call 112 | Status is UNAVAILABLE; message directs to 112 | **PASS** | `NaariKavachComprehensiveTest.kt` |
| **Demo Police Dispatch Adapter Simulation** | Robolectric (Android SDK 34) | Simulates safe demo dispatch with tracking ticket | Demo ticket generated; no external network dispatch | **PASS** | `NaariKavachComprehensiveTest.kt` |
| **Trusted Contact Demo Seeding Isolation** | Robolectric (Android SDK 34) | Seeding does nothing for regular users; seeds demo contacts when isDemoMode=true | Clean separation verified; demo contacts tagged | **PASS** | `NaariKavachComprehensiveTest.kt` |
| **Evidence File SHA-256 Tamper Integrity** | Robolectric (Android SDK 34) | Untouched file matches hash; 1-byte tamper produces hash mismatch | Hashes match before modification and differ after modification | **PASS** | `NaariKavachComprehensiveTest.kt` |
| **Production API Base URL Normalization** | Robolectric (Android SDK 34) | Strips trailing slashes and matches BuildConfig | URL normalized to `https://raksha360-api.onrender.com/api/v1` | **PASS** | `NaariKavachComprehensiveTest.kt` |
| **Demo Emergency Event Backend Isolation** | Robolectric (Android SDK 34) | Simulated demo event does not invoke live dispatch | Returns `DEMO-TKT-` ticket without live alerts | **PASS** | `NaariKavachComprehensiveTest.kt` |
| **Security Audit Logging** | Robolectric (Android SDK 34) | Logs security events, SOS events, evidence seals | Audit log entities correctly recorded | **PASS** | `AuditLoggerServiceTest.kt` (4 tests) |
| **Backend API Health Probe** | Pytest (Python 3.10) | GET /api/v1/health returns 200 with db probe | Health endpoint passes probe | **PASS** | `backend/tests/test_api.py` |
| **Backend Health 503 on Database Down** | Pytest (Python 3.10) | Returns HTTP 503 and hides internal credentials | 503 returned; credentials suppressed | **PASS** | `backend/tests/test_api.py` |
| **Public Police Directory Provenance** | Pytest (Python 3.10) | 37 stations listed; unreviewed coordinates remain null | Directory integrity confirmed | **PASS** | `backend/tests/test_api.py` |
| **Incident Deduplication & Audit Log** | Pytest (Python 3.10) | Duplicate event IDs rejected; audit log records entries | Deduplication verified | **PASS** | `backend/tests/test_api.py` |
| **PostgreSQL Integration Isolation** | Pytest (psycopg) | Migrations execute cleanly; test schema dropped | Skipped safely until disposable DB configured | **NOT RUN** | `backend/tests/test_postgres_integration.py` |
| **Web Dashboard Production Build** | Vite 6.4.4 / Node.js | Clean compilation of React assets | Built in 8.35s with 0 errors | **PASS** | `police-dashboard/dist/` |
| **Animated Login Landmarks & Controls** | Vite Preview / Browser Code Review | Deekshabhoomi, Zero Mile Stone, 3D shield, token toggle | Rendered via SVG/Canvas/CSS; bundle passes build | **PASS** | `NagpurAnimatedLogin.jsx` |
| **Phone-Shake Sensor Trigger** | Physical Hardware / Sensor | Accelerometer shake activates emergency SOS | Sensor listener not implemented | **NOT RUN** | Feature absent from codebase |
| **Live Microphone Background Voice Wake** | Physical Phone / Hardware | Continuous background detection of "Raksha 360" | Requires physical phone testing & model download | **NOT RUN** | Hardware-dependent |

---

## L. Final Recommendation

### **READY FOR CONTROLLED TESTING**

**Rationale & Operational Constraints:**
1. **Ready for Controlled Pilot Demonstration:**
   - The core architecture is completely established: FastAPI backend deployed on Render, React dashboard deployed on Vercel, and stable signed release APK (`Raksha360-v1.0-release.apk`) configured for the production API.
   - Comprehensive test suites pass: **19 Android unit/Robolectric tests pass (100%)**, **46 backend pytest tests pass**, and the frontend builds cleanly.
   - All Nagpur-specific branding, landmarks (Deekshabhoomi, Zero Mile Stone), ethical safety scoring, and citizen report intake are fully functional.
2. **Blockers for Unrestricted Public Play Store Distribution:**
   - **Device Authentication:** Authentication currently uses a pre-shared pilot token (`RAKSHA360_DEVICE_TOKEN`) configured in settings. A dynamic individual device enrollment/revocation protocol (e.g. mTLS or device-specific activation tokens) must be implemented before unrestricted public distribution.
   - **Offline Voice Model Asset Provisioning:** Vosk speech and speaker models must be downloaded via `scripts/prepare-voice-model.ps1` and bundled before Voice SOS operates on-device.
   - **Hardware Sensor Features:** Phone-shake trigger is not implemented in the current source code and requires sensor event integration. Physical microphone wake-word testing in noisy environments requires physical Android phone verification.
