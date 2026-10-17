import io
import json
import pytest
from fastapi.testclient import TestClient
from shapely.geometry import LineString, Polygon, Point

import backend.app.main as api
from backend.app.route_scoring import (
    NagpurRouteSafetyEngine,
    BlackspotRecord,
    FacilityRecord,
    GreenPolygonRecord,
    RoadLightingRecord,
    haversine_distance_m,
)


@pytest.fixture
def client(tmp_path, monkeypatch):
    test_db = tmp_path / "test_route.sqlite3"
    monkeypatch.setattr(api, "DB_PATH", test_db)
    monkeypatch.setattr(api, "DEVICE_TOKEN", "test-device-token")
    monkeypatch.setattr(api, "DASHBOARD_TOKEN", "test-dashboard-token")
    monkeypatch.setenv("RAKSHA360_DB_PATH", str(test_db))
    monkeypatch.setenv("RAKSHA360_DATA_DIR", str(tmp_path))
    monkeypatch.delenv("DATABASE_URL", raising=False)
    api.initialize_database()
    with TestClient(api.app) as test_client:
        yield test_client


@pytest.fixture
def isolated_scoring_engine():
    """Builds a test engine with controlled synthetic and verified geometry records."""
    engine = NagpurRouteSafetyEngine.__new__(NagpurRouteSafetyEngine)
    engine.blackspots = [
        BlackspotRecord(
            id="TEST-BS-001",
            location="Maruti Chowk",
            highway="NH-7",
            accidents_total=12,
            fatalities_total=5,
            latitude=21.1840,
            longitude=79.1171,
            source_url="https://highwaypolice.maharashtra.gov.in/blackspot/",
        )
    ]
    engine.facilities = [
        FacilityRecord(
            osm_id=101,
            osm_type="node",
            name="Sitabuldi Police Station",
            amenity="police",
            category="POLICE",
            latitude=21.1460,
            longitude=79.0880,
            opening_hours="24/7",
            is_emergency=True,
        ),
        FacilityRecord(
            osm_id=102,
            osm_type="node",
            name="Mayo Hospital",
            amenity="hospital",
            category="HEALTHCARE",
            latitude=21.1500,
            longitude=79.0900,
            opening_hours="24/7",
            is_emergency=True,
        ),
    ]
    # Validated green polygon near Ambazari (not a point/centroid!)
    ambazari_poly = Polygon([
        (79.040, 21.130),
        (79.050, 21.130),
        (79.050, 21.140),
        (79.040, 21.140),
        (79.040, 21.130),
    ])
    engine.green_polygons = [
        GreenPolygonRecord(
            osm_id=201,
            category="FOREST",
            geometry=ambazari_poly,
            geometry_quality="mapped_area_geometry",
        )
    ]
    # Road lighting with explicit lit=yes and explicit lit=no
    lit_line = LineString([(79.085, 21.145), (79.090, 21.145)])
    unlit_line = LineString([(79.040, 21.135), (79.050, 21.135)])
    engine.roads_lighting = [
        RoadLightingRecord(
            osm_id=301,
            highway_type="primary",
            lit_status="EXPLICIT_LIT",
            has_sidewalk=True,
            geometry=lit_line,
        ),
        RoadLightingRecord(
            osm_id=302,
            highway_type="unclassified",
            lit_status="EXPLICIT_UNLIT",
            has_sidewalk=False,
            geometry=unlit_line,
        ),
    ]
    engine.extraction_metadata = {
        "generated_at_utc": "2026-10-16T23:08:53Z",
        "bbox_south_west_north_east": [20.95, 78.70, 21.35, 79.40],
    }
    engine.loaded = True
    return engine


# 1. Haversine distance unit test
def test_haversine_distance():
    # Nagpur Central (21.1458, 79.0882) to Sitabuldi (21.1460, 79.0880) is ~30-50m
    d = haversine_distance_m(21.1458, 79.0882, 21.1460, 79.0880)
    assert 20.0 < d < 50.0


