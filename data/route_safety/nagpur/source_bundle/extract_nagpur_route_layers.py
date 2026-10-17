#!/usr/bin/env python3
# Fetch reproducible OpenStreetMap context layers for Nagpur city + peri-urban routes.
# Usage: python extract_nagpur_route_layers.py --out output/nagpur_route_layers
# Dependencies: requests, shapely>=2.0
# The live layer data is fetched when this script is run; this script does not fabricate records.
from __future__ import annotations
import argparse, json, sys, time
from datetime import datetime, timezone
from pathlib import Path
import requests
from shapely.geometry import Point, LineString, Polygon, MultiPolygon, mapping

SOUTH, WEST, NORTH, EAST = 20.95, 78.70, 21.35, 79.40
BBOX = f"{SOUTH},{WEST},{NORTH},{EAST}"
ENDPOINTS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter"]
HEADERS = {"User-Agent": "Raksha360RouteResearch/1.0 (OpenStreetMap attribution; user-run extraction)"}

FACILITIES = f'''[out:json][timeout:180];(
 nwr["amenity"~"^(hospital|clinic|doctors|pharmacy|police|fire_station|fuel|bank|atm|community_centre|social_facility|shelter|bus_station|marketplace|cafe|restaurant|fast_food|place_of_worship|toilets|library|post_office)$"]({BBOX});
 nwr["healthcare"~"^(hospital|clinic|doctor|pharmacy|emergency)$"]({BBOX});
 nwr["emergency"~"^(ambulance_station|fire_hydrant|phone)$"]({BBOX});
 nwr["shop"~"^(convenience|supermarket|mall|chemist)$"]({BBOX});
 nwr["public_transport"~"^(station|platform)$"]({BBOX});
 nwr["railway"~"^(station|halt)$"]({BBOX});
 nwr["highway"="street_lamp"]({BBOX});
);out center meta;'''

GREEN_CONTEXT = f'''[out:json][timeout:180];(
 nwr["natural"~"^(wood|scrub|wetland|heath)$"]({BBOX});
 nwr["landuse"~"^(forest|recreation_ground|grass|orchard|residential|commercial|retail|industrial)$"]({BBOX});
 nwr["leisure"~"^(park|nature_reserve|garden)$"]({BBOX});
 nwr["boundary"="protected_area"]({BBOX});
 nwr["place"~"^(neighbourhood|suburb|quarter|hamlet|village|town)$"]({BBOX});
);out geom meta;'''

ROADS_LIGHTING = f'''[out:json][timeout:180];(
 way["highway"]["lit"]({BBOX});
 way["highway"~"^(motorway|trunk|primary|secondary)$"]({BBOX});
 node["highway"="street_lamp"]({BBOX});
 way["lit"]({BBOX});
 way["sidewalk"]({BBOX});
 node["crossing"]({BBOX});
 way["crossing"]({BBOX});
 node["highway"="traffic_signals"]({BBOX});
);out geom meta;'''

KEEP_TAGS = {"name","name:en","amenity","healthcare","emergency","shop","public_transport","railway","highway","natural","landuse","leisure","boundary","place","lit","sidewalk","crossing","opening_hours","operator","operator:type","access","addr:street","addr:suburb","addr:city","phone","website","description","ref","building","service","check_date","survey:date","type","protect_class"}

def query_overpass(query, label):
    errors=[]
    for endpoint in ENDPOINTS:
        for attempt in range(2):
            try:
                r=requests.post(endpoint, data={"data":query}, headers=HEADERS, timeout=(20,240))
                r.raise_for_status()
                payload=r.json()
                if not isinstance(payload.get("elements"), list):
                    raise ValueError("Unexpected Overpass response shape")
                return payload, endpoint
            except Exception as e:
                errors.append(f"{endpoint} attempt {attempt+1}: {type(e).__name__}: {e}")
                time.sleep(2 + attempt*2)
    raise RuntimeError(f"Could not fetch {label}. Check internet/Overpass availability.\n"+"\n".join(errors))

