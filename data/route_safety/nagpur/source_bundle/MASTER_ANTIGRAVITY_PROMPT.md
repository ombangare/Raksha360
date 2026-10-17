# MASTER ANTIGRAVITY PROMPT — RAKSHA360 FULL REPAIR + NAGPUR ROUTE CONTEXT DATA

You are working in the existing Raksha360 repository. This is a production-minded integration and repair task, not a request to create a separate demo or rewrite the app from scratch. Inspect the actual repository and its current implementation first; preserve working features and existing architectural choices unless a concrete defect requires a change.

## 0. First inspect the repository and the supplied data bundle

Repository path on the user's Windows machine: `C:\Users\omban\Downloads\Raksha360`
GitHub repo: `https://github.com/ombangare/Raksha360`
Known latest user-reported commit: `0368e7f` — `fix(repair): resolve auth timeout, voice enrollment persistence, elevenlabs diagnostics, and floating assistant UI`. Verify the actual current branch, HEAD, `git status`, and remote before doing anything. Do not reset, discard, overwrite, or reformat unrelated user changes.

The attached data bundle includes:
- `nagpur_highway_blackspots_2021_2023.csv`
- `nagpur_highway_blackspots_2021_2023.geojson`
- `extract_nagpur_route_layers.py`
- `nagpur_route_safety_context_query.ql`
- `nagpur_osm_route_safety_query.ql`
- `ROUTE_SCORING_SPEC.md`
- `DATA_SOURCE_REGISTER.json`
- `README.md`

Read the data bundle and this prompt fully before editing application code. Do not state that the new OpenStreetMap facility/woodland data is already extracted: the provided script and query are extraction tools, and the live OSM result files only exist after a successful run.

### Data integration workflow
1. Keep an untouched copy of the supplied source bundle under a clearly named repo directory, e.g. `data/route_safety/nagpur/source_bundle/`. Do not check secrets or huge transient output into Git.
2. Run the extractor from an internet-enabled environment, using Python 3.10+:
   ```powershell
   py -m pip install requests "shapely>=2.0"
   py extract_nagpur_route_layers.py --out ..\extracted
   ```
   Run it from the directory containing `extract_nagpur_route_layers.py`, or use its absolute path. If the environment uses `python` instead of `py`, substitute accordingly.
3. Inspect `extraction_report.json`, each layer's raw JSON, GeoJSON feature counts, timestamps, geometry quality, and samples. If a layer failed, record it as failed/unavailable; never create fake records to make the counts look complete. If the Overpass service times out, retry after a delay or run layers separately. Avoid hammering the public endpoints.
4. The extraction bounding box (20.95, 78.70, 21.35, 79.40) is a planning window covering Nagpur city plus a peri-urban buffer; it is NOT an official NMC boundary or all of Nagpur district. Display the coverage limit in source metadata/UI where relevant.
5. Preserve OSM attribution and ODbL requirements. Retain OSM IDs, object versions, object timestamps, source, extraction time, and geometry-quality metadata.
6. Investigate the official OGD Smart Lights: Nagpur catalogue only as a historical lead: `https://ap.data.gov.in/catalog/smart-lights-nagpur` and the child resource `https://kerala.data.gov.in/resource/smart-lights-nagpur-jan-2019`. Its published resource is from Jan 2019; do not treat it as current operational lighting unless downloaded, geocoded, and field-validated.
7. MRSAC documents a Nagpur land-use GIS project here: `https://mrsac.gov.in/MRSAC/flipbook/project/implementation-of-geographic-information-system-for-nagpur-improvement-trust-nit-ndash-nmrda`. This is a lead, not proof that a downloadable layer was obtained. Use it only if an actual data file can be retrieved and its coverage/licence/date verified.

## 1. Overall mission

Make Raksha360 a properly connected, testable safety-support application for Nagpur and its users. Fix real defects in the existing Android app, FastAPI backend/PostgreSQL integration, and React police dashboard. Connect location, route analysis, incident/complaint events, assistant functionality, and dashboard updates through the actual existing APIs and models.

Prioritize in this order:
1. Verify repository state, builds, API contracts, and data import.
2. Real GPS and verified police-facility coordinates; remove misleading fallback/pilot-area presentation.
3. End-to-end SOS event persistence and live police-dashboard updates.
4. Route support/isolation/lighting/woodland/blackspot data integration and explainable route comparison.
5. Complaint intake pipeline, including truthful multimodal processing.
6. ElevenLabs 401 diagnostics and actual assistant-session operation.
7. Shake SOS, movement/navigation reliability, test coverage, visual polish, and release readiness.

