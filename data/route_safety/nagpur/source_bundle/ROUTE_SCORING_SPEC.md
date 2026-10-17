# Raksha360 — Route support & isolation scoring specification

## Layers in this bundle
1. `nagpur_highway_blackspots_2021_2023.csv/.geojson`: official Maharashtra Highway Traffic Police data, historical 2021–2023. Five listed Nagpur City NH blackspots; not crime data and not a complete citywide safety history.
2. `extract_nagpur_route_layers.py`: live extractor for OSM facilities, green/land-use context, road geometry, street-lamp points, lighting/sidewalk/crossing tags. It creates actual records only when run successfully; this bundle does not pretend an OSM extract was downloaded here.
3. `nagpur_route_safety_context_query.ql`: equivalent Overpass Turbo query recipe.
4. Candidate external layer: Government of India OGD “Smart Lights: Nagpur”. Catalogue lists publication/update in 2019 and child resource “Smart Lights: Nagpur: As on Jan 2019”. Treat as historical until downloaded and field-validated. Sources: https://ap.data.gov.in/catalog/smart-lights-nagpur and https://kerala.data.gov.in/resource/smart-lights-nagpur-jan-2019
5. MRSAC documents a Nagpur Improvement Trust/NMRDA GIS land-use project. The page is project context, not a verified downloadable city layer: https://mrsac.gov.in/MRSAC/flipbook/project/implementation-of-geographic-information-system-for-nagpur-improvement-trust-nit-ndash-nmrda

## Signal interpretation
| Layer/signal | Positive use | Negative/unknown handling |
|---|---|---|
| Verified hospital, police or fire facility | Emergency support/access, especially if open and verified | Do not assume open, staffed or ready to assist without validation |
| Pharmacy, fuel, store, transport stop | Activity/access proxy; consider hours and time | Not a designated safe refuge; unknown opening hours remain unknown |
| `lit=yes` on a mapped road | Explicit mapped lighting claim | Does not confirm the lamp works today |
| `lit=no` on a mapped road | Explicit no-lighting claim; increase night-time isolation penalty | Missing `lit` is unknown; never convert missing to no |
| Mapped street-lamp point | Evidence that a lamp asset is mapped | Not evidence it functions; no mapped point is not proof no lamp exists |
| Wood/forest/scrub polygon | Measure route distance/length overlapping actual area as visibility/isolation context | Do not call it dangerous just because it is wooded; centroid geometry is not area geometry |
| Sparse mapped services/residential/commercial features | Low mapped-support signal, only with a coverage-confidence flag | OSM gaps can mimic isolation; combine signals and acknowledge coverage limits |
| Accident blackspot | Historical vehicle-accident exposure near point | Not evidence of crime or personal assault risk |

## Route summaries
- `Support coverage`: % of route within a configurable distance of vetted emergency/support points.
- `Lighting coverage`: km with explicit `lit=yes`, km with explicit `lit=no`, km unknown.
- `Wooded exposure`: km actually intersecting validated forest/wood/scrub polygons; do not use centroids.
- `Low-support segments`: km with low mapped support/activity combined with unknown/negative lighting or wooded context.
- `Historical accident exposure`: route length weighted by proximity to the supplied blackspots.
- `Data confidence`: freshness, tag completeness and coverage.

Use configurable segment-level weights and test them. Do not score routes solely by counting facilities. Weights are not statistically validated until compared with reliable local incident data and human review.

## Boundaries, licensing and freshness
The extractor bbox (20.95, 78.70, 21.35, 79.40) is a pragmatic city-plus-periphery window, not the official NMC boundary or Nagpur district boundary. Clip to authoritative boundaries if available. Attribute OpenStreetMap contributors and comply with ODbL. Keep OSM ids, versions and timestamps. Do not claim live crowds, functioning lights, crime risk, police response times or official dispatch without a verified source.
