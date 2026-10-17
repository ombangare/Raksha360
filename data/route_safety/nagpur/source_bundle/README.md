# Raksha360 Nagpur route context data bundle

## Included
- `nagpur_highway_blackspots_2021_2023.csv` and `.geojson`: previously extracted five Nagpur highway blackspots.
- `extract_nagpur_route_layers.py`: live Overpass extractor for mapped facilities, vegetation/land-use, roads, lighting and crossings. It was not run here because raw endpoint access was unavailable in this environment; run it where internet access is available.
- `nagpur_route_safety_context_query.ql`: editable Overpass Turbo query.
- `ROUTE_SCORING_SPEC.md`: signal semantics and limitations.
- `ANTIGRAVITY_IMPORT_PROMPT.md`: implementation instructions for Antigravity.
- `DATA_SOURCE_REGISTER.json`: what was found and the actual status of each source.

## Run live extraction
```bash
python -m pip install requests "shapely>=2.0"
python extract_nagpur_route_layers.py --out output/nagpur_route_layers
```

The bbox is a planning window, not an official boundary: S 20.95, W 78.70, N 21.35, E 79.40. It covers Nagpur city and a peri-urban buffer, not the entire Nagpur district.

Compare candidate routes using actual route geometry, historical accident points, vetted support access, explicitly tagged lighting, validated wooded/land-use polygons and mapped activity density. Facilities do not prove a place is open/safe; woods do not prove a location is dangerous. Missing data reduces confidence instead of automatically increasing risk.

## Sources
- Maharashtra Highway Traffic Police blackspot report: source recorded in the original blackspot data.
- OSM: https://www.openstreetmap.org/ and https://overpass-turbo.eu/ (ODbL 1.0; attribution required).
- OGD Smart Lights: Nagpur (2019 catalogue): https://ap.data.gov.in/catalog/smart-lights-nagpur
- Resource “Smart Lights: Nagpur: As on Jan 2019”: https://kerala.data.gov.in/resource/smart-lights-nagpur-jan-2019
- MRSAC Nagpur land-use GIS project description: https://mrsac.gov.in/MRSAC/flipbook/project/implementation-of-geographic-information-system-for-nagpur-improvement-trust-nit-ndash-nmrda

The Smart Lights catalogue is old and not verified as a current working light inventory. OSM features are crowd-maintained and can be incomplete/stale; the extractor retains metadata for review.

## Full integration prompt and feeding instructions
- `MASTER_ANTIGRAVITY_PROMPT.md`: single complete prompt for the full Raksha360 repair/integration, including data ingestion, GPS, route scoring, SOS, dashboard, complaints, auth and ElevenLabs checks.
- `FEED_DATA_TO_ANTIGRAVITY.md`: exact Windows instructions for placing this bundle inside the repo and feeding the prompt into Antigravity.

Use the master prompt for the full job. `ANTIGRAVITY_IMPORT_PROMPT.md` remains a shorter prompt only for route-data ingestion.