Do not silently skip a requirement. If a feature needs an external API key, paid provider, unavailable official dataset, backend deployment, physical-device permission, or manual verification, identify the blocker clearly and implement a safe adapter/fallback instead of pretending it works.

## 2. Existing system — inspect before changing

Known architecture:
- Android app: Kotlin + Jetpack Compose; application ID `com.aistudio.naarikavach.safe7x`.
- Backend: Python FastAPI, Supabase PostgreSQL; API base reported as `https://raksha360-api.onrender.com/api/v1`.
- Police dashboard: React/TypeScript, deployed previously at `https://raksha360-policedashboard.vercel.app/`.
- Backend health URL previously used: `https://raksha360-api.onrender.com/health`.
- Maps: MapLibre and OSRM have been used; dashboard has used React/Leaflet. Inspect the current code and keep the existing map stack if viable. Do not replace it with Google Maps or another paid provider without approval and configured credentials.
- Android uses Room/offline event queue and Vosk for offline voice detection. The voice model assets may require the repository's preparation script.
- Production health previously indicated `officialPoliceGateway: NOT_CONFIGURED` and `externalNotifications: NOT_CONFIGURED`. Verify current health/config rather than assuming this changed.

Known repo history/context is not a substitute for source inspection. Search actual routes, repository methods, models, viewmodels, WebSocket handlers, event schemas, and dashboard subscription code before writing patches.

## 3. GPS and location integrity

- Use Android fused/current location APIs with correct runtime permission handling; expose permission-denied, GPS-disabled, stale, and unavailable states distinctly.
- Never silently substitute Sadar/Nagpur or any fixed coordinates for the user's actual location. Remove hard-coded fallback coordinates from production location UI. If a demo coordinate is required for demo mode, visibly label it as demo data and isolate it from real SOS dispatch.
- Remove misleading `Pilot Area` and “Sadar, Nagpur (Estimated)” production presentation. Provide Nagpur-wide map coverage according to the actual data, while accurately stating the extracted data boundary where applicable.
- On the map, show GPS accuracy, last update time, and whether the marker is current, stale, or demo. Update the user location as valid location updates arrive.
- Do not send stale/estimated/fallback coordinates as if they were precise GPS. Store coordinate provenance and accuracy on incident records where possible.

## 4. Police stations and help facilities

- Audit the existing police-station dataset. Identify the source, actual coordinates, duplicates, and any placeholder/hardcoded locations. Do not keep all markers using one coordinate or artificial distance values.
- Import OSM police/facility features only with their OSM identifiers, source and extraction date. OSM is crowd-maintained and does not by itself certify that a location is an official station or currently open. Where official status matters, cross-check against a reliable government source or clearly mark it unverified.
- Compute distances from the validated current GPS to actual facility coordinates. Use geodesic straight-line distance if routing distance cannot be calculated, and label it as approximate straight-line distance. Never fabricate station distances.
- Differentiate designated official help points from ordinary nearby support facilities. A hospital, shop, pharmacy, petrol station, bus stop or ATM is not automatically a verified “safe spot.”
- Include emergency, healthcare, police, fire, pharmacy, fuel, shelters/community facilities, transit and relevant public support amenities where mapped and useful. Display opening hours/verification status where available; never assume 24/7 operation.

## 5. Nagpur route-context data and scoring

Use the provided blackspot data plus actual extracted source files. Build a reusable data-import/normalization path rather than hardcoding the bundle into UI components.

### Required data layers
A. Historical accident blackspots: use the supplied CSV/GeoJSON for five Nagpur City National Highway blackspots, with 2021–2023 accident and fatality counts. This is historical vehicle-accident data; it is NOT crime, harassment, sexual assault, or a complete personal-safety dataset.

B. Emergency/support facilities: hospitals/healthcare, police, fire, pharmacy, fuel, community centres/shelters, public transport stations/stops, and other mapped public facilities. Classify the facility type and source. Store `opening_hours`, `check_date`, coordinates, source IDs, and verification/freshness fields if present.

