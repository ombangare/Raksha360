from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
import math
import os
import re
import secrets
import smtplib
import sqlite3
import uuid
from email.message import EmailMessage
from contextlib import asynccontextmanager, contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, HTTPException, Query, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, field_validator

ROOT_DIR = Path(__file__).resolve().parents[1]
load_dotenv(ROOT_DIR.parent / ".env")
DATA_DIR = Path(os.getenv("RAKSHA360_DATA_DIR", str(ROOT_DIR / "data"))).resolve()
DATA_DIR.mkdir(parents=True, exist_ok=True)
DB_PATH = Path(os.getenv("RAKSHA360_DATABASE_PATH", str(DATA_DIR / "raksha360.sqlite3"))).resolve()

KNOWN_DEV_DEVICE_TOKEN = "local-dev-device-token-change-me"
KNOWN_DEV_DASHBOARD_TOKEN = "local-dev-dashboard-token-change-me"

def get_configured_tokens() -> tuple[str, str]:
    """Retrieve device and dashboard tokens, respecting development defaults when permitted."""
    d_tok = os.getenv("RAKSHA360_DEVICE_TOKEN", KNOWN_DEV_DEVICE_TOKEN)
    b_tok = os.getenv("RAKSHA360_DASHBOARD_TOKEN", KNOWN_DEV_DASHBOARD_TOKEN)
    return d_tok, b_tok

DEVICE_TOKEN, DASHBOARD_TOKEN = get_configured_tokens()


def get_device_token() -> str:
    return DEVICE_TOKEN


def get_dashboard_token() -> str:
    return DASHBOARD_TOKEN


def get_cors_origins() -> list[str]:
    raw = os.getenv("RAKSHA360_CORS_ORIGINS", "")
    if not raw.strip():
        return ["http://localhost:5173", "http://127.0.0.1:5173"]
    return [item.strip() for item in raw.split(",") if item.strip()]


def validate_environment_configuration(
    env_name: str | None = None,
    device_tok: str | None = None,
    dash_tok: str | None = None,
    cors_raw: str | None = None,
) -> None:
    """
    Fail-fast guard ensuring production-like configurations never silently use placeholder or missing credentials.
    Error messages intentionally omit credential values to prevent log leaks.
    """
    env = (env_name if env_name is not None else os.getenv("RAKSHA360_ENV", os.getenv("ENVIRONMENT", "development"))).strip().lower()
    d_tok = device_tok if device_tok is not None else get_device_token()
    b_tok = dash_tok if dash_tok is not None else get_dashboard_token()
    raw_cors = cors_raw if cors_raw is not None else os.getenv("RAKSHA360_CORS_ORIGINS", "")
    
    allow_dev_tokens = os.getenv(
        "RAKSHA360_ALLOW_DEV_TOKENS",
        "true" if env in ("development", "dev", "test", "testing", "local") else "false"
    ).strip().lower() in ("true", "1", "yes")

    is_production = env in ("production", "prod", "staging") or not allow_dev_tokens

    if is_production:
        if not d_tok or d_tok == KNOWN_DEV_DEVICE_TOKEN:
            raise RuntimeError(
                "Production configuration error: In production environment, RAKSHA360_DEVICE_TOKEN must be explicitly set to a secure, non-default secret."
            )
        if not b_tok or b_tok == KNOWN_DEV_DASHBOARD_TOKEN:
            raise RuntimeError(
                "Production configuration error: In production environment, RAKSHA360_DASHBOARD_TOKEN must be explicitly set to a secure, non-default secret."
            )
        if d_tok == b_tok:
            raise RuntimeError(
                "Production configuration error: RAKSHA360_DEVICE_TOKEN and RAKSHA360_DASHBOARD_TOKEN must be distinct credentials in production environment."
            )
        if not raw_cors.strip():
            raise RuntimeError(
                "Production configuration error: In production environment, RAKSHA360_CORS_ORIGINS must be explicitly configured with trusted frontend origin(s) (e.g. https://raksha360.vercel.app)."
            )
        cors_list = [item.strip() for item in raw_cors.split(",") if item.strip()]
        if "*" in cors_list:
            raise RuntimeError(
                "Production configuration error: Wildcard '*' in RAKSHA360_CORS_ORIGINS is prohibited in production. Explicit origins are required."
            )

CORS_ORIGINS = get_cors_origins()
APP_VERSION = "1.3.0-local"
OFFLINE_THRESHOLD_DEFAULT_SECONDS = 180
OFFLINE_MONITOR_DEFAULT_INTERVAL_SECONDS = 30
OFFLINE_EMAIL_RETRY_DEFAULT_SECONDS = 900
offline_monitor_task: asyncio.Task | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global offline_monitor_task
    validate_environment_configuration()
    initialize_database()
    if offline_monitor_task is None or offline_monitor_task.done():
        offline_monitor_task = asyncio.create_task(offline_monitor_loop())
    yield
    if offline_monitor_task and not offline_monitor_task.done():
        offline_monitor_task.cancel()
        try:
            await offline_monitor_task
        except asyncio.CancelledError:
            pass
    offline_monitor_task = None


app = FastAPI(
    title="Raksha360 Local Control Room API",
    description=(
        "Local development API that receives Raksha360 app events, monitors opted-in device heartbeats, and synchronizes incidents with the Police Control Room. "
        "Offline alerts use last-known location only; this is not an official police dispatch gateway."
    ),
    version=APP_VERSION,
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET", "POST", "PATCH", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-Dashboard-Token"],
)


# ---------- Database ----------
@contextmanager
def db() -> Any:
    conn = sqlite3.connect(DB_PATH, timeout=15)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA busy_timeout = 15000")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def utc_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_json(value: str | None, fallback: Any = None) -> Any:
    if not value:
        return fallback
    try:
        return json.loads(value)
    except (TypeError, json.JSONDecodeError):
        return fallback


def record_dict(row: sqlite3.Row | None) -> dict[str, Any] | None:
    return dict(row) if row is not None else None


