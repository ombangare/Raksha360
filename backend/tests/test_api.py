from __future__ import annotations

import importlib
import uuid
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import backend.app.main as api


@pytest.fixture()
def client(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(api, "DB_PATH", tmp_path / "raksha360-test.sqlite3")
    monkeypatch.setattr(api, "DEVICE_TOKEN", "test-device-token")
    monkeypatch.setattr(api, "DASHBOARD_TOKEN", "test-dashboard-token")
    monkeypatch.setenv("RAKSHA360_SEED_DEMO_DATA", "true")
    with TestClient(api.app) as test_client:
        yield test_client


def test_health_and_device_authentication(client: TestClient):
    health = client.get("/api/v1/health")
    assert health.status_code == 200
    assert health.json()["status"] == "ONLINE"
    assert health.json()["officialPoliceGateway"] == "NOT_CONFIGURED"

    accepted = client.post("/api/v1/device/session", headers={"Authorization": "Bearer test-device-token"})
    assert accepted.status_code == 200
    assert accepted.json()["authenticated"] is True

    denied = client.post("/api/v1/device/session", headers={"Authorization": "Bearer wrong-token"})
    assert denied.status_code == 401


def test_phone_sos_is_persisted_and_duplicate_retry_is_deduplicated(client: TestClient):
    headers = {"Authorization": "Bearer test-device-token"}
    event_id = f"TEST-{uuid.uuid4()}"
    payload = {
        "eventId": event_id,
        "timestamp": 1780000000000,
        "triggerType": "CHILD_SOS",
        "phraseDetected": "CHILD_SOS",
        "speakerConfidence": 0.0,
        "latitude": 21.1458,
        "longitude": 79.0882,
        "accuracyMeters": 12,
        "locationDescription": "Synthetic test coordinate",
        "deviceSessionId": "unit-test",
        "isDemoMode": False,
    }
    first = client.post("/api/v1/emergency-events", headers=headers, json=payload)
    assert first.status_code == 200
    assert first.json()["status"] == "RECEIVED_IN_CONTROL_ROOM_QUEUE"

    retry = client.post("/api/v1/emergency-events", headers=headers, json=payload)
    assert retry.status_code == 200
    assert retry.json()["eventId"] == event_id
    assert client.get("/api/v1/incidents", headers={"X-Dashboard-Token": "test-dashboard-token"}).status_code == 200


def test_location_report_and_internal_dispatch_workflows(client: TestClient):
    device = {"Authorization": "Bearer test-device-token"}
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}
    event_id = f"TEST-{uuid.uuid4()}"
    event = client.post("/api/v1/emergency-events", headers=device, json={
        "eventId": event_id, "triggerType": "PANIC_BUTTON", "isDemoMode": False,
        "latitude": 21.1458, "longitude": 79.0882,
    })
    assert event.status_code == 200

    location = client.post("/api/v1/emergency-locations", headers=device, json={
        "eventId": event_id, "latitude": 21.1460, "longitude": 79.0885,
        "accuracyMeters": 9.5, "speedKmph": 0, "batteryPct": 72,
    })
    assert location.status_code == 200
    assert location.json()["recorded"] is True

    report = client.post("/api/v1/reports", headers=device, json={
        "title": "Synthetic child safety report",
        "category": "Child safety",
        "description": "Testing persistence in an isolated temporary database.",
        "isDemoMode": True,
    })
    assert report.status_code == 200
    report_id = report.json()["reportId"]
    assert client.get("/api/v1/reports", headers=dashboard).json()["total"] >= 1
    update_report = client.patch(f"/api/v1/reports/{report_id}/status", headers=dashboard, json={
        "status": "Under Review", "priority": "High", "notes": "Unit test status change",
    })
    assert update_report.status_code == 200

    dispatch = client.post(f"/api/v1/incidents/{event_id}/dispatch", headers=dashboard, json={
        "station": "Test-only station",
        "assignedOfficer": "Test-only officer",
        "unitLabel": "Synthetic unit",
        "channel": "INTERNAL_CONTROL_ROOM",
        "notes": "Never dispatches externally in this test.",
    })
    assert dispatch.status_code == 200
    assert dispatch.json()["externalDispatchSent"] is False
    assert dispatch.json()["external_gateway_status"] == "NOT_CONFIGURED"

    update_dispatch = client.patch(f"/api/v1/dispatches/{dispatch.json()['dispatch_id']}/status", headers=dashboard, json={
        "status": "ACKNOWLEDGED_IN_CONTROL_ROOM", "notes": "Operator test acknowledgment",
    })
    assert update_dispatch.status_code == 200
    assert update_dispatch.json()["status"] == "ACKNOWLEDGED_IN_CONTROL_ROOM"


def test_local_police_alert_never_claims_official_dispatch(client: TestClient):
    headers = {"Authorization": "Bearer test-device-token"}
    event_id = f"TEST-{uuid.uuid4()}"
    client.post("/api/v1/emergency-events", headers=headers, json={
        "eventId": event_id, "triggerType": "VOICE_SOS", "isDemoMode": False,
    })
    response = client.post("/api/v1/police-alerts", headers=headers, json={"eventId": event_id})
    assert response.status_code == 200
    assert response.json()["acknowledged"] is False
    assert response.json()["externalGatewayStatus"] == "NOT_CONFIGURED"


def test_trusted_contact_alert_is_queued_without_claiming_delivery(client: TestClient):
    device = {"Authorization": "Bearer test-device-token"}
    event_id = f"TEST-{uuid.uuid4()}"
    event = client.post("/api/v1/emergency-events", headers=device, json={
        "eventId": event_id,
        "triggerType": "CHILD_SOS",
        "isDemoMode": False,
    })
    assert event.status_code == 200

    response = client.post("/api/v1/trusted-contact-alerts", headers=device, json={
        "eventId": event_id,
        "triggerType": "CHILD_SOS",
        "contacts": [{"name": "Synthetic Guardian", "phoneNumber": "+910000000000", "deliveryChannels": ["SMS"]}],
        "address": "Synthetic test location",
    })
    assert response.status_code == 200
    payload = response.json()
    assert payload["sentCount"] == 0
    assert payload["channelsDispatched"] == []
    assert payload["deliveryStatus"] == "PENDING_PROVIDER_CONFIGURATION"
    assert "No SMS" in payload["message"]