C. Green/isolated context: validated polygons for `natural=wood/scrub/wetland/heath`, `landuse=forest`, relevant nature reserves/parks/protected areas, plus residential/commercial/retail/industrial land use and place/neighbourhood features where available.

D. Road environment: actual road geometries and types, explicit `lit=yes/no`, street-lamp point features, sidewalk/crossing tags and relevant road access restrictions.

E. Coverage and freshness: extraction timestamp, object timestamps/version, source/licence, data extent, layer success/failure, geometry quality, and gaps.

F. Additional official layers: inspect the historical Smart Lights resource and the MRSAC/NIT-NMRDA land-use lead only if data files can actually be downloaded and their date, coverage, structure and licence reviewed. Keep them in a separate provenance-tagged layer; don't conflate 2019 lights with currently functioning lights.

### Route comparison must use route geometry
- Compare each candidate route's actual polyline against data layers, with a configurable segment size and buffer parameters.
- Do not rank routes by simply counting nearby POIs. Calculate route-level exposure and support coverage.
- Calculate historical blackspot exposure using point-to-route/segment distance with a configurable distance-decay window. Report the definition/units and keep these scores separate from personal/crime risk.
- Calculate support access as the proportion of route segments/length near selected, appropriately classified facilities; also show nearest emergency facility and approximate distance. Prefer network distance when available; otherwise explicitly label straight-line approximations.
- Calculate lighting coverage in distance units: explicit `lit=yes`, explicit `lit=no`, and `unknown`. Missing tags, missing records, or unmapped roads MUST remain unknown, never be converted to unlit.
- Calculate wooded/forest/scrub exposure only from validated polygon geometries intersecting actual route line segments. Never use a centroid or center point as a land-cover polygon. Parks/woodland are environmental context, not evidence of crime.
- Calculate low-support/relative-isolation segments only when more than one signal supports it, for example low mapped facility/activity density together with low/unknown lighting and validated wooded context. Explain that sparse map data could itself be a mapping gap.
- Use residential/commercial/shop/transport features only as imperfect mapped activity indicators. Do not claim real-time foot traffic or crowd density unless a genuine, permissioned, time-stamped feed is configured.
- Respect public-road access, mode suitability, route connectivity, closures and private/access-restricted roads where data exists.
- Include distance and ETA. Only include live traffic when a real provider is configured and returns current data; do not invent traffic.
- Expose weights as named, configurable constants/configuration and document them. Treat weights as heuristic, not scientifically validated, until calibrated with reliable local data and reviewed by humans.

### Route labels and UI
- Prefer evidence-grounded labels such as `More supported`, `Less isolated`, `Higher historical accident exposure`, and `Limited data`.
- If the existing UI requires `Safest / Moderate / Not Recommended`, describe them as relative data-driven estimates, never safety guarantees. Show the main reasons each candidate ranked as it did and expose low-confidence/missing-data flags.
- Avoid alarming users based only on a forest or park. An isolated-segment warning requires combined evidence and must disclose the signals used.
- Do not claim that a mapped light works, that a facility is open/staffed, that a route has no crime, or that an area has a specific crime rate unless a verified source supports the claim.
- Include source information and data freshness in a route-details panel, with accessible explanations.

## 6. Destination search and continuous journey tracking

- Inspect existing MapLibre/OSRM and dashboard routing implementation first. Preserve the current technology unless a clear blocker exists.
- Implement destination search with real geocoding, route candidates, route geometry, distance, ETA, road types, support/lighting/woodland/blackspot analysis, and an explicit Start Journey control.
- When the user starts a journey, request and explain necessary permissions, update the marker from actual GPS, and compare actual movement to the planned route.
- Implement rerouting on meaningful deviations with debounce/hysteresis to avoid excessive requests. Distinguish navigation ETA from route-risk analysis.
- Notify arrival only when an actual location/route progress rule supports it; handle GPS loss, background restrictions, battery limits, denied permissions, and app restart honestly.
- Do not simulate continuous GPS with a fixed point. Keep navigation state and SOS tracking distinct. Keep battery and privacy implications reasonable and documented.

## 7. End-to-end SOS and shared incident event pipeline

All entry methods should flow into a shared, documented event contract: manual SOS, voice SOS, shake SOS (if enabled/implemented), and any other explicit SOS control.

