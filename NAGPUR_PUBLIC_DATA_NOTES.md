# Raksha360 — Nagpur Public Data & Safety Verification Notes

**Document Date:** October 2026  
**Project:** Raksha360 (Continuation of Naari Kavach)  
**Classification Guide & System Boundary Documentation**

---

## 1. Official Public Directory Provenance & Freshness

The station directory snapshot and helpline contact information integrated into Raksha360 are copied from official public Government / Police portals:

1. **Nagpur City Police Public Station Directory:**
   - **URL:** [https://nagpurpolice.gov.in/police-stations](https://nagpurpolice.gov.in/police-stations)
   - **Source Authority:** Nagpur City Police / National Informatics Centre (NIC) Maharashtra
   - **Snapshot Retrieved On:** 11 October 2026
   - **Source Page Last Updated:** 10 August 2026
   - **Record Count:** 37 police station listings
   - **Captured Public Fields:** `station_id`, `name`, `name_mr` (Marathi script where listed), `phone`, `email`, `address`, `jurisdiction_label`
   - **Boundary:** Read-only snapshot of public directory fields. Coordinates were not published in the directory text field used for this export. Phone numbers and email addresses can change over time; always confirm directly before operational reliance.

2. **Nagpur City Police Contact & Emergency Portals:**
   - **URL:** [https://nagpurpolice.gov.in/contact](https://nagpurpolice.gov.in/contact)
   - **Police Office Phone:** `0712-2560601` (Police Bhavan, behind Collector Office, West High Court Road, Civil Lines, Nagpur, Maharashtra)
   - **Control Room Phone:** `+91-712-2563232` (Official police control-room line)

3. **Official Public Helplines (Pan-India & State):**
   - **112:** Emergency Response Support System (ERSS) — All Emergencies
   - **1091:** Women Helpline
   - **1098:** Childline / Child Protection Helpline
   - **1930:** National Cyber Crime Reporting Portal Helpline
   - **108:** Emergency Medical Ambulance Service

> [!IMPORTANT]
> **Operational Boundary:** Raksha360 is an independent safety software application. It is **not** an official Nagpur Police command system and has **no direct integration** with police radio, dispatch, or 112 emergency routing. A listing in the public directory is not proof of an operational unit, duty roster, or live responder availability. Clicking emergency numbers opens the native device phone dialer (`tel:`); Raksha360 never places silent or automated calls.

---

## 2. Mandatory Data Classifications & Boundaries

Every piece of data across the FastAPI backend, SQLite database, Android app, and React police dashboard is strictly partitioned into four non-overlapping data classifications:

| Data Classification | Description | Display Rule | Storage & Handling |
| :--- | :--- | :--- | :--- |
| **`OFFICIAL_PUBLIC_DIRECTORY`** | Public station names, Marathi script, official phone numbers, email addresses, and address text from public government websites. | Displayed with badge `PUBLIC DIRECTORY · NOT A DISPATCH CONNECTION`. | Stored in dedicated `police_station_directory` table. Never mixed into synthetic response metrics. |
| **`OFFICIAL_AGGREGATE_STATISTIC`** | Annual city/year aggregate counts exactly transcribed from official National Crime Records Bureau (NCRB) publications. | Labeled `NCRB aggregate statistic — source year YYYY`. | Kept strictly unimported until verified row-by-row in official PDF tables. Never plotted as geographic incident pins. |
| **`SYNTHETIC_TEST_FIXTURE`** | Pre-generated training and test scenarios (SOS triggers, casework reports, internal dispatch tasks, device states). | Prominent gold/amber badge `DEMO FIXTURE` / `DEMO ONLY` on every card, row, and map popup. | Stored with fixed `DEMO-*` IDs and `is_demo = 1`, `source = SYNTHETIC_FIXTURE`. Never used in official statistics. |
| **`LIVE_USER_EVENT`** | Real emergency alerts or reports received from authenticated user devices connected to the server. | Distinguished by `is_demo = 0`, live timestamp, and severity color code. | Requires valid Bearer device token. Never merged with test fixtures without explicit filter separation. |

---

## 3. Station Geocoding & Geographic Coordinates Policy

1. **Intentional Null Coordinates:**
   The 37 station listings in `reference-data/nagpur-public/nagpur_police_stations.json` have `latitude: null` and `longitude: null`. Coordinates are intentionally blank because the official directory page only lists city/district names rather than precise geospatial facility coordinates.
2. **Strict Prohibition against Guessed Coordinates:**
   The application **never** defaults missing coordinates to `0.0, 0.0`, central Nagpur, or automated unreviewed geocoding hits. Missing coordinates remain `null` and are omitted from map marker layers while remaining fully visible and searchable in the station directory table.
3. **Candidate Review Workflow:**
   The included script `reference-data/nagpur-public/geocode_nagpur_stations_review.py` queries OpenStreetMap Nominatim at a rate-limited 1 request per 1.1s and writes candidate matches to a review CSV (`nagpur_police_stations_geocoded_review.csv`).
4. **Human Verification Standard:**
   No candidate coordinate may be imported into the database without human inspection against OpenStreetMap imagery. Human-approved coordinates must have `coordinates_verified = true`, `coordinate_source`, `coordinate_checked_at`, `osm_type`, and `osm_id`. The database upsert logic explicitly preserves manually approved coordinates upon reseeding (`COALESCE` / `MAX`).

---

## 4. No-Key OpenFreeMap Street Map Integration

1. **Vector Tiles Provider:**
   The map interface uses MapLibre GL JS with OpenFreeMap hosted vector street tiles. No Google Maps Platform API key, Google billing, or proprietary map SDK is required.
   - **Bright Style (Default):** `https://tiles.openfreemap.org/styles/bright` — High-contrast, colorful contemporary vector street map with distinct road colors, place names, and land-use variation.
   - **Liberty Style:** `https://tiles.openfreemap.org/styles/liberty` — Detailed topological and road cartography.
   - **Dark Style:** `https://tiles.openfreemap.org/styles/dark` — Low-glare tactical night view.
2. **Default Geospatial Configuration:**
   - **Center:** Nagpur, Maharashtra (`[79.0882, 21.1458]`)
   - **Default Pitch:** `0` (nadir view)
   - **Controls:** Small style-switch group (Bright / Liberty / Dark), `Center Nagpur` button, 3D vector pitch toggle, navigation zoom controls, and metric scale.
3. **Mandatory Attribution:**
   `OpenFreeMap © OpenMapTiles · Data from OpenStreetMap` is permanently visible via MapLibre AttributionControl at bottom-right. Map popups and legend overlays do not obscure attribution.
4. **Marker Placement Validation:**
   Every coordinate is validated (`-90 <= lat <= 90`, `-180 <= lon <= 180`, `Number.isFinite`). Stations without verified coordinates and reports without coordinates are not pinned.

---

## 5. National Crime Records Bureau (NCRB) Statistics

1. **Source Discovery:**
   - **Catalog:** Government of India Open Government Data Platform: [https://www.data.gov.in/catalog/crime-india-2023](https://www.data.gov.in/catalog/crime-india-2023)
   - **Official Report:** National Crime Records Bureau, Ministry of Home Affairs: *Crime in India 2023*, Part 1 (Volume I): [https://www.ncrb.gov.in/uploads/files/1CrimeinIndia2023PartI1.pdf](https://www.ncrb.gov.in/uploads/files/1CrimeinIndia2023PartI1.pdf)
2. **Transparent Unimported Status:**
   - Status is officially reported as `SOURCE_IDENTIFIED_VALUES_NOT_IMPORTED`.
   - The repository intentionally contains no made-up crime counts for Nagpur.
   - Metrics remain blank in `reference-data/nagpur-public/ncrb_city_year_statistics_import_template.csv` until an operator manually locates the exact metropolitan city row in the official PDF table (e.g., Table 1B.1 to 1B.5 or Crime Against Women), transcribes the exact figure, and records the reviewer initials and extraction date.
3. **Operational Separation:**
   Annual city aggregate crime statistics are publication-year totals for macroscopic analysis. They are never rendered as incident pins or real-time risk predictions on the live control-room map.

---

## 6. Device Heartbeats & Power-Off / Last-Known Location Policy

1. **Physics of Powered-off Devices:**
   A phone that is powered off or has a depleted battery **cannot** acquire a fresh GPS fix or transmit data over mobile networks.
2. **Last-Known Location Semantics:**
   When a device running Guard Mode or an active safety session stops checking in, the server creates a `DEVICE_OFFLINE` incident using the **last valid coordinates previously received by the server**.
3. **Mandatory Display Language:**
   - Map popup and incident queue explicitly display: `LAST KNOWN LOCATION · NOT LIVE`.
   - The timestamp reflects when the location was recorded by the server, not the time of power-off.
4. **Diagnostic Integrity:**
   A missing heartbeat does **not** prove the phone was switched off or that an emergency exists. Plausible alternative causes include cellular dead zones, Wi-Fi loss, OS background execution termination, battery saver restrictions, or backend/network downtime.
5. **Opt-in Last-Known-Location Email:**
   Email alerts require:
   - Explicit user toggle enabled on the device: *Email last-known location if the phone stops checking in*.
   - At least one active trusted contact with a valid email.
   - Laptop administrator configuration of SMTP credentials in private root `.env`.
   - Mocked SMTP in tests; email addresses in fixtures strictly use the reserved RFC 2606 `.invalid` domain (`contact-a@example.invalid`).
