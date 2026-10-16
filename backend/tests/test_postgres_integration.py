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
    assert "incidentRecords" in data


def test_real_postgres_health_endpoint_returns_503_on_simulated_db_failure(real_postgres_database, monkeypatch: pytest.MonkeyPatch):
    """Verifies that /api/v1/health returns HTTP 503 on database connectivity failure without leaking credentials."""
    def broken_postgres_db():
        raise RuntimeError("FATAL: connection to server at 'aws-0-oregon.pooler.supabase.com', port 5432 failed: password authentication failed")

    monkeypatch.setattr(api, "db", broken_postgres_db)
    client = TestClient(api.app)
    res = client.get("/api/v1/health")
    assert res.status_code == 503
    data = res.json()
    assert data["status"] == "UNAVAILABLE"
    assert data["database"] == "postgresql"
    assert data["error"] == "Database connectivity check failed."
    # Ensure sensitive connection details, credentials, or hostnames are never exposed in response
    assert "password" not in str(data).lower()
    assert "supabase" not in str(data).lower()
    assert "5432" not in str(data)


def test_real_postgres_google_auth_and_hashed_session_lifecycle(real_postgres_database, monkeypatch: pytest.MonkeyPatch):
    """Verifies complete Google authentication lifecycle on real PostgreSQL:
    - User upsert into public.users
    - Storage of ONLY SHA-256 hashed session token in public.user_sessions (never plaintext)
    - Session resolution with raw bearer token
    - Signout revokes session (is_active = 0)
    - Subsequent resolution fails with HTTP 401
    """
    mock_claims = {
        "iss": "https://accounts.google.com",
        "sub": "pg-google-uid-88392019",
        "email": "postgres.safety.user@example.com",
        "name": "Postgres Safety User",
        "picture": "https://lh3.googleusercontent.com/a/pg_photo.jpg",
        "aud": "mock-web-client-id.apps.googleusercontent.com",
    }
    monkeypatch.setattr(api.google_id_token, "verify_oauth2_token", lambda *args, **kwargs: mock_claims)
    monkeypatch.setattr(api, "get_expected_google_client_id", lambda: "mock-web-client-id.apps.googleusercontent.com")

    client = TestClient(api.app)
    resp = client.post("/api/v1/auth/google", json={"idToken": "valid-pg-jwt-token"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["authenticated"] is True
    assert data["uid"] == "pg-google-uid-88392019"
    assert data["email"] == "postgres.safety.user@example.com"
    raw_token = data["sessionToken"]
    assert raw_token.startswith("rk_")

    import hashlib
    expected_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()

    # Verify on PostgreSQL that token_hash is stored and raw_token is NOT stored
    with api.db() as conn:
        session_row = conn.execute("SELECT * FROM user_sessions WHERE token_hash = ?", (expected_hash,)).fetchone()
        assert session_row is not None
        assert session_row["token_hash"] == expected_hash
        assert session_row["uid"] == "pg-google-uid-88392019"
        assert session_row["is_active"] == 1

        raw_in_db = conn.execute("SELECT COUNT(*) FROM user_sessions WHERE token_hash = ?", (raw_token,)).fetchone()[0]
        assert raw_in_db == 0

        user_row = conn.execute("SELECT * FROM users WHERE uid = ?", ("pg-google-uid-88392019",)).fetchone()
        assert user_row is not None
        assert user_row["email"] == "postgres.safety.user@example.com"
        assert user_row["role"] == "CITIZEN_USER"

    # Verify session lookup with Bearer header
    session_resp = client.get("/api/v1/auth/session", headers={"Authorization": f"Bearer {raw_token}"})
    assert session_resp.status_code == 200
    assert session_resp.json()["email"] == "postgres.safety.user@example.com"

    # Verify signout invalidates the session
    signout_resp = client.post("/api/v1/auth/signout", headers={"Authorization": f"Bearer {raw_token}"})
    assert signout_resp.status_code == 200
    assert signout_resp.json()["signedOut"] is True

    # Re-verify session lookup returns 401
    post_signout = client.get("/api/v1/auth/session", headers={"Authorization": f"Bearer {raw_token}"})
    assert post_signout.status_code == 401


def test_real_postgres_session_expiration_rejection(real_postgres_database):
    """Verifies that expired sessions on PostgreSQL are rejected with HTTP 401."""
    import hashlib
    client = TestClient(api.app)
    raw_expired = "rk_expired_pg_session_token"
    token_hash = hashlib.sha256(raw_expired.encode("utf-8")).hexdigest()
    now = api.utc_ms()
    past_expiry = now - 30_000

    with api.db() as conn:
        conn.execute(
            """INSERT INTO users (uid, email, display_name, photo_url, role, created_at, last_login_at)
               VALUES ('pg_exp_uid', 'expired_pg@test.com', 'Expired PG User', NULL, 'CITIZEN_USER', ?, ?)""",
            (now, now),
        )
        conn.execute(
            """INSERT INTO user_sessions (token_hash, uid, created_at, expires_at, is_active)
               VALUES (?, 'pg_exp_uid', ?, ?, 1)""",
            (token_hash, now - 60_000, past_expiry),
        )

    resp = client.get("/api/v1/auth/session", headers={"Authorization": f"Bearer {raw_expired}"})
    assert resp.status_code == 401
    assert "Session token is invalid or expired" in resp.json()["detail"]


def test_real_postgres_tampered_token_rejection(real_postgres_database):
    """Verifies that tampered or forged Bearer tokens on PostgreSQL return HTTP 401."""
    client = TestClient(api.app)
    resp = client.get("/api/v1/auth/session", headers={"Authorization": "Bearer rk_forged_pg_bearer_token"})
    assert resp.status_code == 401
    assert "Session token is invalid or expired" in resp.json()["detail"]


def test_real_postgres_user_cascade_deletion(real_postgres_database):
    """Verifies that deleting a user from public.users cascades to public.user_sessions on PostgreSQL."""
    import hashlib
    raw_token = "rk_cascade_test_token"
    token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
    now = api.utc_ms()

    with api.db() as conn:
        conn.execute(
            """INSERT INTO users (uid, email, display_name, photo_url, role, created_at, last_login_at)
               VALUES ('pg_cascade_uid', 'cascade@test.com', 'Cascade User', NULL, 'CITIZEN_USER', ?, ?)""",
            (now, now),
        )
        conn.execute(
            """INSERT INTO user_sessions (token_hash, uid, created_at, expires_at, is_active)
               VALUES (?, 'pg_cascade_uid', ?, ?, 1)""",
            (token_hash, now, now + 100_000),
        )
        # Verify session exists
        assert conn.execute("SELECT COUNT(*) FROM user_sessions WHERE token_hash = ?", (token_hash,)).fetchone()[0] == 1

        # Delete user
        conn.execute("DELETE FROM users WHERE uid = 'pg_cascade_uid'")

        # Verify session was cascade deleted
        assert conn.execute("SELECT COUNT(*) FROM user_sessions WHERE token_hash = ?", (token_hash,)).fetchone()[0] == 0

