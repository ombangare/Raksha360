# Raksha360 — Android Real-Device Safety Trigger Test Checklist

**Date:** 14 October 2026  
**Target Package ID:** `com.aistudio.naarikavach.safe7x`  
**Version:** `1.0` (versionCode: `1`)  
**Target Build:** Signed Release APK (`distribution/Raksha360-v1.0-release.apk`)  
**APK SHA-256:** `bd2b74d3beeb62873952010102240a5efca91eb61485d04aca06c75348e29865`  
**Target Backend:** `https://raksha360-api.onrender.com/api/v1`  
**Device Connection State:** No ADB device currently attached. Physical phone testing pending manual execution by operator.

---

## 1. Safety Trigger & Operational Test Matrix

> **Status Definitions:**
> - `PASS`: Verified directly on physical phone hardware.
> - `FAIL`: Executed on phone and failed expected behavior.
> - `BLOCKED`: Cannot execute on phone due to missing asset, prerequisite, or hardware constraint.
> - `NOT IMPLEMENTED`: Feature is absent from the active codebase.
> - `NOT TESTED`: Awaiting manual verification on physical phone.

| Test ID | Feature | Exact action | Expected result | Actual result | Status | Evidence or log reference |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-AND-01** | **Clean App Launch** | Install and tap Raksha360 launcher icon on phone home screen. | App launches cleanly into splash screen, initializes Room database, and opens Home Screen without crashes. | Unit tests pass cleanly on Android SDK 34 (`NaariKavachComprehensiveTest`). Pending physical phone tap. | **NOT TESTED** | `MainActivity.kt`, `NaariKavachApp.kt` |
| **TC-AND-02** | **First-Launch Permissions** | Launch app and observe runtime permission dialogs for Location, Microphone, and Notifications. | Dialogs request `ACCESS_FINE_LOCATION`, `RECORD_AUDIO`, and `POST_NOTIFICATIONS` with clear civic safety rationales. | Permission request launchers configured in `HomeScreen.kt` & `MainActivity.kt`. | **NOT TESTED** | `AndroidManifest.xml` (lines 6-25) |
| **TC-AND-03** | **Manual SOS Button** | On Home Screen, tap the prominent red **Emergency** quick-action square (`action_emergency_panic`). | Button provides immediate visual haptic/touch response; triggers `viewModel.triggerManualPanic()`; active emergency state displayed. | Unit test verified dispatch pipeline. Pending physical screen touch. | **NOT TESTED** | `HomeScreen.kt` (lines 328-338) |
| **TC-AND-04** | **SOS Request Success (Demo Mode)** | Trigger Manual SOS while in default Demo Mode (`isDemoMode = true`). | Immediate local simulation; returns `DEMO-TKT-XXXX` ticket; inserts into Room DB; zero external network alerts dispatched. | Verified by unit test `testDemoEmergencyEventIsolationPreventsLiveDispatch`. | **NOT TESTED** | `BackendApiClient.kt` (lines 196-207) |
| **TC-AND-05** | **SOS Request Failure & Offline Queuing** | Turn off Wi-Fi/Mobile Data, or connect to unconfigured endpoint, and trigger SOS. | Request fails gracefully; error is visible; event is encrypted and saved to `OfflineQueueEntity` in Room DB; no crash. | Handled via `eventDao.insertEvent` followed by `queueDao.enqueueItem`. | **NOT TESTED** | `EmergencyRepository.kt` (lines 101-118) |
| **TC-AND-06** | **Offline Behavior & Network Recovery** | Reconnect Wi-Fi/Mobile Data after offline SOS trigger. | `OfflineSyncManager` / `EmergencyRepository` detects network connectivity (`isOnline()`) and flushes pending queue. | `pendingQueueCount` flow monitored; queue flushed on reconnect. | **NOT TESTED** | `EmergencyRepository.kt` (lines 36-44) |
| **TC-AND-07** | **Location Allowed (GPS Fix)** | Grant Precise Location permission and verify coordinates attached to incident. | `AppLocationManager` receives GPS/Network fix; updates `currentLocation`; sets `isFallback = false`; attaches live coordinates to incident. | Real GPS listeners registered on `GPS_PROVIDER` and `NETWORK_PROVIDER`. | **NOT TESTED** | `LocationManager.kt` (lines 60-70) |
| **TC-AND-08** | **Location Denied (Fallback Handling)** | Deny location permission or disable GPS toggle in Android settings. | App does not crash; defaults to Sadar, Nagpur (`21.1612, 79.0825`); sets `isFallback = true`; incident reports location as approximate. | Verified fallback logic in `LocationManager.kt` (`SADAR_NAGPUR_LAT`, `SADAR_NAGPUR_LNG`). | **NOT TESTED** | `LocationManager.kt` (lines 34-43) |
| **TC-AND-09** | **Voice-Model Asset Loading** | Inspect `app/src/main/assets/` and observe Guard Mode start in Voice SOS Settings. | Requires `vosk-model-small-en-us-0.15` and `vosk-model-spk-0.4`. If missing, app displays warning notice. | Assets missing from release APK. `VoiceModelEngine` reports: *"Voice SOS models are missing. Run scripts/prepare-voice-model.ps1"*. | **BLOCKED** | `app/src/main/assets/models/README.txt` |
| **TC-AND-10** | **Voice SOS Phrase Detection** | Speak *"Raksha 360, help me"* after model provisioning and Guard Mode activation. | Vosk offline ASR matches phrase variants (`three sixty`, `three six zero`) and verifies speaker cosine similarity $\ge 0.82$. | Code handles ASR variants; blocked on phone due to unbundled models. | **BLOCKED** | `VoiceModelEngine.kt` (lines 316-345) |
| **TC-AND-11** | **Voice False-Positive Check** | Speak ordinary conversations, background chatter, or loud non-speech noise near phone. | Ordinary speech and ambient sounds do not trigger emergency; no false SOS alerts dispatched. | Algorithmic safeguard: strict text match + speaker similarity; no sound-energy fallback. | **BLOCKED** | `VoiceModelEngine.kt` (lines 70-74) |
| **TC-AND-12** | **Voice Monitoring Start / Stop** | Toggle Guard Mode switch in Home Screen or Voice SOS Settings. | `GuardModeService` starts/stops foreground service (`microphone\|location`) with sticky notification; microphone released on stop. | Foreground service lifecycle implemented in `GuardModeService.kt`. | **NOT TESTED** | `AndroidManifest.xml` (line 59) |
| **TC-AND-13** | **Phone-Shake SOS Trigger** | Shake phone vigorously, walk, jog, or move device abruptly. | **Feature is absent from codebase.** No shake listener or accelerometer sensor code exists. | Inspected entire codebase; 0 shake listeners or accelerometer registrations found. | **NOT IMPLEMENTED** | Source inspection of `app/src/main/java` |
| **TC-AND-14** | **Duplicate-Event Prevention** | Rapidly double-tap or triple-tap the Emergency SOS button within 1 second. | Debounced in `EmergencyOrchestrator`; only one emergency event record (`eventId`) is created; duplicate dispatches suppressed. | `activeEventId` check prevents overlapping simultaneous triggers. | **NOT TESTED** | `EmergencyOrchestrator.kt` (lines 23-27) |
| **TC-AND-15** | **Device Authentication & Session Check** | Open Privacy Center $\rightarrow$ Backend Connection; enter production URL and valid/invalid token; tap "Test Connection". | Valid token returns HTTP 200 and confirms session; invalid token returns HTTP 401 with clear rejection message; no crash. | `BackendApiClient.testConnection` validates HTTPS, health check, and `/device/session`. | **NOT TESTED** | `BackendApiClient.kt` (lines 118-150) |
| **TC-AND-16** | **Session Expiry & Token Rotation** | Rotate or invalidate device token on backend and attempt heartbeat/dispatch. | Backend returns HTTP 401; app notifies user of rejected authentication; does not retry in an infinite loop. | HTTP status 401 triggers authentication error in `BackendApiClient`. | **NOT TESTED** | `BackendApiClient.kt` (line 143) |

