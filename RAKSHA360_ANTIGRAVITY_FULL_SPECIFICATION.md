# Raksha360 — Full Antigravity Audit, Repair, and Verification Specification

You are working on the existing Raksha360 workspace extracted from the supplied ZIP. This is a safety application with three connected components: an Android phone app, a FastAPI server that runs on the user's laptop, and a separate React/Vite Police Control Room that runs in the laptop browser.

**Do not replace the repository with a new scaffold.** First inspect the real files and Git status. Preserve all existing code and local data. Do not print secrets from `.env`, do not add credentials to source control, and do not claim a feature works solely because its screen renders.

## Stage 1 — read-only baseline audit

Inspect the repository tree, dependencies, source, API contracts, Room entities/DAO/migrations, Android manifest, foreground-service configuration, backend SQLite schema, WebSocket events, frontend page routes, map components, start scripts, `.env.example`, and tests. Identify missing services/models and all commands available on this machine.

Run safe baseline checks without editing files:

- Python version and backend dependency presence.
- `python -m py_compile backend/app/main.py`.
- `python -m pytest -q backend/tests`.
- Node/npm versions; whether `police-dashboard/node_modules` exists; `npm run build` only when dependencies are installed.
- Whether Android Gradle wrapper launch scripts, wrapper JAR, Gradle, and Android SDK are actually present. Never claim an Android build passed if it was not run.
- Whether Vosk ASR and speaker-model binaries exist. The voice-model download script is `scripts/prepare-voice-model.ps1`; the binary models are not expected in source control.

Report the baseline, issues, and proposed repair order before making wide-ranging edits. Do not delete files, reset databases, regenerate secrets, or rewrite working modules as an audit shortcut.

## Stage 2 — system architecture to preserve

### Android phone app

- Kotlin and Jetpack Compose UI.
- Room local persistence and existing database migrations.
- Existing manual SOS, incident/report flows, trusted contacts, child-safety profile/journey/school-handover features, safety map, and emergency orchestrator.
- Existing backend API configuration screen. For a phone on the same trusted Wi-Fi as the laptop use `http://<LAPTOP-LAN-IP>:4000/api/v1`; for the Android emulator use `http://10.0.2.2:4000/api/v1`.
- Guard Mode / active journey must send heartbeats while the safety session is actually armed and permitted. Do not create a heartbeat-only “monitoring active” claim when no repeating service is running.
- Voice SOS should use actual offline phrase recognition plus enrolled-speaker verification where supported, fail closed if either model is missing, and never trigger from loudness alone. Binary models are omitted from the source archive; install only from the documented script and verify on a real phone.

### Laptop backend

- FastAPI on port 4000; SQLite persistence in `backend/data/raksha360.sqlite3` by default; startup via the existing Windows launcher scripts.
- Token-authenticated device endpoints and token-authenticated dashboard endpoints for local development.
- Persist SOS incidents, coordinates/location timestamps, reports, internal dispatch tasks, device heartbeats/offline alerts, consented emergency email contacts, per-recipient email delivery state, station directory, and audit trail.
- `/ws/police` broadcasts backend events to the dashboard without requiring a full page refresh.
- Internal dispatch records are not proof of real-world police assignment or official 112 contact. No official police gateway is assumed configured.
- Keep schema changes additive and backward-compatible. Never delete the user's SQLite database to apply a change.

### Police Control Room

- Separate React/Vite dashboard at `http://localhost:5173`.
- Dark glassmorphism interface with black/gold/blue accents and red/amber/green state signals.
- Persisted incident queue, report review, internal dispatch task log, station directory, audit trail, and `Device Watch` view.
- Default geographic view uses MapLibre GL JS and OpenFreeMap Bright/Liberty vector street tiles. No Google Maps key or billing is required. Retain correct OpenFreeMap/OpenMapTiles/OpenStreetMap attribution.
- All pins need valid coordinates. Demo/sample pins must be visibly marked synthetic. Missing and stale coordinates must not be invented or shown as live.

## Stage 3 — implement and verify last-known-location email alert correctly

### Required behavior

The phrase “phone switched off” must be implemented as a missed-heartbeat condition, because a phone with no power cannot acquire or transmit new GPS coordinates. The feature sends the **last location previously received by the server**, not a live location from the powered-off device.