def test_websocket_receives_new_phone_incident_without_refresh(client: TestClient):
    device = {"Authorization": "Bearer test-device-token"}
    event_id = f"TEST-{uuid.uuid4()}"
    with client.websocket_connect("/ws/police?token=test-dashboard-token") as socket:
        connected = socket.receive_json()
        assert connected["type"] == "CONNECTED"

        response = client.post("/api/v1/emergency-events", headers=device, json={
            "eventId": event_id,
            "triggerType": "PANIC_BUTTON",
            "latitude": 21.1458,
            "longitude": 79.0882,
            "isDemoMode": False,
        })
        assert response.status_code == 200
        packet = socket.receive_json()
        assert packet["type"] == "INCIDENT_CREATED"
        assert packet["data"]["event_id"] == event_id



def test_device_heartbeat_records_last_known_location_and_explicit_email_consent(client: TestClient):
    device = {"Authorization": "Bearer test-device-token"}
    response = client.post("/api/v1/device/heartbeat", headers=device, json={
        "deviceId": "test-device-heartbeat-001",
        "timestamp": api.utc_ms() - 600_000,
        "monitoringEnabled": True,
        "offlineLocationEmailConsent": True,
        "latitude": 21.1234,
        "longitude": 79.1234,
        "accuracyMeters": 7.5,
        "locationTimestamp": api.utc_ms(),
        "batteryPct": 64,
        "contacts": [{
            "name": "Synthetic Contact", "relationship": "Guardian", "email": "trusted@example.com",
            "notifyByEmail": True, "isActive": True,
        }],
    })
    assert response.status_code == 200, response.text
    assert response.json()["monitoringEnabled"] is True
    assert response.json()["lastLocationRecorded"] is True
    assert response.json()["emailContactsConfigured"] == 1
    with api.db() as conn:
        row = conn.execute("SELECT * FROM device_status WHERE device_id = ?", ("test-device-heartbeat-001",)).fetchone()
        contact = conn.execute("SELECT * FROM emergency_contacts WHERE device_id = ?", ("test-device-heartbeat-001",)).fetchone()
    assert row["last_latitude"] == pytest.approx(21.1234)
    assert row["last_longitude"] == pytest.approx(79.1234)
    assert row["last_seen_at"] > api.utc_ms() - 60_000  # server receive time, not the stale client clock
    assert row["offline_location_email_consent"] == 1
    assert contact["email"] == "trusted@example.com"


