# Raksha360 — Nagpur public data and feature test fixtures

Prepared: 2026-10-11

## Contents

- `nagpur_police_stations.json` and `.csv`: 37 station records copied from the public Nagpur City Police station directory. Contacts and addresses are publicly listed source fields; lat/lon are intentionally blank until independently geocoded and reviewed.
- `official_helplines_and_links.json`: emergency/help contact numbers and official portals publicly shown by Nagpur City Police.
- `public_sources_manifest.json`: provenance, sources, scope and non-use boundaries.
- `ncrb_2023_city_statistics_source_manifest.json`: verified source catalogue and table map for official annual aggregate crime statistics. It contains no fabricated case counts.
- `ncrb_city_year_statistics_import_template.csv`: blank schema for exact values only after checking the official row in the source PDF.
- `demo_incidents.json`, `demo_reports.json`, `demo_dispatches.json`: synthetic UI/API workflow fixtures for Raksha360 features.
- `demo_device_contacts_child_safety.json`: synthetic device, contact, email-delivery and child safety scenarios. E-mail addresses use the reserved `.invalid` domain.
- `feature_parameters_and_safety_invariants.json`: thresholds and safety invariants reported by the attached audit.
- `feature_test_coverage_matrix.csv`: coverage checklist for all prominent feature families from the audit/context.
- `map_provider_config.json`: no-key OpenFreeMap + MapLibre Bright street map configuration.

## Real vs. synthetic is non-negotiable

**Real public data** in this pack consists of the station directory and public helpline/office contacts cited to official Nagpur City Police pages. The public source page last-update stamp was 10 Aug 2026 and the snapshot was checked on 11 Oct 2026. These are snapshots; re-check before operational use.

**Not included:** real FIRs, real SOS alerts, real victim identities, actual complaint narratives, child identities, private police records, the authenticated Nagpur Police dashboard's data, real-time dispatch feeds or individual crime records. Those are not in the public source used here and must not be invented or scraped behind login.

The test incidents, reports, dispatches, device statuses and child-safety records are synthetic. Every such row carries `is_demo: true`, `DEMO` IDs and a prominent synthetic note. Coordinates are training-only points around the map viewport, **not geocoded incident addresses**.

## Loading public stations into the app

Tell Antigravity to add an additive `police_station_directory` table and a token-protected read-only endpoint such as `GET /api/v1/police-station-directory`. Keep it separate from response units and incident dispatch lifecycle. Use `station_id`, `name`, `name_mr`, `phone`, `email`, `address`, `source_url`, `retrieved_on`, `latitude`, `longitude`, `coordinates_verified`, `data_class` and jurisdiction caveat. Do not convert missing coordinates to 0,0 or fake positions.

Optional map geocoding: use the included `geocode_nagpur_stations_review.py`, which queries OpenStreetMap Nominatim slowly and writes **candidate coordinates for manual review only**. Review every result before importing it into the app; do not let the script silently update production pins.

## Map

Use MapLibre GL JS with `https://tiles.openfreemap.org/styles/bright` as default. OpenFreeMap currently documents a public no-key hosted tile service and lists Bright/Liberty/Dark styles. Display `OpenFreeMap © OpenMapTiles · Data from OpenStreetMap` attribution. This is a colorful vector street map, not satellite/photorealistic imagery or Google's proprietary 3D.

## Official source links

- Station directory: https://nagpurpolice.gov.in/police-stations
- Nagpur City Police contact page: https://nagpurpolice.gov.in/contact
- Government of India NCRB Crime in India 2023 catalog: https://www.data.gov.in/catalog/crime-india-2023
- NCRB Vol I PDF: https://www.ncrb.gov.in/uploads/files/1CrimeinIndia2023PartI1.pdf
- OpenFreeMap quick start: https://openfreemap.org/quick_start/
- OpenFreeMap service info / attribution: https://openfreemap.org/

## Test parameter caveat

`feature_parameters_and_safety_invariants.json` preserves threshold values described in the attached audit (for example the default 180-second heartbeat threshold, 30-second monitor interval, 900-second retry interval, 0.82 speaker similarity threshold, 6-digit pickup PIN and 5 failed attempts). Antigravity must compare them against source/config before changing runtime defaults. These are configuration references—not a claim that every physical-device behavior has been fully verified.
