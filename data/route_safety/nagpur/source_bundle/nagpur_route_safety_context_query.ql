/* Raksha360 — OpenStreetMap Overpass Turbo query for Nagpur city + peri-urban context.
   BBOX: south 20.95, west 78.70, north 21.35, east 79.40.
   This is an extraction window, not a legal city-boundary polygon. Run at https://overpass-turbo.eu
   Then Export -> GeoJSON. If this is too heavy, use the Python extractor to fetch layers separately.
*/
[out:json][timeout:180];
(
  nwr["amenity"~"^(hospital|clinic|doctors|pharmacy|police|fire_station|fuel|bank|atm|community_centre|social_facility|shelter|bus_station|marketplace|cafe|restaurant|fast_food|place_of_worship|toilets|library|post_office)$"](20.95,78.70,21.35,79.40);
  nwr["healthcare"~"^(hospital|clinic|doctor|pharmacy|emergency)$"](20.95,78.70,21.35,79.40);
  nwr["emergency"~"^(ambulance_station|fire_hydrant|phone)$"](20.95,78.70,21.35,79.40);
  nwr["shop"~"^(convenience|supermarket|mall|chemist)$"](20.95,78.70,21.35,79.40);
  nwr["public_transport"~"^(station|platform)$"](20.95,78.70,21.35,79.40);
  nwr["railway"~"^(station|halt)$"](20.95,78.70,21.35,79.40);
  nwr["natural"~"^(wood|scrub|wetland|heath)$"](20.95,78.70,21.35,79.40);
  nwr["landuse"~"^(forest|recreation_ground|grass|orchard|residential|commercial|retail|industrial)$"](20.95,78.70,21.35,79.40);
  nwr["leisure"~"^(park|nature_reserve|garden)$"](20.95,78.70,21.35,79.40);
  nwr["boundary"="protected_area"](20.95,78.70,21.35,79.40);
  nwr["highway"="street_lamp"](20.95,78.70,21.35,79.40);
  way["highway"]["lit"](20.95,78.70,21.35,79.40);
  way["sidewalk"](20.95,78.70,21.35,79.40);
);
out center tags;
