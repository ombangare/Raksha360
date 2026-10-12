from __future__ import annotations

import uuid
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import backend.app.main as api
from backend.app.main import haversine_distance_km, format_tel_uri


@pytest.fixture()
def client(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(api, "DB_PATH", tmp_path / "raksha360-nearby-test.sqlite3")
    monkeypatch.setattr(api, "DEVICE_TOKEN", "test-device-token")
    monkeypatch.setattr(api, "DASHBOARD_TOKEN", "test-dashboard-token")
    monkeypatch.setenv("RAKSHA360_SEED_DEMO_DATA", "false")
    with TestClient(api.app) as test_client:
        yield test_client


def seed_test_stations_and_incidents(client: TestClient):
    """Seed controlled stations and incidents for offline, deterministic testing."""
    with api.db() as conn:
        # Station A: 1.0 km away from Nagpur center (21.1458, 79.0882)
        # Station B: 5.0 km away
        # Station C: 15.0 km away (outside 10 km default radius)
        # Station D: No coordinates (OFFICIAL_ADDRESS_ONLY)
        # Demo unit: Separate demo station table
        conn.execute(
            """INSERT OR REPLACE INTO police_station_directory (
                station_id, name, name_mr, phone, email, address, jurisdiction_label,
                latitude, longitude, coordinates_verified, coordinate_source, coordinate_status,
                osm_type, osm_id, source_url, source_page_last_updated, retrieved_on, data_class, updated_at
            ) VALUES
            ('TEST-STA-A', 'Sitabuldi Station', 'सीताबर्डी ठाणे', '0712-2561111', 'sitabuldi@police.example', 'Sitabuldi, Nagpur', 'City Zone',
             21.1466, 79.0833, 1, 'HUMAN_VERIFIED_SURVEY', 'HUMAN_VERIFIED_COORDINATE', 'node', 'osm-101', 'https://example.com', '2026-08', '2026-10-11', 'OFFICIAL_PUBLIC_DIRECTORY', '2026-10-11'),
            ('TEST-STA-B', 'Ambazari Station', 'अंबाझरी ठाणे', '2560008', 'ambazari@police.example', 'Ambazari, Nagpur', 'West Zone',
             21.1486, 79.0554, 0, 'OSM_NOMINATIM_CANDIDATE', 'OSM_COORDINATE_CANDIDATE', 'node', '3143194649', 'https://example.com', '2026-08', '2026-10-11', 'OFFICIAL_PUBLIC_DIRECTORY', '2026-10-11'),
            ('TEST-STA-C', 'Far Station Kalmeshwar', 'कळमेश्वर ठाणे', '+91 7118 271222', 'kalmeshwar@police.example', 'Kalmeshwar, Nagpur Rural', 'Rural Zone',
             21.2320, 78.9180, 1, 'HUMAN_VERIFIED_SURVEY', 'HUMAN_VERIFIED_COORDINATE', 'node', 'osm-103', 'https://example.com', '2026-08', '2026-10-11', 'OFFICIAL_PUBLIC_DIRECTORY', '2026-10-11'),
            ('TEST-STA-D', 'Unlocated Station', 'पत्ता ठाणे', '2500000', 'unlocated@police.example', 'Address text only', 'Unknown',
             NULL, NULL, 0, NULL, 'OFFICIAL_ADDRESS_ONLY', NULL, NULL, 'https://example.com', '2026-08', '2026-10-11', 'OFFICIAL_PUBLIC_DIRECTORY', '2026-10-11')"""
        )

        # Demo response unit in synthetic stations table
        conn.execute(
            """INSERT OR REPLACE INTO stations (station_id, name, zone, latitude, longitude, status, is_demo)
               VALUES ('DEMO-UNIT-TEST', 'Demo Patrol Unit 1', 'Zone A', 21.1458, 79.0882, 'DEMO_ONLY', 1)"""
        )


def test_haversine_and_phone_utilities():
    # Known distance: Nagpur Zero Mile (21.1466, 79.0882) to Sitabuldi (21.1466, 79.0833) ~ 0.51 km
    d = haversine_distance_km(21.1466, 79.0882, 21.1466, 79.0833)
    assert 0.45 <= d <= 0.60

    # Phone URI formats
    assert format_tel_uri("0712-2560008") == "tel:+917122560008"
    assert format_tel_uri("2746555 / 2706361") == "tel:+917122746555"
    assert format_tel_uri("+919876543210") == "tel:+919876543210"
    assert format_tel_uri(None) is None
    assert format_tel_uri("") is None


def test_nearby_stations_authentication(client: TestClient):
    event_id = "TEST-EVT-001"
    # No auth header -> 401
    resp = client.get(f"/api/v1/incidents/{event_id}/nearby-stations")
    assert resp.status_code == 401


def test_nearby_stations_404_when_incident_not_found(client: TestClient):
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}
    resp = client.get("/api/v1/incidents/NONEXISTENT-INCIDENT/nearby-stations", headers=dashboard)
    assert resp.status_code == 404
    assert "Incident not found" in resp.json()["detail"]