def row_to_incident(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None
    item = dict(row)
    item["isDemo"] = bool(item.pop("is_demo", 0))
    item["details"] = parse_json(item.pop("details_json", None), {})
    return item


def row_to_report(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None
    item = dict(row)
    item["isDemo"] = bool(item.pop("is_demo", 0))
    return item


def row_to_dispatch(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None
    item = dict(row)
    item["isDemo"] = bool(item.pop("is_demo", 0))
    return item


def initialize_database() -> None:
    with db() as conn:
        conn.execute("PRAGMA journal_mode = WAL")
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS incidents (
                event_id TEXT PRIMARY KEY,
                trigger_type TEXT NOT NULL,
                phrase_detected TEXT NOT NULL DEFAULT '',
                speaker_confidence REAL NOT NULL DEFAULT 0,
                reported_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'New',
                severity TEXT NOT NULL DEFAULT 'High',
                latitude REAL,
                longitude REAL,
                accuracy_meters REAL,
                location_description TEXT NOT NULL DEFAULT 'Location unavailable',
                device_session_id TEXT,
                is_demo INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL DEFAULT 'ANDROID_APP',
                updated_at INTEGER NOT NULL,
                assignment_station TEXT,
                assigned_officer TEXT,
                notes TEXT NOT NULL DEFAULT '',
                details_json TEXT NOT NULL DEFAULT '{}',
                last_location_at INTEGER,
                last_latitude REAL,
                last_longitude REAL
            );
            CREATE INDEX IF NOT EXISTS idx_incidents_reported ON incidents(reported_at DESC);
            CREATE INDEX IF NOT EXISTS idx_incidents_status ON incidents(status);
            CREATE TABLE IF NOT EXISTS location_updates (
                id TEXT PRIMARY KEY,
                event_id TEXT NOT NULL REFERENCES incidents(event_id) ON DELETE CASCADE,
                timestamp INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                accuracy_meters REAL,
                speed_kmph REAL,
                battery_pct INTEGER,
                created_at INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_locations_event_time ON location_updates(event_id, timestamp DESC);
            CREATE TABLE IF NOT EXISTS reports (
                report_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                category TEXT NOT NULL,
                description TEXT NOT NULL,
                location_description TEXT NOT NULL DEFAULT 'Location unavailable',
                latitude REAL,
                longitude REAL,
                reported_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'New',
                priority TEXT NOT NULL DEFAULT 'Medium',
                source TEXT NOT NULL DEFAULT 'ANDROID_APP',
                source_event_id TEXT,
                evidence_sha256 TEXT,
                is_demo INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_reports_reported ON reports(reported_at DESC);
            CREATE TABLE IF NOT EXISTS trusted_contact_alerts (
                alert_id TEXT PRIMARY KEY,
                event_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                contact_count INTEGER NOT NULL,
                delivery_status TEXT NOT NULL,
                channels_json TEXT NOT NULL,
                details_json TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS dispatches (
                dispatch_id TEXT PRIMARY KEY,
                event_id TEXT NOT NULL REFERENCES incidents(event_id),
                requested_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'REQUESTED',
                station TEXT,
                assigned_officer TEXT,
                unit_label TEXT,
                channel TEXT NOT NULL DEFAULT 'INTERNAL_CONTROL_ROOM',
                notes TEXT NOT NULL DEFAULT '',
                is_demo INTEGER NOT NULL DEFAULT 0,
                external_gateway_status TEXT NOT NULL DEFAULT 'NOT_CONFIGURED'
            );
            CREATE INDEX IF NOT EXISTS idx_dispatches_requested ON dispatches(requested_at DESC);
            CREATE TABLE IF NOT EXISTS audit_log (
                id TEXT PRIMARY KEY,
                created_at INTEGER NOT NULL,
                actor TEXT NOT NULL,
                action TEXT NOT NULL,
                target_id TEXT,
                message TEXT NOT NULL,
                is_demo INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at DESC);
            CREATE TABLE IF NOT EXISTS stations (
                station_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                zone TEXT NOT NULL,
                latitude REAL,
                longitude REAL,
                status TEXT NOT NULL DEFAULT 'UNVERIFIED',
                is_demo INTEGER NOT NULL DEFAULT 1
            );
            CREATE TABLE IF NOT EXISTS police_station_directory (
                station_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                name_mr TEXT NOT NULL DEFAULT '',
                phone TEXT,
                email TEXT,
                address TEXT NOT NULL DEFAULT '',
                jurisdiction_label TEXT NOT NULL DEFAULT '',
                latitude REAL,
                longitude REAL,
                coordinates_verified INTEGER NOT NULL DEFAULT 0,
                coordinate_source TEXT,
                coordinate_checked_at TEXT,
                coordinate_status TEXT NOT NULL DEFAULT 'OFFICIAL_ADDRESS_ONLY',
                osm_type TEXT,
                osm_id TEXT,
                source_url TEXT NOT NULL,
                source_page_last_updated TEXT,
                retrieved_on TEXT NOT NULL,
                data_class TEXT NOT NULL DEFAULT 'OFFICIAL_PUBLIC_DIRECTORY',
                updated_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_police_station_directory_name ON police_station_directory(name);

            CREATE TABLE IF NOT EXISTS device_status (
                device_id TEXT PRIMARY KEY,
                last_seen_at INTEGER NOT NULL,
                last_latitude REAL,
                last_longitude REAL,
                last_accuracy_meters REAL,
                last_location_at INTEGER,
                last_battery_pct INTEGER,
                monitoring_enabled INTEGER NOT NULL DEFAULT 0,
                offline_location_email_consent INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'DISARMED',
                offline_detected_at INTEGER,
                offline_incident_id TEXT,
                offline_email_last_attempt_at INTEGER,
                offline_email_sent_at INTEGER,
                offline_email_delivery_status TEXT NOT NULL DEFAULT 'NOT_ATTEMPTED',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_device_status_monitoring ON device_status(monitoring_enabled, last_seen_at);
            CREATE TABLE IF NOT EXISTS emergency_contacts (
                device_id TEXT NOT NULL,
                email TEXT NOT NULL,
                name TEXT NOT NULL,
                relationship TEXT NOT NULL DEFAULT '',
                notify_by_email INTEGER NOT NULL DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (device_id, email)
            );
            CREATE TABLE IF NOT EXISTS offline_email_deliveries (
                device_id TEXT NOT NULL,
                incident_id TEXT NOT NULL,
                email TEXT NOT NULL,
                status TEXT NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                last_attempt_at INTEGER,
                sent_at INTEGER,
                last_error TEXT,
                PRIMARY KEY (device_id, incident_id, email)
            );
            CREATE INDEX IF NOT EXISTS idx_offline_email_delivery_episode
                ON offline_email_deliveries(device_id, incident_id, status);
            """
        )
        try:
            conn.execute("ALTER TABLE police_station_directory ADD COLUMN coordinate_status TEXT DEFAULT 'OFFICIAL_ADDRESS_ONLY'")
        except sqlite3.OperationalError:
            pass
        # Public station directory is a separate reference dataset, not live dispatch status.
        # Seed from the checked-in public snapshot; preserve any manually reviewed coordinates.
        directory_path = ROOT_DIR.parent / "reference-data" / "nagpur-public" / "nagpur_police_stations.json"
        if directory_path.is_file():
            try:
                directory_payload = json.loads(directory_path.read_text(encoding="utf-8"))
                for station in directory_payload.get("records", []):
                    conn.execute(
                        """INSERT INTO police_station_directory (
                               station_id, name, name_mr, phone, email, address, jurisdiction_label,
                               latitude, longitude, coordinates_verified, coordinate_source, coordinate_checked_at,
                               coordinate_status, osm_type, osm_id, source_url, source_page_last_updated, retrieved_on, data_class, updated_at
                           ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                           ON CONFLICT(station_id) DO UPDATE SET
                               name=excluded.name,
                               name_mr=excluded.name_mr,
                               phone=excluded.phone,
                               email=excluded.email,
                               address=excluded.address,
                               jurisdiction_label=excluded.jurisdiction_label,
                               latitude=COALESCE(police_station_directory.latitude, excluded.latitude),
                               longitude=COALESCE(police_station_directory.longitude, excluded.longitude),
                               coordinates_verified=MAX(police_station_directory.coordinates_verified, excluded.coordinates_verified),
                               coordinate_source=COALESCE(police_station_directory.coordinate_source, excluded.coordinate_source),
                               coordinate_checked_at=COALESCE(police_station_directory.coordinate_checked_at, excluded.coordinate_checked_at),
                               coordinate_status=COALESCE(police_station_directory.coordinate_status, excluded.coordinate_status),
                               osm_type=COALESCE(police_station_directory.osm_type, excluded.osm_type),
                               osm_id=COALESCE(police_station_directory.osm_id, excluded.osm_id),
                               source_url=excluded.source_url,
                               source_page_last_updated=excluded.source_page_last_updated,
                               retrieved_on=excluded.retrieved_on,
                               data_class=excluded.data_class,
                               updated_at=excluded.updated_at""",
                        (
                            station.get("station_id"), station.get("name", ""), station.get("name_mr", ""),
                            station.get("phone"), station.get("email"), station.get("address", ""),
                            station.get("jurisdiction_label", ""), station.get("latitude"), station.get("longitude"),
                            int(bool(station.get("coordinates_verified", False))), station.get("coordinate_source"),
                            station.get("coordinate_checked_at"), station.get("coordinate_status", "OFFICIAL_ADDRESS_ONLY"),
                            station.get("osm_type"), station.get("osm_id"),
                            station.get("source_url", ""), station.get("source_page_last_updated"),
                            station.get("retrieved_on", ""), station.get("data_class", "OFFICIAL_PUBLIC_DIRECTORY"),
                            now_iso(),
                        ),
                    )
            except (OSError, json.JSONDecodeError) as exc:
                # Backend must remain available if the optional public snapshot is missing/malformed.
                print(f"WARNING: public police station snapshot not loaded: {exc}")

        incident_count = conn.execute("SELECT COUNT(*) FROM incidents").fetchone()[0]
        if incident_count == 0 and os.getenv("RAKSHA360_SEED_DEMO_DATA", "false").lower() == "true":
            seed_time = utc_ms() - 45 * 60 * 1000
            samples = [
                ("DEMO-SOS-001", "PANIC_BUTTON", "High", "Acknowledged", 21.1458, 79.0882, "Central Nagpur · DEMO PIN", "Women’s manual SOS"),
                ("DEMO-CHILD-002", "CHILD_SOS", "Critical", "New", 21.1514, 79.0825, "North-central Nagpur · DEMO PIN", "Child SOS training event"),
                ("DEMO-VOICE-003", "VOICE_SOS", "Critical", "Responding", 21.1392, 79.0946, "East-central Nagpur · DEMO PIN", "Voice SOS training event"),
                ("DEMO-JOURNEY-004", "JOURNEY_TIMEOUT", "High", "New", 21.1541, 79.0758, "West-central Nagpur · DEMO PIN", "Overdue journey training event"),
            ]
            for i, (event_id, trigger, severity, status, lat, lon, location, note) in enumerate(samples):
                stamp = seed_time - i * 8 * 60 * 1000
                conn.execute(
                    """INSERT INTO incidents (event_id, trigger_type, reported_at, status, severity, latitude, longitude,
                       accuracy_meters, location_description, is_demo, source, updated_at, notes, details_json)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'SYNTHETIC_SAMPLE', ?, ?, ?)""",
                    (event_id, trigger, stamp, status, severity, lat, lon, 20.0, location, stamp, note,
                     json.dumps({"sample": True, "displayNote": "Synthetic training sample — not a real emergency"})),
                )
            conn.execute(
                "INSERT INTO audit_log (id, created_at, actor, action, target_id, message, is_demo) VALUES (?, ?, ?, ?, ?, ?, 1)",
                (str(uuid.uuid4()), seed_time, "SYSTEM", "DEMO_DATA_SEEDED", None, "Synthetic sample incidents created for UI evaluation."),
            )
        station_count = conn.execute("SELECT COUNT(*) FROM stations").fetchone()[0]
        if station_count == 0 and os.getenv("RAKSHA360_SEED_DEMO_DATA", "false").lower() == "true":
            # Names are deliberately marked as demo; no real station locations are implied.
            stations = [
                ("DEMO-UNIT-CENTRAL", "Demo response unit · Central", "Training zone A", 21.1458, 79.0882),
                ("DEMO-UNIT-NORTH", "Demo response unit · North", "Training zone B", 21.1595, 79.0832),
                ("DEMO-UNIT-EAST", "Demo response unit · East", "Training zone C", 21.1430, 79.1050),
            ]
            for station_id, name, zone, lat, lon in stations:
                conn.execute(
                    "INSERT INTO stations (station_id, name, zone, latitude, longitude, status, is_demo) VALUES (?, ?, ?, ?, ?, 'DEMO_ONLY', 1)",
                    (station_id, name, zone, lat, lon),
                )


# ---------- Authentication ----------
def constant_time_token_match(candidate: str | None, expected: str) -> bool:
    return bool(candidate) and hmac.compare_digest(candidate, expected)


def require_device(authorization: str | None = Header(default=None)) -> None:
    bearer = authorization[7:].strip() if authorization and authorization.lower().startswith("bearer ") else None
    if not constant_time_token_match(bearer, get_device_token()):
        raise HTTPException(status_code=401, detail="A valid Raksha360 device bearer token is required.")


def require_dashboard(x_dashboard_token: str | None = Header(default=None)) -> None:
    if not constant_time_token_match(x_dashboard_token, get_dashboard_token()):
        raise HTTPException(status_code=401, detail="Dashboard session is missing or expired. Sign in again.")


def require_any_api_client(
    authorization: str | None = Header(default=None),
    x_dashboard_token: str | None = Header(default=None),
) -> str:
    bearer = authorization[7:].strip() if authorization and authorization.lower().startswith("bearer ") else None
    if constant_time_token_match(bearer, get_device_token()):
        return "ANDROID_APP"
    if constant_time_token_match(x_dashboard_token, get_dashboard_token()):
        return "POLICE_DASHBOARD"
    raise HTTPException(status_code=401, detail="A valid device or dashboard token is required.")


def create_audit(actor: str, action: str, target_id: str | None, message: str, is_demo: bool) -> None:
    with db() as conn:
        conn.execute(
            "INSERT INTO audit_log (id, created_at, actor, action, target_id, message, is_demo) VALUES (?, ?, ?, ?, ?, ?, ?)",
            (str(uuid.uuid4()), utc_ms(), actor, action, target_id, message[:600], int(is_demo)),
        )


# ---------- Realtime event bus ----------
class ConnectionManager:
    def __init__(self) -> None:
        self.connections: set[WebSocket] = set()
        self.lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket) -> None:
        await websocket.accept()
        async with self.lock:
            self.connections.add(websocket)
        await websocket.send_json({"type": "CONNECTED", "data": {"at": now_iso(), "service": "Raksha360 Control Room"}})

    async def disconnect(self, websocket: WebSocket) -> None:
        async with self.lock:
            self.connections.discard(websocket)

    async def broadcast(self, event_type: str, data: dict[str, Any]) -> None:
        packet = {"type": event_type, "data": data, "sentAt": now_iso()}
        async with self.lock:
            targets = list(self.connections)
        stale: list[WebSocket] = []
        for socket in targets:
            try:
                await socket.send_json(packet)
            except Exception:
                stale.append(socket)
        if stale:
            async with self.lock:
                for socket in stale:
                    self.connections.discard(socket)


manager = ConnectionManager()


# ---------- API models ----------
class DeviceEmergencyEvent(BaseModel):
    eventId: str = Field(min_length=1, max_length=100)
    timestamp: int = Field(default_factory=utc_ms)
    triggerType: str = Field(min_length=1, max_length=80)
    phraseDetected: str = Field(default="", max_length=160)
    speakerConfidence: float = Field(default=0, ge=0, le=1)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    accuracyMeters: float | None = Field(default=None, ge=0, le=100000)
    locationDescription: str = Field(default="Location unavailable", max_length=300)
    deviceSessionId: str | None = Field(default=None, max_length=100)
    isDemoMode: bool = False

    @field_validator("eventId")
    @classmethod
    def strip_event_id(cls, value: str) -> str:
        return value.strip()


class DeviceLocationUpdate(BaseModel):
    eventId: str = Field(min_length=1, max_length=100)
    timestamp: int = Field(default_factory=utc_ms)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    accuracyMeters: float | None = Field(default=None, ge=0, le=100000)
    speedKmph: float | None = Field(default=None, ge=0, le=400)
    batteryPct: int | None = Field(default=None, ge=0, le=100)


class OfflineEmailContact(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    relationship: str = Field(default="", max_length=80)
    email: str | None = Field(default=None, max_length=254)
    notifyByEmail: bool = False
    isActive: bool = True

    @field_validator("email")
    @classmethod
    def validate_email_shape(cls, value: str | None) -> str | None:
        if value is None or not value.strip():
            return None
        value = value.strip()
        if "@" not in value or "." not in value.rsplit("@", 1)[-1] or any(ch.isspace() for ch in value):
            raise ValueError("A valid email address is required for email notifications.")
        return value


class DeviceHeartbeat(BaseModel):
    deviceId: str = Field(min_length=8, max_length=100)
    timestamp: int = Field(default_factory=utc_ms)
    monitoringEnabled: bool = False
    offlineLocationEmailConsent: bool = False
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    accuracyMeters: float | None = Field(default=None, ge=0, le=100000)
    locationTimestamp: int | None = None
    batteryPct: int | None = Field(default=None, ge=0, le=100)
    contacts: list[OfflineEmailContact] = Field(default_factory=list, max_length=20)

    @field_validator("deviceId")
    @classmethod
    def normalize_device_id(cls, value: str) -> str:
        return value.strip()


class TrustedContactAlert(BaseModel):
    eventId: str = Field(min_length=1, max_length=100)
    timestamp: int = Field(default_factory=utc_ms)
    contacts: list[dict[str, Any]] = Field(default_factory=list, max_length=20)
    triggerType: str = Field(default="SOS", max_length=80)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    address: str = Field(default="Location unavailable", max_length=300)
    liveTrackingUrl: str | None = Field(default=None, max_length=500)


class ReportCreate(BaseModel):
    title: str = Field(min_length=3, max_length=160)
    category: str = Field(min_length=2, max_length=80)
    description: str = Field(min_length=5, max_length=5000)
    locationDescription: str = Field(default="Location unavailable", max_length=300)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    reportedAt: int = Field(default_factory=utc_ms)
    sourceEventId: str | None = Field(default=None, max_length=100)
    evidenceSha256: str | None = Field(default=None, max_length=64)
    isDemoMode: bool = False


class DashboardReportCreate(ReportCreate):
    pass


class StatusUpdate(BaseModel):
    status: Literal["New", "Acknowledged", "Assigned", "Responding", "Resolved", "False Alarm", "Review Required"]
    notes: str = Field(default="", max_length=1000)


class ReportStatusUpdate(BaseModel):
    status: Literal["New", "Under Review", "Assigned", "Closed", "Rejected"]
    priority: Literal["Critical", "High", "Medium", "Low"] | None = None
    notes: str = Field(default="", max_length=1000)


class DispatchCreate(BaseModel):
    station: str = Field(min_length=2, max_length=160)
    assignedOfficer: str = Field(default="Unassigned", max_length=160)
    unitLabel: str = Field(default="Control-room unit pending confirmation", max_length=160)
    channel: Literal["INTERNAL_CONTROL_ROOM", "PHONE_112_MANUAL", "AUTHORIZED_EXTERNAL_GATEWAY"] = "INTERNAL_CONTROL_ROOM"
    notes: str = Field(default="", max_length=1000)


class DispatchStatusUpdate(BaseModel):
    status: Literal["REQUESTED", "ACKNOWLEDGED_IN_CONTROL_ROOM", "UNIT_EN_ROUTE_REPORTED", "ON_SCENE_REPORTED", "COMPLETED", "CANCELLED"]
    notes: str = Field(default="", max_length=1000)


class DashboardSessionRequest(BaseModel):
    token: str = Field(min_length=1, max_length=500)


# ---------- Common mappings ----------
def severity_for(trigger: str) -> str:
    key = trigger.upper()
    if key in {"VOICE_SOS", "PANIC_BUTTON", "MANUAL_SOS", "CHILD_SOS", "LOST_CHILD"}:
        return "Critical"
    if key in {"JOURNEY_TIMEOUT", "GEOFENCE_EXIT", "SCHOOL_HANDOVER_ESCALATION", "MISSED_CHECK_IN"}:
        return "High"
    return "Medium"


def get_incident(event_id: str) -> dict[str, Any] | None:
    with db() as conn:
        return row_to_incident(conn.execute("SELECT * FROM incidents WHERE event_id = ?", (event_id,)).fetchone())


def get_report(report_id: str) -> dict[str, Any] | None:
    with db() as conn:
        return row_to_report(conn.execute("SELECT * FROM reports WHERE report_id = ?", (report_id,)).fetchone())


@app.get("/")
def root() -> dict[str, Any]:
    return {"service": "Raksha360 Local Control Room API", "version": APP_VERSION, "docs": "/docs", "health": "/api/v1/health"}


@app.get("/health")
def simple_health() -> dict[str, Any]:
    return {"status": "ok", "service": "Raksha360 Control Room", "time": now_iso()}


@app.get("/api/v1/health")
def api_health() -> dict[str, Any]:
    with db() as conn:
        incident_total = conn.execute("SELECT COUNT(*) FROM incidents").fetchone()[0]
    return {
        "status": "ONLINE",
        "service": "Raksha360 Local Control Room API",
        "version": APP_VERSION,
        "database": "sqlite",
        "incidentRecords": incident_total,
        "officialPoliceGateway": "NOT_CONFIGURED",
        "externalNotifications": "SMTP_CONFIGURED" if _smtp_is_configured() else "NOT_CONFIGURED",
        "offlineHeartbeatThresholdSeconds": max(60, int(os.getenv("RAKSHA360_OFFLINE_THRESHOLD_SECONDS", str(OFFLINE_THRESHOLD_DEFAULT_SECONDS)))),
        "time": now_iso(),
    }


@app.post("/api/v1/dashboard/session")
def verify_dashboard_session(payload: DashboardSessionRequest) -> dict[str, Any]:
    if not constant_time_token_match(payload.token, DASHBOARD_TOKEN):
        raise HTTPException(status_code=401, detail="Invalid dashboard token.")
    return {"authenticated": True, "role": "CONTROL_ROOM_OPERATOR", "expires": None, "note": "Local development token; restrict the server to a trusted network."}


@app.post("/api/v1/device/session", dependencies=[Depends(require_device)])
def verify_device_session() -> dict[str, Any]:
    """Verify that the Android app can authenticate to this local backend."""
    return {"authenticated": True, "role": "RAKSHA360_DEVICE", "service": "local-control-room-api", "time": now_iso()}


def _smtp_is_configured() -> bool:
    return all(os.getenv(key, "").strip() for key in ("RAKSHA360_SMTP_HOST", "RAKSHA360_SMTP_FROM_EMAIL"))


def send_offline_location_email(contact: dict[str, Any], device: dict[str, Any], incident_id: str) -> None:
    """Send a consented last-known-location notice. This is not a live-location message."""
    host = os.getenv("RAKSHA360_SMTP_HOST", "").strip()
    sender = os.getenv("RAKSHA360_SMTP_FROM_EMAIL", "").strip()
    if not host or not sender:
        raise RuntimeError("SMTP_NOT_CONFIGURED")
    port = int(os.getenv("RAKSHA360_SMTP_PORT", "587"))
    username = os.getenv("RAKSHA360_SMTP_USERNAME", "").strip()
    password = os.getenv("RAKSHA360_SMTP_PASSWORD", "")
    use_tls = os.getenv("RAKSHA360_SMTP_USE_TLS", "true").lower() == "true"
    latitude = device.get("last_latitude")
    longitude = device.get("last_longitude")
    if latitude is not None and longitude is not None:
        location_link = f"https://www.openstreetmap.org/?mlat={latitude}&mlon={longitude}#map=17/{latitude}/{longitude}"
        location_lines = (
            f"Coordinates: {latitude:.6f}, {longitude:.6f}\n"
            f"OpenStreetMap link: {location_link}\n"
        )
    else:
        location_lines = "Coordinates are unavailable; no real device location was recorded by the server.\n"
    last_seen = datetime.fromtimestamp(int(device["last_seen_at"]) / 1000, tz=timezone.utc).isoformat()
    location_at = device.get("last_location_at")
    location_time = datetime.fromtimestamp(int(location_at) / 1000, tz=timezone.utc).isoformat() if location_at else "Unavailable"
    offline_since = datetime.fromtimestamp(int(device.get("offline_detected_at") or utc_ms()) / 1000, tz=timezone.utc).isoformat()
    message = EmailMessage()
    message["Subject"] = "Raksha360 alert: phone stopped checking in"
    message["From"] = sender
    message["To"] = str(contact["email"])
    message.set_content(
        f"Hello {contact.get('name') or 'trusted contact'},\n\n"
        "Raksha360 has not received a device heartbeat for the configured timeout. "
        "The phone may be powered off, out of coverage, disconnected, or the app/service may have stopped. "
        "This is not proof that the person is in danger.\n\n"
        f"Last server heartbeat (UTC): {last_seen}\n"
        f"Last known location timestamp (UTC): {location_time}\n"
        f"Offline alert detected (UTC): {offline_since}\n"
        f"{location_lines}\n"
        "IMPORTANT: This is a LAST KNOWN LOCATION, not a live GPS position. A fully powered-off phone cannot transmit new location updates.\n\n"
        f"Raksha360 local control-room reference: {incident_id}\n"
        "If you believe there is immediate danger, contact local emergency services directly.\n"
    )
    with smtplib.SMTP(host, port, timeout=15) as server:
        if use_tls:
            server.starttls()
        if username:
            server.login(username, password)
        server.send_message(message)


def _device_status_payload(row: sqlite3.Row | dict[str, Any]) -> dict[str, Any]:
    item = dict(row)
    item["monitoringEnabled"] = bool(item.pop("monitoring_enabled", 0))
    item["offlineLocationEmailConsent"] = bool(item.pop("offline_location_email_consent", 0))
    item["isOffline"] = item.get("status") == "OFFLINE"
    return item


@app.post("/api/v1/device/heartbeat", dependencies=[Depends(require_device)])
async def receive_device_heartbeat(payload: DeviceHeartbeat) -> dict[str, Any]:
    current = utc_ms()
    has_location = payload.latitude is not None and payload.longitude is not None
    location_timestamp = payload.locationTimestamp or payload.timestamp if has_location else None
    active_email_contacts = [
        contact for contact in payload.contacts
        if payload.offlineLocationEmailConsent and contact.isActive and contact.notifyByEmail and contact.email
    ]
    with db() as conn:
        previous = conn.execute("SELECT * FROM device_status WHERE device_id = ?", (payload.deviceId,)).fetchone()
        was_offline = bool(previous and previous["offline_detected_at"] is not None)
        conn.execute(
            """INSERT INTO device_status (
                device_id, last_seen_at, last_latitude, last_longitude, last_accuracy_meters, last_location_at,
                last_battery_pct, monitoring_enabled, offline_location_email_consent, status,
                offline_detected_at, offline_incident_id, offline_email_last_attempt_at,
                offline_email_sent_at, offline_email_delivery_status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, NULL, ?, ?, ?, ?)
            ON CONFLICT(device_id) DO UPDATE SET
                last_seen_at=excluded.last_seen_at,
                last_latitude=COALESCE(excluded.last_latitude, device_status.last_latitude),
                last_longitude=COALESCE(excluded.last_longitude, device_status.last_longitude),
                last_accuracy_meters=COALESCE(excluded.last_accuracy_meters, device_status.last_accuracy_meters),
                last_location_at=COALESCE(excluded.last_location_at, device_status.last_location_at),
                last_battery_pct=COALESCE(excluded.last_battery_pct, device_status.last_battery_pct),
                monitoring_enabled=excluded.monitoring_enabled,
                offline_location_email_consent=excluded.offline_location_email_consent,
                status=excluded.status,
                offline_detected_at=NULL,
                offline_email_last_attempt_at=NULL,
                offline_email_sent_at=CASE WHEN device_status.offline_detected_at IS NOT NULL THEN device_status.offline_email_sent_at ELSE device_status.offline_email_sent_at END,
                offline_email_delivery_status=CASE WHEN device_status.offline_detected_at IS NOT NULL THEN device_status.offline_email_delivery_status ELSE device_status.offline_email_delivery_status END,
                updated_at=excluded.updated_at""",
            (payload.deviceId, current, payload.latitude if has_location else None,
             payload.longitude if has_location else None, payload.accuracyMeters if has_location else None,
             location_timestamp, payload.batteryPct, int(payload.monitoringEnabled),
             int(payload.offlineLocationEmailConsent), "ONLINE" if payload.monitoringEnabled else "DISARMED",
             previous["offline_incident_id"] if previous else None,
             previous["offline_email_sent_at"] if previous else None,
             previous["offline_email_delivery_status"] if previous else "NOT_ATTEMPTED",
             previous["created_at"] if previous else current, current),
        )
        # The backend only keeps email addresses while the owner has opted in to last-known-location emails.
        conn.execute("DELETE FROM emergency_contacts WHERE device_id = ?", (payload.deviceId,))
        if payload.offlineLocationEmailConsent:
            for contact in active_email_contacts:
                conn.execute(
                    "INSERT OR REPLACE INTO emergency_contacts (device_id, email, name, relationship, notify_by_email, is_active, updated_at) VALUES (?, ?, ?, ?, 1, 1, ?)",
                    (payload.deviceId, contact.email, contact.name.strip(), contact.relationship.strip(), current),
                )
        else:
            # Revoke server-side access to email destinations when the user disables consent.
            conn.execute("DELETE FROM offline_email_deliveries WHERE device_id = ?", (payload.deviceId,))
        row = conn.execute("SELECT * FROM device_status WHERE device_id = ?", (payload.deviceId,)).fetchone()
    device = _device_status_payload(row)
    if was_offline:
        create_audit("ANDROID_APP", "DEVICE_BACK_ONLINE", device.get("offline_incident_id"),
                     "The device reconnected after a missed-heartbeat offline alert.", False)
        await manager.broadcast("DEVICE_ONLINE_AGAIN", device)
    await manager.broadcast("DEVICE_HEARTBEAT", device)
    return {
        "accepted": True,
        "deviceId": payload.deviceId,
        "status": device["status"],
        "monitoringEnabled": device["monitoringEnabled"],
        "lastLocationRecorded": has_location,
        "offlineLocationEmailConsent": device["offlineLocationEmailConsent"],
        "emailContactsConfigured": len(active_email_contacts),
        "smtpConfigured": _smtp_is_configured(),
        "message": "Heartbeat saved. If the phone stops checking in, the server can only alert using this last-known location; it cannot obtain new GPS from a powered-off phone.",
    }


async def evaluate_offline_devices_once() -> None:
    current = utc_ms()
    threshold_ms = max(60, int(os.getenv("RAKSHA360_OFFLINE_THRESHOLD_SECONDS", str(OFFLINE_THRESHOLD_DEFAULT_SECONDS)))) * 1000
    retry_ms = max(300, int(os.getenv("RAKSHA360_OFFLINE_EMAIL_RETRY_SECONDS", str(OFFLINE_EMAIL_RETRY_DEFAULT_SECONDS)))) * 1000
    with db() as conn:
        devices = conn.execute(
            """SELECT * FROM device_status
               WHERE (monitoring_enabled = 1 AND last_seen_at <= ?)
                  OR (offline_incident_id IS NOT NULL AND offline_location_email_consent = 1
                      AND offline_email_delivery_status IN ('PARTIAL','FAILED','SMTP_NOT_CONFIGURED','NO_CONSENTED_EMAIL_CONTACTS')
                      AND (offline_email_last_attempt_at IS NULL OR offline_email_last_attempt_at <= ?))
               ORDER BY last_seen_at ASC""",
            (current - threshold_ms, current - retry_ms),
        ).fetchall()
    for row in devices:
        device = dict(row)
        heartbeat_is_stale = device.get("monitoring_enabled") == 1 and int(device.get("last_seen_at") or 0) <= current - threshold_ms
        if device.get("offline_detected_at") is None and heartbeat_is_stale:
            offline_detected = current
            digest = hashlib.sha256(device["device_id"].encode("utf-8")).hexdigest()[:10].upper()
            incident_id = f"OFFLINE-{digest}-{offline_detected}"
            loc_available = device.get("last_latitude") is not None and device.get("last_longitude") is not None
            location_text = "Last known position" if loc_available else "Location unavailable"
            if loc_available and device.get("last_location_at"):
                location_text += f" · timestamp {device['last_location_at']}"
            details = {
                "deviceId": device["device_id"],
                "offlineDetectedAt": offline_detected,
                "lastHeartbeatAt": device["last_seen_at"],
                "lastLocationAt": device.get("last_location_at"),
                "locationKind": "LAST_KNOWN_NOT_LIVE" if loc_available else "UNAVAILABLE",
                "offlineLocationEmailConsent": bool(device.get("offline_location_email_consent")),
                "privacyNote": "A powered-off phone cannot provide new coordinates. This incident uses only previously received coordinates.",
            }
            with db() as conn:
                current_row = conn.execute("SELECT * FROM device_status WHERE device_id = ?", (device["device_id"],)).fetchone()
                if not current_row or current_row["monitoring_enabled"] != 1 or current_row["offline_detected_at"] is not None or current_row["last_seen_at"] > current - threshold_ms:
                    continue
                conn.execute(
                    """INSERT OR IGNORE INTO incidents (event_id, trigger_type, reported_at, status, severity, latitude,
                       longitude, accuracy_meters, location_description, device_session_id, is_demo, source, updated_at,
                       notes, details_json, last_location_at, last_latitude, last_longitude)
                       VALUES (?, 'DEVICE_OFFLINE', ?, 'New', 'High', ?, ?, ?, ?, ?, 0, 'BACKEND_OFFLINE_MONITOR', ?, ?, ?, ?, ?, ?)""",
                    (incident_id, offline_detected, device.get("last_latitude"), device.get("last_longitude"),
                     device.get("last_accuracy_meters"), location_text, device["device_id"], offline_detected,
                     "Device missed the configured heartbeat threshold. Possible power-off, coverage loss, or app/service stop; verify before concluding danger.",
                     json.dumps(details), device.get("last_location_at"), device.get("last_latitude"), device.get("last_longitude")),
                )
                conn.execute(
                    "UPDATE device_status SET status='OFFLINE', offline_detected_at=?, offline_incident_id=?, offline_email_last_attempt_at=NULL, offline_email_sent_at=NULL, offline_email_delivery_status=?, updated_at=? WHERE device_id=?",
                    (offline_detected, incident_id, "PENDING_CONSENT" if device.get("offline_location_email_consent") else "NOT_CONSENTED", offline_detected, device["device_id"]),
                )
                incident_row = conn.execute("SELECT * FROM incidents WHERE event_id = ?", (incident_id,)).fetchone()
                current_device_row = conn.execute("SELECT * FROM device_status WHERE device_id = ?", (device["device_id"],)).fetchone()
            incident = row_to_incident(incident_row) or {}
            device = dict(current_device_row)
            create_audit("OFFLINE_MONITOR", "DEVICE_OFFLINE_DETECTED", incident_id,
                         "A monitored device missed its heartbeat threshold; location, if present, is last-known only.", False)
            await manager.broadcast("DEVICE_OFFLINE_DETECTED", {"device": _device_status_payload(current_device_row), "incident": incident})
            await manager.broadcast("INCIDENT_CREATED", incident)

        # Contact email is a separate, explicitly consented feature. Failed attempts are retried with a long backoff.
        with db() as conn:
            current_row = conn.execute("SELECT * FROM device_status WHERE device_id = ?", (device["device_id"],)).fetchone()
            if current_row is None:
                continue
            device = dict(current_row)
            contacts = [dict(item) for item in conn.execute(
                "SELECT * FROM emergency_contacts WHERE device_id = ? AND is_active = 1 AND notify_by_email = 1 ORDER BY email",
                (device["device_id"],),
            ).fetchall()]
        if not device.get("offline_location_email_consent"):
            continue
        incident_id = device.get("offline_incident_id") or "DEVICE-OFFLINE"
        # A fully delivered episode is final. A retry must never duplicate emails already accepted for a contact.
        if device.get("offline_email_sent_at") and device.get("offline_email_delivery_status") == "SENT":
            continue
        if device.get("offline_email_last_attempt_at") and current - int(device["offline_email_last_attempt_at"]) < retry_ms:
            continue
        if not _smtp_is_configured():
            with db() as conn:
                conn.execute("UPDATE device_status SET offline_email_last_attempt_at=?, offline_email_delivery_status='SMTP_NOT_CONFIGURED', updated_at=? WHERE device_id=?",
                             (current, current, device["device_id"]))
            continue
        if not contacts:
            with db() as conn:
                conn.execute("UPDATE device_status SET offline_email_last_attempt_at=?, offline_email_delivery_status='NO_CONSENTED_EMAIL_CONTACTS', updated_at=? WHERE device_id=?", (current, current, device["device_id"]))
            continue

        with db() as conn:
            conn.execute("UPDATE device_status SET offline_email_last_attempt_at=?, offline_email_delivery_status='SENDING', updated_at=? WHERE device_id=?",
                         (current, current, device["device_id"]))
        success_count = 0
        failed_count = 0
        for contact in contacts:
            with db() as conn:
                previous_delivery = conn.execute(
                    "SELECT * FROM offline_email_deliveries WHERE device_id=? AND incident_id=? AND email=?",
                    (device["device_id"], incident_id, contact["email"]),
                ).fetchone()
            if previous_delivery and previous_delivery["status"] == "SENT":
                success_count += 1
                continue
            with db() as conn:
                conn.execute(
                    """INSERT INTO offline_email_deliveries (device_id, incident_id, email, status, attempt_count, last_attempt_at, sent_at, last_error)
                       VALUES (?, ?, ?, 'SENDING', 1, ?, NULL, NULL)
                       ON CONFLICT(device_id, incident_id, email) DO UPDATE SET
                         status='SENDING', attempt_count=offline_email_deliveries.attempt_count+1,
                         last_attempt_at=excluded.last_attempt_at, last_error=NULL""",
                    (device["device_id"], incident_id, contact["email"], current),
                )
            try:
                await asyncio.to_thread(send_offline_location_email, contact, device, incident_id)
                sent_at = utc_ms()
                with db() as conn:
                    conn.execute(
                        "UPDATE offline_email_deliveries SET status='SENT', sent_at=?, last_error=NULL WHERE device_id=? AND incident_id=? AND email=?",
                        (sent_at, device["device_id"], incident_id, contact["email"]),
                    )
                success_count += 1
            except Exception as error:
                with db() as conn:
                    conn.execute(
                        "UPDATE offline_email_deliveries SET status='FAILED', last_error=? WHERE device_id=? AND incident_id=? AND email=?",
                        (type(error).__name__, device["device_id"], incident_id, contact["email"]),
                    )
                failed_count += 1
        delivered = success_count == len(contacts)
        delivery_status = "SENT" if delivered else "PARTIAL" if success_count else "FAILED"
        with db() as conn:
            conn.execute(
                "UPDATE device_status SET offline_email_sent_at=?, offline_email_delivery_status=?, updated_at=? WHERE device_id=?",
                (utc_ms() if delivered else device.get("offline_email_sent_at"), delivery_status, utc_ms(), device["device_id"]),
            )
            refreshed = conn.execute("SELECT * FROM device_status WHERE device_id = ?", (device["device_id"],)).fetchone()
        create_audit("OFFLINE_MONITOR", "OFFLINE_LOCATION_EMAIL_" + delivery_status, incident_id,
                     f"Last-known-location email state={delivery_status}; consented recipients={len(contacts)}; accepted={success_count}; failed={failed_count}.", False)
        await manager.broadcast("DEVICE_OFFLINE_EMAIL_STATUS", _device_status_payload(refreshed))


async def offline_monitor_loop() -> None:
    interval = max(10, int(os.getenv("RAKSHA360_OFFLINE_MONITOR_INTERVAL_SECONDS", str(OFFLINE_MONITOR_DEFAULT_INTERVAL_SECONDS))))
    while True:
        try:
            await evaluate_offline_devices_once()
        except asyncio.CancelledError:
            raise
        except Exception as error:
            # Keep the API healthy if an SMTP/network/SQLite monitor iteration fails.
            create_audit("OFFLINE_MONITOR", "OFFLINE_MONITOR_ERROR", None, f"Monitor iteration failed: {type(error).__name__}.", False)
        await asyncio.sleep(interval)


@app.get("/api/v1/devices/status", dependencies=[Depends(require_dashboard)])
def list_device_status() -> dict[str, Any]:
    with db() as conn:
        rows = conn.execute("SELECT * FROM device_status ORDER BY updated_at DESC LIMIT 200").fetchall()
    return {"items": [_device_status_payload(row) for row in rows], "total": len(rows), "smtpConfigured": _smtp_is_configured(), "offlineThresholdSeconds": max(60, int(os.getenv("RAKSHA360_OFFLINE_THRESHOLD_SECONDS", str(OFFLINE_THRESHOLD_DEFAULT_SECONDS))))}


@app.post("/api/v1/emergency-events", dependencies=[Depends(require_device)])
async def receive_emergency_event(payload: DeviceEmergencyEvent) -> dict[str, Any]:
    event_id = payload.eventId
    severity = severity_for(payload.triggerType)
    is_demo = payload.isDemoMode or payload.triggerType.upper().startswith("DEMO_")
    current = utc_ms()
    details = {
        "phraseDetected": payload.phraseDetected,
        "speakerConfidence": payload.speakerConfidence,
        "deviceSessionId": payload.deviceSessionId,
        "source": "ANDROID_APP",
        "privacyNote": "No raw audio or speaker embedding is stored by this API endpoint.",
    }
    with db() as conn:
        existing = conn.execute("SELECT * FROM incidents WHERE event_id = ?", (event_id,)).fetchone()
        if existing:
            conn.execute(
                "UPDATE incidents SET updated_at = ?, details_json = ? WHERE event_id = ?",
                (current, json.dumps(details), event_id),
            )
            row = conn.execute("SELECT * FROM incidents WHERE event_id = ?", (event_id,)).fetchone()
            created = False
        else:
            conn.execute(
                """INSERT INTO incidents (event_id, trigger_type, phrase_detected, speaker_confidence, reported_at,
                   status, severity, latitude, longitude, accuracy_meters, location_description, device_session_id,
                   is_demo, source, updated_at, last_location_at, last_latitude, last_longitude, details_json)
                   VALUES (?, ?, ?, ?, ?, 'New', ?, ?, ?, ?, ?, ?, ?, 'ANDROID_APP', ?, ?, ?, ?, ?)""",
                (event_id, payload.triggerType, payload.phraseDetected, payload.speakerConfidence, payload.timestamp,
                 severity, payload.latitude, payload.longitude, payload.accuracyMeters, payload.locationDescription,
                 payload.deviceSessionId, int(is_demo), current, payload.timestamp if payload.latitude is not None else None,
                 payload.latitude, payload.longitude, json.dumps(details)),
            )
            row = conn.execute("SELECT * FROM incidents WHERE event_id = ?", (event_id,)).fetchone()
            created = True
    incident = row_to_incident(row) or {}
    create_audit("ANDROID_APP", "EMERGENCY_CREATED" if created else "EMERGENCY_RETRY_DEDUPLICATED", event_id,
                 f"{payload.triggerType} received from Android app; isDemo={is_demo}.", is_demo)
    await manager.broadcast("INCIDENT_CREATED" if created else "INCIDENT_UPDATED", incident)
    return {
        "eventId": event_id,
        "status": "RECEIVED_IN_CONTROL_ROOM_QUEUE" if not is_demo else "RECEIVED_DEMO_EVENT",
        "trackingToken": f"RKS-{event_id[:8].upper()}",
        "serverTimestamp": current,
        "isDemo": is_demo,
        "officialPoliceDispatch": "NOT_CONFIGURED",
    }


@app.post("/api/v1/emergency-locations", dependencies=[Depends(require_device)])
async def receive_location_update(payload: DeviceLocationUpdate) -> dict[str, Any]:
    current = utc_ms()
    with db() as conn:
        incident = conn.execute("SELECT event_id, is_demo FROM incidents WHERE event_id = ?", (payload.eventId,)).fetchone()
        if incident is None:
            raise HTTPException(status_code=404, detail="Emergency event does not exist on this server.")
        conn.execute(
            """INSERT INTO location_updates (id, event_id, timestamp, latitude, longitude, accuracy_meters,
               speed_kmph, battery_pct, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (str(uuid.uuid4()), payload.eventId, payload.timestamp, payload.latitude, payload.longitude,
             payload.accuracyMeters, payload.speedKmph, payload.batteryPct, current),
        )
        conn.execute(
            "UPDATE incidents SET last_location_at = ?, last_latitude = ?, last_longitude = ?, latitude = ?, longitude = ?, accuracy_meters = ?, updated_at = ? WHERE event_id = ?",
            (payload.timestamp, payload.latitude, payload.longitude, payload.latitude, payload.longitude,
             payload.accuracyMeters, current, payload.eventId),
        )
        row = conn.execute("SELECT * FROM incidents WHERE event_id = ?", (payload.eventId,)).fetchone()
    incident_dict = row_to_incident(row) or {}
    await manager.broadcast("EMERGENCY_LOCATION_UPDATED", {"incident": incident_dict, "location": payload.model_dump()})
    return {"recorded": True, "liveSessionUrl": f"/control-room/map?incident={payload.eventId}", "storedAt": current}


@app.post("/api/v1/trusted-contact-alerts", dependencies=[Depends(require_device)])
async def receive_trusted_contact_alert(payload: TrustedContactAlert) -> dict[str, Any]:
    alert_id = str(uuid.uuid4())
    details = payload.model_dump()
    # Store a pending delivery request, not a success claim. Providers are intentionally not configured by default.
    delivery_state = "PENDING_PROVIDER_CONFIGURATION"
    with db() as conn:
        conn.execute(
            "INSERT INTO trusted_contact_alerts (alert_id, event_id, created_at, contact_count, delivery_status, channels_json, details_json) VALUES (?, ?, ?, ?, ?, ?, ?)",
            (alert_id, payload.eventId, utc_ms(), len(payload.contacts), delivery_state, "[]", json.dumps(details)),
        )
    create_audit("ANDROID_APP", "TRUSTED_CONTACT_ALERT_QUEUED", payload.eventId,
                 f"Contact notification request queued for provider configuration; contacts={len(payload.contacts)}.", False)
    await manager.broadcast("CONTACT_ALERT_QUEUED", {"alertId": alert_id, "eventId": payload.eventId, "status": delivery_state, "contactCount": len(payload.contacts)})
    return {
        "eventId": payload.eventId,
        "sentCount": 0,
        "failedCount": len(payload.contacts),
        "channelsDispatched": [],
        "deliveryStatus": delivery_state,
        "message": "Request stored. No SMS, email, or push provider is configured, so no contact was told this was delivered.",
    }


@app.post("/api/v1/police-alerts", dependencies=[Depends(require_device)])
async def receive_police_alert(payload: dict[str, Any]) -> dict[str, Any]:
    event_id = str(payload.get("eventId", "")).strip()
    if not event_id:
        raise HTTPException(status_code=422, detail="eventId is required.")
    incident = get_incident(event_id)
    if incident is None:
        raise HTTPException(status_code=404, detail="Create the incident before submitting a control-room alert.")
    ticket = f"RKS-CR-{event_id[:8].upper()}"
    create_audit("ANDROID_APP", "CONTROL_ROOM_ALERT_QUEUED", event_id,
                 "Android alert associated with the Raksha360 Control Room queue. No official police gateway is configured.", incident["isDemo"])
    await manager.broadcast("CONTROL_ROOM_ALERT_QUEUED", {"eventId": event_id, "ticket": ticket, "externalGatewayStatus": "NOT_CONFIGURED"})
    # acknowledged=false is intentional. The Android client must not mistake local queue insertion for police dispatch.
    return {
        "acknowledged": False,
        "controlRoomTicketId": ticket,
        "assignedUnit": None,
        "estimatedResponseMinutes": None,
        "message": "The event is in Raksha360's local control-room queue. No official police dispatch gateway is configured; call 112 directly for an emergency.",
        "externalGatewayStatus": "NOT_CONFIGURED",
    }


@app.post("/api/v1/reports")
async def create_report(payload: ReportCreate, actor: str = Depends(require_any_api_client)) -> dict[str, Any]:
    return await _create_report(payload, actor)


async def _create_report(payload: ReportCreate, source: str) -> dict[str, Any]:
    report_id = str(uuid.uuid4())
    is_demo = bool(payload.isDemoMode)
    priority = "High" if payload.category.lower() in {"harassment", "child safety", "missing child", "violence", "assault"} else "Medium"
    current = utc_ms()
    with db() as conn:
        conn.execute(
            """INSERT INTO reports (report_id, title, category, description, location_description, latitude, longitude,
               reported_at, status, priority, source, source_event_id, evidence_sha256, is_demo, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'New', ?, ?, ?, ?, ?, ?, ?)""",
            (report_id, payload.title.strip(), payload.category.strip(), payload.description.strip(), payload.locationDescription,
             payload.latitude, payload.longitude, payload.reportedAt, priority, source, payload.sourceEventId,
             payload.evidenceSha256, int(is_demo), current, current),
        )
        row = conn.execute("SELECT * FROM reports WHERE report_id = ?", (report_id,)).fetchone()
    report = row_to_report(row) or {}
    create_audit(source, "REPORT_SUBMITTED", report_id, f"{payload.category} report submitted to local Raksha360 Control Room.", is_demo)
    await manager.broadcast("REPORT_SUBMITTED", report)
    return {"reportId": report_id, "status": "New", "receivedAt": current, "isDemo": is_demo, "message": "Report recorded in the Raksha360 Control Room queue. It has not been filed with an official police system."}


# ---------- Dashboard REST API ----------
@app.get("/api/v1/dashboard/overview", dependencies=[Depends(require_dashboard)])
def dashboard_overview() -> dict[str, Any]:
    with db() as conn:
        all_events = conn.execute("SELECT COUNT(*) FROM incidents WHERE is_demo = 0").fetchone()[0]
        demo_events = conn.execute("SELECT COUNT(*) FROM incidents WHERE is_demo = 1").fetchone()[0]
        open_events = conn.execute("SELECT COUNT(*) FROM incidents WHERE status NOT IN ('Resolved','False Alarm') AND is_demo = 0").fetchone()[0]
        critical_events = conn.execute("SELECT COUNT(*) FROM incidents WHERE severity = 'Critical' AND status NOT IN ('Resolved','False Alarm') AND is_demo = 0").fetchone()[0]
        child_events = conn.execute("SELECT COUNT(*) FROM incidents WHERE trigger_type IN ('CHILD_SOS','LOST_CHILD','JOURNEY_TIMEOUT','GEOFENCE_EXIT','MISSED_CHECK_IN','SCHOOL_HANDOVER_ESCALATION') AND status NOT IN ('Resolved','False Alarm') AND is_demo = 0").fetchone()[0]
        open_reports = conn.execute("SELECT COUNT(*) FROM reports WHERE status NOT IN ('Closed','Rejected') AND is_demo = 0").fetchone()[0]
        pending_dispatches = conn.execute("SELECT COUNT(*) FROM dispatches WHERE status IN ('REQUESTED','ACKNOWLEDGED_IN_CONTROL_ROOM') AND is_demo = 0").fetchone()[0]
        contacts_pending = conn.execute("SELECT COUNT(*) FROM trusted_contact_alerts WHERE delivery_status != 'DELIVERED'").fetchone()[0]
        incidents = [row_to_incident(r) for r in conn.execute("SELECT * FROM incidents WHERE is_demo = 0 ORDER BY reported_at DESC LIMIT 8").fetchall()]
    return {
        "metrics": {
            "liveIncidents": all_events,
            "openIncidents": open_events,
            "criticalIncidents": critical_events,
            "childSafetyEvents": child_events,
            "openReports": open_reports,
            "pendingDispatches": pending_dispatches,
            "contactRequestsPendingProvider": contacts_pending,
            "demoIncidents": demo_events,
        },
        "recentIncidents": incidents,
        "officialPoliceGateway": "NOT_CONFIGURED",
        "notificationProviders": "NOT_CONFIGURED",
        "generatedAt": utc_ms(),
    }


@app.get("/api/v1/incidents", dependencies=[Depends(require_dashboard)])
def list_incidents(
    status: str | None = Query(default=None, max_length=80),
    severity: str | None = Query(default=None, max_length=40),
    trigger: str | None = Query(default=None, max_length=80),
    q: str | None = Query(default=None, max_length=200),
    include_demo: bool = True,
    limit: int = Query(default=200, ge=1, le=500),
) -> dict[str, Any]:
    clauses: list[str] = []
    args: list[Any] = []
    if status and status != "All": clauses.append("status = ?"); args.append(status)
    if severity and severity != "All": clauses.append("severity = ?"); args.append(severity)
    if trigger and trigger != "All": clauses.append("trigger_type = ?"); args.append(trigger)
    if not include_demo: clauses.append("is_demo = 0")
    if q and q.strip():
        clauses.append("(event_id LIKE ? OR trigger_type LIKE ? OR location_description LIKE ? OR notes LIKE ?)")
        term = f"%{q.strip()}%"
        args.extend([term, term, term, term])
    where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
    with db() as conn:
        total = conn.execute(f"SELECT COUNT(*) FROM incidents {where}", args).fetchone()[0]
        rows = conn.execute(f"SELECT * FROM incidents {where} ORDER BY reported_at DESC LIMIT ?", [*args, limit]).fetchall()
    return {"items": [row_to_incident(r) for r in rows], "total": total, "generatedAt": utc_ms()}


@app.get("/api/v1/incidents/{event_id}", dependencies=[Depends(require_dashboard)])
def incident_detail(event_id: str) -> dict[str, Any]:
    item = get_incident(event_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Incident not found.")
    with db() as conn:
        locations = conn.execute("SELECT * FROM location_updates WHERE event_id = ? ORDER BY timestamp DESC LIMIT 100", (event_id,)).fetchall()
        dispatches = conn.execute("SELECT * FROM dispatches WHERE event_id = ? ORDER BY requested_at DESC", (event_id,)).fetchall()
    item["locationHistory"] = [dict(r) for r in locations]
    item["dispatchHistory"] = [row_to_dispatch(r) for r in dispatches]
    return item


def haversine_distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Accurate great circle distance between two points on the earth in km."""
    R = 6371.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    a = math.sin(delta_phi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return round(R * c, 2)


def format_tel_uri(phone: str | None) -> str | None:
    if not phone:
        return None
    cleaned = phone.split("/")[0].split(",")[0].strip()
    digits = re.sub(r"[^\d+]", "", cleaned)
    if not digits:
        return None
    if digits.startswith("+"):
        return f"tel:{digits}"
    if digits.startswith("0"):
        return f"tel:+91{digits[1:]}"
    if len(digits) == 10:
        return f"tel:+91{digits}"
    if len(digits) in (7, 8):
        return f"tel:+91712{digits}"
    return f"tel:{digits}"


@app.get("/api/v1/incidents/{event_id}/nearby-stations", dependencies=[Depends(require_dashboard)])
def incident_nearby_stations(
    event_id: str,
    limit: int = Query(default=5, ge=1, le=50),
    radius_km: float = Query(default=10.0, gt=0.0, le=100.0),
) -> dict[str, Any]:
    incident = get_incident(event_id)
    if incident is None:
        raise HTTPException(status_code=404, detail="Incident not found.")

    inc_lat = incident.get("latitude")
    inc_lon = incident.get("longitude")
    if inc_lat is None or inc_lon is None:
        inc_lat = incident.get("lastLatitude")
        inc_lon = incident.get("lastLongitude")

    disclaimer = "Nearby public-directory stations — distance estimate; confirm jurisdiction and availability."

    if (
        inc_lat is None
        or inc_lon is None
        or not (-90.0 <= inc_lat <= 90.0)
        or not (-180.0 <= inc_lon <= 180.0)
    ):
        return {
            "incidentId": event_id,
            "locationAvailable": False,
            "incidentCoordinates": None,
            "radiusKm": radius_km,
            "totalFound": 0,
            "stations": [],
            "disclaimer": disclaimer,
            "message": "Location unavailable — nearby stations cannot be ranked yet.",
            "officialPoliceGateway": "NOT_CONFIGURED",
        }

    with db() as conn:
        rows = conn.execute(
            """SELECT station_id, name, name_mr, phone, email, address, jurisdiction_label,
                      latitude, longitude, coordinates_verified, coordinate_source, coordinate_status,
                      osm_type, osm_id, source_url
               FROM police_station_directory
               WHERE latitude IS NOT NULL AND longitude IS NOT NULL"""
        ).fetchall()

    candidate_stations: list[dict[str, Any]] = []
    seen_ids: set[str] = set()

    for row in rows:
        sid = row["station_id"]
        if sid in seen_ids:
            continue
        seen_ids.add(sid)

        s_lat = float(row["latitude"])
        s_lon = float(row["longitude"])
        if not (-90.0 <= s_lat <= 90.0) or not (-180.0 <= s_lon <= 180.0):
            continue

        dist = haversine_distance_km(inc_lat, inc_lon, s_lat, s_lon)
        if dist <= radius_km:
            c_status = row["coordinate_status"] or ("HUMAN_VERIFIED_COORDINATE" if row["coordinates_verified"] else "OSM_COORDINATE_CANDIDATE")
            osm_url = f"https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route={inc_lat}%2C{inc_lon}%3B{s_lat}%2C{s_lon}"
            candidate_stations.append({
                "stationId": sid,
                "name": row["name"],
                "nameMr": row["name_mr"],
                "phone": row["phone"],
                "telUri": format_tel_uri(row["phone"]),
                "email": row["email"],
                "address": row["address"],
                "latitude": s_lat,
                "longitude": s_lon,
                "distanceKm": dist,
                "coordinatesVerified": bool(row["coordinates_verified"]),
                "coordinateStatus": c_status,
                "coordinateSource": row["coordinate_source"] or "Nagpur City Police Public Directory",
                "osmType": row["osm_type"],
                "osmId": row["osm_id"],
                "osmDirectionsUrl": osm_url,
                "sourceUrl": row["source_url"],
            })

    candidate_stations.sort(key=lambda s: s["distanceKm"])
    total_found = len(candidate_stations)
    ranked = candidate_stations[:limit]

    return {
        "incidentId": event_id,
        "locationAvailable": True,
        "incidentCoordinates": {
            "latitude": inc_lat,
            "longitude": inc_lon,
            "accuracyMeters": incident.get("accuracyMeters"),
            "isLastKnown": bool(incident.get("isLastKnownLocation")),
            "lastLocationAt": incident.get("lastLocationAt"),
        },
        "radiusKm": radius_km,
        "totalFound": total_found,
        "stations": ranked,
        "disclaimer": disclaimer,
        "officialPoliceGateway": "NOT_CONFIGURED",
    }


@app.patch("/api/v1/incidents/{event_id}/status", dependencies=[Depends(require_dashboard)])
async def update_incident_status(event_id: str, payload: StatusUpdate) -> dict[str, Any]:
    with db() as conn:
        row = conn.execute("SELECT * FROM incidents WHERE event_id = ?", (event_id,)).fetchone()
        if row is None: raise HTTPException(status_code=404, detail="Incident not found.")
        conn.execute("UPDATE incidents SET status = ?, notes = ?, updated_at = ? WHERE event_id = ?", (payload.status, payload.notes or row["notes"], utc_ms(), event_id))
        updated = conn.execute("SELECT * FROM incidents WHERE event_id = ?", (event_id,)).fetchone()
    item = row_to_incident(updated) or {}
    create_audit("CONTROL_ROOM", "INCIDENT_STATUS_CHANGED", event_id, f"Incident status changed to {payload.status}.", item["isDemo"])
    await manager.broadcast("INCIDENT_UPDATED", item)
    return item


@app.get("/api/v1/reports", dependencies=[Depends(require_dashboard)])
def list_reports(
    status: str | None = Query(default=None, max_length=80),
    q: str | None = Query(default=None, max_length=200),
    include_demo: bool = True,
    limit: int = Query(default=200, ge=1, le=500),
) -> dict[str, Any]:
    clauses: list[str] = []
    args: list[Any] = []
    if status and status != "All": clauses.append("status = ?"); args.append(status)
    if q and q.strip():
        clauses.append("(report_id LIKE ? OR title LIKE ? OR category LIKE ? OR description LIKE ?)")
        term = f"%{q.strip()}%"; args.extend([term, term, term, term])
    if not include_demo: clauses.append("is_demo = 0")
    where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
    with db() as conn:
        total = conn.execute(f"SELECT COUNT(*) FROM reports {where}", args).fetchone()[0]
        rows = conn.execute(f"SELECT * FROM reports {where} ORDER BY reported_at DESC LIMIT ?", [*args, limit]).fetchall()
    return {"items": [row_to_report(r) for r in rows], "total": total}


@app.get("/api/v1/reports/{report_id}", dependencies=[Depends(require_dashboard)])
def report_detail(report_id: str) -> dict[str, Any]:
    report = get_report(report_id)
    if report is None: raise HTTPException(status_code=404, detail="Report not found.")
    return report


@app.patch("/api/v1/reports/{report_id}/status", dependencies=[Depends(require_dashboard)])
async def update_report_status(report_id: str, payload: ReportStatusUpdate) -> dict[str, Any]:
    with db() as conn:
        row = conn.execute("SELECT * FROM reports WHERE report_id = ?", (report_id,)).fetchone()
        if row is None: raise HTTPException(status_code=404, detail="Report not found.")
        priority = payload.priority or row["priority"]
        conn.execute("UPDATE reports SET status = ?, priority = ?, updated_at = ? WHERE report_id = ?", (payload.status, priority, utc_ms(), report_id))
        updated = conn.execute("SELECT * FROM reports WHERE report_id = ?", (report_id,)).fetchone()
    item = row_to_report(updated) or {}
    create_audit("CONTROL_ROOM", "REPORT_STATUS_CHANGED", report_id, f"Report status changed to {payload.status}; priority={priority}.", item["isDemo"])
    await manager.broadcast("REPORT_UPDATED", item)
    return item


@app.get("/api/v1/dispatches", dependencies=[Depends(require_dashboard)])
def list_dispatches(status: str | None = Query(default=None), include_demo: bool = True, limit: int = Query(default=200, ge=1, le=500)) -> dict[str, Any]:
    clauses: list[str] = []
    args: list[Any] = []
    if status and status != "All": clauses.append("status = ?"); args.append(status)
    if not include_demo: clauses.append("is_demo = 0")
    where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
    with db() as conn:
        rows = conn.execute(f"SELECT * FROM dispatches {where} ORDER BY requested_at DESC LIMIT ?", [*args, limit]).fetchall()
    return {"items": [row_to_dispatch(r) for r in rows], "total": len(rows), "externalGateway": "NOT_CONFIGURED"}


@app.post("/api/v1/incidents/{event_id}/dispatch", dependencies=[Depends(require_dashboard)])
async def create_dispatch(event_id: str, payload: DispatchCreate) -> dict[str, Any]:
    incident = get_incident(event_id)
    if incident is None: raise HTTPException(status_code=404, detail="Incident not found.")
    dispatch_id = str(uuid.uuid4())
    current = utc_ms()
    with db() as conn:
        conn.execute(
            """INSERT INTO dispatches (dispatch_id, event_id, requested_at, updated_at, status, station,
               assigned_officer, unit_label, channel, notes, is_demo, external_gateway_status)
               VALUES (?, ?, ?, ?, 'REQUESTED', ?, ?, ?, ?, ?, ?, 'NOT_CONFIGURED')""",
            (dispatch_id, event_id, current, current, payload.station, payload.assignedOfficer,
             payload.unitLabel, payload.channel, payload.notes, int(incident["isDemo"])),
        )
        conn.execute("UPDATE incidents SET status = 'Assigned', assignment_station = ?, assigned_officer = ?, updated_at = ? WHERE event_id = ?",
                     (payload.station, payload.assignedOfficer, current, event_id))
        dispatch_row = conn.execute("SELECT * FROM dispatches WHERE dispatch_id = ?", (dispatch_id,)).fetchone()
        incident_row = conn.execute("SELECT * FROM incidents WHERE event_id = ?", (event_id,)).fetchone()
    dispatch = row_to_dispatch(dispatch_row) or {}
    item = row_to_incident(incident_row) or {}
    create_audit("CONTROL_ROOM", "INTERNAL_DISPATCH_TASK_CREATED", event_id,
                 "Internal dispatch task created. External police/patrol dispatch is not connected.", item["isDemo"])
    await manager.broadcast("DISPATCH_TASK_CREATED", {"dispatch": dispatch, "incident": item})
    await manager.broadcast("INCIDENT_UPDATED", item)
    return {
        **dispatch,
        "externalDispatchSent": False,
        "message": "Internal dispatch task created. Confirm with the authorized control room or call 112 manually; no external police gateway is connected.",
    }


@app.patch("/api/v1/dispatches/{dispatch_id}/status", dependencies=[Depends(require_dashboard)])
async def update_dispatch_status(dispatch_id: str, payload: DispatchStatusUpdate) -> dict[str, Any]:
    with db() as conn:
        row = conn.execute("SELECT * FROM dispatches WHERE dispatch_id = ?", (dispatch_id,)).fetchone()
        if row is None: raise HTTPException(status_code=404, detail="Dispatch task not found.")
        notes = (row["notes"] + "\n" + payload.notes).strip() if payload.notes else row["notes"]
        conn.execute("UPDATE dispatches SET status = ?, notes = ?, updated_at = ? WHERE dispatch_id = ?", (payload.status, notes, utc_ms(), dispatch_id))
        updated = conn.execute("SELECT * FROM dispatches WHERE dispatch_id = ?", (dispatch_id,)).fetchone()
    item = row_to_dispatch(updated) or {}
    create_audit("CONTROL_ROOM", "DISPATCH_TASK_STATUS_CHANGED", item["event_id"], f"Internal dispatch task status: {payload.status}.", item["isDemo"])
    await manager.broadcast("DISPATCH_TASK_UPDATED", item)
    return {**item, "externalDispatchSent": False}


@app.get("/api/v1/police-station-directory", dependencies=[Depends(require_dashboard)])
def list_police_station_directory() -> dict[str, Any]:
    """Read-only official public-directory snapshot; not a police dispatch integration."""
    with db() as conn:
        rows = conn.execute(
            "SELECT * FROM police_station_directory ORDER BY name COLLATE NOCASE"
        ).fetchall()
    items = []
    for row in rows:
        item = dict(row)
        item["coordinates_verified"] = bool(item.get("coordinates_verified"))
        item["coordinateStatus"] = item.get("coordinate_status") or ("HUMAN_VERIFIED_COORDINATE" if item.get("coordinates_verified") else "OFFICIAL_ADDRESS_ONLY")
        item["telUri"] = format_tel_uri(item.get("phone"))
        item["isOfficialPublicDirectory"] = True
        item["isDemo"] = False
        item["status"] = "PUBLIC_DIRECTORY_REFERENCE"
        items.append(item)
    return {
        "items": items,
        "total": len(items),
        "dataClass": "OFFICIAL_PUBLIC_DIRECTORY",
        "sourceUrl": "https://nagpurpolice.gov.in/police-stations",
        "retrievedOn": "2026-10-11",
        "note": "Public directory reference only. This response does not report operational availability and does not connect Raksha360 to official police dispatch. Coordinates remain absent until individually verified.",
    }


@app.get("/api/v1/stations", dependencies=[Depends(require_dashboard)])
def list_stations() -> dict[str, Any]:
    with db() as conn:
        rows = conn.execute("SELECT * FROM stations ORDER BY name").fetchall()
    return {"items": [dict(r) | {"isDemo": bool(r["is_demo"])} for r in rows], "total": len(rows), "note": "Demo units are synthetic. Import a verified station directory before operational use."}


@app.get("/api/v1/public-helplines", dependencies=[Depends(require_any_api_client)])
def get_public_helplines() -> dict[str, Any]:
    """Read-only official public emergency numbers and Nagpur City Police contacts."""
    helplines_path = ROOT_DIR.parent / "reference-data" / "nagpur-public" / "official_helplines_and_links.json"
    if helplines_path.is_file():
        try:
            return json.loads(helplines_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            pass
    return {
        "metadata": {
            "data_class": "OFFICIAL_PUBLIC_CONTACTS",
            "retrieved_on": "2026-10-11",
            "note": "Confirm contact numbers on official sources before operational or emergency reliance.",
        },
        "contacts": [
            {"id": "india-emergency-112", "name": "Emergency Response Support System", "category": "emergency", "phone": "112", "scope": "India-wide emergency number shown on Nagpur City Police website", "source_url": "https://nagpurpolice.gov.in/police-stations"},
            {"id": "nagpur-women-1091", "name": "Women Helpline", "category": "women_safety", "phone": "1091", "scope": "Shown on Nagpur City Police website", "source_url": "https://nagpurpolice.gov.in/police-stations"},
            {"id": "child-1098", "name": "Child Helpline", "category": "child_safety", "phone": "1098", "scope": "Shown on Nagpur City Police website", "source_url": "https://nagpurpolice.gov.in/police-stations"},
            {"id": "cybercrime-1930", "name": "Cyber Crime Helpline", "category": "cybercrime", "phone": "1930", "scope": "Shown on Nagpur City Police website", "source_url": "https://nagpurpolice.gov.in/police-stations"},
            {"id": "ambulance-108", "name": "Ambulance", "category": "medical_emergency", "phone": "108", "scope": "Shown on Nagpur City Police website", "source_url": "https://nagpurpolice.gov.in/police-stations"},
            {"id": "nagpur-police-office", "name": "Nagpur City Police office", "category": "police_office", "phone": "0712-2560601", "scope": "Police Bhavan, behind Collector Office, West High Court Road, Civil Lines, Nagpur, Maharashtra", "source_url": "https://nagpurpolice.gov.in/contact"},
            {"id": "nagpur-control-room", "name": "Nagpur City Police control room", "category": "police_control_room", "phone": "+91-712-2563232", "scope": "Contact page lists this number for the control room; verify before reliance", "source_url": "https://nagpurpolice.gov.in/contact"},
        ],
        "official_links": [
            {"name": "Nagpur City Police public station directory", "url": "https://nagpurpolice.gov.in/police-stations"},
            {"name": "Nagpur City Police contact page", "url": "https://nagpurpolice.gov.in/contact"},
            {"name": "National Cyber Crime Reporting Portal", "url": "https://cybercrime.gov.in/"},
            {"name": "Government of India Crime in India 2023 catalog", "url": "https://www.data.gov.in/catalog/crime-india-2023"},
        ],
    }


@app.get("/api/v1/ncrb-manifest", dependencies=[Depends(require_any_api_client)])
def get_ncrb_manifest() -> dict[str, Any]:
    """NCRB Crime in India 2023 source manifest and city statistics status."""
    manifest_path = ROOT_DIR.parent / "reference-data" / "nagpur-public" / "ncrb_2023_city_statistics_source_manifest.json"
    if manifest_path.is_file():
        try:
            return json.loads(manifest_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            pass
    return {
        "title": "NCRB Crime in India 2023 — validated extraction manifest",
        "retrieved_on": "2026-10-11",
        "source_url": "https://www.data.gov.in/catalog/crime-india-2023",
        "status": "SOURCE_IDENTIFIED_VALUES_NOT_IMPORTED",
        "reason": "No numeric Nagpur row is included without validated extraction of the exact official table. Do not infer or fabricate these values.",
        "important_caveat": "Annual crime statistics are aggregate counts for a publication year, may be revised, are not geolocated incidents, and must not be shown as live incident pins or predictions.",
    }


@app.get("/api/v1/audit-log", dependencies=[Depends(require_dashboard)])
def get_audit_log(limit: int = Query(default=100, ge=1, le=500)) -> dict[str, Any]:
    with db() as conn:
        rows = conn.execute("SELECT * FROM audit_log ORDER BY created_at DESC LIMIT ?", (limit,)).fetchall()
    items = []
    for row in rows:
        item = dict(row); item["isDemo"] = bool(item.pop("is_demo", 0)); items.append(item)
    return {"items": items, "total": len(items)}


@app.websocket("/ws/police")
async def police_websocket(websocket: WebSocket, token: str = Query(default="")) -> None:
    env = os.getenv("RAKSHA360_ENV", os.getenv("ENVIRONMENT", "development")).strip().lower()
    is_production = env in ("production", "prod", "staging")

    if is_production and token:
        # Strictly reject query tokens in production to prevent credentials appearing in reverse-proxy logs
        await websocket.close(code=1008, reason="URL query tokens prohibited in production. Authenticate via AUTH frame.")
        return

    # Accept the connection first so client can authenticate over secure JSON frame
    await websocket.accept()

    authenticated = False
    expected_token = get_dashboard_token()

    if token and not is_production:
        # Legacy development query token compatibility
        if constant_time_token_match(token, expected_token):
            authenticated = True
        else:
            await websocket.close(code=1008, reason="Dashboard token invalid")
            return

    if not authenticated:
        try:
            raw_msg = await asyncio.wait_for(websocket.receive_text(), timeout=5.0)
            data = json.loads(raw_msg)
            if not isinstance(data, dict) or data.get("type") != "AUTH":
                await websocket.close(code=1008, reason="Initial message must be an AUTH frame")
                return
            if not constant_time_token_match(data.get("token"), expected_token):
                await websocket.close(code=1008, reason="Invalid authentication token")
                return
            authenticated = True
        except asyncio.TimeoutError:
            await websocket.close(code=1008, reason="Authentication timed out")
            return
        except Exception:
            await websocket.close(code=1008, reason="Authentication failed")
            return

    # Connection is verified and authenticated; register with connection manager
    async with manager.lock:
        manager.connections.add(websocket)
    await websocket.send_json({"type": "CONNECTED", "data": {"at": now_iso(), "service": "Raksha360 Control Room"}})

    try:
        while True:
            # Keep the connection alive and allow client ping/diagnostics; the server does not treat client messages as incidents.
            try:
                message = await asyncio.wait_for(websocket.receive_text(), timeout=25)
                if message == "ping":
                    await websocket.send_json({"type": "PONG", "data": {"at": now_iso()}})
            except asyncio.TimeoutError:
                await websocket.send_json({"type": "HEARTBEAT", "data": {"at": now_iso()}})
    except WebSocketDisconnect:
        await manager.disconnect(websocket)
    except Exception:
        await manager.disconnect(websocket)