# 2. Point-to-polyline distance and blackspot exposure
def test_blackspot_point_to_polyline_exposure(isolated_scoring_engine):
    # Route passing directly through Maruti Chowk blackspot (21.1840, 79.1171)
    route_passing_bs = [
        (21.1820, 79.1171),
        (21.1840, 79.1171),
        (21.1860, 79.1171),
    ]
    res_bs = isolated_scoring_engine.analyze_route(route_passing_bs, label="Route Past Blackspot")
    assert res_bs["historicalAccidentExposure"]["blackspotCount"] >= 1
    assert res_bs["historicalAccidentExposure"]["exposureScore"] > 0.0

    # Route far away from Maruti Chowk (Sitabuldi)
    route_far = [
        (21.1450, 79.0880),
        (21.1470, 79.0880),
    ]
    res_far = isolated_scoring_engine.analyze_route(route_far, label="Route Far From Blackspots")
    assert res_far["historicalAccidentExposure"]["blackspotCount"] == 0
    assert res_far["historicalAccidentExposure"]["exposureScore"] == 0.0


# 3. Woodland polygon intersection strictly requires polygon (not centroid fallback)
def test_woodland_polygon_intersection(isolated_scoring_engine):
    # Route intersecting the Ambazari forest polygon (lat 21.135, lon 79.040 to 79.050)
    wooded_route = [
        (21.135, 79.035),
        (21.135, 79.045),
        (21.135, 79.055),
    ]
    res = isolated_scoring_engine.analyze_route(wooded_route, label="Forest Route")
    assert res["woodedExposure"]["intersectingKm"] > 0.0
    assert "FOREST" in res["woodedExposure"]["categories"]
    assert "environmental context" in res["woodedExposure"]["note"]

    # Route outside polygon
    outside_route = [
        (21.160, 79.080),
        (21.162, 79.082),
    ]
    res_outside = isolated_scoring_engine.analyze_route(outside_route, label="Urban Route")
    assert res_outside["woodedExposure"]["intersectingKm"] == 0.0


# 4. Unknown lighting vs explicit lit=no semantics
def test_lighting_unknown_vs_unlit_semantics(isolated_scoring_engine):
    # Unlit route: passes along unlit_line (lat 21.135, lon 79.040 to 79.050)
    unlit_route = [
        (21.135, 79.040),
        (21.135, 79.050),
    ]
    res_unlit = isolated_scoring_engine.analyze_route(unlit_route, label="Unlit Segment")
    assert res_unlit["lightingCoverage"]["explicitUnlitKm"] > 0.0

    # Route with unmapped roads must have unknownLightingKm > 0, NEVER converted to unlit!
    unmapped_route = [
        (21.200, 79.200),
        (21.205, 79.205),
    ]
    res_unmapped = isolated_scoring_engine.analyze_route(unmapped_route, label="Unmapped Route")
    assert res_unmapped["lightingCoverage"]["unknownLightingKm"] > 0.0
    assert res_unmapped["lightingCoverage"]["explicitUnlitKm"] == 0.0
    assert "unknown lighting" in res_unmapped["lightingCoverage"]["note"].lower()


# 5. Route alternatives comparison with contrasting signals
def test_route_comparison_ranking(isolated_scoring_engine):
    # Route 1: Near police and hospital with high support
    route_supported = {
        "id": "R1",
        "label": "Supported Boulevard",
        "coordinates": [(21.1458, 79.0880), (21.1465, 79.0885), (21.1480, 79.0890)],
        "mode": "walking",
    }
    # Route 2: Intersects unlit road + forest
    route_isolated = {
        "id": "R2",
        "label": "Isolated Track",
        "coordinates": [(21.1350, 79.0400), (21.1350, 79.0500)],
        "mode": "walking",
    }
    comparison = isolated_scoring_engine.compare_routes([route_supported, route_isolated])
    assert len(comparison["routes"]) == 2
    assert comparison["routes"][0]["routeId"] == "R1"
    assert comparison["routes"][0]["rank"] == 1
    assert comparison["routes"][0]["compositeSupportScore"] > comparison["routes"][1]["compositeSupportScore"]
    assert "comparativeAnalysis" in comparison


