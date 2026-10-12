"""
Real PostgreSQL Integration Test Suite for Raksha360.
Executes only against an explicitly approved, disposable PostgreSQL test database.
Guarantees schema isolation across all application pooled connections and ensures
cleanup affects only objects created in the isolated test schema.

To execute this test suite against an actual disposable PostgreSQL instance:
  export RAKSHA360_ALLOW_DESTRUCTIVE_TESTING="true"
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

ISOLATED_TEST_SCHEMA = "test_raksha360_isolated"
TEST_PG_URL = os.getenv("RAKSHA360_TEST_DATABASE_URL", os.getenv("TEST_DATABASE_URL", "")).strip()


def assert_disposable_test_database(url: str) -> None:
    """Safety guard: asserts that the configured test database is explicitly approved for destructive testing.
    Never relies on string matching alone.
    """
    if not url or not url.strip():
        raise ValueError("No test database URL provided.")

    # 1. Require explicit approval via dedicated environment variable.
    # String matching alone must NEVER be used as proof that a database is disposable.
    allow_destructive = os.getenv("RAKSHA360_ALLOW_DESTRUCTIVE_TESTING", "").strip().lower()
    if allow_destructive not in ("true", "1", "yes"):
        raise RuntimeError(
            "SAFETY ABORT: Destructive testing is not approved! "
            "To run PostgreSQL integration tests, set RAKSHA360_ALLOW_DESTRUCTIVE_TESTING=true "
            "along with a dedicated disposable database URL (RAKSHA360_TEST_DATABASE_URL)."
        )

    # 2. Refuse execution if environment is tagged as production or staging
    env = os.getenv("RAKSHA360_ENV", "").strip().lower()
    if env in ("production", "prod", "staging"):
        raise RuntimeError(f"SAFETY ABORT: RAKSHA360_ENV is set to '{env}'. Integration tests aborted!")

    # 3. Guard against using ambient production/demonstration DATABASE_URL
    ambient_db_url = os.getenv("DATABASE_URL", "").strip()
    if ambient_db_url and not os.getenv("RAKSHA360_TEST_DATABASE_URL"):
        raise RuntimeError(
            "SAFETY ABORT: Ambient DATABASE_URL detected without explicit RAKSHA360_TEST_DATABASE_URL. "
            "Tests must point to an explicitly approved disposable database target."
        )

    # 4. Refuse if URL points to known production endpoints or contains production identifiers
    lower = url.lower()
    if "production" in lower or ("prod" in lower and "test" not in lower):
        raise RuntimeError("SAFETY ABORT: Database URL appears to target a production environment!")


pytestmark = pytest.mark.skipif(
    not TEST_PG_URL or os.getenv("RAKSHA360_ALLOW_DESTRUCTIVE_TESTING", "").strip().lower() not in ("true", "1", "yes"),
    reason="Real PostgreSQL integration test requires disposable RAKSHA360_TEST_DATABASE_URL and RAKSHA360_ALLOW_DESTRUCTIVE_TESTING=true."
)


@pytest.fixture(scope="module")
def real_postgres_database():
    assert_disposable_test_database(TEST_PG_URL)

    import psycopg
    conn_url = TEST_PG_URL
    if conn_url.startswith("postgres://"):
        conn_url = "postgresql://" + conn_url[len("postgres://"):]

    # Clean and initialize disposable test schema on an admin connection
    admin_conn = psycopg.connect(conn_url, autocommit=True)
    admin_cur = admin_conn.cursor()
    admin_cur.execute(f'DROP SCHEMA IF EXISTS "{ISOLATED_TEST_SCHEMA}" CASCADE;')
    admin_cur.execute(f'CREATE SCHEMA "{ISOLATED_TEST_SCHEMA}";')
    admin_cur.execute(f'SET search_path TO "{ISOLATED_TEST_SCHEMA}", public;')

    # Run initial schema migration inside the isolated test schema
    migration_file = Path(__file__).resolve().parents[1] / "app" / "migrations" / "001_initial_schema.sql"
    admin_cur.execute(migration_file.read_text(encoding="utf-8"))
    admin_conn.close()

    # Configure application environment for this test session
    os.environ["DATABASE_URL"] = conn_url
    os.environ["RAKSHA360_PG_SCHEMA"] = ISOLATED_TEST_SCHEMA
    os.environ["RAKSHA360_SEED_DEMO_DATA"] = "false"
    close_db_pool()

    # Requirement 4: Verify from an application-created pooled connection that the
    # current schema is actually the intended test schema before running any test.
    with api.db() as pooled_conn:
        current_schema = pooled_conn.execute("SELECT current_schema()").fetchone()[0]
        if current_schema != ISOLATED_TEST_SCHEMA:
            close_db_pool()
            raise RuntimeError(
                f"SCHEMA ISOLATION CHECK FAILED: Application pooled connection target is '{current_schema}', "
                f"expected '{ISOLATED_TEST_SCHEMA}'. Test execution aborted!"
            )

    yield conn_url

    # Teardown: Clean up ONLY the isolated schema created by this fixture.
    close_db_pool()
    cleanup_conn = psycopg.connect(conn_url, autocommit=True)
    cleanup_cur = cleanup_conn.cursor()
    cleanup_cur.execute(f'DROP SCHEMA IF EXISTS "{ISOLATED_TEST_SCHEMA}" CASCADE;')
    cleanup_conn.close()


def test_real_postgres_initialization_and_provenance(real_postgres_database):
    """Verifies that schema initialization and public directory seeding succeed within the isolated schema."""
    with api.db() as conn:
        current_schema = conn.execute("SELECT current_schema()").fetchone()[0]
        assert current_schema == ISOLATED_TEST_SCHEMA

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
        current_schema = conn.execute("SELECT current_schema()").fetchone()[0]
        assert current_schema == ISOLATED_TEST_SCHEMA
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
    """Verifies incident insertion and deduplication on PostgreSQL using clearly marked non-emergency test events."""
    with api.db() as conn:
        assert conn.execute("SELECT current_schema()").fetchone()[0] == ISOLATED_TEST_SCHEMA

    device_headers = {"Authorization": "Bearer local-dev-device-token-change-me"}
    client = TestClient(api.app)

    event_payload = {
        "eventId": "TEST-DISPOSABLE-EVENT-001",
        "triggerType": "DEMO_TEST_DRILL",
        "latitude": 21.1466,
        "longitude": 79.0833,
        "locationDescription": "AUTOMATED_TEST_FIXTURE · DO NOT DISPATCH",
        "isDemoMode": True,
    }

    # First event
    res1 = client.post("/api/v1/emergency-events", headers=device_headers, json=event_payload)
    assert res1.status_code == 200
    assert res1.json()["eventId"] == "TEST-DISPOSABLE-EVENT-001"

    # Duplicate submission should be idempotent
    res2 = client.post("/api/v1/emergency-events", headers=device_headers, json=event_payload)
    assert res2.status_code == 200
    assert res2.json()["status"] in ("New", "Acknowledged", "RECEIVED_DEMO_EVENT")

    with api.db() as conn:
        count = conn.execute("SELECT COUNT(*) FROM incidents WHERE event_id = 'TEST-DISPOSABLE-EVENT-001'").fetchone()[0]
        assert count == 1


def test_real_postgres_transaction_rollback(real_postgres_database):
    """Verifies that an error inside with api.db() triggers an automatic transaction rollback in PostgreSQL."""
    with api.db() as conn:
        assert conn.execute("SELECT current_schema()").fetchone()[0] == ISOLATED_TEST_SCHEMA

    try:
        with api.db() as conn:
            conn.execute(
                """INSERT INTO incidents (event_id, trigger_type, reported_at, status, severity, is_demo, source, updated_at, notes)
                   VALUES ('TEST-ROLLBACK-DISPOSABLE-001', 'DEMO_TEST_DRILL', 1700000000000, 'New', 'Low', 1, 'AUTOMATED_TEST', 1700000000000, 'AUTOMATED_TEST_FIXTURE · DO NOT DISPATCH')"""
            )
            raise ValueError("Forced error to test transaction rollback")
    except ValueError:
        pass

    # Record must not exist due to rollback
    with api.db() as conn:
        row = conn.execute("SELECT * FROM incidents WHERE event_id = 'TEST-ROLLBACK-DISPOSABLE-001'").fetchone()
        assert row is None


def test_real_postgres_foreign_key_enforcement(real_postgres_database):
    """Verifies that PostgreSQL enforces foreign key constraints on dependent tables in the isolated schema."""
    with api.db() as conn:
        assert conn.execute("SELECT current_schema()").fetchone()[0] == ISOLATED_TEST_SCHEMA

    with pytest.raises(Exception) as exc_info:
        with api.db() as conn:
            # Inserting location update for non-existent incident should fail FK constraint
            conn.execute(
                """INSERT INTO location_updates (id, event_id, timestamp, latitude, longitude, created_at)
                   VALUES ('TEST-LOC-001', 'TEST-NONEXISTENT-EVENT', 1700000000000, 21.1, 79.1, 1700000000000)"""
            )
    assert "foreign key" in str(exc_info.value).lower() or "violates foreign key constraint" in str(exc_info.value).lower()


def test_real_postgres_health_endpoint_reports_postgresql(real_postgres_database):
    """Verifies that /api/v1/health reports database engine as postgresql when connected to real Postgres."""
    with api.db() as conn:
        assert conn.execute("SELECT current_schema()").fetchone()[0] == ISOLATED_TEST_SCHEMA

    client = TestClient(api.app)
    res = client.get("/api/v1/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ONLINE"
    assert data["database"] == "postgresql"
