"""
Real PostgreSQL Integration Test Suite for Raksha360.
Executes only against an explicitly configured, disposable PostgreSQL test database.
Will NOT run against an arbitrary DATABASE_URL or any database flagged as production.

To execute this test suite against a real PostgreSQL instance:
  export RAKSHA360_TEST_DATABASE_URL="postgresql://postgres:password@localhost:5432/raksha360_disposable_test"
  python -m pytest backend/tests/test_postgres_integration.py
"""

from __future__ import annotations

import os
from pathlib import Path
import pytest
from fastapi.testclient import TestClient

import backend.app.main as api
from backend.app.db import is_postgres, get_engine_name, close_db_pool

TEST_PG_URL = os.getenv("RAKSHA360_TEST_DATABASE_URL", os.getenv("TEST_DATABASE_URL", "")).strip()


def assert_disposable_test_database(url: str) -> None:
    """Safety guard: prevents running destructive migration tests against production databases."""
    lower = url.lower()
    if not lower:
        raise ValueError("No test database URL provided.")
    if "production" in lower or "prod" in lower and "test" not in lower:
        raise RuntimeError("SAFETY ABORT: Refusing to run tests against a database identified as production!")
    if os.getenv("RAKSHA360_ENV", "").strip().lower() in ("production", "prod", "staging"):
        raise RuntimeError("SAFETY ABORT: RAKSHA360_ENV is set to production. Integration tests aborted!")


pytestmark = pytest.mark.skipif(
    not TEST_PG_URL,
    reason="Real PostgreSQL integration test requires disposable RAKSHA360_TEST_DATABASE_URL to be set."
)


@pytest.fixture(scope="module")
def real_postgres_database():
    assert_disposable_test_database(TEST_PG_URL)

    import psycopg
    conn_url = TEST_PG_URL
    if conn_url.startswith("postgres://"):
        conn_url = "postgresql://" + conn_url[len("postgres://"):]

    # Clean and initialize disposable test schema
    conn = psycopg.connect(conn_url, autocommit=True)
    cur = conn.cursor()
    cur.execute("DROP SCHEMA IF EXISTS test_raksha360 CASCADE;")
    cur.execute("CREATE SCHEMA test_raksha360;")
    cur.execute("SET search_path TO test_raksha360, public;")

    # Run initial schema migration
    migration_file = Path(__file__).resolve().parents[1] / "app" / "migrations" / "001_initial_schema.sql"
    cur.execute(migration_file.read_text(encoding="utf-8"))
    conn.close()

    # Configure application environment for this test session
    os.environ["DATABASE_URL"] = conn_url
    os.environ["RAKSHA360_SEED_DEMO_DATA"] = "false"
    close_db_pool()

    yield conn_url

    # Teardown
    close_db_pool()
    cleanup_conn = psycopg.connect(conn_url, autocommit=True)
    cleanup_cur = cleanup_conn.cursor()
    cleanup_cur.execute("DROP SCHEMA IF EXISTS test_raksha360 CASCADE;")
    cleanup_conn.close()


def test_real_postgres_initialization_and_provenance(real_postgres_database):
    """Verifies that schema initialization and public directory seeding succeed on PostgreSQL."""
    api.initialize_database()

    with api.db() as conn:
        # Reference directory count
        dir_count = conn.execute("SELECT COUNT(*) FROM police_station_directory").fetchone()[0]
        assert dir_count == 37

        # Operational tables must remain completely clean
        assert conn.execute("SELECT COUNT(*) FROM incidents").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM reports").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM dispatches").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM stations").fetchone()[0] == 0


def test_real_postgres_station_upsert_preserves_verified_coordinates(real_postgres_database):
    """Verifies that the portable CASE-based upsert preserves human-verified coordinates on PostgreSQL."""
    with api.db() as conn:
        conn.execute(
            """UPDATE police_station_directory
               SET latitude = 21.1458, longitude = 79.0882, coordinates_verified = 1,
                   coordinate_source = 'HUMAN_VERIFIED_REVIEW'
               WHERE station_id = 'NGP-SITABULDI'"""
        )

    # Re-running initialize_database must NOT overwrite verified coordinates with nulls or fail on MAX()
    api.initialize_database()

    with api.db() as conn:
        row = conn.execute("SELECT * FROM police_station_directory WHERE station_id = 'NGP-SITABULDI'").fetchone()
        assert row["coordinates_verified"] == 1
        assert row["latitude"] == pytest.approx(21.1458)
        assert row["longitude"] == pytest.approx(79.0882)


def test_real_postgres_incident_creation_and_deduplication(real_postgres_database):
    """Verifies incident insertion and deduplication on PostgreSQL."""
    device_headers = {"Authorization": "Bearer local-dev-device-token-change-me"}
    client = TestClient(api.app)

    event_payload = {
        "eventId": "EVT-PG-INTEG-001",
        "triggerType": "PANIC_BUTTON",
        "latitude": 21.1466,
        "longitude": 79.0833,
        "isDemoMode": False,
    }

    # First event
    res1 = client.post("/api/v1/emergency-events", headers=device_headers, json=event_payload)
    assert res1.status_code == 200
    assert res1.json()["eventId"] == "EVT-PG-INTEG-001"

    # Duplicate submission should be idempotent
    res2 = client.post("/api/v1/emergency-events", headers=device_headers, json=event_payload)
    assert res2.status_code == 200
    assert res2.json()["status"] in ("New", "Acknowledged")

    with api.db() as conn:
        count = conn.execute("SELECT COUNT(*) FROM incidents WHERE event_id = 'EVT-PG-INTEG-001'").fetchone()[0]
        assert count == 1


def test_real_postgres_transaction_rollback(real_postgres_database):
    """Verifies that an error inside with api.db() triggers an automatic transaction rollback in PostgreSQL."""
    try:
        with api.db() as conn:
            conn.execute(
                """INSERT INTO incidents (event_id, trigger_type, reported_at, status, severity, is_demo, source, updated_at)
                   VALUES ('EVT-ROLLBACK-TEST', 'PANIC_BUTTON', 1700000000000, 'New', 'High', 0, 'TEST', 1700000000000)"""
            )
            raise ValueError("Forced error to test transaction rollback")
    except ValueError:
        pass

    # Record must not exist due to rollback
    with api.db() as conn:
        row = conn.execute("SELECT * FROM incidents WHERE event_id = 'EVT-ROLLBACK-TEST'").fetchone()
        assert row is None


def test_real_postgres_foreign_key_enforcement(real_postgres_database):
    """Verifies that PostgreSQL enforces foreign key constraints on dependent tables."""
    import psycopg

    with pytest.raises(Exception) as exc_info:
        with api.db() as conn:
            # Inserting location update for non-existent incident should fail FK constraint
            conn.execute(
                """INSERT INTO location_updates (id, event_id, timestamp, latitude, longitude, created_at)
                   VALUES ('LOC-001', 'NON-EXISTENT-INCIDENT', 1700000000000, 21.1, 79.1, 1700000000000)"""
            )
    assert "foreign key" in str(exc_info.value).lower() or "violates foreign key constraint" in str(exc_info.value).lower()


def test_real_postgres_health_endpoint_reports_postgresql(real_postgres_database):
    """Verifies that /api/v1/health reports database engine as postgresql when connected to real Postgres."""
    client = TestClient(api.app)
    res = client.get("/api/v1/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ONLINE"
    assert data["database"] == "postgresql"