def coords_from_geom(e):
    return [(float(p["lon"]), float(p["lat"])) for p in (e.get("geometry") or []) if "lon" in p and "lat" in p]

def center_point(e):
    if e.get("type")=="node" and "lon" in e and "lat" in e:
        return Point(float(e["lon"]),float(e["lat"]))
    c=e.get("center")
    if c and "lon" in c and "lat" in c:
        return Point(float(c["lon"]),float(c["lat"]))
    pts=coords_from_geom(e)
    if pts:
        return LineString(pts).interpolate(0.5, normalized=True) if len(pts)>1 else Point(pts[0])
    return None

def way_geometry(e, polygon_mode=False):
    coords=coords_from_geom(e)
    if len(coords)<2: return None
    t=e.get("tags") or {}
    closed=coords[0]==coords[-1]
    area_tag=any(k in t for k in ("natural","landuse","leisure","boundary")) and t.get("area")!="no"
    if polygon_mode and closed and area_tag and len(coords)>=4:
        try:
            poly=Polygon(coords)
            if poly.is_valid and poly.area>0: return poly
        except Exception: pass
    return LineString(coords)

def relation_polygon(e):
    # Assemble outer/inner rings from OSM multipolygon relation way members.
    roles={"outer":[],"inner":[]}
    for m in e.get("members") or []:
        if m.get("type")!="way" or not m.get("geometry"): continue
        role=m.get("role") or "outer"
        if role not in roles: continue
        pts=coords_from_geom(m)
        if len(pts)>=2: roles[role].append(pts)
    def assemble(segments):
        rings=[]; segments=[list(s) for s in segments]
        while segments:
            ring=segments.pop(0); guard=0
            while ring[0]!=ring[-1] and segments and guard<10000:
                guard+=1; joined=False
                for idx,s in enumerate(segments):
                    if ring[-1]==s[0]: ring.extend(s[1:])
                    elif ring[-1]==s[-1]: ring.extend(reversed(s[:-1]))
                    elif ring[0]==s[-1]: ring=s[:-1]+ring
                    elif ring[0]==s[0]: ring=list(reversed(s[1:]))+ring
                    else: continue
                    segments.pop(idx); joined=True; break
                if not joined: break
            if len(ring)>=4 and ring[0]==ring[-1]: rings.append(ring)
        return rings
    outers=assemble(roles["outer"]); inners=assemble(roles["inner"])
    if not outers: return None
    holes={i:[] for i in range(len(outers))}
    for inner in inners:
        try:
            ip=Polygon(inner)
            for i,outer in enumerate(outers):
                if Polygon(outer).covers(ip.representative_point()): holes[i].append(inner); break
        except Exception: pass
    polys=[]
    for i,outer in enumerate(outers):
        try:
            p=Polygon(outer, holes[i])
            if p.is_valid and p.area>0: polys.append(p)
        except Exception: pass
    if not polys: return None
    return polys[0] if len(polys)==1 else MultiPolygon(polys)

