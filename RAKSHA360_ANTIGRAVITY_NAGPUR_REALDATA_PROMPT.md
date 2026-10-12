# ANTIGRAVITY MASTER PROMPT — RAKSHA360 REAL NAGPUR PUBLIC DATA + COLORFUL FREE MAP + FULL FEATURE VERIFICATION

You are editing the existing Raksha360 repository in place. This project continues Naari Kavach. Do not create a separate replacement demo. First inspect all current files, preserve working behavior, then make a narrowly scoped, additive and testable upgrade.

## 0. Mandatory baseline and safety boundaries

Read `README.md`, `RECOVERY_NOTES.md`, `RAKSHA360_ANTIGRAVITY_FULL_SPECIFICATION.md`, `RAKSHA360_RUNNING_COMMANDS.md`, `ANTIGRAVITY_INSPECTION_PROMPT.md`, the attached RAKSHA360 audit report, and every file under `reference-data/nagpur-public/` before editing. The uploaded audit says the FastAPI suite had 11 passing tests, the Vite production build passed after installing packages, Vosk models were downloaded, and the app/API were probed. Re-run verification in the current workspace; do not assume those results apply to new code.

The system has three parts:
1. Native Android Kotlin/Jetpack Compose app + Room database + foreground Guard Mode/voice/location services + family/child safety workflows.
2. Local FastAPI backend on port 4000 + SQLite + authenticated REST + WebSocket fan-out + missed-heartbeat monitor + opt-in SMTP email.
3. React 19/Vite police control-room dashboard on port 5173 + MapLibre GL JS + casework/dispatch/audit/device-watch views.

**Do not misrepresent the application as an official or connected Nagpur Police system.** Use the official public station directory and public help numbers from the included files only. Do not scrape the authenticated Nagpur Police command dashboard, bypass a login, access private FIR/victim data, invent incident-level police data, invent API access, or create fake officer/unit identities. There is no official police/112 dispatch gateway configured. A local dashboard dispatch task is not real deployment of personnel.

**Data classifications are mandatory:**
- `OFFICIAL_PUBLIC_DIRECTORY`: public station names/contact fields with source URL/retrieval date.
- `OFFICIAL_AGGREGATE_STATISTIC`: exact annual/city aggregate figure transcribed from a cited official NCRB table and independently checked.
- `SYNTHETIC_TEST_FIXTURE`: all local SOS/report/child/journey/device/dispatch training records. Keep `is_demo=true` and a visible DEMO badge everywhere.
- `LIVE_USER_EVENT`: an actual event received from a connected user device, clearly distinguished from test data and public directory records.
Do not merge categories into a shared metric without a separate filter/source label. Public station directory data is not evidence that an operational unit is online, reachable, assigned or dispatched.

A powered-off phone cannot send fresh GPS. When a heartbeat is missed, show the last location that the server actually received, timestamped `LAST KNOWN LOCATION · NOT LIVE`. A missing heartbeat is not proof the phone was switched off; other causes include signal loss, app/OS stopping the service, or laptop/backend downtime. Email requires explicit consent, active/valid contacts and configured SMTP. SMTP acceptance is not confirmed inbox delivery.

## 1. Files to treat as canonical data inputs

Use these local files rather than inventing data:
- `reference-data/nagpur-public/nagpur_police_stations.json`
- `reference-data/nagpur-public/nagpur_police_stations.csv`
- `reference-data/nagpur-public/official_helplines_and_links.json`
- `reference-data/nagpur-public/public_sources_manifest.json`
- `reference-data/nagpur-public/ncrb_2023_city_statistics_source_manifest.json`
- `reference-data/nagpur-public/ncrb_city_year_statistics_import_template.csv`
- `reference-data/nagpur-public/feature_parameters_and_safety_invariants.json`
- `reference-data/nagpur-public/demo_incidents.json`
- `reference-data/nagpur-public/demo_reports.json`
- `reference-data/nagpur-public/demo_dispatches.json`
- `reference-data/nagpur-public/demo_device_contacts_child_safety.json`
- `reference-data/nagpur-public/feature_test_coverage_matrix.csv`
- `reference-data/nagpur-public/map_provider_config.json`

The station dataset intentionally has null coordinates. Do not set missing coordinates to 0,0 or fabricate lat/lon. If geocoding is desired, run the optional `geocode_nagpur_stations_review.py`, review candidate outputs manually against OpenStreetMap, and store only human-approved coordinates with `coordinates_verified=true`, `coordinate_source`, `coordinate_checked_at`, `osm_type`, and `osm_id`. Never use a result simply because it is the first geocoder hit.

