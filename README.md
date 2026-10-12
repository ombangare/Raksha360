# Raksha360 — Android Safety App + Local Police Control Room

Raksha360 is the rebranded continuation of the Naari Kavach Android safety app. This workspace now contains **three connected pieces**:

1. **Android phone app** — Kotlin + Jetpack Compose. It can send configured SOS events and reports to the laptop backend and contains child/family safety workflows and the Voice SOS implementation.
2. **FastAPI backend on your laptop** — persists incidents, locations, reports, dispatch tasks, stations, and audit events in local SQLite and broadcasts changes using WebSockets.
3. **Separate police dashboard on your laptop** — React + Vite + MapLibre GL JS with dark glassmorphism styling, red/amber/green status signals, incident queue, report workflows, internal dispatch task tracking, response unit directory, audit trail, and map.

> **Operational boundary:** dispatch in this starter is an auditable internal control-room task. No official police gateway, police dispatch system, automatic 112 dispatch, SMS provider, push-notification provider, or email delivery provider is configured. A dashboard status such as “unit en route reported” is an operator-entered status, not evidence that a real officer received a message. The initial events and response units are clearly marked synthetic demo records.

## Quick start on Windows

### Requirements

- Windows 10/11
- Python 3.11+ on PATH
- Node.js LTS and npm on PATH
- Internet access the first time so the dashboard packages and map basemap can load
- Android Studio / Android SDK to compile and install the phone app

### Start backend and dashboard

1. Extract the ZIP to a folder you can write to, for example `C:\Raksha360`.
2. Double-click **`START_RAKSHA360.bat`**.
3. On first run it creates a private root `.env` with freshly generated device and dashboard tokens, creates `.venv`, installs Python dependencies, then opens separate PowerShell windows for the backend and dashboard. The first run can take several minutes.
4. On the laptop, open:
   - Police Control Room: `http://localhost:5173`
   - Backend health: `http://127.0.0.1:4000/api/v1/health`
   - Interactive API docs: `http://127.0.0.1:4000/docs`
5. Copy the value of `RAKSHA360_DASHBOARD_TOKEN` from the root `.env` into the dashboard login screen. Do not share `.env`, post either token in screenshots, or commit the file.

If port 4000 or 5173 is already occupied, close the old process or change the port and its matching environment/API settings. Keep the local server on a trusted private network; do not expose it directly to the public internet.

## Connect the Android phone to the laptop

Both devices should be on the same trusted Wi-Fi network. `START_RAKSHA360.bat` prints a likely laptop LAN IPv4 address. On the phone app, open **Privacy Center → Backend & Control Room Connection**:

1. For a physical phone, enter `http://<LAPTOP-LAN-IP>:4000/api/v1`, replacing the placeholder with the printed laptop IPv4.
2. For the Android emulator, use `http://10.0.2.2:4000/api/v1`.
3. Copy `RAKSHA360_DEVICE_TOKEN` from the root `.env` into the device-token field.
4. Tap **Test connection**, then **Connect phone**. The app only saves the connection and disables demo mode after the backend health check and device-token check succeed.
5. If a physical phone cannot connect, verify the laptop LAN IP, that both devices use the same network, and that Windows Firewall allows TCP port 4000 on **Private networks only**. Do not create a public-network or internet-wide firewall rule.

The backend binds to `0.0.0.0` so a phone on the same LAN can reach it. Traffic uses plain HTTP for local development only. Use HTTPS and a production-grade identity/session system before deploying beyond a trusted development network.

## What syncs

- Android emergency events are persisted by the backend and appear in the dashboard queue/map when usable coordinates are sent.
- Location updates are recorded against an existing incident and broadcast to connected dashboard clients.
- Incident status changes, report creation/status changes, internal dispatch-task creation/status changes, and audit entries persist in SQLite.
- The dashboard reads the same database-backed API and connects to `/ws/police` for real-time events; REST polling provides a fallback refresh every 15 seconds.
- The Android report form creates a Raksha360 report record; it does not submit an official police/cybercrime complaint.