def test_missed_heartbeat_creates_last_known_location_incident_and_sends_consented_email(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    import asyncio

    device = {"Authorization": "Bearer test-device-token"}
    monkeypatch.setenv("RAKSHA360_OFFLINE_THRESHOLD_SECONDS", "60")
    monkeypatch.setenv("RAKSHA360_SMTP_HOST", "smtp.example.test")
    monkeypatch.setenv("RAKSHA360_SMTP_FROM_EMAIL", "raksha360@example.test")
    sent = []
    monkeypatch.setattr(api, "send_offline_location_email", lambda contact, status, incident_id: sent.append((contact["email"], status["last_latitude"], incident_id)))
    broadcasts = []
    async def capture_broadcast(event_type, data):
        broadcasts.append((event_type, data))
    monkeypatch.setattr(api.manager, "broadcast", capture_broadcast)
    device_id = "test-device-offline-002"
    last_seen = api.utc_ms() - 180_000
    response = client.post("/api/v1/device/heartbeat", headers=device, json={
        "deviceId": device_id,
        "timestamp": last_seen,
        "monitoringEnabled": True,
        "offlineLocationEmailConsent": True,
        "latitude": 21.1555,
        "longitude": 79.1555,
        "accuracyMeters": 11,
        "locationTimestamp": last_seen,
        "contacts": [{"name": "Synthetic Contact", "relationship": "Guardian", "email": "offline@example.com", "notifyByEmail": True, "isActive": True}],
    })
    assert response.status_code == 200, response.text
    # Last-heartbeat freshness is measured by the server, never by a client-supplied clock.
    with api.db() as conn:
        conn.execute("UPDATE device_status SET last_seen_at = ? WHERE device_id = ?", (last_seen, device_id))
    asyncio.run(api.evaluate_offline_devices_once())
    with api.db() as conn:
        status = conn.execute("SELECT * FROM device_status WHERE device_id = ?", (device_id,)).fetchone()
        incident = conn.execute("SELECT * FROM incidents WHERE event_id = ?", (status["offline_incident_id"],)).fetchone()
    assert status["status"] == "OFFLINE"
    assert status["offline_email_delivery_status"] == "SENT"
    assert incident["trigger_type"] == "DEVICE_OFFLINE"
    assert incident["latitude"] == pytest.approx(21.1555)
    assert incident["longitude"] == pytest.approx(79.1555)
    assert "Last known" in incident["location_description"]
    assert sent and sent[0][0] == "offline@example.com"
    assert [event for event, _ in broadcasts if event in {"DEVICE_OFFLINE_DETECTED", "INCIDENT_CREATED"}] == ["DEVICE_OFFLINE_DETECTED", "INCIDENT_CREATED"]


def test_offline_email_retry_does_not_duplicate_successful_recipient(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    import asyncio

    device = {"Authorization": "Bearer test-device-token"}
    monkeypatch.setenv("RAKSHA360_OFFLINE_THRESHOLD_SECONDS", "60")
    monkeypatch.setenv("RAKSHA360_SMTP_HOST", "smtp.example.test")
    monkeypatch.setenv("RAKSHA360_SMTP_FROM_EMAIL", "raksha360@example.test")
    attempts: dict[str, int] = {"first@example.com": 0, "second@example.com": 0}

    def fake_send(contact, status, incident_id):
        email = contact["email"]
        attempts[email] += 1
        if email == "second@example.com" and attempts[email] == 1:
            raise RuntimeError("synthetic SMTP failure")

    monkeypatch.setattr(api, "send_offline_location_email", fake_send)
    device_id = "test-device-offline-retry-003"
    last_seen = api.utc_ms() - 180_000
    response = client.post("/api/v1/device/heartbeat", headers=device, json={
        "deviceId": device_id,
        "monitoringEnabled": True,
        "offlineLocationEmailConsent": True,
        "latitude": 21.1444,
        "longitude": 79.1444,
        "locationTimestamp": last_seen,
        "contacts": [
            {"name": "Contact One", "email": "first@example.com", "notifyByEmail": True, "isActive": True},
            {"name": "Contact Two", "email": "second@example.com", "notifyByEmail": True, "isActive": True},
        ],
    })
    assert response.status_code == 200, response.text
    with api.db() as conn:
        conn.execute("UPDATE device_status SET last_seen_at=? WHERE device_id=?", (last_seen, device_id))
    asyncio.run(api.evaluate_offline_devices_once())
    with api.db() as conn:
        row = conn.execute("SELECT * FROM device_status WHERE device_id=?", (device_id,)).fetchone()
    assert row["offline_email_delivery_status"] == "PARTIAL"

    # Simulate reconnection before retry: only the failed recipient should be retried.
    reconnection = client.post("/api/v1/device/heartbeat", headers=device, json={
        "deviceId": device_id,
        "monitoringEnabled": True,
        "offlineLocationEmailConsent": True,
        "contacts": [
            {"name": "Contact One", "email": "first@example.com", "notifyByEmail": True, "isActive": True},
            {"name": "Contact Two", "email": "second@example.com", "notifyByEmail": True, "isActive": True},
        ],
    })
    assert reconnection.status_code == 200, reconnection.text
    with api.db() as conn:
        reconnected = conn.execute("SELECT * FROM device_status WHERE device_id=?", (device_id,)).fetchone()
    assert reconnected["status"] == "ONLINE"
    assert reconnected["offline_detected_at"] is None
    asyncio.run(api.evaluate_offline_devices_once())
    assert attempts["first@example.com"] == 1
    assert attempts["second@example.com"] == 2
    with api.db() as conn:
        rows = conn.execute("SELECT email, status, attempt_count FROM offline_email_deliveries WHERE device_id=?", (device_id,)).fetchall()
        status = conn.execute("SELECT offline_email_delivery_status, offline_email_sent_at FROM device_status WHERE device_id=?", (device_id,)).fetchone()
    deliveries = {item["email"]: (item["status"], item["attempt_count"]) for item in rows}
    assert deliveries["first@example.com"] == ("SENT", 1)
    assert deliveries["second@example.com"] == ("SENT", 2)
    assert status["offline_email_delivery_status"] == "SENT"
    assert status["offline_email_sent_at"] is not None


def test_offline_monitor_does_not_email_without_explicit_consent(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    import asyncio

    device = {"Authorization": "Bearer test-device-token"}
    monkeypatch.setenv("RAKSHA360_OFFLINE_THRESHOLD_SECONDS", "60")
    monkeypatch.setenv("RAKSHA360_SMTP_HOST", "smtp.example.test")
    monkeypatch.setenv("RAKSHA360_SMTP_FROM_EMAIL", "raksha360@example.test")
    sent = []
    monkeypatch.setattr(api, "send_offline_location_email", lambda *args: sent.append(args))
    device_id = "test-device-offline-consent-004"
    last_seen = api.utc_ms() - 180_000
    response = client.post("/api/v1/device/heartbeat", headers=device, json={
        "deviceId": device_id,
        "monitoringEnabled": True,
        "offlineLocationEmailConsent": False,
        "latitude": 21.1444,
        "longitude": 79.1444,
        "locationTimestamp": last_seen,
        "contacts": [{"name": "No Consent", "email": "no-consent@example.com", "notifyByEmail": True, "isActive": True}],
    })
    assert response.status_code == 200, response.text
    with api.db() as conn:
        conn.execute("UPDATE device_status SET last_seen_at=? WHERE device_id=?", (last_seen, device_id))
    asyncio.run(api.evaluate_offline_devices_once())
    assert sent == []
    with api.db() as conn:
        row = conn.execute("SELECT offline_email_delivery_status, offline_location_email_consent FROM device_status WHERE device_id=?", (device_id,)).fetchone()
        contacts = conn.execute("SELECT COUNT(*) FROM emergency_contacts WHERE device_id=?", (device_id,)).fetchone()[0]
    assert row["offline_email_delivery_status"] == "NOT_CONSENTED"
    assert row["offline_location_email_consent"] == 0
    assert contacts == 0


def test_disarmed_device_does_not_generate_offline_incident(client: TestClient):
    import asyncio

    device = {"Authorization": "Bearer test-device-token"}
    device_id = "test-device-disarmed-005"
    response = client.post("/api/v1/device/heartbeat", headers=device, json={
        "deviceId": device_id,
        "monitoringEnabled": False,
        "offlineLocationEmailConsent": True,
        "latitude": 21.1333,
        "longitude": 79.1333,
        "contacts": [{"name": "Synthetic Contact", "email": "disarmed@example.com", "notifyByEmail": True, "isActive": True}],
    })
    assert response.status_code == 200, response.text
    with api.db() as conn:
        conn.execute("UPDATE device_status SET last_seen_at=? WHERE device_id=?", (api.utc_ms() - 600_000, device_id))
    asyncio.run(api.evaluate_offline_devices_once())
    with api.db() as conn:
        status = conn.execute("SELECT status, monitoring_enabled FROM device_status WHERE device_id=?", (device_id,)).fetchone()
        incident_count = conn.execute("SELECT COUNT(*) FROM incidents WHERE device_session_id=?", (device_id,)).fetchone()[0]
    assert status["monitoring_enabled"] == 0
    assert status["status"] == "DISARMED"
    assert incident_count == 0


def test_public_police_station_directory_is_authenticated_provenanced_and_idempotent(client: TestClient):
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}
    denied = client.get("/api/v1/police-station-directory")
    assert denied.status_code == 401

    first = client.get("/api/v1/police-station-directory", headers=dashboard)
    assert first.status_code == 200
    payload = first.json()
    assert payload["dataClass"] == "OFFICIAL_PUBLIC_DIRECTORY"
    assert payload["total"] == 37
    assert payload["sourceUrl"] == "https://nagpurpolice.gov.in/police-stations"
    assert all(item["isOfficialPublicDirectory"] is True for item in payload["items"])
    assert all(item["isDemo"] is False for item in payload["items"])
    assert all(item["latitude"] is None and item["longitude"] is None for item in payload["items"])
    assert all(item["coordinates_verified"] is False for item in payload["items"])

    # Reinitialization imports public directory rows with stable IDs rather than duplicates.
    api.initialize_database()
    second = client.get("/api/v1/police-station-directory", headers=dashboard)
    assert second.status_code == 200
    assert second.json()["total"] == 37
    assert [item["station_id"] for item in second.json()["items"]] == [
        item["station_id"] for item in payload["items"]
    ]


def test_public_directory_preserves_manually_approved_coordinates_and_avoids_operational_inference(client: TestClient):
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}
    # Simulate manual geocoding review approval for one station
    with api.db() as conn:
        conn.execute(
            """UPDATE police_station_directory
               SET latitude = 21.1458, longitude = 79.0882, coordinates_verified = 1,
                   coordinate_source = 'MANUAL_OSM_REVIEW', coordinate_checked_at = '2026-10-11'
               WHERE station_id = 'NGP-AJNI'"""
        )

    # Reseeding/initialization must NOT overwrite human-approved coordinates
    api.initialize_database()

    response = client.get("/api/v1/police-station-directory", headers=dashboard)
    assert response.status_code == 200
    items = {item["station_id"]: item for item in response.json()["items"]}
    ajni = items["NGP-AJNI"]
    assert ajni["coordinates_verified"] is True
    assert ajni["latitude"] == pytest.approx(21.1458)
    assert ajni["longitude"] == pytest.approx(79.0882)
    assert ajni["coordinate_source"] == "MANUAL_OSM_REVIEW"

    # Verify other stations remain null and unverified
    ambazari = items["NGP-AMBAZARI"]
    assert ambazari["latitude"] is None
    assert ambazari["longitude"] is None
    assert ambazari["coordinates_verified"] is False

    # Verify no station claims operational status
    for item in response.json()["items"]:
        assert item["status"] == "PUBLIC_DIRECTORY_REFERENCE"
        assert item["status"] not in {"AVAILABLE", "ON_DUTY", "DISPATCHED", "ONLINE"}


def test_official_directory_and_demo_units_in_separate_endpoints(client: TestClient):
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}
    pub_dir = client.get("/api/v1/police-station-directory", headers=dashboard).json()
    demo_units = client.get("/api/v1/stations", headers=dashboard).json()

    assert pub_dir["dataClass"] == "OFFICIAL_PUBLIC_DIRECTORY"
    assert pub_dir["total"] == 37
    assert all(item["isOfficialPublicDirectory"] is True for item in pub_dir["items"])
    assert all(item["isDemo"] is False for item in pub_dir["items"])

    assert demo_units["total"] >= 1
    assert all(item["isDemo"] is True for item in demo_units["items"])
    assert all(item.get("status") == "DEMO_ONLY" for item in demo_units["items"])