## 2. Integrate the real public Nagpur station directory correctly

Add a separate additive SQLite table such as `police_station_directory` with columns:
`station_id PRIMARY KEY`, `name`, `name_mr`, `phone`, `email`, `address`, `jurisdiction_label`, `latitude nullable`, `longitude nullable`, `coordinates_verified default false`, `source_url`, `source_page_last_updated`, `retrieved_on`, `data_class`.

Load the snapshot from `reference-data/nagpur-public/nagpur_police_stations.json` using an idempotent seed/import step. Add token-protected read-only API endpoint `GET /api/v1/police-station-directory`. Return source metadata and the important note that these are public directory references, not an official dispatch integration. Keep the existing `stations` table for synthetic response units unless a migration clearly separates the two data concepts; never silently replace demo units with unverified `OFFICIAL UNIT` records. Use additive migrations and preserve existing SQLite rows.

Update the dashboard's **Stations & Units** page into two clearly distinct sections:
A. `Nagpur City Police — public directory reference`: official source name, local script/name when available, public phone/email, listed address, source/retrieval date, source link, `PUBLIC DIRECTORY · NOT A DISPATCH CONNECTION` badge, and map/open-location action only when coordinates have been manually verified.
B. `Raksha360 demo response units`: current synthetic units with DEMO badges.
Search/filter must operate on the correct section. Do not show blank coordinates as `0,0`. Do not label a public listing “verified operational”, “available”, “on duty” or “dispatched”.

The public helpline cards must use the included source file; show 112, 1091, 1098, 1930, 108 and the listed public office/control-room lines with a “verify the current number on the official source” note. If click-to-call is supported, it may open the dialer; never silently call or submit a report.

## 3. Replace the map style with a clear, colorful no-key street map

Use MapLibre GL JS (already in the stack) with OpenFreeMap's **Bright** vector style as the default:
`https://tiles.openfreemap.org/styles/bright`

Optional style switch: Bright / Liberty / Dark using:
- `https://tiles.openfreemap.org/styles/bright`
- `https://tiles.openfreemap.org/styles/liberty`
- `https://tiles.openfreemap.org/styles/dark`

Use Nagpur center from `map_provider_config.json`, default pitch 0, responsive zoom, smooth focus controls, metric scale and navigation zoom. This is a free hosted vector street map with OpenStreetMap-based data, not Google's proprietary Google Maps SDK, not Google's Photorealistic 3D Tiles, and not satellite imagery. Do not require `VITE_GOOGLE_MAPS_API_KEY`, Google billing or other paid map keys. Remove the old Google-3D upsell/button/page if it remains in this project. Do not promise permanent availability or SLA for a public tile endpoint.

Keep visible attribution: `OpenFreeMap © OpenMapTiles · Data from OpenStreetMap`. Ensure popups do not hide attribution. Internet is required for hosted tiles. Always validate coordinates before adding a marker; `lat` in [-90,90], `lng` in [-180,180], finite numbers. If no coordinates exist, retain the record in the directory/list but do not pin it. If coordinates are last-known, visually differentiate that marker and keep `NOT LIVE` wording and timestamp.

Map marker rules:
- Real incoming SOS: red/critical based on severity and visibly labeled as app-reported, not an official FIR.
- Synthetic fixture: gold/amber and `DEMO` label in popup and list.
- Official public police directory: a distinct station icon only when approved coordinates exist; popup includes source link and “public directory reference; no connection/availability information”.
- Device online/offline marker: blue/green/red plus last heartbeat; offline location popup must say `LAST KNOWN LOCATION · NOT LIVE`.
- Never map annual NCRB aggregate counts as incident points, never geocode sensitive report text, and never show location as live if not receiving current GPS.

## 4. Source and integrate public crime statistics safely

The included NCRB manifest points to the Government of India `Crime in India 2023` catalog and official report. The bundle intentionally does **not** contain made-up Nagpur crime counts. If importing values, access the exact public NCRB table, identify `Nagpur` metropolitan-city row and table heading/year, transcribe the exact figure, add table ID/page/direct source URL/retrieval date and reviewer to a CSV/JSON aggregate-statistics file, and run numeric/schema validation. If the right row cannot be verified, leave the metric unavailable and show the official source link instead. Do not scrape the protected Nagpur Police dashboard. Keep 2021–2023 annual aggregate statistics separate from current incident monitoring and label each chart `NCRB aggregate statistic — source year YYYY`. Never use raw counts for per-area risk maps without valid area denominators and methodology.

