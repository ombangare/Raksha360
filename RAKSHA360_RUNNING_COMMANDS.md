# Raksha360 — Windows running commands

## Start both laptop services

Recommended first-run command from the extracted project root:

```powershell
.\START_RAKSHA360.bat
```

It creates a private root `.env` with unique development tokens on first startup, sets up the Python virtual environment, installs backend dependencies, and opens separate PowerShell windows for the backend and Police Control Room.

Open:

- Police Control Room: `http://localhost:5173`
- API health: `http://127.0.0.1:4000/api/v1/health`
- FastAPI interactive docs: `http://127.0.0.1:4000/docs`

Use `RAKSHA360_DASHBOARD_TOKEN` from the private root `.env` on the dashboard login screen. Keep this token private.

## Manual commands

Backend (from project root):

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r backend\requirements.txt
python -m pip install pytest httpx
python -m uvicorn backend.app.main:app --host 0.0.0.0 --port 4000 --reload
```

Police dashboard (second terminal):

```powershell
cd police-dashboard
npm install
npm run build
npm run dev
```

Backend tests (project root):

```powershell
python -m py_compile backend\app\main.py
python -m pytest -q backend\tests
```

## Connect a physical Android phone

1. Connect the phone and laptop to the same trusted Wi-Fi network.
2. Find the laptop's LAN IPv4 address (the start script prints an address).
3. On the phone, open `Privacy Center → Backend & Control Room Connection`.
4. Set the API URL to `http://<LAPTOP-LAN-IP>:4000/api/v1` and paste `RAKSHA360_DEVICE_TOKEN` from the private root `.env`.
5. Tap **Test connection**, then **Connect phone**.
6. If needed, allow inbound TCP 4000 in Windows Firewall for Private networks only. Do not expose this development server to the public internet.

For an Android emulator, use `http://10.0.2.2:4000/api/v1`.

## Configure missed-heartbeat last-known-location email

This feature cannot pull a fresh location from a powered-off phone. The server detects that heartbeats stopped, creates a `DEVICE_OFFLINE` incident at the last coordinates received, updates the police dashboard map, and sends a message to consented contacts if SMTP is configured.

After the first startup, stop the backend server and edit the root `.env` (not `.env.example`) using SMTP settings from your mail provider:

```dotenv
RAKSHA360_OFFLINE_THRESHOLD_SECONDS=180
RAKSHA360_OFFLINE_MONITOR_INTERVAL_SECONDS=30
RAKSHA360_OFFLINE_EMAIL_RETRY_SECONDS=900
RAKSHA360_SMTP_HOST=smtp.your-provider.example
RAKSHA360_SMTP_PORT=587
RAKSHA360_SMTP_USERNAME=your-smtp-username
RAKSHA360_SMTP_PASSWORD=your-provider-app-password
RAKSHA360_SMTP_FROM_EMAIL=alerts@your-domain.example
RAKSHA360_SMTP_USE_TLS=true
```

Restart the backend. On the Android phone, add an active trusted contact with a valid email and enable the switch **Email last-known location if the phone stops checking in**. The person must also keep Guard Mode or a tracked safety session running so periodic heartbeats continue. The backend/laptop and network must remain available; SMTP must be reachable. Never commit or share `.env`.

### Safe test procedure

Use a spare/test installation. Enable the consent switch and Guard Mode/tracking, make sure Device Watch shows at least one received heartbeat and a real GPS timestamp, then stop network/monitoring and wait for the configured threshold. Verify:

- A `DEVICE_OFFLINE` incident appears in the dashboard queue.
- Its map marker/popup says **last known location · not live** and includes the location timestamp.
- Device Watch shows the offline status and email delivery state.
- The contact receives an email only if configured/consented.
- Reconnecting the phone clears the current offline state.

The phone cannot send new GPS while fully powered off. A missed heartbeat may also reflect no coverage or a stopped service; it is not proof of danger. SMTP acceptance is not guaranteed inbox delivery.

## Android build

This recovered source archive does not ship the `gradlew` / `gradlew.bat` launch scripts and wrapper JAR in the ZIP, so use Android Studio on the target machine to open the project root, sync the Gradle configuration, and run **Build → Make Project** or install to a test device. Do not claim an APK build passed until Android Studio/Gradle actually finishes successfully.

Voice SOS model binaries are not included. Model setup (PowerShell, project root):

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\prepare-voice-model.ps1
```

Then sync/build/install and test phrase + speaker pass/fail cases on the phone. Model download/build success alone is not proof the emergency trigger is reliable.