1. When the user has explicitly consented and Guard Mode or a tracked safety session is active, the Android app sends an authenticated heartbeat at the configured interval.
2. Include location only when it is a valid real device location, never a fallback/demo coordinate. Keep the exact time of the location fix separate from the server's heartbeat-received time.
3. Include a persistent per-installation device ID, monitoring state, available location accuracy, location timestamp, battery percentage where available, global email-sharing consent, and active trusted contacts that have opted into email and have valid addresses.
4. Measure heartbeat freshness using the **server receive time**, not a timestamp trusted from the Android client. Handle reconnects and clear the offline state when a new authenticated heartbeat arrives.
5. With the default threshold of 180 seconds, a backend monitor task scans armed devices. Make the interval and timeout configurable with `RAKSHA360_OFFLINE_THRESHOLD_SECONDS` and `RAKSHA360_OFFLINE_MONITOR_INTERVAL_SECONDS`.
6. When the threshold is exceeded, create exactly one `DEVICE_OFFLINE` incident for that offline episode. Persist the last known lat/lon and accuracy; set the location source to `LAST_KNOWN_NOT_LIVE`. If coordinates are unavailable, show that plainly rather than substituting Nagpur or another pin.
7. Broadcast the offline event and `INCIDENT_CREATED` through `/ws/police`; send device status over the authenticated device-status API. The dashboard must display the alert immediately, refresh over REST as fallback, and draw a red last-known marker on the MapLibre map.
8. A popup/incident record must clearly say `LAST KNOWN LOCATION · NOT LIVE` and show the location-fix timestamp. A missed heartbeat is not proof of danger: the phone may be off, outside coverage, disconnected, the app/service may have stopped, or the laptop backend may be unavailable.
9. Only if the owner has explicitly enabled the location-email consent switch and an active trusted contact has both a valid email and email notification enabled may the backend send a notification.
10. Use SMTP from the laptop backend via these environment variables: `RAKSHA360_SMTP_HOST`, `RAKSHA360_SMTP_PORT`, `RAKSHA360_SMTP_USERNAME`, `RAKSHA360_SMTP_PASSWORD`, `RAKSHA360_SMTP_FROM_EMAIL`, `RAKSHA360_SMTP_USE_TLS`. Credentials stay only in the private root `.env`; no real credentials in code, prompts, tests, or Git.
11. If SMTP is not configured, there are no eligible contacts, consent is false, or sending fails, return and display an honest state (`SMTP_NOT_CONFIGURED`, `NO_CONSENTED_EMAIL_CONTACTS`, `NOT_CONSENTED`, `FAILED`, `PARTIAL`, `SENT`). A server acceptance is not proof the message reached the inbox.
12. Track delivery per recipient and incident. On a partial failure, do not resend to recipients whose SMTP submission already succeeded when retrying a failed recipient. Retry failed delivery only with a configurable backoff (`RAKSHA360_OFFLINE_EMAIL_RETRY_SECONDS`, default 900 seconds).
13. When consent is revoked and the phone next checks in, delete server-stored contact email destinations/delivery-recipient records for the device. Avoid logging recipient addresses, credentials, precise coordinates, or unnecessary child data.
14. Document that laptop backend, operating system, network/internet and SMTP provider must still be available for an email to be sent. The laptop itself cannot create an email while powered off.

### Police dashboard location synchronization

- An SOS event with real coordinates appears in the incident queue and as an incident marker on MapLibre.
- A location update changes the location for the correct incident/device and broadcasts a WebSocket event.
- A missed heartbeat creates a `DEVICE_OFFLINE` incident whose coordinates match the last real location stored in `device_status`; the marker and Device Watch row both identify it as last-known, not live.
- Never use the sample/default map center or a demo incident as a substitute for missing phone GPS.
- Make click targets, forms, selects, dialog buttons, map controls, refresh, logout/login, record details, report creation/status actions, dispatch-task creation/status actions, and device-watch navigation work as labelled. Every async write must show a pending state and a success/error state. No dead buttons.

## Stage 4 — security and privacy boundaries