Required path:
1. Detect/confirm SOS based on the selected mode and current device policy.
2. Capture current GPS with accuracy/timestamp/provenance, if permission is granted; mark location unavailable rather than using a fake fallback.
3. Generate an idempotency key/event ID and persist locally immediately through the offline queue.
4. Attempt authenticated backend submission using the existing `/api/v1` contracts; inspect real endpoint names/schema before edits.
5. Persist accepted events durably in PostgreSQL before broadcasting them.
6. Push updates through the actual authenticated police WebSocket (previously `/ws/police`, verify the current path) or an existing reliable live update channel.
7. Ensure the React police dashboard updates live without manual refresh and deduplicates/reconciles events after reconnects.
8. Reconcile pending offline events safely on reconnect; never lose/recreate a duplicate alert during retries.
9. Return clear, truthful states: `queued offline`, `submitted`, `backend accepted`, `dashboard received`, and `official dispatch confirmed` are separate statuses. Do not label an event officially dispatched unless the configured and verified police gateway confirms it.

- Police operator endpoints stay protected by server-side authentication, role and authorization checks. Do not make protected endpoints public merely to make citizen device calls work.
- Audit the previously applied citizen-session authorization changes; validate token hash storage, expiration, revocation, ownership and RBAC/ABAC enforcement.
- Preserve DEMO mode isolation. When `SOS_EMAIL_MODE=DEMO`, no real email/SMS/call/official dispatch is sent. Never weaken this safety guard.
- No fake acknowledgements, fabricated officer names, fake responders, dummy live incidents in production, or fabricated dispatch times.

## 8. Voice SOS / Vosk regression — debug the whole chain

Latest reported physical-device behavior: voice profile enrollment was saved and Guard Mode turned on, but the phrase appears to be recognized/listened to without triggering SOS. Do not assume recognition means activation.

Trace the real runtime pipeline end to end:
1. microphone permission and AudioRecord lifecycle
2. wake-word/phrase recognition result and confidence
3. enrollment profile availability, sample count, descriptor dimensions and model version
4. speaker verification score/threshold and why a sample was accepted/rejected
5. Guard Mode state and lifecycle/background restrictions
6. phrase/intent match and debounce/cooldown
7. creation of the common SOS event contract
8. immediate local persistence and offline queue
9. authenticated backend sync and final UI feedback

Important existing context: a legacy 32-dimensional spectral FFT descriptor is incompatible with the current 128-dimensional Vosk speaker-model representation. Do not silently convert invented vectors, lower a verification threshold to make demos pass, or claim a valid verification when vectors are incompatible. Require fresh five-sample enrollment for incompatible legacy profiles and explain the problem to the user.

- Add privacy-safe diagnostics using categories and numeric metadata only; never log raw audio, auth tokens, API keys or sensitive speech contents.
- Keep `stopRecordingEarly()`/stop flags and saved enrollment behavior race-free. Confirm the UI reports success only if the data commit actually succeeded.
- Add unit/regression tests for no profile, legacy incompatible profile, too few samples, low confidence, false trigger, valid trigger, guard disabled, cooldown, permission loss, and event-persistence failure.
- On device test actual end-to-end triggering. If hands-on physical testing cannot be completed, report the exact unverified device steps rather than marking the chain fully tested.

## 9. Manual SOS, shake SOS and guard-mode UI

- Preserve the currently preferred floating circular microphone button above the bottom navigation; do not restore the large rectangular “ElevenLabs” card/badge on Home.
- Keep the manual SOS switch/button clearly accessible, with accidental-trigger protection that does not delay genuine emergencies excessively.
- Inspect whether shake SOS is actually implemented; earlier audits found it missing. If missing, implement Android sensor-based detection behind user opt-in, configurable sensitivity, foreground/guard-mode policy, debounce, false-positive protections and a clear test UI. If hardware/sensor is absent, show it unavailable.
- Verify notification, vibration and audio behavior respects platform permissions and Android 14/background restrictions. No claim that shake or voice SOS works until tested.

## 10. Complaints and multimodal intake

Audit complaint creation, attachment upload, persistence, status tracking, officer review and dashboard visibility.