def test_nearby_stations_ranking_and_radius(client: TestClient):
    seed_test_stations_and_incidents(client)
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}
    device = {"Authorization": "Bearer test-device-token"}

    # Post an incident at Central Nagpur (21.1458, 79.0882)
    event_id = "INC-NAGPUR-CENTER-001"
    evt = client.post("/api/v1/emergency-events", headers=device, json={
        "eventId": event_id,
        "triggerType": "PANIC_BUTTON",
        "latitude": 21.1458,
        "longitude": 79.0882,
        "isDemoMode": False,
    })
    assert evt.status_code == 200

    # Query nearby stations with default radius 10 km
    resp = client.get(f"/api/v1/incidents/{event_id}/nearby-stations?radius_km=10&limit=5", headers=dashboard)
    assert resp.status_code == 200
    data = resp.json()

    assert data["incidentId"] == event_id
    assert data["locationAvailable"] is True
    assert data["incidentCoordinates"]["latitude"] == pytest.approx(21.1458)
    assert data["incidentCoordinates"]["longitude"] == pytest.approx(79.0882)
    assert data["disclaimer"] == "Nearby public-directory stations — distance estimate; confirm jurisdiction and availability."

    stations = data["stations"]
    assert len(stations) == 2  # Station A (~0.5 km) and Station B (~3.4 km). Station C is > 10 km away.
    # Closest station must be Station A
    assert stations[0]["stationId"] == "TEST-STA-A"
    assert stations[0]["name"] == "Sitabuldi Station"
    assert stations[0]["nameMr"] == "सीताबर्डी ठाणे"
    assert stations[0]["coordinateStatus"] == "HUMAN_VERIFIED_COORDINATE"
    assert stations[0]["coordinatesVerified"] is True
    assert stations[0]["distanceKm"] < stations[1]["distanceKm"]
    assert "tel:" in stations[0]["telUri"]
    assert "openstreetmap.org/directions" in stations[0]["osmDirectionsUrl"]

    # Station B is second
    assert stations[1]["stationId"] == "TEST-STA-B"
    assert stations[1]["coordinateStatus"] == "OSM_COORDINATE_CANDIDATE"
    assert stations[1]["coordinatesVerified"] is False

    # Check that unlocated Station D was NOT included in ranked distance results
    station_ids = [s["stationId"] for s in stations]
    assert "TEST-STA-D" not in station_ids

    # Check that demo unit is NOT included in public directory results
    assert "DEMO-UNIT-TEST" not in station_ids