Check event source and `DEMO` flags carefully. The seed records use illustrative Nagpur-area sample pins; they are not real emergency locations or verified police stations. The `reference-data/nagpur-public/` folder now contains a snapshot of public station names and contact details from the official Nagpur City Police directory. Phone/email values should be rechecked before reliance. Missing station coordinates are intentionally blank until independently geocoded and reviewed. The directory is not a dispatch integration.

## Power-off / missed-heartbeat last-known-location alerts

The Android app can send a device heartbeat while Guard Mode or an active tracked location session is running. The backend records the **last real GPS location it actually received**. If the laptop backend later misses the configured heartbeat timeout (default 180 seconds), it creates a `DEVICE_OFFLINE` incident, broadcasts the incident through the police dashboard WebSocket, and places a marker at the last known coordinates when available.

**This is not live tracking after power-off.** A phone with no power cannot acquire or transmit a fresh GPS location. A missed heartbeat can also mean loss of mobile/Wi-Fi coverage, the app/service being stopped, or the laptop/backend being unreachable. Treat it as a condition to verify, not proof of danger. The last-known timestamp is stored separately and the map labels the pin as `LAST KNOWN LOCATION · NOT LIVE`.

Email is off unless the user explicitly enables **Email last-known location if the phone stops checking in** in Trusted Contacts, has at least one active contact with a valid email, and the laptop administrator configures SMTP. The laptop must stay powered on with the FastAPI backend running and must be able to reach the SMTP server. A successful SMTP submission is not the same as confirmed inbox delivery.

After the first `START_RAKSHA360.bat` run, stop the backend console, then edit the root `.env` (not `.env.example`) and set at minimum:

```dotenv
RAKSHA360_OFFLINE_THRESHOLD_SECONDS=180
RAKSHA360_OFFLINE_MONITOR_INTERVAL_SECONDS=30
RAKSHA360_OFFLINE_EMAIL_RETRY_SECONDS=900
RAKSHA360_SMTP_HOST=smtp.your-provider.example
RAKSHA360_SMTP_PORT=587
RAKSHA360_SMTP_USERNAME=your-smtp-username
RAKSHA360_SMTP_PASSWORD=your-smtp-app-password
RAKSHA360_SMTP_FROM_EMAIL=alerts@your-domain.example
RAKSHA360_SMTP_USE_TLS=true
```

Restart the backend after changing `.env`. Keep SMTP credentials private and out of Git/screenshots. Use a provider-issued credential or app password; do not paste any real secret into a chat or the specification prompt. If SMTP is blank, the Device Watch page explicitly reports `Not configured`; no email is claimed to have been sent.

Test this in a controlled way: connect a test phone, enable consent and Guard Mode / a tracked journey, wait for at least one heartbeat that contains an actual (not fallback) GPS fix, then stop network access or stop monitoring and wait through the configured timeout. The phone should stop sending; the laptop server should create a `DEVICE_OFFLINE` incident using the saved location, and the dashboard should show that incident plus the device's last-known marker. Do not actually power down your everyday safety phone to perform an initial test; use a spare/test installation.

## Map options

### Default: MapLibre operations map

The desktop dashboard uses MapLibre GL JS with **OpenFreeMap Bright** as its default colorful street map, powered by OpenStreetMap-based vector data. This requires no Google Maps key or billing. A style switch can move between Bright and Liberty. Visible attribution is `OpenFreeMap © OpenMapTiles · Data from OpenStreetMap`. The hosted basemap requires internet access and is not satellite or photorealistic 3D imagery. See `reference-data/nagpur-public/map_provider_config.json` for style URLs and source details.

### Public Nagpur station directory versus demo units