- Support user-provided text and attachments (images, audio, documents) only through processing methods that truly exist and are configured.
- Images: use a configured vision model or real image-processing pipeline. Audio: use actual speech-to-text/transcription. PDFs/documents: use actual extraction/parsing. Do not claim all modalities were analysed just because an attachment uploaded.
- Store attachment metadata and files securely, validate MIME/size, use controlled access/signed URLs where supported, and protect against malicious uploads. Don't save private files in a publicly served folder.
- AI can suggest summaries/categories/priority and evidence-extraction prompts, but do not state that unverified allegations are facts. Human review must remain explicit for triage and operational decisions.
- Preserve original complaint text/media and provenance. Record AI-generated summary separately from citizen-provided content.
- Make complaint statuses, timestamps, attachments and officer actions visible to the correct role; enforce server-side authorization.
- When an AI/provider key is unavailable, explain the unavailable analysis stage and allow normal complaint submission where possible; do not invent a result.

## 11. ElevenLabs assistant — fix actual HTTP 401

Known prior finding: the Android assistant UI was corrected to show an actual `ElevenLabs API returned 401` rather than a misleading “missing configuration” message. The UI now exposes a real backend/upstream auth problem; that does not mean conversations work.

Known intended environment variables on Render:
- `ELEVENLABS_API_KEY`
- `ELEVENLABS_AGENT_ID`
- reported agent ID: `agent_3701m51qchkaenxsjnzr8jvrm66`

Inspect the actual backend route and SDK/API used to start a conversation, and compare required authentication for the chosen ElevenLabs product (agent conversation/WebSocket/signed URL) against the implementation. Verify key/agent pairing, agent access, key permissions, API URL, environment variable trimming, and endpoint-specific auth requirements. Do not reveal or print secrets.

- Keep ElevenLabs API credentials on the server; never place secret API keys in Android or web client bundles.
- Return sanitized errors with a correlation/request ID and safe status category; redact all credentials and signed tokens.
- For private agents or WebSocket sessions, implement the provider's supported server-side signed/conversation URL/session flow as appropriate to the actual SDK; don't guess a URL or call arbitrary endpoints.
- Provide a backend diagnostic that only reveals safe booleans/status (`apiKeyPresent`, `agentIdPresent`, provider status category), never secret values.
- Add tests for missing key, malformed config, 401/403/404 provider responses, timeout, valid response and sanitized error propagation.
- Test an actual authorized conversation with a properly configured environment before claiming success. If an upstream key/agent permission must be changed manually in ElevenLabs, state exactly what to check.

## 12. Authentication and sessions

Preserve the prior changes around `AuthenticationException.kt`, `AuthenticationRepository.kt`, `BackendApiClient.kt`, `SignInScreen.kt`, and related tests. Audit the current code instead of blindly reverting it.

- Previous report described 30-second connect/read/write timeout settings and stage-based auth diagnostics; verify actual code and keep timeouts reasonable.
- Google login must validate the proper audience/client IDs on the backend, handle cancellation separately from backend timeout, show actionable sanitized errors and persist sessions securely.
- Keep Web Client ID local/configured only where intended; never commit secrets from `local.properties` or any `.env`.
- Server must resolve roles from the database/session, not user-controlled claims alone. Apply token expiration, revocation, ownership, RBAC/ABAC, CORS and file upload security.
- Existing production tables reported as `public.users` and `public.user_sessions`, storing SHA-256 hashes of session tokens. Verify schema and behavior; never store raw session bearer tokens server-side if only a hash is needed.

## 13. Backend, PostgreSQL, dashboard and WebSocket reliability

- Verify `/health` against real database/provider configuration, but do not expose secrets, connection strings or internal stack traces.
- Verify all event and complaint endpoints against their actual Pydantic schemas, migrations, DB models and clients. Add version-safe migrations if genuinely needed, but do not apply them to production without explicit approval.
- Store events transactionally; broadcast only after successful durable commit. Reconnect should resync missed events from persistent storage.
- Authenticate WebSocket connections and authorize subscriptions by role/area/scope. Validate message schemas and limit sizes/rates.
- Dashboard needs real loading/error/empty states, reconnect/resync, dedupe by stable event ID, filters, incident details, complaint status, location freshness and map rendering. It must not present dummy sample incidents as production data.
- Distinguish backend event acknowledgement from official police gateway acknowledgement. Existing official dispatch gateway and external notification provider were previously marked `NOT_CONFIGURED`; verify before presenting any operational claim.

## 14. Data architecture and performance