---

## 2. Real-Device Physical Testing Instructions

### Prerequisite: APK Location & File Details
- **File Path on Computer:** `distribution/Raksha360-v1.0-release.apk`
- **File Size:** `111.13 MB` (`116,526,417 bytes`)
- **Package ID:** `com.aistudio.naarikavach.safe7x`
- **Build Type:** Release (Signed with stable keystore `keystores/raksha360-release.jks`, alias `upload`)
- **SHA-256 Checksum:** `bd2b74d3beeb62873952010102240a5efca91eb61485d04aca06c75348e29865`

### Installation Options:

#### Option A — Via USB Cable & ADB (Recommended for Developers):
1. Enable **Developer Options** and **USB Debugging** on your Android phone.
2. Connect your phone to your computer via USB cable.
3. Open a terminal in the project root and run:
   ```powershell
   adb install -r distribution/Raksha360-v1.0-release.apk
   ```
4. If an older build with a conflicting debug signature is already installed, run:
   > ⚠️ **Note on Data Loss:** Running `uninstall` deletes local Room database tables (stored contacts and local test incidents). Ensure you do not need previous local demo logs.
   ```powershell
   adb uninstall com.aistudio.naarikavach.safe7x
   adb install distribution/Raksha360-v1.0-release.apk
   ```