## 5. Populate synthetic fixtures for every user-shared feature

Use the included synthetic fixtures for local testing only. If a seed importer is required, create a repeatable command (for example `python scripts/import_raksha360_fixtures.py --demo`) that:
- is explicit opt-in, not run in production automatically;
- is idempotent and uses fixed `DEMO-*` IDs;
- calculates relative event times from current server UTC time;
- sets `is_demo=true`, `source=SYNTHETIC_FIXTURE`, and user-visible DEMO tags;
- uses only the sample coordinates and generic descriptions in those fixtures;
- never sends SMTP email, SMS, push, phone call, or official gateway requests;
- adds synthetic audit events for seed and cleanup;
- offers a clear `--clear-demo` option that deletes only fixture rows, never real/live rows.

Cover all these families, as applicable to existing app architecture:
- Manual panic-button SOS and dedupe/idempotency.
- Voice SOS exact phrase `Raksha 360, help me`, speaker similarity threshold `0.82`; fail closed if voice model unavailable; no loudness-only trigger; real device test remains separate.
- Child SOS and lost-child alert.
- Safe journey deadline/overdue/cancelled-as-safe.
- Guardian-consented safe-place geofence exit/entry; denied if guardian consent or required foreground/background permissions are absent.
- Missed child check-in and school handover/pickup escalation.
- Six-digit single-use school handover PIN; store only a secure hash; expiry 10 minutes after scheduled pickup time; lock/require review after 5 failed attempts; reject replay.
- Location stream associated to event; coordinate and accuracy validation.
- Trusted contacts with active flags and explicit separate email consent.
- Device heartbeat/device watch: ONLINE, OFFLINE, DISARMED, missing-location and stale-last-location cases.
- Missed-heartbeat incident: default 180-second threshold, monitor interval 30 seconds; server-received heartbeat time not client-supplied time; one incident per offline episode; reset on reconnection; no incident when disarmed.
- Last-known-location email retry: 900-second default retry backoff, per-recipient de-duplication, all delivery statuses honest (`SENT`, `PARTIAL`, `FAILED`, `SMTP_NOT_CONFIGURED`, `NO_CONSENTED_EMAIL_CONTACTS`, `NOT_CONSENTED`). Mock SMTP in automated tests. Do not send to `.invalid` fixture addresses.
- Reports/complaints categories, priority and status updates, optional location/evidence hash, evidence metadata; never claim official filing.
- Internal dispatch status lifecycle, assignments and audit trail; external gateway state always `NOT_CONFIGURED` until a real authorized integration is configured and verified.
- Stations directory, public helplines and no-key map provider.
- Localization or multilingual UI currently implemented in source; do not remove existing English/Hindi/Marathi/Telugu/other supported language features if present.

Parameters above reflect the included audit report/project snapshot and must be compared against source at runtime before changing defaults. Do not arbitrarily change safety thresholds.

## 6. Integration, API, security, schema and UX quality bar

- Keep endpoint contracts synchronized across Kotlin client, FastAPI Pydantic models, SQLite schema and React dashboard API methods.
- Use additive database migrations, transactions, valid indexes, idempotent seed scripts and tests. Do not delete existing user data to reseed.
- Preserve device bearer token vs dashboard token authorization; keep tokens out of frontend bundles, data files, logs and screenshots. No new unauthenticated private-data endpoint.
- Public directory endpoint may still require dashboard token, since the application is local/private. Return only public directory fields it needs.
- Validate source/file schemas; reject malformed lat/lng, bad emails, out-of-bounds values, oversized strings, and invalid status transitions.
- No automatic publishing/sending/contacting real people. Add clear confirmation and safe test paths for actual user actions.
- Retain responsive dashboard layout, contrast, readable font sizes, loading/empty/error states, map attribution, keyboard focus, accessible button labels, and mobile/large-desktop layouts.
- Avoid reducing source files to stubs or replacing app features with a mock static screen.
- Keep `.env`, SQLite database, node_modules, build outputs, signing files and device data out of the distributable ZIP unless explicitly required. Keep `.env.example` with placeholders only.

## 7. Automated test acceptance criteria

