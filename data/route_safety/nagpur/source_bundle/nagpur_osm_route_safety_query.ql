/*
Raksha360 - Nagpur route network and map-feature extraction query
Data source: OpenStreetMap via Overpass API / Overpass Turbo.
Run in https://overpass-turbo.eu/ and export as GeoJSON.
This uses the geocoded Nagpur boundary. Review the returned polygon against Raksha360's intended Nagpur City Police jurisdiction before production use.
Do not treat missing tags as negative evidence.
*/
[out:json][timeout:180];
{{geocodeArea:Nagpur, Maharashtra, India}}->.nagpur;
(
  /* The road graph / route geometry. */
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|road|pedestrian|footway|path|steps|cycleway|track)$"](area.nagpur);
  /* Lighting: keep positive, negative and unknown distinct. */
  way["highway"]["lit"](area.nagpur);
  node["highway"="street_lamp"](area.nagpur);
  /* Nearby response/health/support facilities (not proof a route is safer). */
  nwr["amenity"~"^(police|police_station|hospital|clinic|doctors|fire_station|shelter|community_centre)$"](area.nagpur);
  nwr["emergency"~"^(ambulance_station|fire_hydrant|phone|assembly_point)$"](area.nagpur);
  /* Signals, marked crossings and public transport nodes can be useful context. */
  node["highway"~"^(traffic_signals|crossing)$"](area.nagpur);
  nwr["public_transport"~"^(station|platform|stop_position)$"](area.nagpur);
  nwr["railway"~"^(station|halt|tram_stop)$"](area.nagpur);
);
out body geom;
