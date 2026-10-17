"""
Raksha360 — Nagpur Route Safety & Support Scoring Service
Evaluates candidate route geometries against verified Nagpur context layers:
  1. Historical accident blackspots (Maharashtra Highway Traffic Police 2021-2023)
  2. Verified public facilities & emergency services (OpenStreetMap ODbL)
  3. Green & environmental context polygons (OpenStreetMap ODbL)
  4. Mapped road environment & explicit lighting (OpenStreetMap ODbL)

Ethical & Technical Principles:
  - NEVER claims any route is "guaranteed safe" or "dangerous".
  - Missing tags MUST remain UNKNOWN, never converted to unlit or negative.
  - Wooded / park areas are environmental context, not crime evidence; evaluated strictly from validated polygons.
  - Blackspots are vehicle crash statistics, not personal crime data.
  - Relative isolation requires multiple coinciding signals (low support + unknown/unlit lighting + wooded context).
  - Configurable, transparent heuristic weights documented for calibration.
"""

from __future__ import annotations

import json
import logging
import math
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from shapely.geometry import LineString, MultiPolygon, Point, Polygon
from shapely.ops import nearest_points

logger = logging.getLogger("raksha360.route_scoring")

# Heuristic weights for route support scoring (documented and configurable)
DEFAULT_SUPPORT_RADIUS_METERS = 300.0
DEFAULT_BLACKSPOT_DECAY_METERS = 300.0
DEFAULT_SEGMENT_STEP_METERS = 50.0

WEIGHT_FACILITY_SUPPORT = 0.40
WEIGHT_LIGHTING_KNOWN = 0.25
WEIGHT_ISOLATION_PENALTY = 0.20
WEIGHT_BLACKSPOT_EXPOSURE = 0.15

# Earth radius in meters
EARTH_RADIUS_M = 6371000.0