# 6. REST API: /api/v1/routes/analyze and /compare
def test_routes_api_endpoints(client: TestClient):
    device = {"Authorization": "Bearer test-device-token"}

    # 1. Layers summary
    summary_res = client.get("/api/v1/routes/layers/summary", headers=device)
    assert summary_res.status_code == 200
    summary_data = summary_res.json()
    assert summary_data["status"] == "LOADED"
    assert "counts" in summary_data
    assert "blackspots" in summary_data["counts"]
    assert "facilities" in summary_data["counts"]
    assert "limitations" in summary_data

    # 2. Analyze single route
    analyze_payload = {
        "coordinates": [
            {"latitude": 21.1458, "longitude": 79.0882},
            {"latitude": 21.1500, "longitude": 79.0900},
        ],
        "label": "Central Nagpur Test Route",
        "mode": "walking",
    }
    analyze_res = client.post("/api/v1/routes/analyze", headers=device, json=analyze_payload)
    assert analyze_res.status_code == 200
    res_data = analyze_res.json()
    assert res_data["label"] == "Central Nagpur Test Route"
    assert "compositeSupportScore" in res_data
    assert "lightingCoverage" in res_data
    assert "woodedExposure" in res_data

    # 3. Compare multiple routes
    compare_payload = {
        "routes": [
            {
                "id": "route-a",
                "label": "Route A (Central)",
                "coordinates": [
                    {"latitude": 21.1458, "longitude": 79.0882},
                    {"latitude": 21.1500, "longitude": 79.0900},
                ],
                "mode": "walking",
            },
            {
                "id": "route-b",
                "label": "Route B (Outer)",
                "coordinates": [
                    {"latitude": 21.1820, "longitude": 79.1171},
                    {"latitude": 21.1850, "longitude": 79.1171},
                ],
                "mode": "walking",
            },
        ]
    }
    compare_res = client.post("/api/v1/routes/compare", headers=device, json=compare_payload)
    assert compare_res.status_code == 200
    comp_data = compare_res.json()
    assert len(comp_data["routes"]) == 2
    assert "recommendedRouteId" in comp_data


# 7. Complaint Attachment Pipeline (Upload, MIME check, List, Download)
def test_complaint_attachment_intake_pipeline(client: TestClient):
    device = {"Authorization": "Bearer test-device-token"}
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}

    # 1. Create a report first
    report_res = client.post(
        "/api/v1/reports",
        headers=device,
        json={
            "title": "Unlit stretch near Ambazari",
            "category": "Unsafe route",
            "description": "Streetlights are broken and several people reported feeling unsafe.",
            "locationDescription": "Ambazari lake road",
            "latitude": 21.1350,
            "longitude": 79.0450,
            "isDemoMode": False,
        },
    )
    assert report_res.status_code == 200
    report_id = report_res.json()["reportId"]

    # 2. Upload valid image attachment
    fake_img = io.BytesIO(b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDRtest_image_data")
    upload_res = client.post(
        f"/api/v1/reports/{report_id}/attachments",
        headers=device,
        files={"file": ("evidence_scene.png", fake_img, "image/png")},
    )
    assert upload_res.status_code == 200
    attach_data = upload_res.json()
    assert attach_data["fileName"] == "evidence_scene.png"
    assert attach_data["mimeType"] == "image/png"
    assert attach_data["analysisStatus"] == "PENDING_OFFICER_REVIEW"
    assert "sha256Hash" in attach_data
    attachment_id = attach_data["id"]

    # 3. Reject invalid MIME type (e.g. .exe / binary)
    fake_exe = io.BytesIO(b"MZ\x90\x00dangerous_payload")
    rej_res = client.post(
        f"/api/v1/reports/{report_id}/attachments",
        headers=device,
        files={"file": ("malicious.exe", fake_exe, "application/x-msdownload")},
    )
    assert rej_res.status_code == 400
    assert "Unsupported file type" in rej_res.json()["detail"]

    # 4. List attachments
    list_res = client.get(f"/api/v1/reports/{report_id}/attachments", headers=device)
    assert list_res.status_code == 200
    items = list_res.json()
    assert len(items) == 1
    assert items[0]["id"] == attachment_id

    # 5. Detail endpoint returns attached items
    detail_res = client.get(f"/api/v1/reports/{report_id}", headers=dashboard)
    assert detail_res.status_code == 200
    assert len(detail_res.json()["attachments"]) == 1

    # 6. Download attachment
    down_res = client.get(f"/api/v1/reports/{report_id}/attachments/{attachment_id}/download", headers=device)
    assert down_res.status_code == 200
    assert down_res.headers["content-type"] == "image/png"
    assert b"IHDRtest_image_data" in down_res.content
