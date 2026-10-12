# Raksha360 Recovery Notes — October 2026

## Repository contents
This recovered workspace contains three related components:

- `app/`: Android Kotlin/Jetpack Compose client, existing Naari Kavach package/application identity and Room database filename retained for compatibility, child-safety local records, Voice SOS code, and configurable backend REST client.
- `backend/`: local FastAPI development server, SQLite database, shared token authentication for the phone and dashboard, REST endpoints, WebSocket fan-out, demo records, and backend integration tests.
- `police-dashboard/`: separate Vite/React desktop control-room app, MapLibre map, incident and report queues, internal dispatch tasks, response-unit directory, and audit view.

## Server startup
On Windows double-click `START_RAKSHA360.bat`. The PowerShell launcher generates a private root `.env` with random development tokens if absent, makes a Python `.venv`, installs the backend requirements, and starts the backend and dashboard in separate consoles. Dashboard is `http://localhost:5173`; API docs are `http://127.0.0.1:4000/docs`. Configure the Android client with the laptop LAN IPv4 plus `/api/v1` or use `http://10.0.2.2:4000/api/v1` from the Android emulator.

## Verified here
- Backend API suite: `python -m pytest -q backend/tests` — 4 tests passed in the recovery sandbox.
- Python backend syntax compilation passed.
- Dashboard source syntax transpilation and JSON/XML static validation passed after the latest changes.

## Not verified in this environment
- A Vite production bundle: npm dependency installation timed out in the isolated environment.
- Android Gradle build and physical-phone testing: the uploaded project has no Gradle wrapper launch scripts/JAR and this environment has no Android SDK or installed Gradle.
- Voice SOS recognition on a device: the binary Vosk ASR and speaker model files are not bundled. Follow `scripts/README-voice-models.md` and `scripts/prepare-voice-model.ps1` then run real positive/negative audio tests.
- Real external police dispatch, official complaint intake, SMS/email/push providers, and remote child/guardian identity authorization: not configured.

## Safety boundary
The backend/dashboard syncs real app requests to its local SQLite DB and emits WebSocket notifications. It does not connect to an official police gateway. Dispatch rows are internal, auditable control-room tasks only. Seeded incidents and response units are synthetic and marked demo; they must not be represented as real emergency events or police units. Google photorealistic 3D tiles are an optional provider requiring a user-owned restricted Google Maps Platform API key, billing and terms compliance.

## Offline / phone-power-loss email feature (2026-10-10)

The latest update adds server-side missed-heartbeat monitoring in FastAPI. While Guard Mode or an active tracked session is actually sending device heartbeats, the backend remembers the latest valid coordinates received. If the server misses the configured threshold (default 180 seconds), it creates one `DEVICE_OFFLINE` incident with that location/time and broadcasts `DEVICE_OFFLINE_DETECTED` and `INCIDENT_CREATED` to the police dashboard. MapLibre uses a red marker whose popup explicitly says the position is last-known, not live.

Email is off by default and requires the user's explicit consent switch, an active trusted contact with a valid opted-in email, SMTP settings in the private laptop `.env`, and a running laptop backend/network. A powered-off phone cannot send new GPS; a missed heartbeat is not conclusive proof the phone was switched off or the person is in danger. Per-recipient delivery rows prevent successful email recipients from being emailed again when retrying another failed recipient. Consent revocation on a later heartbeat deletes stored recipient addresses and per-recipient delivery records for that device.

Backend tests: `python -m pytest -q backend/tests` — 11 passed in this environment. Tests mock SMTP and do not send real emails. JavaScript/JSX source syntax and CSS parsing passed. Dashboard dependency installation timed out so Vite production build was not run. Android Kotlin parser-level checking found no syntax diagnostics in edited files, but Android/AndroidX dependencies and Gradle wrapper launcher files were unavailable, so a compile and physical-device test are still required.

See `RAKSHA360_ANTIGRAVITY_FULL_SPECIFICATION.md` and `RAKSHA360_RUNNING_COMMANDS.md` for the current full specification, verification protocol, and Windows commands.