Backend:
1. `python -m py_compile backend/app/main.py`
2. `python -m pytest -q backend/tests`
3. Unit tests for importing 37 public station directory rows, missing optional coordinates remaining null, source provenance, query auth, no duplicates on repeat seed and no overwriting coordinates that were manually approved.
4. Verify official directory list and demo response units return in separate keys/categories.
5. Verify a synthetic fixture seed is explicit/idempotent and does not email or external dispatch.
6. Verify a real/live event remains `is_demo=false`, while fixture remains `is_demo=true`.
7. Verify no consent means zero SMTP sends and invalid `.invalid` addresses are never sent.
8. Verify offline map popup wording `LAST KNOWN LOCATION · NOT LIVE` and reconnection semantics.
9. Verify the public directory doesn't infer operational status from a phone/email record.

Dashboard:
1. `cd police-dashboard; npm install; npm run build`.
2. Parse or lint changed JSX; no browser console errors on load.
3. Confirm MapLibre loads Bright style with no API key, and style switch works.
4. Confirm attribution remains visible, pins appear only for valid coordinates, and demo/public/live layers look distinct.
5. Confirm Stations & Units shows official public directory with exact phone/email/source and no fabricated coordinates; demo unit section remains visibly synthetic.
6. Confirm offline marker popup and queue entries show last-known, not live.
7. Confirm all view links, refresh and WebSocket polling work.

Android:
- Run Gradle sync/build in the user's Android Studio if wrapper/toolchain is available.
- Check Android source compile, permissions and foreground service types, Room migrations, WorkManager/journey flow, consent gates, voice model fail-closed behavior and backend base URL (`/api/v1`).
- Download/use Vosk models only through existing official preparation script, not by committing large model artifacts in this data pack.
- Physical-device testing for microphone permissions, battery optimization, background location and real GPS is mandatory before claiming a feature works in real-world operation.

Full-system:
- Run backend + dashboard, test a synthetic event through API -> SQLite -> WebSocket/dashboard -> status update -> audit trail.
- Test a real Android event only on an owned/test device; never use real emergency numbers/contact recipients for demo testing.
- Check that `officialPoliceGateway` and `externalGatewayStatus` remain `NOT_CONFIGURED` unless an actual approved integration is established.
- Report actual pass/fail results, exact commands, build environment and any missing dependencies. Do not claim Android build or real-device tests passed unless actually performed.

## 8. Map and data UX acceptance: visual behavior

The map should look like a clear, colorful contemporary street map with normal road colors, place names and land-use color variation—not the current near-black CARTO Dark Matter map. Keep the Raksha360 UI dark/glassmorphism if desired; make the map canvas itself bright. Default map center is Nagpur. Add small style-switch buttons (Bright / Liberty / Dark), `Center Nagpur`, zoom controls and a restrained legend. The basemap must not obscure incident overlays.

For every record, users should be able to answer:
- Is this a live app-reported incident, a synthetic demo, an official public directory listing, or annual aggregate statistic?
- Is the coordinate current GPS, last-known GPS, a verified station coordinate, a synthetic demonstration coordinate, or absent?
- Is there any actual connection to police dispatch? If not, say `Internal Raksha360 record only; external police gateway not configured.`

## 9. Final deliverables required from you

1. Update the current repo in place with minimal, additive changes.
2. Add the public directory table/API and a clear Station Directory UX while keeping demo response units distinct.
3. Change map default to OpenFreeMap Bright + MapLibre; remove Google Maps/Google 3D dependency/upsell and all requirement for Google billing/key.
4. Add idempotent demo importer/test fixtures for the included data pack; never use synthetic fixture data in official statistics or mark it real.
5. Keep source provenance links and the NCRB aggregate import blank/template until exact figures are verified.
6. Write `NAGPUR_PUBLIC_DATA_NOTES.md` documenting sources, freshness, jurisdiction caveats, map attribution and real-vs-demo boundary.
7. Write `IMPLEMENTATION_AND_TEST_REPORT.md` with changed files, test outputs, limitations and anything not verified.
8. Provide exact PowerShell commands to run backend, dashboard and Android build. Keep secrets private.
9. Produce a fresh project ZIP without `.env`, private SQLite database, tokens, `node_modules`, or generated build artifacts.

Start by inspecting the project and these files. Then implement and run tests. Do not stop after generating a plan or only editing a README. If a source record or integration cannot be verified, leave it clearly unavailable and explain the blocker rather than inventing data.
