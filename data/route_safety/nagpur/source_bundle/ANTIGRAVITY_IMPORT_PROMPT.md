# Antigravity task: Expand Raksha360 route data with real facilities + isolation context

Read this bundle first. Preserve the existing five official Maharashtra Highway Traffic Police blackspots in `nagpur_highway_blackspots_2021_2023.csv` and the matching GeoJSON. Then run the new extractor rather than fabricating coordinates.

## Execute extraction
1. Ensure Python 3.10+ is available. Install dependencies: `python -m pip install requests "shapely>=2.0"`.
2. Run `python extract_nagpur_route_layers.py --out output/nagpur_route_layers` from this bundle's folder. It queries public OpenStreetMap Overpass endpoints for a configurable Nagpur urban/peri-urban bounding box.
3. If Overpass times out, retry later or run layers separately. Do not write fake/empty data as if successfully fetched. Save `*_raw_osm.json`, `*.geojson`, and `extraction_report.json` inside the project data directory. Inspect counts and sample features and note the extraction timestamp.
4. Respect OSM attribution/ODbL. Keep `osm_type`, `osm_id`, `osm_version`, `osm_timestamp`, `source`, and `geometry_quality` metadata.

## Route context to evaluate
- **Support/access facilities:** hospitals/healthcare, police, fire stations, pharmacies, fuel stops, community centres/shelters, transit stops/stations, convenience stores/supermarkets, banks/ATMs and public facilities where mapped.
- **Green/isolated context:** `natural=wood/scrub/wetland/heath`, `landuse=forest`, parks/nature reserves, protected areas; residential/commercial/retail land use and neighbourhood/place features as imperfect built-up/activity context.
- **Road environment:** actual road geometry/type, explicit `lit=yes/no`, mapped street lamps, sidewalks and crossings where tagged.
- **Historical road risk:** the supplied five highway blackspots (2021–2023), with accident and fatality counts.

## Make route-level comparisons—not a flat count of places
For each candidate route, match features to actual route geometry and divide it into small segments. Compute:
1. Historical accident-risk exposure: distance/length near blackspots with distance decay; do not imply this is crime data.
2. Emergency/support access: route length with a vetted support site within a configurable buffer and nearest emergency-facility distance. Prefer network distance; label straight-line estimates if that is all the implementation can calculate.
3. Explicit lighting coverage: proportion of route length tagged `lit=yes` vs `lit=no`; missing/unmapped records are `unknown`, never `unlit`.
4. Isolation context: route length intersecting validated wooded/forest/scrub polygons, combined with LOW mapped facility/activity density and sparse mapped residential/commercial context. Do not penalize a route just for passing a park/forest unless combined with low support/lighting or credible evidence. Do not score center-only or line-only vegetation features as polygons.
5. Road usability: connected public roads suitable for the requested mode; avoid private/access-restricted land. Include ETA/distance, and current traffic only if a real traffic feed is connected.
6. Freshness/coverage confidence: reflect missing tags, extraction age and data-source completeness.

## Honest labels
Use relative labels such as **More supported**, **Less isolated**, **Higher historical accident exposure**, or **Limited data**. If the UI retains “Safest / Moderate / Not Recommended”, make clear this is a data-driven estimate, not a safety guarantee. Only use “safe spot” for a vetted designated help point; label ordinary clinics/shops/transit as “nearby support facility” or “activity indicator”.

## Critical caveats
- An OSM shop, park, station, clinic or police point may be stale, closed, unmanned or inaccurate. Check `opening_hours` and timestamps; do not assume 24/7.
- Missing OSM `lit`, sidewalk, building or facility tags are UNKNOWN—not proof the feature is absent.
- Woodland/forest is not evidence of crime. It is an isolation/visibility context layer only when combined with other signals.
- Do not add fake live crowd density, crime heatmaps, police response times or mobile-network coverage. If no trusted time-stamped source exists, display unavailable.
- Government OGD’s “Smart Lights: Nagpur” catalogue is from 2019 and the child resource says “as on Jan 2019”. It may be a historical asset baseline, not proof lights work now. Fetch/inspect separately before using; record source date and require verification.
- No official police dispatch is implied. Existing Raksha360 production status and demo isolation remain unchanged.

## Integration and verification
Inspect the existing MapLibre/OSRM implementation before replacing anything. Create an import job, normalized models, route-segment scoring service, and tests for point-to-route distance, forest polygon intersections, unknown vs explicit `lit=no`, stale facilities, duplicate OSM ids, no false safety guarantees and graceful layer failures. Cache data and refresh politely; do not hit Overpass on every app launch. Show sources and confidence.

Do not deploy, mutate production DB or send real alerts. Run extraction/build/tests and report what actually succeeded, row counts per layer, date/endpoint, omissions, tests and manual-download needs. Do not report live data if the query failed.