- Normalize imported data into a versioned schema or cached GeoJSON layers with source metadata. Avoid one-off constants in React components.
- Keep large raw Overpass JSON out of Android assets unless there is a justified offline use case; likely store/cache on backend or as generated data assets appropriate to the existing architecture.
- Import once per data refresh, deduplicate by OSM type + ID, save source timestamps, log feature counts, support partial layer failures, and never query Overpass on every app launch/navigation update.
- Design a refresh policy that respects public Overpass services (cache results, user-agent identification, moderate rate). Add admin/manual refresh capability or scheduled job only if it fits the current architecture.
- Keep raw source files for reproducibility where appropriate and normalized feature files for runtime use. Document attribution and licensing.
- Add configurable limits/timeouts and a fallback to last known extracted data when refresh fails, visibly marking its age.

## 15. UI and user experience

- Preserve Raksha360's existing style where functional. Avoid unnecessary full redesigns during reliability fixes.
- The home floating round microphone button must remain; no large ElevenLabs rectangle/badge.
- Make GPS state, route score reasons, uncertainty, data freshness, SOS delivery states, and complaint state visually clear and accessible.
- Avoid UI language promising complete safety. Provide concise explanations of why route A differs from route B and which data layers were unavailable.
- Support the existing languages if translation infrastructure is available; don't hardcode a different language into otherwise localized flows.

## 16. Test and verification gates

Run tests based on actual repository layout. Known historical reports included 59 backend API tests passing, Android `testDebugUnitTest` passing, and `assembleDebug` succeeding; these are historical only. Rerun and report current results, don't reuse past counts as proof.

Required checks where relevant:
- backend lint/type checks, unit/API/integration tests, and DB tests or mocks
- Android unit tests, Gradle test task, debug build, APK installation/device tests when device is available
- dashboard type check, lint and production build
- route scoring unit tests for point-to-polyline distance, polygon intersection, segment bucketing, facility buffer, missing lighting tag semantics, explicit `lit=no`, stale records, duplicate OSM ID, empty/failed layer, partial layer failure, route alternatives with different signals, and score explanation stability
- end-to-end tests for manual/voice/shake SOS event to DB to dashboard update, offline queue replay, duplicate submission, WebSocket reconnect, and permissions/location missing
- ElevenLabs configuration/401/timeout regression tests; complaint text/image/audio/document paths and unavailable-provider behavior

Do not mark a test as passed unless it ran. Include exact commands, exit statuses, result summaries, generated artifact paths and any skipped tests.

## 17. Safety, privacy and release boundaries

- Maintain `SOS_EMAIL_MODE=DEMO` unless the user explicitly authorizes production messaging. Demo mode must never send real external alerts.
- Do not send real emergency calls/emails/SMS, notify police, mutate production data, apply production migrations, deploy Render/Vercel, or push commits as part of this task. The user must explicitly approve those actions later.
- Do not log or commit API keys, Google credentials, signed URLs, raw session tokens, raw audio, or sensitive complaint attachments.
- Do not fabricate official police integrations, real-time crowds, live crimes, working streetlights, active staffed facilities, or dispatch acknowledgements.
- SOS is emergency-sensitive functionality. Never silently relax recognition thresholds or disable meaningful confirmation protections just to make a demo pass.

## 18. Final report required

At the end, produce a fact-based report with:
1. Repository branch/HEAD and initial/final working-tree status.
2. Exact files changed and the purpose of each important change.
3. Data ingestion results: extraction time, Overpass endpoint, bounding box, row/feature counts per layer, source timestamp, geometry-quality counts, licence/attribution, files written, failed layers and gaps. State clearly if extraction did not run or failed.
4. Route scoring features actually implemented, exact scoring factors/weights, unknown-data handling, and one or more test examples. Do not call the results validated without appropriate validation.
5. SOS event path verification, offline tests, database persistence and dashboard live update evidence.
6. Voice enrollment/trigger tests, authentication results, ElevenLabs status and any required manual provider fix.
7. Complaint multimodal stages actually implemented/tested versus merely uploaded or unavailable.
8. Exact test/build commands and results.
9. Device tests performed and not performed.
10. Remaining blockers, manual environment settings, data sources not obtained, and next steps.
11. Explicit statement whether any deployment, production DB mutation, real alert or Git push occurred. Under this prompt, none should occur.

Start by inspecting the repo and reading the data bundle. Then make a short implementation plan based on the current code, execute in small reversible steps, test, and report truthfully. Do not stop after producing a plan unless a required action is blocked. Do not claim completion based only on static inspection or a successful build.