- Default global email sharing to off. Show clear consent language in the Android app. The owner can revoke consent. An email contact is eligible only if active, email-valid, and marked for email notifications.
- Store actual locations only as needed for the configured safety/incident use; keep the last location timestamp and source state. Keep the local database file and `.env` private and out of Git.
- Keep CORS restricted to the dashboard origins for local development. Do not expose the development service directly to the public internet. Use HTTPS, real identity/role authorization, rate limiting, monitoring, backup/retention policy, and a reviewed data-protection model before production.
- Do not add hidden/covert location tracking. Use explicit permissions and visible foreground-service controls/notifications for Android background work.
- Do not claim SMS/email/push delivery occurred unless the configured provider reports successful acceptance and label this accurately.
- No claim of official police dispatch or legal/official complaint filing unless a genuine authorized external integration exists.

## Stage 5 — automated tests to add/run

Backend tests should cover:

- Device and dashboard auth.
- Phone SOS persistence and idempotent retry.
- Location update persistence and API validation.
- Report submission and status lifecycle.
- Internal dispatch task creation/status lifecycle with official gateway remaining `NOT_CONFIGURED`.
- Heartbeat stores only supplied valid coordinate pairs and retains the last known location when a later heartbeat has no fresh GPS.
- A stale client-provided clock does not make a fresh server-received heartbeat appear offline.
- Monitoring disabled means no offline incident.
- Monitoring enabled + stale server heartbeat creates exactly one `DEVICE_OFFLINE` incident using the saved coordinates/timestamp and broadcasts the event.
- Consent false never calls the email sender or retains stored email destinations.
- Missing SMTP and missing recipient states are explicit.
- Email sending is mocked in tests; no real mail is sent by automated tests.
- Partial email delivery retry does not resend to contacts already marked `SENT`.
- Reconnecting device clears its current offline state and allows future separate offline episodes.
- WebSocket receives new incidents without manual browser refresh.

Dashboard checks:
- `npm install`, `npm run build`, login using the local dashboard token, API polling, WebSocket reconnect, map markers, Device Watch content/statuses, search/filter, report and dispatch interactions, browser/mobile responsive layout and keyboard navigation.
- Verify every action has a working handler and every error is visible.

Android checks:
- Open in Android Studio, synchronize Gradle, compile, install on a real Android device, test API connection and permissions, confirm real coordinates are sent instead of fallback values, confirm SOS/report sync to the dashboard, and verify heartbeat while Guard Mode/tracked journey is active.
- Run voice positive/negative phrase and speaker tests after model setup; a successful Gradle build is not evidence voice recognition works.
- Test offline timeout with a spare/test installation, using the saved last-known coordinates; do not use a real emergency or depend on this for personal protection until verified.

## Stage 6 — local run commands (Windows PowerShell)

From the extracted project root:

```powershell
# The easiest path starts both backend and dashboard, and provisions the local .env once.
.\START_RAKSHA360.bat
```

Manual backend from the root:

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r backend\requirements.txt
python -m pip install pytest httpx
python -m uvicorn backend.app.main:app --host 0.0.0.0 --port 4000 --reload
```

Dashboard in a second terminal:

```powershell
cd police-dashboard
npm install
npm run build
npm run dev
```

Backend checks in a terminal at the root:

```powershell
python -m py_compile backend\app\main.py
python -m pytest -q backend\tests
```

On the phone, use `http://<LAPTOP-LAN-IP>:4000/api/v1`; Android emulator uses `http://10.0.2.2:4000/api/v1`. Use the device/dashboard tokens from the private `.env`. Do not paste tokens into the prompt or expose the server to the public internet.

## Stage 7 — final report format

After fixes, provide:

1. Files changed and reasoning.
2. Exact commands executed and exact pass/fail outputs.
3. Which features have automated backend coverage and counts.
4. Whether Vite build succeeded, or why it could not be run.
5. Whether Android Gradle compile/device tests succeeded, or why they could not be run.
6. Last-known-location timeout, consent, email provider and dashboard-map end-to-end test results.
7. Missing keys, models, service configuration and deployment requirements.
8. Known issues and specific steps to resolve them.

Do not claim “everything works” until every relevant build and real-device test has actually passed. Begin with the existing repository and use small, auditable fixes. Keep all current files/data unless a change is explained and necessary.