def haversine_distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculates great-circle distance between two GPS coordinates in meters."""
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (
        math.sin(delta_phi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return EARTH_RADIUS_M * c


@dataclass
class BlackspotRecord:
    id: str
    location: str
    highway: str
    accidents_total: int
    fatalities_total: int
    latitude: float
    longitude: float
    source_url: str


@dataclass
class FacilityRecord:
    osm_id: int
    osm_type: str
    name: str
    amenity: str
    category: str  # EMERGENCY, HEALTHCARE, POLICE, FIRE, PHARMACY, TRANSIT, COMMERCIAL, OTHER
    latitude: float
    longitude: float
    opening_hours: Optional[str] = None
    is_emergency: bool = False


@dataclass
class GreenPolygonRecord:
    osm_id: int
    category: str  # FOREST, WOOD, SCRUB, PARK, NATURE_RESERVE, OTHER
    geometry: Polygon | MultiPolygon
    geometry_quality: str


@dataclass
class RoadLightingRecord:
    osm_id: int
    highway_type: str
    lit_status: str  # EXPLICIT_LIT, EXPLICIT_UNLIT, UNKNOWN
    has_sidewalk: bool
    geometry: LineString


class NagpurRouteSafetyEngine:
    """In-memory spatial index and route scoring engine for Nagpur city & peri-urban routes."""

    def __init__(self, data_dir: Optional[Path] = None):
        if data_dir is None:
            # Default to repo root / data / route_safety / nagpur
            self.base_dir = (
                Path(__file__).resolve().parent.parent.parent
                / "data"
                / "route_safety"
                / "nagpur"
            )
        else:
            self.base_dir = Path(data_dir)

        self.extracted_dir = self.base_dir / "extracted"
        self.bundle_dir = self.base_dir / "source_bundle"

        self.blackspots: List[BlackspotRecord] = []
        self.facilities: List[FacilityRecord] = []
        self.green_polygons: List[GreenPolygonRecord] = []
        self.roads_lighting: List[RoadLightingRecord] = []
        self.extraction_metadata: Dict[str, Any] = {}
        self.loaded: bool = False

        self._load_datasets()

    def _load_datasets(self) -> None:
        """Loads and normalizes blackspots, facilities, green context, and lighting layers."""
        self._load_blackspots()
        self._load_extraction_metadata()
        self._load_facilities()
        self._load_green_context()
        self._load_roads_lighting()
        self.loaded = True
        logger.info(
            "NagpurRouteSafetyEngine loaded: %d blackspots, %d facilities, %d green polygons, %d road features",
            len(self.blackspots),
            len(self.facilities),
            len(self.green_polygons),
            len(self.roads_lighting),
        )

    def _load_blackspots(self) -> None:
        """Loads Maharashtra Highway Traffic Police national highway blackspot GeoJSON/CSV."""
        geojson_candidates = [
            self.extracted_dir / "nagpur_highway_blackspots_2021_2023.geojson",
            self.bundle_dir / "nagpur_highway_blackspots_2021_2023.geojson",
            self.bundle_dir
            / "raksha360_nagpur_route_data"
            / "raksha360_nagpur_route_data"
            / "nagpur_highway_blackspots_2021_2023.geojson",
        ]
        target = next((p for p in geojson_candidates if p.exists()), None)
        if not target:
            logger.warning("Blackspots GeoJSON not found in source bundle.")
            return

        try:
            data = json.loads(target.read_text(encoding="utf-8"))
            for feat in data.get("features", []):
                props = feat.get("properties", {})
                geom = feat.get("geometry", {})
                coords = geom.get("coordinates", [])
                if len(coords) >= 2:
                    lon, lat = float(coords[0]), float(coords[1])
                    self.blackspots.append(
                        BlackspotRecord(
                            id=props.get("id", f"BS-{len(self.blackspots)+1}"),
                            location=props.get("location", "Highway Junction"),
                            highway=props.get("highway", "National Highway"),
                            accidents_total=int(props.get("accidents_total_2021_2023", 0)),
                            fatalities_total=int(props.get("fatalities_total_2021_2023", 0)),
                            latitude=lat,
                            longitude=lon,
                            source_url=props.get("source_url", "https://highwaypolice.maharashtra.gov.in/blackspot/"),
                        )
                    )
        except Exception as exc:
            logger.error("Failed to load blackspots GeoJSON: %s", exc)

    def _load_extraction_metadata(self) -> None:
        report_file = self.extracted_dir / "extraction_report.json"
        if report_file.exists():
            try:
                self.extraction_metadata = json.loads(report_file.read_text(encoding="utf-8"))
            except Exception as exc:
                logger.error("Failed to read extraction_report.json: %s", exc)

    def _load_facilities(self) -> None:
        file_path = self.extracted_dir / "facilities.geojson"
        if not file_path.exists():
            return
        try:
            data = json.loads(file_path.read_text(encoding="utf-8"))
            for feat in data.get("features", []):
                props = feat.get("properties", {})
                geom = feat.get("geometry", {})
                coords = geom.get("coordinates", [])
                if not coords or len(coords) < 2:
                    continue
                lon, lat = float(coords[0]), float(coords[1])
                amenity = props.get("amenity") or props.get("healthcare") or props.get("emergency") or props.get("shop") or "facility"
                name = props.get("name") or props.get("name:en") or amenity.replace("_", " ").title()

                cat = "OTHER"
                is_emerg = False
                if amenity in ("police", "police_station"):
                    cat = "POLICE"
                    is_emerg = True
                elif amenity in ("hospital", "clinic", "doctors", "emergency", "ambulance_station"):
                    cat = "HEALTHCARE"
                    is_emerg = True
                elif amenity in ("fire_station", "fire_hydrant"):
                    cat = "FIRE"
                    is_emerg = True
                elif amenity in ("pharmacy", "chemist"):
                    cat = "PHARMACY"
                elif amenity in ("bus_station", "station", "platform", "railway"):
                    cat = "TRANSIT"
                elif amenity in ("convenience", "supermarket", "mall", "fuel", "bank", "atm"):
                    cat = "COMMERCIAL"

                self.facilities.append(
                    FacilityRecord(
                        osm_id=int(props.get("osm_id", 0)),
                        osm_type=props.get("osm_type", "node"),
                        name=name,
                        amenity=amenity,
                        category=cat,
                        latitude=lat,
                        longitude=lon,
                        opening_hours=props.get("opening_hours"),
                        is_emergency=is_emerg,
                    )
                )
        except Exception as exc:
            logger.error("Failed to load facilities.geojson: %s", exc)

    def _load_green_context(self) -> None:
        file_path = self.extracted_dir / "green_context.geojson"
        if not file_path.exists():
            return
        try:
            data = json.loads(file_path.read_text(encoding="utf-8"))
            for feat in data.get("features", []):
                quality = feat.get("properties", {}).get("geometry_quality", "")
                # Critical rule: NEVER use centroid fallbacks as land-cover polygons!
                if "not_validated_area" in quality:
                    continue
                geom_dict = feat.get("geometry", {})
                gtype = geom_dict.get("type")
                coords = geom_dict.get("coordinates")
                if not coords:
                    continue

                poly_geom = None
                try:
                    if gtype == "Polygon":
                        poly_geom = Polygon(coords[0], coords[1:]) if len(coords) > 1 else Polygon(coords[0])
                    elif gtype == "MultiPolygon":
                        polys = []
                        for poly_coords in coords:
                            if len(poly_coords) > 1:
                                polys.append(Polygon(poly_coords[0], poly_coords[1:]))
                            else:
                                polys.append(Polygon(poly_coords[0]))
                        poly_geom = MultiPolygon(polys)
                except Exception:
                    continue

                if poly_geom and poly_geom.is_valid and not poly_geom.is_empty:
                    props = feat.get("properties", {})
                    cat = props.get("natural") or props.get("landuse") or props.get("leisure") or "green"
                    self.green_polygons.append(
                        GreenPolygonRecord(
                            osm_id=int(props.get("osm_id", 0)),
                            category=cat.upper(),
                            geometry=poly_geom,
                            geometry_quality=quality,
                        )
                    )
        except Exception as exc:
            logger.error("Failed to load green_context.geojson: %s", exc)

    def _load_roads_lighting(self) -> None:
        file_path = self.extracted_dir / "roads_lighting.geojson"
        if not file_path.exists():
            return
        try:
            data = json.loads(file_path.read_text(encoding="utf-8"))
            for feat in data.get("features", []):
                geom_dict = feat.get("geometry", {})
                if geom_dict.get("type") != "LineString":
                    continue
                coords = geom_dict.get("coordinates", [])
                if len(coords) < 2:
                    continue
                props = feat.get("properties", {})
                lit_tag = (props.get("lit") or "").strip().lower()
                lit_status = "UNKNOWN"
                if lit_tag in ("yes", "24/7", "dusk-dawn", "sunset-sunrise"):
                    lit_status = "EXPLICIT_LIT"
                elif lit_tag in ("no", "none", "disused"):
                    lit_status = "EXPLICIT_UNLIT"

                has_sidewalk = (props.get("sidewalk") or "").strip().lower() in ("yes", "both", "left", "right")

                try:
                    line = LineString(coords)
                    self.roads_lighting.append(
                        RoadLightingRecord(
                            osm_id=int(props.get("osm_id", 0)),
                            highway_type=props.get("highway", "road"),
                            lit_status=lit_status,
                            has_sidewalk=has_sidewalk,
                            geometry=line,
                        )
                    )
                except Exception:
                    continue
        except Exception as exc:
            logger.error("Failed to load roads_lighting.geojson: %s", exc)

    def analyze_route(
        self,
        coordinates: List[Tuple[float, float]],
        label: str = "Candidate Route",
        mode: str = "walking",
    ) -> Dict[str, Any]:
        """
        Analyzes a single route polyline against the Nagpur spatial context layers.
        Coordinates are expected as [(latitude, longitude), ...].
        """
        if len(coordinates) < 2:
            raise ValueError("Route polyline requires at least two coordinate points.")

        # 1. Calculate total length and segment points
        total_length_m = 0.0
        segment_lengths: List[float] = []
        for i in range(len(coordinates) - 1):
            dist = haversine_distance_m(
                coordinates[i][0], coordinates[i][1],
                coordinates[i + 1][0], coordinates[i + 1][1]
            )
            segment_lengths.append(dist)
            total_length_m += dist

        route_length_km = round(total_length_m / 1000.0, 3)
        route_line_lonlat = LineString([(lon, lat) for lat, lon in coordinates])

        # 2. Sample route points along polyline (~50m steps)
        sample_points: List[Tuple[float, float]] = []
        num_samples = max(int(total_length_m / DEFAULT_SEGMENT_STEP_METERS), 5)
        for step in range(num_samples + 1):
            fraction = step / float(num_samples)
            pt = route_line_lonlat.interpolate(fraction, normalized=True)
            sample_points.append((pt.y, pt.x))  # (lat, lon)

        # 3. Proximity to Historical Accident Blackspots
        near_blackspots = []
        blackspot_exposure_score = 0.0
        for bs in self.blackspots:
            # Min distance from blackspot to route line
            bs_point = Point(bs.longitude, bs.latitude)
            nearest_on_route, _ = nearest_points(route_line_lonlat, bs_point)
            dist_m = haversine_distance_m(bs.latitude, bs.longitude, nearest_on_route.y, nearest_on_route.x)
            if dist_m <= DEFAULT_BLACKSPOT_DECAY_METERS:
                decay = 1.0 / (1.0 + (dist_m / 100.0) ** 2)
                severity_weight = (bs.fatalities_total * 3 + bs.accidents_total)
                exposure = round(severity_weight * decay, 2)
                blackspot_exposure_score += exposure
                near_blackspots.append({
                    "id": bs.id,
                    "location": bs.location,
                    "highway": bs.highway,
                    "accidentsTotal2021_2023": bs.accidents_total,
                    "fatalitiesTotal2021_2023": bs.fatalities_total,
                    "distanceMeters": round(dist_m, 1),
                    "decayFactor": round(decay, 3),
                })

        # 4. Support & Facility Proximity
        supported_samples_count = 0
        nearest_emergency_facility: Optional[Dict[str, Any]] = None
        min_emergency_dist_m = float("inf")

        for lat, lon in sample_points:
            pt_has_support = False
            for fac in self.facilities:
                d = haversine_distance_m(lat, lon, fac.latitude, fac.longitude)
                if d <= DEFAULT_SUPPORT_RADIUS_METERS:
                    pt_has_support = True
                if fac.is_emergency and d < min_emergency_dist_m:
                    min_emergency_dist_m = d
                    nearest_emergency_facility = {
                        "name": fac.name,
                        "category": fac.category,
                        "distanceMeters": round(d, 1),
                        "openingHours": fac.opening_hours or "Unknown/Unverified",
                        "verificationStatus": "OpenStreetMap community mapping",
                    }
            if pt_has_support:
                supported_samples_count += 1

        support_coverage_pct = round((supported_samples_count / len(sample_points)) * 100.0, 1)

        # 5. Wooded / Environmental Polygon Intersections
        wooded_intersect_m = 0.0
        intersecting_green_layers = set()
        for gp in self.green_polygons:
            if gp.geometry.intersects(route_line_lonlat):
                try:
                    intersection = gp.geometry.intersection(route_line_lonlat)
                    # Convert degree length of intersection to approximate meters
                    if not intersection.is_empty:
                        # Ratio of intersection length in degrees to total route length in degrees
                        ratio = intersection.length / max(route_line_lonlat.length, 1e-9)
                        wooded_intersect_m += min(ratio * total_length_m, total_length_m)
                        intersecting_green_layers.add(gp.category)
                except Exception:
                    pass

        wooded_intersect_km = round(wooded_intersect_m / 1000.0, 3)
        wooded_coverage_pct = round((wooded_intersect_m / max(total_length_m, 1.0)) * 100.0, 1)

        # 6. Lighting Coverage Breakdown
        # Check buffer overlap with road geometries
        lit_m = 0.0
        unlit_m = 0.0
        unknown_lit_m = 0.0

        for rl in self.roads_lighting:
            if rl.geometry.intersects(route_line_lonlat.buffer(0.0003)):  # ~30m buffer
                try:
                    inter = rl.geometry.intersection(route_line_lonlat.buffer(0.0003))
                    ratio = min(inter.length / max(route_line_lonlat.length, 1e-9), 1.0)
                    seg_len = ratio * total_length_m
                    if rl.lit_status == "EXPLICIT_LIT":
                        lit_m += seg_len
                    elif rl.lit_status == "EXPLICIT_UNLIT":
                        unlit_m += seg_len
                    else:
                        unknown_lit_m += seg_len
                except Exception:
                    pass

        # Normalize lighting lengths so sum does not exceed total route length
        mapped_road_m = lit_m + unlit_m + unknown_lit_m
        if mapped_road_m > total_length_m:
            scale = total_length_m / mapped_road_m
            lit_m *= scale
            unlit_m *= scale
            unknown_lit_m *= scale
        else:
            # Any remaining route length without mapped roads is strictly UNKNOWN
            unknown_lit_m += (total_length_m - mapped_road_m)

        lit_km = round(lit_m / 1000.0, 3)
        unlit_km = round(unlit_m / 1000.0, 3)
        unknown_lit_km = round(unknown_lit_m / 1000.0, 3)

        # 7. Low Support & Relative Isolation
        # Low support segments: points where distance to any facility > 500m AND (wooded OR unlit/unknown)
        isolated_sample_count = 0
        for lat, lon in sample_points:
            pt_geom = Point(lon, lat)
            min_fac_dist = min(
                (haversine_distance_m(lat, lon, f.latitude, f.longitude) for f in self.facilities),
                default=1000.0,
            )
            is_wooded_pt = any(gp.geometry.contains(pt_geom) for gp in self.green_polygons)
            is_unlit_pt = any(
                rl.lit_status == "EXPLICIT_UNLIT" and rl.geometry.distance(pt_geom) < 0.0003
                for rl in self.roads_lighting
            )
            if min_fac_dist > 500.0 and (is_wooded_pt or is_unlit_pt):
                isolated_sample_count += 1

        isolated_km = round((isolated_sample_count / len(sample_points)) * route_length_km, 3)

        # 8. Composite Support Index (0-100)
        # Higher score = more infrastructure support, verified lighting, lower blackspot proximity
        facility_score = support_coverage_pct  # 0 to 100
        lighting_score = max(0.0, 100.0 - (unlit_km / max(route_length_km, 0.1)) * 100.0 - (unknown_lit_km / max(route_length_km, 0.1)) * 30.0)
        isolation_penalty = min((isolated_km / max(route_length_km, 0.1)) * 100.0, 100.0)
        blackspot_penalty = min(blackspot_exposure_score * 5.0, 100.0)

        composite_score = (
            facility_score * WEIGHT_FACILITY_SUPPORT
            + lighting_score * WEIGHT_LIGHTING_KNOWN
            - isolation_penalty * WEIGHT_ISOLATION_PENALTY
            - blackspot_penalty * WEIGHT_BLACKSPOT_EXPOSURE
        )
        composite_score = round(max(5.0, min(95.0, composite_score)), 1)

        # 9. Evidence-Grounded Labels & Explanations
        reasons: List[str] = []
        if support_coverage_pct >= 70.0:
            reasons.append(f"High support coverage ({support_coverage_pct}% of route near mapped public amenities)")
        elif support_coverage_pct <= 30.0:
            reasons.append(f"Lower mapped support coverage ({support_coverage_pct}% near mapped amenities)")

        if near_blackspots:
            reasons.append(f"Passes near {len(near_blackspots)} historical vehicle collision blackspot(s)")

        if wooded_intersect_km > 0.1:
            reasons.append(f"Intersects {wooded_intersect_km} km of green/woodland areas ({', '.join(intersecting_green_layers)})")

        if unlit_km > 0.05:
            reasons.append(f"{unlit_km} km of road tagged with explicit lit=no")

        if unknown_lit_km > (route_length_km * 0.5):
            reasons.append(f"Limited lighting tag data ({unknown_lit_km} km unconfirmed on OpenStreetMap)")

        # Determine relative category tag
        if composite_score >= 65.0:
            primary_label = "More Supported Route"
        elif composite_score >= 45.0:
            primary_label = "Moderate Support Route"
        else:
            primary_label = "Less Supported / Relatively Isolated"

        # Estimated travel duration based on mode (walking: 4.5 km/h, driving: 25 km/h)
        speed_kmh = 4.5 if mode.lower() == "walking" else 25.0
        eta_minutes = max(int(round((route_length_km / speed_kmh) * 60.0)), 1)

        return {
            "label": label,
            "mode": mode,
            "routeLengthKm": route_length_km,
            "etaMinutes": eta_minutes,
            "primaryLabel": primary_label,
            "compositeSupportScore": composite_score,
            "supportCoveragePercent": support_coverage_pct,
            "nearestEmergencyFacility": nearest_emergency_facility,
            "lightingCoverage": {
                "explicitLitKm": lit_km,
                "explicitUnlitKm": unlit_km,
                "unknownLightingKm": unknown_lit_km,
                "note": "Missing tags represent unknown lighting on OpenStreetMap, not proof lights are absent.",
            },
            "woodedExposure": {
                "intersectingKm": wooded_intersect_km,
                "coveragePercent": wooded_coverage_pct,
                "categories": list(intersecting_green_layers),
                "note": "Woodland and parks are environmental context; they do not indicate crime or danger.",
            },
            "relativeIsolation": {
                "lowSupportSegmentsKm": isolated_km,
                "coincidingSignals": "Areas with low facility density coinciding with unlit or wooded context.",
            },
            "historicalAccidentExposure": {
                "blackspotCount": len(near_blackspots),
                "exposureScore": round(blackspot_exposure_score, 2),
                "nearBlackspots": near_blackspots,
                "disclaimer": "National Highway vehicle crash statistics (2021-2023) from Maharashtra Highway Police. Not crime or personal assault data.",
            },
            "evidenceExplanations": reasons,
            "dataFreshness": {
                "extractedAtUtc": self.extraction_metadata.get("generated_at_utc", "2026-10-16T23:08:53+00:00"),
                "source": "OpenStreetMap contributors (ODbL 1.0) & Maharashtra Highway Traffic Police",
                "coverageNote": "Nagpur city plus peri-urban planning bbox (20.95, 78.70 to 21.35, 79.40); not an official municipal boundary.",
            },
        }

    def compare_routes(self, routes: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Compares multiple candidate route polylines and ranks them by evidence-grounded criteria.
        Each candidate route must contain 'id', 'coordinates', and optional 'label'.
        """
        if not routes:
            return {"rankings": [], "comparisonSummary": "No routes provided."}

        analyzed_routes = []
        for r in routes:
            coords = r.get("coordinates") or []
            label = r.get("label") or f"Route {len(analyzed_routes)+1}"
            mode = r.get("mode") or "walking"
            analysis = self.analyze_route(coords, label=label, mode=mode)
            analysis["routeId"] = r.get("id", str(len(analyzed_routes) + 1))
            analyzed_routes.append(analysis)

        # Rank routes descending by compositeSupportScore
        ranked = sorted(analyzed_routes, key=lambda x: x["compositeSupportScore"], reverse=True)

        for rank_idx, route in enumerate(ranked):
            route["rank"] = rank_idx + 1

        # Generate comparative explanation
        best = ranked[0]
        comparisons = []
        if len(ranked) > 1:
            second = ranked[1]
            diff = round(best["compositeSupportScore"] - second["compositeSupportScore"], 1)
            diff_support = round(best["supportCoveragePercent"] - second["supportCoveragePercent"], 1)
            summary = (
                f"'{best['label']}' is evaluated with higher support (+{diff_support}% public facility coverage) "
                f"compared to '{second['label']}'."
            )
            if best["historicalAccidentExposure"]["blackspotCount"] < second["historicalAccidentExposure"]["blackspotCount"]:
                summary += f" It also avoids {second['historicalAccidentExposure']['blackspotCount']} historical NH accident blackspot(s)."
            comparisons.append(summary)
        else:
            comparisons.append(f"Evaluated single route '{best['label']}'.")

        return {
            "routes": ranked,
            "recommendedRouteId": best["routeId"],
            "comparativeAnalysis": comparisons,
            "disclaimer": "Route comparison ranks relative infrastructure presence and historical accident proximity. It does not provide absolute safety guarantees.",
        }


# Singleton engine instance
_engine_instance: Optional[NagpurRouteSafetyEngine] = None


def get_nagpur_route_safety_engine() -> NagpurRouteSafetyEngine:
    global _engine_instance
    if _engine_instance is None:
        _engine_instance = NagpurRouteSafetyEngine()
    return _engine_instance