#### Option B — Direct Transfer / Sideloading:
1. Copy `distribution/Raksha360-v1.0-release.apk` to your phone via USB file transfer, Google Drive, or local storage.
2. On your phone, tap the APK in your File Manager.
3. If prompted, allow "Install from Unknown Sources" for your File Manager.
4. Tap **Install** / **Update**.

---

## 3. Prioritized Step-by-Step Test Sequence on Phone

### Test Step 1: Clean Launch & Permissions (TC-AND-01, TC-AND-02)
1. Tap the **Raksha360** app icon on your phone home screen.
2. Confirm that the application opens smoothly to the Home Screen.
3. When prompted, grant **Location** (select "While using the app" or "Precise") and **Notifications**.

### Test Step 2: Safe Manual SOS Test (TC-AND-03, TC-AND-04)
1. Verify that the app is in **Demo Mode** (this is enabled by default out of the box).
2. Tap the red **Emergency** button on the Home Screen.
3. **Verify:**
   - The button responds immediately with touch feedback.
   - An active emergency notification appears in your Android notification shade (`Raksha360 Emergency Active`).
   - A demo tracking ticket (`DEMO-TKT-XXXXXXXX`) is displayed.
   - No external SMS, emails, or police dispatch calls are made.

### Test Step 3: Location Fallback Test (TC-AND-07, TC-AND-08)
1. Open the Android Quick Settings shade and turn OFF **Location** (GPS).
2. Trigger the Emergency SOS button again.
3. Verify that the incident records fallback coordinates (`Sadar, Nagpur (Default)`) with `isFallback = true` and does not crash.

### Test Step 4: Rapid Tap Debounce Test (TC-AND-14)
1. Rapidly tap the Emergency button 3 to 4 times within 1 second.
2. Open Audit Logs or Incident History.
3. Verify that only a single emergency incident was generated rather than multiple duplicate incidents.

### Test Step 5: Backend Connection Verification (TC-AND-15)
1. In the app bottom navigation, go to **Settings** / **Privacy Center**.
2. Select **Backend Connection**.
3. Confirm that the default URL is pre-filled with:
   `https://raksha360-api.onrender.com/api/v1`
4. Tap **Test Connection**.
5. Observe the result:
   - Without an authorized pilot token: displays `Health check reachable but device authentication rejected (HTTP 401). Verify device token.`
   - This proves the app connects via secure HTTPS to the live Render backend and correctly rejects unauthenticated device requests.