def test_synthetic_fixture_importer_and_clear_lifecycle(client: TestClient):
    import sys
    workspace_root = Path(__file__).resolve().parents[2]
    if str(workspace_root) not in sys.path:
        sys.path.insert(0, str(workspace_root))
    from scripts.import_raksha360_fixtures import import_demo_fixtures, clear_demo_fixtures

    device = {"Authorization": "Bearer test-device-token"}
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}

    # 1. Post a real live user event
    live_event_id = f"LIVE-{uuid.uuid4()}"
    client.post("/api/v1/emergency-events", headers=device, json={
        "eventId": live_event_id,
        "triggerType": "PANIC_BUTTON",
        "isDemoMode": False,
        "latitude": 21.1400,
        "longitude": 79.0800,
    })

    # 2. Run explicit demo importer
    with api.db() as conn:
        counts = import_demo_fixtures(conn)
    assert counts["incidents"] == 10
    assert counts["reports"] == 6
    assert counts["dispatches"] == 4
    assert counts["device_statuses"] == 3

    # Check incidents: live event is is_demo=False, fixtures are is_demo=True
    incidents = client.get("/api/v1/incidents", headers=dashboard).json()["items"]
    live_record = next(item for item in incidents if item["event_id"] == live_event_id)
    assert live_record["isDemo"] is False

    demo_voice = next(item for item in incidents if item["event_id"] == "DEMO-VOICE-SOS-002")
    assert demo_voice["isDemo"] is True
    assert demo_voice["phrase_detected"] == "Raksha 360, help me"
    assert demo_voice["details"]["source"] == "SYNTHETIC_FIXTURE"

    demo_offline = next(item for item in incidents if item["event_id"] == "DEMO-OFFLINE-009")
    assert demo_offline["isDemo"] is True
    assert "LAST KNOWN LOCATION" in demo_offline["location_description"]
    assert "NOT LIVE" in demo_offline["location_description"]

    # 3. Running demo importer again is idempotent
    with api.db() as conn:
        counts_second = import_demo_fixtures(conn)
    assert counts_second["incidents"] == 10

    # 4. Clear demo fixtures
    with api.db() as conn:
        clear_counts = clear_demo_fixtures(conn)
    assert clear_counts["incidents"] >= 10

    # Verify live event is PRESERVED, and all DEMO-* events are deleted
    remaining = client.get("/api/v1/incidents", headers=dashboard).json()["items"]
    assert any(item["event_id"] == live_event_id for item in remaining)
    assert not any(item["event_id"].startswith("DEMO-") for item in remaining)


def test_public_helplines_and_ncrb_manifest_endpoints(client: TestClient):
    dashboard = {"X-Dashboard-Token": "test-dashboard-token"}

    helplines = client.get("/api/v1/public-helplines", headers=dashboard)
    assert helplines.status_code == 200
    h_data = helplines.json()
    assert h_data["metadata"]["data_class"] == "OFFICIAL_PUBLIC_CONTACTS"
    phones = [c["phone"] for c in h_data["contacts"]]
    assert "112" in phones
    assert "1091" in phones
    assert "1098" in phones
    assert "1930" in phones
    assert "108" in phones
    assert any("2560601" in p for p in phones)  # Police office
    assert any("2563232" in p for p in phones)  # Control room

    ncrb = client.get("/api/v1/ncrb-manifest", headers=dashboard)
    assert ncrb.status_code == 200
    n_data = ncrb.json()
    assert n_data["status"] == "SOURCE_IDENTIFIED_VALUES_NOT_IMPORTED"
    assert "data.gov.in" in n_data["source_url"]