`reference-data/nagpur-public/nagpur_police_stations.json` and `.csv` contain 37 public directory entries captured from the official [Nagpur City Police station directory](https://nagpurpolice.gov.in/police-stations), retrieved 11 October 2026; the page reports last update 10 August 2026. A separate authenticated endpoint exposes these as read-only `OFFICIAL_PUBLIC_DIRECTORY` records. Coordinates remain blank unless independently verified. They are not live station availability, roster or dispatch status. The existing `DEMO_ONLY` units remain synthetic and visibly distinct.

For public contact numbers, see `reference-data/nagpur-public/official_helplines_and_links.json`. The NCRB 2023 statistics source manifest links to publicly available annual city-aggregate tables but deliberately contains no unverified numerical claims. Never invent actual FIRs, victim data, or official incidents to fill the dashboard; all demo incident/report/dispatch fixtures remain synthetic and clearly labeled.
## Child safety and Voice SOS status

The Android project includes local child profile/guardian-consent workflows, Android geofence registration, safe-journey deadlines/overdue alerts, and a one-time school pickup PIN flow with hash/expiry/attempt checks. These child safety records are currently local-first; they do not yet have a complete server-side guardian account/authorization model or school system integration. Real child profile/location data should not be entered into this prototype until those boundaries are completed and reviewed.

Voice SOS has been switched away from a loudness-only phrase fallback to an offline Vosk speech-recognition path and speaker-embedding verification code. **The binary Vosk models are not included in this ZIP**, and the speech/speaker model pipeline has not been compiled or tested on a physical Android phone in this environment. It must be treated as unavailable until the model-preparation script completes, Android Gradle build succeeds, microphone permissions work, and positive/negative speaker/phrase tests pass:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\prepare-voice-model.ps1
```

See `scripts/README-voice-models.md`. The speaker threshold is not statistically calibrated. Do not depend on Voice SOS as the sole safety mechanism.

## Build and verification

Backend tests run with:

```powershell
python -m pip install -r backend\requirements.txt
python -m pip install pytest httpx
python -m pytest -q backend\tests
```

The tests validate device and dashboard authentication, event persistence/idempotency, location, report and internal dispatch workflows, last-known location on missed heartbeats, consent enforcement, and retry behavior without duplicate emails. The SMTP sender is mocked in tests; no real emails are sent by the test suite.

Dashboard:

```powershell
cd police-dashboard
npm install
npm run build
npm run dev
```

Android: open the extracted project root in Android Studio, sync Gradle, build/install to a real test phone, and test app-to-dashboard end-to-end. This archive did not contain Gradle wrapper launch scripts/JAR and this environment did not have an Android SDK; therefore **an Android compile or physical-device verification is not claimed**. Dashboard npm dependency installation timed out in the recovery environment; JSX syntax was checked, but a Vite production build must still be run on your computer.

## Next steps before production

- Add real station/unit directory and verified coordinates under authorization.
- Implement a server-side multi-user auth/role model rather than the local development shared tokens.
- Add secure guardian/child relationships and server-side permission enforcement before syncing child data.
- Configure notification providers with delivery callbacks and retry monitoring.
- If real police dispatch is required, integrate only through an authorized police gateway; log request/acceptance states accurately.
- Use HTTPS, restricted API keys, secret management, rate limits, monitoring, tested backups, retention policy, and a reviewed security model.
- Calibrate the biometric speaker threshold with consented genuine/impostor audio and conduct safety testing.

## Project materials

- `ANTIGRAVITY_INSPECTION_PROMPT.md` — existing-project audit prompt.
- `RAKSHA360_ANTIGRAVITY_FULL_SPECIFICATION.md` — full multi-layer audit, repair, and test specification.
- `RAKSHA360_RUNNING_COMMANDS.md` — Windows start, phone connection, SMTP setup, and build/test commands.
- `RAKSHA360_PRODUCT_BRIEF.md` — child-safety/recovery feature notes from prior recovery.
- `RECOVERY_NOTES.md` — summary of earlier source-recovery and voice-model limits.
- `backend/` — local FastAPI server, SQLite persistence, WebSocket stream and tests.
- `police-dashboard/` — separate desktop web app.
- `app/` — Android phone app.
