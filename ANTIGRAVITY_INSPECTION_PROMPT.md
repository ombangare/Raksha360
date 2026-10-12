# ANTIGRAVITY AUDIT PROMPT — RAKSHA360 ANDROID + BACKEND + POLICE CONTROL ROOM

You are opening an EXISTING extracted Raksha360 project. It has three parts: (1) an Android Kotlin/Jetpack Compose safety app, (2) a Python FastAPI local server that should run on this laptop, and (3) a separate React/Vite police dashboard intended for this laptop browser. Do not replace the project with a new scaffold and do not make large edits before you finish the baseline audit.

## Phase 1 — READ-ONLY AUDIT FIRST

1. Inspect the whole tree and identify `app/`, `backend/`, `police-dashboard/`, model preparation scripts, root startup scripts, `.env.example`, README, and current tests.
2. Inspect code, package files, API request/response models, Room entities/DAO/migrations, Android manifest permissions, API base URL/token configuration, and WebSocket event contracts.
3. Never open secrets by printing their values if a root `.env` already exists. Do not add keys or tokens to code, screenshots, logs, or Git.
4. Check that the backend owns persistence for SOS incidents, location updates, reports, internal dispatch tasks, stations, and audit logs.
5. Check the phone's configured events/requests against the backend endpoints and Pydantic models. Check WebSocket message names and payload shapes against the dashboard subscription handlers.
6. Check dashboard flows: token login, command center metrics, incident details/status update, report create/status update, internal dispatch create/status update, station directory, audit trail, MapLibre pins, missing/stale coordinate handling, polling fallback, and WebSocket reconnection.
7. Check that all demo incidents/response units are clearly identified as synthetic and cannot be mistaken for live cases.
8. Inspect map dependencies and attribution. MapLibre + OpenFreeMap Bright is the default no-key colorful street map. Do not require Google Maps keys or billing. Keep OpenFreeMap/OpenMapTiles/OpenStreetMap attribution visible.
9. Inspect child-safety database migration, guardian-consent, geofence, journey timeout/check-in, school PIN expiry/replay/attempt limits, and local-vs-server sync boundaries.
10. Inspect Voice SOS model initialization and source files. Binary Vosk ASR and speaker models are not shipped. Do not claim the voice engine works until models are present, app compiles, and positive/negative tests are performed on an Android phone. Verify fail-closed behavior on missing models, revoked microphone permission, phrase mismatch and speaker mismatch. Loudness alone must never count as phrase recognition.
11. Run only read-only/static/baseline checks first and report exact results: Python version, Android/Gradle/SDK availability, Node/npm version, backend test result, dashboard syntax/build status, and missing model files.
12. Return an audit report containing: existing architecture, build status, critical errors grouped by layer, risky assumptions, API contract mismatches, and a prioritized repair plan. Do not edit any files in Phase 1.

## Phase 2 — WAIT FOR APPROVAL

After reporting the audit, wait for my approval before editing. When approved, use small, reversible commits. Preserve existing files and database data, inspect files before editing, add migrations rather than destructive schema reset, and never overwrite unrelated changes.

## Phase 3 — FIX AND VERIFY AFTER APPROVAL

### Backend
- Start it from the repository root using the existing scripts. Preserve `.env` and keep secrets out of output.
- Run `python -m pytest -q backend/tests`; add tests for any new behavior and fix failing tests before saying the task passes.
- Confirm device token auth, dashboard token auth, event idempotency, location updates, reports, incident status, internal dispatch workflow, and audit records.
- Confirm that local dispatch tasks are not reported as official police dispatch. No 112 integration or authorized external police gateway is configured unless credentials and actual integration evidence are supplied.
- Confirm REST and WebSocket behavior on reconnect and prevent duplicate emergency events.

### Police Control Room (laptop)
- Run `npm install` and `npm run build` in `police-dashboard/`. Report genuine failures and fix them; don't stop at JSX syntax checks.
- Keep the dark glassmorphism / black-gold-blue layout, readable status indicators (red critical, amber waiting/in-progress, green resolved/verified), responsive nav, keyboard-accessible controls, and descriptive button labels.
- Every button/action must do the labeled work, show loading/error/success feedback, and never claim unsupported actions. Verify incident acknowledgement, incident status transitions, report form/status, dispatch task, dispatch-status changes, refresh, login/logout, search/filters and map controls.
- MapLibre should validate coordinates before placing markers, label demo coordinates as demo, show public directory stations and incidents with distinct styles, and make unavailable/stale locations explicit. Use OpenFreeMap Bright by default; no Google map key or billing is needed. Preserve map attribution.
- Do not invent real police station coordinates. Use only verified imported data or clearly labeled synthetic test data.

### Android phone app
- Open/sync/build with a real Android Gradle setup. Do not state that it compiles if Gradle wrapper/Android SDK is missing.
- Verify the Backend Connection screen against `GET /api/v1/health` and the device-session endpoint. Connect using laptop LAN IPv4 plus `/api/v1` path on a physical device, or `http://10.0.2.2:4000/api/v1` on emulator.
- Test physical-phone connectivity only with phone and laptop on a trusted same Wi-Fi and firewall limited to private network.
- Verify a manual SOS creates one persistent backend incident that appears in the laptop dashboard without refresh. Verify a location update changes the dashboard/map record. Verify report submit creates a database record visible to the dashboard.
- Never create test SOS events through the normal emergency button without explicit test-mode control and clear labels.
- Verify that demo-mode or failed connection states are clear and no false “police notified” / “SMS sent” confirmation is presented.
- For child data, verify guardian links and consent before remote sharing. Current child data may be local-first and not fully synchronized; document what remains local.
- Download models only using the supplied documented script / official sources after review. Do not check binary models or credentials into source control. Test phrase recognition and speaker match/mismatch using consented audio.

### Final report
Report exactly which tests/builds ran and their actual results, files changed, remaining failures, real device steps, environment requirements, and anything still mocked/not integrated. Never call the system “fully functional” merely because a page loads. Clearly distinguish working local app/database flow, internal dispatch tasking, provider notification, and official police dispatch. Do not publish the local backend directly to the public internet.

Begin now with Phase 1 only: inspect, execute safe baseline checks, and report. Make no file edits until I approve Phase 2.

## Additional required checks: phone-off location email

Read `RAKSHA360_ANTIGRAVITY_FULL_SPECIFICATION.md` for the full current requirements. Specifically verify the backend missed-heartbeat monitor, server-received timestamps, additive SQLite schema for `device_status`, `emergency_contacts`, and `offline_email_deliveries`, the explicit owner consent switch, per-recipient SMTP retry de-duplication, and the `DEVICE_OFFLINE` WebSocket event. The dashboard must display the last-known position as **not live** on its Device Watch page and on the map popup. Do not claim live GPS from a powered-off device.

For run commands and SMTP setup, read `RAKSHA360_RUNNING_COMMANDS.md`. Check all build and on-device limitations honestly; do not mark voice SOS verified while binary models are missing or an Android build/device test has not passed.


## 2026-10-11 Nagpur public-data + map upgrade

For a full implementation task, use `RAKSHA360_ANTIGRAVITY_NAGPUR_REALDATA_PROMPT.md`. Public station/contact files and synthetic feature fixtures are in `reference-data/nagpur-public/`.