def test_invalid_email_domain_and_unconfigured_smtp_never_sent(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("RAKSHA360_SMTP_HOST", "")
    called = []
    monkeypatch.setattr(api, "send_offline_location_email", lambda *args: called.append(args))

    device = {"Authorization": "Bearer test-device-token"}
    res = client.post("/api/v1/device/heartbeat", headers=device, json={
        "deviceId": "dev-invalid-email",
        "monitoringEnabled": True,
        "offlineLocationEmailConsent": True,
        "contacts": [{"name": "Demo", "email": "test@example.invalid", "notifyByEmail": True, "isActive": True}],
    })
    assert res.status_code == 200
    assert called == []


def test_production_startup_rejection_for_default_or_missing_tokens():
    # Production with default device token must raise RuntimeError
    with pytest.raises(RuntimeError) as exc_info:
        api.validate_environment_configuration(
            env_name="production",
            device_tok=api.KNOWN_DEV_DEVICE_TOKEN,
            dash_tok="secure-custom-dashboard-token-12345",
            cors_raw="https://raksha360.vercel.app",
        )
    assert "RAKSHA360_DEVICE_TOKEN" in str(exc_info.value)
    assert "secure-custom-dashboard-token" not in str(exc_info.value)  # Credential value never disclosed

    # Production with default dashboard token must raise RuntimeError
    with pytest.raises(RuntimeError) as exc_info:
        api.validate_environment_configuration(
            env_name="production",
            device_tok="secure-custom-device-token-12345",
            dash_tok=api.KNOWN_DEV_DASHBOARD_TOKEN,
            cors_raw="https://raksha360.vercel.app",
        )
    assert "RAKSHA360_DASHBOARD_TOKEN" in str(exc_info.value)
    assert "secure-custom-device-token" not in str(exc_info.value)

    # Production with empty tokens must raise RuntimeError
    with pytest.raises(RuntimeError) as exc_info:
        api.validate_environment_configuration(
            env_name="production",
            device_tok="",
            dash_tok="",
            cors_raw="https://raksha360.vercel.app",
        )
    assert "Production configuration error" in str(exc_info.value)

    # Production with identical device and dashboard tokens must raise RuntimeError
    with pytest.raises(RuntimeError) as exc_info:
        api.validate_environment_configuration(
            env_name="production",
            device_tok="identical-secret-shared-between-both",
            dash_tok="identical-secret-shared-between-both",
            cors_raw="https://raksha360.vercel.app",
        )
    assert "must be distinct" in str(exc_info.value)
    assert "identical-secret" not in str(exc_info.value)


def test_production_startup_rejection_for_wildcard_or_missing_cors():
    # Production with missing CORS origins must raise RuntimeError
    with pytest.raises(RuntimeError) as exc_info:
        api.validate_environment_configuration(
            env_name="production",
            device_tok="secure-prod-device-token-xyz-987",
            dash_tok="secure-prod-dashboard-token-abc-456",
            cors_raw="",
        )
    assert "RAKSHA360_CORS_ORIGINS" in str(exc_info.value)

    # Production with wildcard '*' CORS origin must raise RuntimeError
    with pytest.raises(RuntimeError) as exc_info:
        api.validate_environment_configuration(
            env_name="production",
            device_tok="secure-prod-device-token-xyz-987",
            dash_tok="secure-prod-dashboard-token-abc-456",
            cors_raw="*",
        )
    assert "Wildcard '*'" in str(exc_info.value)


def test_production_configuration_valid_with_distinct_tokens():
    # Production with valid, distinct, non-default tokens, valid CORS, and valid DATABASE_URL should not raise
    api.validate_environment_configuration(
        env_name="production",
        device_tok="secure-prod-device-token-xyz-987",
        dash_tok="secure-prod-dashboard-token-abc-456",
        cors_raw="https://raksha360-policedashboard.vercel.app",
        db_url_raw="postgresql://postgres:secret@aws-0.pooler.supabase.com:5432/postgres",
    )


def test_development_configuration_permits_dev_tokens():
    # Development environment explicitly allows development tokens
    api.validate_environment_configuration(
        env_name="development",
        device_tok=api.KNOWN_DEV_DEVICE_TOKEN,
        dash_tok=api.KNOWN_DEV_DASHBOARD_TOKEN,
    )


def test_authentication_rejection_and_success(client: TestClient):
    # Rejected device bearer token
    res = client.post("/api/v1/device/session", headers={"Authorization": "Bearer invalid-wrong-token"})
    assert res.status_code == 401
    assert "valid Raksha360 device bearer token" in res.json()["detail"]

    # Rejected dashboard session
    res = client.post("/api/v1/dashboard/session", json={"token": "invalid-dashboard-token"})
    assert res.status_code == 401
    assert "Invalid dashboard token" in res.json()["detail"]

    # Rejected dashboard REST route with wrong header
    res = client.get("/api/v1/incidents", headers={"X-Dashboard-Token": "invalid-dashboard-token"})
    assert res.status_code == 401

    # Successful dashboard session
    res = client.post("/api/v1/dashboard/session", json={"token": "test-dashboard-token"})
    assert res.status_code == 200
    assert res.json()["authenticated"] is True

    # Successful device session
    res = client.post("/api/v1/device/session", headers={"Authorization": "Bearer test-device-token"})
    assert res.status_code == 200
    assert res.json()["authenticated"] is True


def test_websocket_frame_authentication_without_query_token(client: TestClient):
    # Connect without query param, authenticate via initial frame
    with client.websocket_connect("/ws/police") as websocket:
        websocket.send_json({"type": "AUTH", "token": "test-dashboard-token"})
        connected = websocket.receive_json()
        assert connected["type"] in ("AUTHENTICATED", "CONNECTED")


def test_websocket_frame_authentication_failure_invalid_token(client: TestClient):
    from starlette.websockets import WebSocketDisconnect
    with pytest.raises(WebSocketDisconnect) as exc_info:
        with client.websocket_connect("/ws/police") as websocket:
            websocket.send_json({"type": "AUTH", "token": "invalid-wrong-token"})
            websocket.receive_json()
    assert exc_info.value.code == 1008


def test_websocket_access_before_authentication_rejected(client: TestClient):
    from starlette.websockets import WebSocketDisconnect
    with pytest.raises(WebSocketDisconnect) as exc_info:
        with client.websocket_connect("/ws/police") as websocket:
            websocket.send_json({"type": "PING", "message": "unauthorized"})
            websocket.receive_json()
    assert exc_info.value.code == 1008


def test_websocket_production_rejects_query_token(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    from starlette.websockets import WebSocketDisconnect
    monkeypatch.setenv("RAKSHA360_ENV", "production")
    with pytest.raises(WebSocketDisconnect) as exc_info:
        with client.websocket_connect("/ws/police?token=test-dashboard-token") as websocket:
            websocket.receive_json()
    assert exc_info.value.code == 1008


def test_fixture_cleanup_preserves_operational_records_with_demo_session(tmp_path: Path):
    import scripts.import_raksha360_fixtures as fixture_importer

    test_db = tmp_path / "test-cleanup.sqlite3"
    conn = fixture_importer.get_connection(test_db)
    fixture_importer.ensure_schema(conn, test_db)

    # 1. Seed demo fixtures (all marked is_demo = 1)
    seeded = fixture_importer.import_demo_fixtures(conn)
    assert seeded["incidents"] > 0

    # 2. Insert a real operational incident (is_demo = 0) that happens to reference a demo session id
    # (like the offline evaluation incident OFFLINE-CD825E3545-1791724506522)
    now_ms = fixture_importer.utc_ms()
    conn.execute(
        """INSERT INTO incidents (
            event_id, reported_at, updated_at, trigger_type, severity, status, latitude, longitude,
            accuracy_meters, location_description, device_session_id, is_demo, details_json
        ) VALUES (
            'OFFLINE-CD825E3545-1791724506522', ?, ?, 'DEVICE_OFFLINE', 'High', 'New',
            21.1458, 79.0882, 10.0, 'Operational location', 'DEMO-DEVICE-ONLINE-001', 0, '{"note": "Real alert"}'
        )""",
        (now_ms, now_ms),
    )

    # Also insert an operational device record
    conn.execute(
        """INSERT OR REPLACE INTO device_status (
            device_id, last_seen_at, last_latitude, last_longitude, last_accuracy_meters,
            last_location_at, last_battery_pct, monitoring_enabled, offline_location_email_consent,
            status, created_at, updated_at
        ) VALUES (
            'DEMO-DEVICE-ONLINE-001', ?, 21.1458, 79.0882, 10.0, ?, 85, 1, 1, 'ARMED', ?, ?
        )""",
        (now_ms, now_ms, now_ms, now_ms),
    )
    conn.commit()

    # 3. Execute cleanup
    deleted = fixture_importer.clear_demo_fixtures(conn, dry_run=False)

    # 4. Verify that demo incidents were deleted
    remaining_demo_incidents = conn.execute("SELECT COUNT(*) FROM incidents WHERE is_demo = 1").fetchone()[0]
    assert remaining_demo_incidents == 0

    # 5. Verify that operational incident with is_demo = 0 was strictly preserved
    operational_incident = conn.execute(
        "SELECT event_id, device_session_id, is_demo FROM incidents WHERE event_id = 'OFFLINE-CD825E3545-1791724506522'"
    ).fetchone()
    assert operational_incident is not None
    assert operational_incident["event_id"] == "OFFLINE-CD825E3545-1791724506522"
    assert operational_incident["device_session_id"] == "DEMO-DEVICE-ONLINE-001"
    assert operational_incident["is_demo"] == 0

    # 6. Verify that the device linked to this operational incident was also preserved
    preserved_device = conn.execute(
        "SELECT device_id FROM device_status WHERE device_id = 'DEMO-DEVICE-ONLINE-001'"
    ).fetchone()
    assert preserved_device is not None

    conn.close()


def test_fresh_database_initialization_seeds_reference_data_without_demo_fixtures(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    fresh_db = tmp_path / "fresh_test.sqlite3"
    monkeypatch.setattr(api, "DB_PATH", fresh_db)
    monkeypatch.setenv("RAKSHA360_SEED_DEMO_DATA", "false")

    api.initialize_database()

    with api.db() as conn:
        # Reference data exists
        dir_count = conn.execute("SELECT COUNT(*) FROM police_station_directory").fetchone()[0]
        assert dir_count == 37

        addr_count = conn.execute("SELECT COUNT(*) FROM police_station_directory WHERE coordinate_status = 'OFFICIAL_ADDRESS_ONLY'").fetchone()[0]
        assert addr_count == 37

        verified_count = conn.execute("SELECT COUNT(*) FROM police_station_directory WHERE coordinates_verified = 1").fetchone()[0]
        assert verified_count == 0

        # Pure official public directory snapshot contains no operational coordinates
        assert conn.execute("SELECT COUNT(*) FROM police_station_directory WHERE latitude IS NOT NULL OR longitude IS NOT NULL").fetchone()[0] == 0

        # Operational tables are completely clean
        assert conn.execute("SELECT COUNT(*) FROM incidents").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM reports").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM dispatches").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM stations").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM audit_log").fetchone()[0] == 0


def test_cors_preflight_and_response_headers(client: TestClient):
    # Origin from default allowed origins (e.g. localhost)
    res = client.get("/api/v1/health", headers={"Origin": "http://localhost:5173"})
    assert res.status_code == 200
    assert res.headers.get("access-control-allow-origin") == "http://localhost:5173"

    # Preflight OPTIONS request
    opt = client.options("/api/v1/health", headers={
        "Origin": "http://localhost:5173",
        "Access-Control-Request-Method": "GET",
    })
    assert opt.status_code == 200
    assert opt.headers.get("access-control-allow-origin") == "http://localhost:5173"


def test_production_startup_rejection_for_invalid_or_missing_database_url():
    # Production with empty DATABASE_URL when required must raise RuntimeError
    with pytest.raises(RuntimeError) as exc_info:
        api.validate_environment_configuration(
            env_name="production",
            device_tok="secure-prod-device-token-xyz-987",
            dash_tok="secure-prod-dashboard-token-abc-456",
            cors_raw="https://raksha360-policedashboard.vercel.app",
            db_url_raw="",
        )
    assert "DATABASE_URL" in str(exc_info.value)

    # Production with non-PostgreSQL DATABASE_URL must raise RuntimeError
    with pytest.raises(RuntimeError) as exc_info:
        api.validate_environment_configuration(
            env_name="production",
            device_tok="secure-prod-device-token-xyz-987",
            dash_tok="secure-prod-dashboard-token-abc-456",
            cors_raw="https://raksha360-policedashboard.vercel.app",
            db_url_raw="mysql://user:pass@localhost:3306/db",
        )
    assert "PostgreSQL connection string" in str(exc_info.value)


def test_production_startup_success_with_postgres_database_url():
    # Production with valid PostgreSQL / Supabase pooler URL must pass validation
    api.validate_environment_configuration(
        env_name="production",
        device_tok="secure-prod-device-token-xyz-987",
        dash_tok="secure-prod-dashboard-token-abc-456",
        cors_raw="https://raksha360-policedashboard.vercel.app",
        db_url_raw="postgresql://postgres.testproject:mock-secret@aws-0-ap-south-1.pooler.supabase.com:5432/postgres",
    )


def test_database_row_compatibility_and_conversions():
    from backend.app.db import DatabaseRow, convert_query_to_postgres

    # DatabaseRow acts as dictionary, supports integer indexing, and dict() conversion
    row = DatabaseRow({"id": "INC-001", "status": "New", "count": 10})
    assert row["id"] == "INC-001"
    assert row["status"] == "New"
    assert row.get("count") == 10
    assert row[0] == "INC-001"
    assert row[1] == "New"
    assert row[2] == 10
    assert dict(row) == {"id": "INC-001", "status": "New", "count": 10}

    # Query converter translates ? to %s and case-insensitive collations
    q1 = "SELECT * FROM incidents WHERE event_id = ? AND is_demo = ?"
    assert convert_query_to_postgres(q1) == "SELECT * FROM incidents WHERE event_id = %s AND is_demo = %s"

    q2 = "SELECT * FROM police_station_directory ORDER BY name COLLATE NOCASE"
    assert convert_query_to_postgres(q2) == "SELECT * FROM police_station_directory ORDER BY LOWER(name)"


def test_healthcheck_reports_active_database_engine(client: TestClient):
    res = client.get("/api/v1/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ONLINE"
    assert data["database"] in ("sqlite", "postgresql")
    assert "incidentRecords" in data


def test_healthcheck_returns_503_on_simulated_database_failure(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    def failing_db():
        raise sqlite3.OperationalError("Simulated database failure: /var/data/private.db")

    monkeypatch.setattr(api, "db", failing_db)
    res = client.get("/api/v1/health")
    assert res.status_code == 503
    data = res.json()
    assert data["status"] == "UNAVAILABLE"
    assert data["error"] == "Database connectivity check failed."
    # Ensure no internal error strings or database paths are exposed in response
    assert "Simulated database failure" not in str(data)
    assert "private.db" not in str(data)


def test_health_endpoint_requires_no_authentication(client: TestClient):
    """Render health checker polls /api/v1/health without Authorization headers."""
    res = client.get("/api/v1/health")
    assert res.status_code == 200


def test_production_startup_fails_when_database_url_is_missing_regardless_of_optional_flags(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.delenv("RAKSHA360_REQUIRE_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    with pytest.raises(RuntimeError) as exc_info:
        api.validate_environment_configuration(
            env_name="production",
            device_tok="secure-prod-device-token-xyz-987",
            dash_tok="secure-prod-dashboard-token-abc-456",
            cors_raw="https://raksha360-policedashboard.vercel.app",
        )
    assert "DATABASE_URL" in str(exc_info.value)


def test_station_upsert_preserves_verified_coordinates_on_portable_syntax():
    # Simulate a human-verified coordinate in Nagpur
    with api.db() as conn:
        conn.execute(
            """UPDATE police_station_directory
               SET latitude = 21.1458, longitude = 79.0882, coordinates_verified = 1,
                   coordinate_source = 'HUMAN_VERIFIED_REVIEW'
               WHERE station_id = 'NGP-SITABULDI'"""
        )

    # Re-running initialize_database must preserve the verified status and coordinates
    api.initialize_database()

    with api.db() as conn:
        row = conn.execute("SELECT * FROM police_station_directory WHERE station_id = 'NGP-SITABULDI'").fetchone()
        assert row["coordinates_verified"] == 1 or row["coordinates_verified"] is True
        assert row["latitude"] == 21.1458
        assert row["longitude"] == 79.0882


def test_google_auth_successful_verification(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    mock_claims = {
        "iss": "https://accounts.google.com",
        "sub": "google-uid-1002938475",
        "email": "sarah.safety@example.com",
        "name": "Sarah Safety",
        "picture": "https://lh3.googleusercontent.com/a/photo.jpg",
        "aud": "mock-web-client-id.apps.googleusercontent.com",
    }
    monkeypatch.setattr(api.google_id_token, "verify_oauth2_token", lambda *args, **kwargs: mock_claims)

    resp = client.post("/api/v1/auth/google", json={"idToken": "valid-synthetic-jwt-token-for-testing"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["authenticated"] is True
    assert data["uid"] == "google-uid-1002938475"
    assert data["email"] == "sarah.safety@example.com"
    assert data["displayName"] == "Sarah Safety"
    assert data["photoUrl"] == "https://lh3.googleusercontent.com/a/photo.jpg"
    assert data["role"] == "CITIZEN_USER"
    assert data["role"] == "CITIZEN_USER"
    assert data["sessionToken"].startswith("rk_")
    assert data["expiresAt"] > 1700000000000

    # Verify that the database stores ONLY the SHA-256 hash and NEVER the raw session token
    import hashlib
    expected_hash = hashlib.sha256(data["sessionToken"].encode("utf-8")).hexdigest()
    with api.db() as conn:
        row = conn.execute("SELECT * FROM user_sessions WHERE token_hash = ?", (expected_hash,)).fetchone()
        assert row is not None
        assert row["token_hash"] == expected_hash
        assert row["uid"] == "google-uid-1002938475"
        assert row["is_active"] == 1
        # Raw session token must NOT exist in the database!
        raw_match = conn.execute("SELECT COUNT(*) FROM user_sessions WHERE token_hash = ?", (data["sessionToken"],)).fetchone()[0]
        assert raw_match == 0

    # Verify session is retrievable via GET /api/v1/auth/session
    session_resp = client.get("/api/v1/auth/session", headers={"Authorization": f"Bearer {data['sessionToken']}"})
    assert session_resp.status_code == 200
    session_data = session_resp.json()
    assert session_data["email"] == "sarah.safety@example.com"
    assert session_data["displayName"] == "Sarah Safety"

    # Verify signout invalidates session
    signout_resp = client.post("/api/v1/auth/signout", headers={"Authorization": f"Bearer {data['sessionToken']}"})
    assert signout_resp.status_code == 200
    assert signout_resp.json()["signedOut"] is True

    # Confirm database row is_active is now 0
    with api.db() as conn:
        inactive_row = conn.execute("SELECT * FROM user_sessions WHERE token_hash = ?", (expected_hash,)).fetchone()
        assert inactive_row["is_active"] == 0

    # Re-checking session after signout must yield 401
    after_signout = client.get("/api/v1/auth/session", headers={"Authorization": f"Bearer {data['sessionToken']}"})
    assert after_signout.status_code == 401


def test_session_token_tampered_or_invalid_bearer_rejection(client: TestClient):
    # Calling auth/session with a non-existent or tampered token returns 401
    resp = client.get("/api/v1/auth/session", headers={"Authorization": "Bearer rk_fake_tampered_token_xyz"})
    assert resp.status_code == 401
    assert "Session token is invalid or expired" in resp.json()["detail"]


def test_session_token_expired_rejection(client: TestClient):
    import hashlib
    raw_token = "rk_expired_test_token_12345"
    token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
    now = api.utc_ms()
    past_expiry = now - 60_000  # Expired 1 minute ago

    with api.db() as conn:
        conn.execute(
            """INSERT INTO users (uid, email, display_name, photo_url, role, created_at, last_login_at)
               VALUES ('expired_uid_99', 'expired@test.com', 'Expired Tester', NULL, 'CITIZEN_USER', ?, ?)""",
            (now, now),
        )
        conn.execute(
            """INSERT INTO user_sessions (token_hash, uid, created_at, expires_at, is_active)
               VALUES (?, 'expired_uid_99', ?, ?, 1)""",
            (token_hash, now - 120_000, past_expiry),
        )

    # Calling auth/session with the expired raw token must be rejected with 401
    resp = client.get("/api/v1/auth/session", headers={"Authorization": f"Bearer {raw_token}"})
    assert resp.status_code == 401
    assert "Session token is invalid or expired" in resp.json()["detail"]


def test_google_auth_invalid_token_rejection(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    def mock_fail(*args, **kwargs):
        raise ValueError("Invalid signature on token")

    monkeypatch.setattr(api.google_id_token, "verify_oauth2_token", mock_fail)

    resp = client.post("/api/v1/auth/google", json={"idToken": "tampered-or-invalid-token"})
    assert resp.status_code == 401
    assert "Invalid signature" in resp.json()["detail"]


def test_google_auth_expired_token_rejection(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    def mock_expired(*args, **kwargs):
        raise ValueError("Token expired")

    monkeypatch.setattr(api.google_id_token, "verify_oauth2_token", mock_expired)

    resp = client.post("/api/v1/auth/google", json={"idToken": "expired-jwt-token"})
    assert resp.status_code == 401
    assert "Token expired" in resp.json()["detail"]


def test_google_auth_invalid_issuer_rejection(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    mock_bad_issuer = {
        "iss": "https://untrusted-auth-server.bad",
        "sub": "bad-user-id",
        "email": "hacker@evil.com",
    }
    monkeypatch.setattr(api.google_id_token, "verify_oauth2_token", lambda *args, **kwargs: mock_bad_issuer)

    resp = client.post("/api/v1/auth/google", json={"idToken": "token-from-wrong-issuer"})
    assert resp.status_code == 401
    assert "Invalid token issuer" in resp.json()["detail"]


def test_google_auth_service_unavailable(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    def mock_network_err(*args, **kwargs):
        raise ConnectionError("Google certs server unreachable")

    monkeypatch.setattr(api.google_id_token, "verify_oauth2_token", mock_network_err)

    resp = client.post("/api/v1/auth/google", json={"idToken": "any-token-while-offline"})
    assert resp.status_code == 503
    assert "Unable to verify token" in resp.json()["detail"]


def test_google_auth_missing_client_id_configuration(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(api, "get_expected_google_client_id", lambda: None)
    resp = client.post("/api/v1/auth/google", json={"idToken": "some-id-token"})
    assert resp.status_code == 500
    assert "Server Google OAuth audience is not configured" in resp.json()["detail"]


def test_authenticated_user_session_authorizes_device_endpoints(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    """Verifies that an authenticated citizen user session token authorizes device calls
    without requiring manual entry of a static device token."""
    mock_claims = {
        "iss": "https://accounts.google.com",
        "sub": "user_device_auth_sub_123",
        "email": "user.device@example.com",
        "name": "User Device Tester",
        "aud": "mock-web-client-id.apps.googleusercontent.com",
    }
    monkeypatch.setattr(api.google_id_token, "verify_oauth2_token", lambda *args, **kwargs: mock_claims)
    monkeypatch.setattr(api, "get_expected_google_client_id", lambda: "mock-web-client-id.apps.googleusercontent.com")

    # 1. User signs in with Google
    auth_resp = client.post("/api/v1/auth/google", json={"idToken": "fake-jwt-token"})
    assert auth_resp.status_code == 200
    session_token = auth_resp.json()["sessionToken"]
    user_headers = {"Authorization": f"Bearer {session_token}"}

    # 2. Check /device/session with user session token
    dev_session_resp = client.post("/api/v1/device/session", headers=user_headers)
    assert dev_session_resp.status_code == 200
    assert dev_session_resp.json()["authenticated"] is True

    # 3. Post emergency event with user session token
    event_payload = {
        "eventId": f"USER-EVENT-{uuid.uuid4()}",
        "triggerType": "MANUAL_SOS",
        "latitude": 21.1458,
        "longitude": 79.0882,
        "isDemoMode": False,
    }
    event_resp = client.post("/api/v1/emergency-events", headers=user_headers, json=event_payload)
    assert event_resp.status_code == 200
    assert event_resp.json()["status"] == "RECEIVED_IN_CONTROL_ROOM_QUEUE"

    # 4. Post device heartbeat with user session token
    heartbeat_payload = {
        "deviceId": "dev-phone-99",
        "timestamp": api.utc_ms(),
        "monitoringEnabled": True,
        "offlineLocationEmailConsent": False,
        "latitude": 21.1458,
        "longitude": 79.0882,
        "contacts": [],
    }
    heartbeat_resp = client.post("/api/v1/device/heartbeat", headers=user_headers, json=heartbeat_payload)
    assert heartbeat_resp.status_code == 200
    assert heartbeat_resp.json()["status"] == "ONLINE"

    # 5. Post incident report with user session token
    report_payload = {
        "title": "Street Harassment",
        "category": "Harassment",
        "description": "Report submitted via citizen session",
        "isDemoMode": False,
    }
    report_resp = client.post("/api/v1/reports", headers=user_headers, json=report_payload)
    assert report_resp.status_code == 200
    assert report_resp.json()["status"] == "New"


def test_device_endpoints_reject_invalid_and_expired_user_sessions(client: TestClient):
    # Missing auth
    no_auth = client.post("/api/v1/device/session")
    assert no_auth.status_code == 401

    # Tampered bearer
    tampered = client.post("/api/v1/device/session", headers={"Authorization": "Bearer rk_tampered_bearer_token"})
    assert tampered.status_code == 401
    assert "A valid Raksha360 device bearer token or authenticated user session is required" in tampered.json()["detail"]

    # Expired session
    now = api.utc_ms()
    raw_exp = "rk_expired_device_token"
    token_hash = api.hash_session_token(raw_exp)
    with api.db() as conn:
        conn.execute(
            """INSERT INTO users (uid, email, display_name, photo_url, role, created_at, last_login_at)
               VALUES ('exp_uid_device', 'exp_device@test.com', 'Exp Device', NULL, 'CITIZEN_USER', ?, ?)""",
            (now, now),
        )
        conn.execute(
            """INSERT INTO user_sessions (token_hash, uid, created_at, expires_at, is_active)
               VALUES (?, 'exp_uid_device', ?, ?, 1)""",
            (token_hash, now - 60_000, now - 10_000),
        )

    expired_resp = client.post("/api/v1/device/session", headers={"Authorization": f"Bearer {raw_exp}"})
    assert expired_resp.status_code == 401