def test_radius_and_limit_boundaries(client: TestClient):
    seed_test_stations_and_incidents(client)
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}
    device = {"Authorization": "Bearer test-device-token"}

    event_id = "INC-NAGPUR-CENTER-002"
    client.post("/api/v1/emergency-events", headers=device, json={
        "eventId": event_id,
        "triggerType": "VOICE_SOS",
        "latitude": 21.1458,
        "longitude": 79.0882,
        "isDemoMode": False,
    })

    # Test limit=1: only returns 1 closest station even if 2 are in radius
    resp_limit1 = client.get(f"/api/v1/incidents/{event_id}/nearby-stations?limit=1&radius_km=10", headers=dashboard)
    assert resp_limit1.status_code == 200
    assert len(resp_limit1.json()["stations"]) == 1
    assert resp_limit1.json()["stations"][0]["stationId"] == "TEST-STA-A"

    # Test radius=1.0 km: only returns Station A (~0.5 km), excludes Station B (~3.4 km)
    resp_r1 = client.get(f"/api/v1/incidents/{event_id}/nearby-stations?radius_km=1.0", headers=dashboard)
    assert resp_r1.status_code == 200
    assert len(resp_r1.json()["stations"]) == 1
    assert resp_r1.json()["stations"][0]["stationId"] == "TEST-STA-A"

    # Test expanded radius=30 km: includes Station C (Kalmeshwar ~ 20 km away)
    resp_r30 = client.get(f"/api/v1/incidents/{event_id}/nearby-stations?radius_km=30", headers=dashboard)
    assert resp_r30.status_code == 200
    assert len(resp_r30.json()["stations"]) == 3
    assert resp_r30.json()["stations"][2]["stationId"] == "TEST-STA-C"


def test_missing_or_invalid_incident_coordinates(client: TestClient):
    seed_test_stations_and_incidents(client)
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}
    device = {"Authorization": "Bearer test-device-token"}

    # Incident with null coordinates
    event_id_null = "INC-NULL-COORDS-003"
    client.post("/api/v1/emergency-events", headers=device, json={
        "eventId": event_id_null,
        "triggerType": "PANIC_BUTTON",
        "latitude": None,
        "longitude": None,
        "isDemoMode": False,
    })
    resp_null = client.get(f"/api/v1/incidents/{event_id_null}/nearby-stations", headers=dashboard)
    assert resp_null.status_code == 200
    data_null = resp_null.json()
    assert data_null["locationAvailable"] is False
    assert data_null["stations"] == []
    assert data_null["message"] == "Location unavailable — nearby stations cannot be ranked yet."


def test_empty_results_when_no_stations_in_radius(client: TestClient):
    seed_test_stations_and_incidents(client)
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}
    device = {"Authorization": "Bearer test-device-token"}

    # Incident far away from Nagpur (e.g., Delhi coordinates)
    event_id_delhi = "INC-DELHI-004"
    client.post("/api/v1/emergency-events", headers=device, json={
        "eventId": event_id_delhi,
        "triggerType": "PANIC_BUTTON",
        "latitude": 28.6139,
        "longitude": 77.2090,
        "isDemoMode": False,
    })
    resp = client.get(f"/api/v1/incidents/{event_id_delhi}/nearby-stations?radius_km=10", headers=dashboard)
    assert resp.status_code == 200
    data = resp.json()
    assert data["locationAvailable"] is True
    assert data["stations"] == []
    assert data["totalFound"] == 0


def test_different_incidents_return_distinct_rankings(client: TestClient):
    seed_test_stations_and_incidents(client)
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}
    device = {"Authorization": "Bearer test-device-token"}

    # Incident 1 near Sitabuldi
    evt1 = "INC-SITA-005"
    client.post("/api/v1/emergency-events", headers=device, json={
        "eventId": evt1, "triggerType": "PANIC_BUTTON",
        "latitude": 21.1466, "longitude": 79.0830, "isDemoMode": False,
    })

    # Incident 2 near Ambazari
    evt2 = "INC-AMBA-006"
    client.post("/api/v1/emergency-events", headers=device, json={
        "eventId": evt2, "triggerType": "PANIC_BUTTON",
        "latitude": 21.1486, "longitude": 79.0550, "isDemoMode": False,
    })

    resp1 = client.get(f"/api/v1/incidents/{evt1}/nearby-stations", headers=dashboard).json()
    resp2 = client.get(f"/api/v1/incidents/{evt2}/nearby-stations", headers=dashboard).json()

    # Closest station for Incident 1 is Sitabuldi
    assert resp1["stations"][0]["stationId"] == "TEST-STA-A"
    # Closest station for Incident 2 is Ambazari
    assert resp2["stations"][0]["stationId"] == "TEST-STA-B"