def make_feature(geom, e, layer, stamp, geometry_quality):
    if geom is None or geom.is_empty: return None
    tags=e.get("tags") or {}
    props={k:v for k,v in tags.items() if k in KEEP_TAGS}
    props.update({"osm_type":e.get("type"),"osm_id":e.get("id"),"osm_version":e.get("version"),
                  "osm_timestamp":e.get("timestamp"),"source":"OpenStreetMap contributors",
                  "source_license":"ODbL 1.0; attribution required", "source_layer":layer,
                  "extracted_at_utc":stamp,"geometry_quality":geometry_quality})
    return {"type":"Feature","geometry":mapping(geom),"properties":props}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--out",default="output/nagpur_route_layers")
    args=parser.parse_args(); out=Path(args.out); out.mkdir(parents=True,exist_ok=True)
    stamp=datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    configs=[("facilities",FACILITIES),("green_context",GREEN_CONTEXT),("roads_lighting",ROADS_LIGHTING)]
    report={"generated_at_utc":stamp,"bbox_south_west_north_east":[SOUTH,WEST,NORTH,EAST],
            "bbox_note":"Extraction window only; not an official municipal boundary.","source":"OpenStreetMap contributors",
            "license":"ODbL 1.0; provide attribution and observe ODbL obligations", "layers":{}}
    for layer,query in configs:
        raw_file = out / f"{layer}_raw_osm.json"
        if raw_file.exists() and raw_file.stat().st_size > 1000:
            print(f"Using already downloaded {layer} raw JSON from {raw_file.name}…", flush=True)
            payload = json.loads(raw_file.read_text(encoding="utf-8"))
            endpoint = "cached_local_raw_extract"
        else:
            print(f"Fetching {layer} from Overpass…", flush=True)
            try:
                payload, endpoint = query_overpass(query, layer)
                raw_file.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
            except Exception as err:
                print(f"  Layer {layer} failed: {err}", flush=True)
                report["layers"][layer] = {"status": "FAILED", "error": str(err)}
                continue
        features=[]; counts={"nodes":0,"ways":0,"relations":0,"area_polygons":0,"center_or_line_fallbacks":0}
        for e in payload.get("elements",[]):
            typ=e.get("type")
            if typ in ("node","way","relation"): counts[{"node":"nodes","way":"ways","relation":"relations"}[typ]]+=1
            geom=None; quality="mapped_geometry"
            if layer=="facilities":
                geom=center_point(e); quality="mapped_point" if typ=="node" else "OSM_object_center"
            elif layer=="green_context":
                if typ=="way": geom=way_geometry(e,polygon_mode=True)
                elif typ=="relation": geom=relation_polygon(e)
                else: geom=center_point(e)
                if geom is not None and geom.geom_type in ("Polygon","MultiPolygon"):
                    counts["area_polygons"]+=1; quality="mapped_area_geometry"
                else:
                    if geom is None: geom=center_point(e)
                    quality="center_or_line_only_not_validated_area"; counts["center_or_line_fallbacks"]+=1
            else:
                geom=center_point(e) if typ=="node" else way_geometry(e,polygon_mode=False)
                quality="mapped_geometry" if geom is not None else "unavailable"
            feat=make_feature(geom,e,layer,stamp,quality)
            if feat: features.append(feat)
        (out/f"{layer}.geojson").write_text(json.dumps({"type":"FeatureCollection","features":features},ensure_ascii=False),encoding="utf-8")
        report["layers"][layer]={"osm_elements":len(payload.get("elements",[])),"geojson_features":len(features),"counts":counts,
                                  "endpoint":endpoint,"osm_base_timestamp":payload.get("osm3s",{}).get("timestamp_osm_base"),
                                  "raw_file":f"{layer}_raw_osm.json","geojson_file":f"{layer}.geojson"}
        print(f"  {len(payload.get('elements',[]))} OSM elements -> {len(features)} GeoJSON features",flush=True)
    (out/"extraction_report.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    (out/"SOURCES_AND_LIMITATIONS.txt").write_text(
      f"Raksha360 Nagpur route layers\nExtracted: {stamp}\nSource: OpenStreetMap contributors (ODbL 1.0)\n\n"
      f"Extraction window south,west,north,east: {SOUTH},{WEST},{NORTH},{EAST}\n"
      "This is a bounding-box planning window, not an official Nagpur municipal boundary.\n"
      "Missing OSM tags mean UNKNOWN, not NO. POIs can be outdated; validate operation and opening hours.\n"
      "Parks/woodland are land-cover context, not evidence of crime or danger. A route through trees is not automatically unsafe.\n"
      "Facility proximity is a support/access proxy, not proof a place is staffed, open, safe, or able to assist.\n"
      "Do not use center_or_line_only_not_validated_area geometry for polygon-overlap scoring.\n"
      "No official police dispatch is implied by these files. Inspect extraction_report.json and object timestamps.\n",encoding="utf-8")
    print(f"Done. Review extraction_report.json and spot-check features in {out.resolve()}")

if __name__=="__main__":
    try: main()
    except Exception as e:
        print(f"ERROR: {e}",file=sys.stderr); sys.exit(1)
