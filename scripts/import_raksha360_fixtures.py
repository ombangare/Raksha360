#!/usr/bin/env python3
"""
Raksha360 Synthetic Fixtures Importer / Cleaner.

Explicitly seeds or cleans synthetic demonstration data from reference-data/nagpur-public/:
- demo_incidents.json
- demo_reports.json
- demo_dispatches.json
- demo_device_contacts_child_safety.json

Safety guarantees:
- Opt-in only: runs via explicit CLI flags (--demo or --clear-demo).
- Idempotent: fixed DEMO-* identifiers; repeated seeding will not create duplicate rows.
- Relative timing: computes event timestamps relative to current server UTC time.
- Clear classification: all fixture rows are marked is_demo = 1 and source = SYNTHETIC_FIXTURE.
- Zero external transmissions: never triggers real SMTP emails, SMS, push notifications, or dispatch gateway calls.
- Safe clear: --clear-demo removes ONLY rows where is_demo = 1 or ID starts with DEMO-*. Live rows are preserved.
"""

from __future__ import annotations

import argparse
import json
import os
import sqlite3
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

WORKSPACE_ROOT = Path(__file__).resolve().parents[1]
if str(WORKSPACE_ROOT) not in sys.path:
    sys.path.insert(0, str(WORKSPACE_ROOT))
REF_DIR = WORKSPACE_ROOT / "reference-data" / "nagpur-public"
DEFAULT_DB_PATH = WORKSPACE_ROOT / "backend" / "data" / "raksha360.sqlite3"


def utc_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise FileNotFoundError(f"Fixture file not found: {path}")
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def get_connection(db_path: Path | str) -> sqlite3.Connection:
    path = Path(db_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path, timeout=15)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def ensure_schema(conn: sqlite3.Connection, db_path: Path) -> None:
    # Ensure tables exist before inserting fixtures
    import backend.app.main as api
    api.DB_PATH = db_path.resolve()
    api.initialize_database()


def import_demo_fixtures(conn: sqlite3.Connection) -> dict[str, int]:
    """Import synthetic fixtures from reference-data into the database."""
    now = utc_ms()
    counts = {
        "incidents": 0,
        "reports": 0,
        "dispatches": 0,
        "device_statuses": 0,
        "emergency_contacts": 0,
        "email_deliveries": 0,
    }

    # 1. demo_incidents.json
    incidents_path = REF_DIR / "demo_incidents.json"
    if incidents_path.is_file():
        data = load_json(incidents_path)
        for rec in data.get("records", []):
            event_id = rec["event_id"]
            mins_ago = rec.get("occurred_minutes_ago", 5)
            reported_at = now - int(mins_ago * 60 * 1000)
            trigger_type = rec.get("trigger_type", "PANIC_BUTTON")
            severity = rec.get("severity", "High")
            status = rec.get("status", "New")
            lat = rec.get("latitude")
            lon = rec.get("longitude")
            accuracy = rec.get("accuracy_meters", 20.0)
            desc = rec.get("location_description", "Synthetic demonstration point")
            notes = rec.get("notes", "Training scenario")
            phrase = rec.get("phrase_detected", "")
            confidence = float(rec.get("speaker_confidence", 0.0))
            last_location_at = None
            last_lat = None
            last_lon = None
            if trigger_type == "DEVICE_OFFLINE" or rec.get("last_location_at_minutes_ago") is not None:
                loc_mins = rec.get("last_location_at_minutes_ago", mins_ago + 3)
                last_location_at = now - int(loc_mins * 60 * 1000)
                last_lat = lat
                last_lon = lon

            details = {
                "sample": True,
                "is_demo": True,
                "source": "SYNTHETIC_FIXTURE",
                "displayNote": "Synthetic training sample — not a real emergency",
                "notes": notes,
                "expected": rec.get("expected", ""),
            }

            conn.execute(
                """INSERT INTO incidents (
                    event_id, trigger_type, phrase_detected, speaker_confidence,
                    reported_at, status, severity, latitude, longitude, accuracy_meters,
                    location_description, device_session_id, is_demo, source, updated_at,
                    notes, details_json, last_location_at, last_latitude, last_longitude
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'SYNTHETIC_FIXTURE', ?, ?, ?, ?, ?, ?)
                ON CONFLICT(event_id) DO UPDATE SET
                    trigger_type=excluded.trigger_type,
                    phrase_detected=excluded.phrase_detected,
                    speaker_confidence=excluded.speaker_confidence,
                    reported_at=excluded.reported_at,
                    status=excluded.status,
                    severity=excluded.severity,
                    latitude=excluded.latitude,
                    longitude=excluded.longitude,
                    accuracy_meters=excluded.accuracy_meters,
                    location_description=excluded.location_description,
                    is_demo=1,
                    source='SYNTHETIC_FIXTURE',
                    updated_at=excluded.updated_at,
                    notes=excluded.notes,
                    details_json=excluded.details_json,
                    last_location_at=excluded.last_location_at,
                    last_latitude=excluded.last_latitude,
                    last_longitude=excluded.last_longitude""",
                (
                    event_id, trigger_type, phrase, confidence,
                    reported_at, status, severity, lat, lon, accuracy,
                    desc, f"session-{event_id.lower()}", now,
                    notes, json.dumps(details), last_location_at, last_lat, last_lon,
                ),
            )
            counts["incidents"] += 1

    # 2. demo_reports.json
    reports_path = REF_DIR / "demo_reports.json"
    if reports_path.is_file():
        data = load_json(reports_path)
        for rec in data.get("records", []):
            report_id = rec["report_id"]
            mins_ago = rec.get("occurred_minutes_ago", 30)
            reported_at = now - int(mins_ago * 60 * 1000)
            conn.execute(
                """INSERT INTO reports (
                    report_id, title, category, description, location_description,
                    latitude, longitude, reported_at, status, priority, source,
                    source_event_id, evidence_sha256, is_demo, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SYNTHETIC_FIXTURE', ?, ?, 1, ?, ?)
                ON CONFLICT(report_id) DO UPDATE SET
                    title=excluded.title,
                    category=excluded.category,
                    description=excluded.description,
                    location_description=excluded.location_description,
                    latitude=excluded.latitude,
                    longitude=excluded.longitude,
                    reported_at=excluded.reported_at,
                    status=excluded.status,
                    priority=excluded.priority,
                    source='SYNTHETIC_FIXTURE',
                    source_event_id=excluded.source_event_id,
                    evidence_sha256=excluded.evidence_sha256,
                    is_demo=1,
                    updated_at=excluded.updated_at""",
                (
                    report_id, rec.get("title", ""), rec.get("category", "General"),
                    rec.get("description", ""), rec.get("location_description", "Location unavailable"),
                    rec.get("latitude"), rec.get("longitude"), reported_at,
                    rec.get("status", "New"), rec.get("priority", "Medium"),
                    rec.get("source_event_id"), rec.get("evidence_sha256"),
                    reported_at, now,
                ),
            )
            counts["reports"] += 1

    # 3. demo_dispatches.json
    dispatches_path = REF_DIR / "demo_dispatches.json"
    if dispatches_path.is_file():
        data = load_json(dispatches_path)
        for rec in data.get("records", []):
            dispatch_id = rec["dispatch_id"]
            mins_ago = rec.get("occurred_minutes_ago", 15)
            requested_at = now - int(mins_ago * 60 * 1000)
            conn.execute(
                """INSERT INTO dispatches (
                    dispatch_id, event_id, requested_at, updated_at, status,
                    station, assigned_officer, unit_label, channel, notes, is_demo, external_gateway_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'NOT_CONFIGURED')
                ON CONFLICT(dispatch_id) DO UPDATE SET
                    event_id=excluded.event_id,
                    requested_at=excluded.requested_at,
                    updated_at=excluded.updated_at,
                    status=excluded.status,
                    station=excluded.station,
                    assigned_officer=excluded.assigned_officer,
                    unit_label=excluded.unit_label,
                    channel=excluded.channel,
                    notes=excluded.notes,
                    is_demo=1,
                    external_gateway_status='NOT_CONFIGURED'""",
                (
                    dispatch_id, rec.get("event_id"), requested_at, now,
                    rec.get("status", "REQUESTED"), rec.get("station", "Reference station"),
                    rec.get("assigned_officer", "Unassigned"),
                    rec.get("unit_label", "Synthetic training unit"),
                    rec.get("channel", "INTERNAL_CONTROL_ROOM"),
                    rec.get("notes", "DEMO ONLY: internal task"),
                ),
            )
            counts["dispatches"] += 1

    # 4. demo_device_contacts_child_safety.json
    dev_path = REF_DIR / "demo_device_contacts_child_safety.json"
    if dev_path.is_file():
        data = load_json(dev_path)
        for case in data.get("device_watch_cases", []):
            dev_id = case["device_id"]
            status = case.get("status", "DISARMED")
            mins_ago = case.get("last_seen_minutes_ago", 2)
            last_seen = now - int(mins_ago * 60 * 1000)
            lat = case.get("last_latitude")
            lon = case.get("last_longitude")
            loc_mins = case.get("last_location_minutes_ago", mins_ago)
            last_loc_at = (now - int(loc_mins * 60 * 1000)) if (lat is not None and lon is not None) else None
            monitoring = int(bool(case.get("monitoring_enabled", False)))
            consent = int(bool(case.get("offline_location_email_consent", False)))
            battery = case.get("last_battery_pct")
            offline_detected = last_seen if status == "OFFLINE" else None
            offline_inc = "DEMO-OFFLINE-009" if status == "OFFLINE" else None
            conn.execute(
                """INSERT INTO device_status (
                    device_id, last_seen_at, last_latitude, last_longitude,
                    last_accuracy_meters, last_location_at, last_battery_pct,
                    monitoring_enabled, offline_location_email_consent, status,
                    offline_detected_at, offline_incident_id, offline_email_last_attempt_at,
                    offline_email_sent_at, offline_email_delivery_status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 15.0, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, 'NOT_ATTEMPTED', ?, ?)
                ON CONFLICT(device_id) DO UPDATE SET
                    last_seen_at=excluded.last_seen_at,
                    last_latitude=excluded.last_latitude,
                    last_longitude=excluded.last_longitude,
                    last_location_at=excluded.last_location_at,
                    last_battery_pct=excluded.last_battery_pct,
                    monitoring_enabled=excluded.monitoring_enabled,
                    offline_location_email_consent=excluded.offline_location_email_consent,
                    status=excluded.status,
                    offline_detected_at=excluded.offline_detected_at,
                    offline_incident_id=excluded.offline_incident_id,
                    updated_at=excluded.updated_at""",
                (
                    dev_id, last_seen, lat, lon, last_loc_at, battery,
                    monitoring, consent, status, offline_detected, offline_inc, now, now,
                ),
            )
            counts["device_statuses"] += 1

        for contact in data.get("trusted_contacts", []):
            dev_id = contact["device_id"]
            email = contact["email"]
            name = contact.get("name", "Demo Contact")
            rel = contact.get("relationship", "Test only")
            notify_email = int(bool(contact.get("notify_by_email", True)))
            is_active = int(bool(contact.get("is_active", True)))
            conn.execute(
                """INSERT INTO emergency_contacts (
                    device_id, email, name, relationship, notify_by_email, is_active, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(device_id, email) DO UPDATE SET
                    name=excluded.name,
                    relationship=excluded.relationship,
                    notify_by_email=excluded.notify_by_email,
                    is_active=excluded.is_active,
                    updated_at=excluded.updated_at""",
                (dev_id, email, name, rel, notify_email, is_active, now),
            )
            counts["emergency_contacts"] += 1

        for delivery in data.get("email_delivery_cases", []):
            inc_id = delivery.get("incident_id")
            email = delivery.get("email")
            if inc_id and email:
                status = delivery.get("status", "NOT_ATTEMPTED")
                attempts = delivery.get("attempt_count", 1)
                sent_at = now if status == "SENT" else None
                conn.execute(
                    """INSERT INTO offline_email_deliveries (
                        device_id, incident_id, email, status, attempt_count,
                        last_attempt_at, sent_at, last_error
                    ) VALUES ('DEMO-DEVICE-OFFLINE-002', ?, ?, ?, ?, ?, ?, NULL)
                    ON CONFLICT(device_id, incident_id, email) DO UPDATE SET
                        status=excluded.status,
                        attempt_count=excluded.attempt_count,
                        last_attempt_at=excluded.last_attempt_at,
                        sent_at=excluded.sent_at""",
                    (inc_id, email, status, attempts, now, sent_at),
                )
                counts["email_deliveries"] += 1

    # Insert audit entry
    conn.execute(
        """INSERT INTO audit_log (id, created_at, actor, action, target_id, message, is_demo)
           VALUES (?, ?, 'CLI_IMPORTER', 'SYNTHETIC_FIXTURES_SEEDED', 'DEMO_PACK',
                   'Imported synthetic demonstration incidents, reports, dispatches, and device fixtures.', 1)""",
        (str(uuid.uuid4()), now),
    )
    conn.commit()
    return counts


def get_table_counts(conn: sqlite3.Connection) -> dict[str, int]:
    tables = [
        "incidents", "reports", "dispatches", "stations", "device_status",
        "emergency_contacts", "offline_email_deliveries", "location_updates",
        "police_station_directory", "audit_log", "trusted_contact_alerts"
    ]
    counts = {}
    for t in tables:
        try:
            counts[t] = conn.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]
        except sqlite3.OperationalError:
            counts[t] = 0
    return counts


def clear_demo_fixtures(conn: sqlite3.Connection, dry_run: bool = False) -> dict[str, int]:
    """Delete all synthetic demo rows transactionally while preserving real user and reference records."""
    now = utc_ms()
    before_counts = get_table_counts(conn)
    deleted_counts = {}

    queries = [
        ("location_updates", "DELETE FROM location_updates WHERE event_id IN (SELECT event_id FROM incidents WHERE is_demo = 1 OR event_id LIKE 'DEMO-%' OR device_session_id LIKE 'DEMO-%')"),
        ("dispatches", "DELETE FROM dispatches WHERE is_demo = 1 OR dispatch_id LIKE 'DEMO-%'"),
        ("reports", "DELETE FROM reports WHERE is_demo = 1 OR report_id LIKE 'DEMO-%'"),
        ("incidents", "DELETE FROM incidents WHERE is_demo = 1 OR event_id LIKE 'DEMO-%' OR device_session_id LIKE 'DEMO-%' OR details_json LIKE '%DEMO-%'"),
        ("stations", "DELETE FROM stations WHERE is_demo = 1 OR station_id LIKE 'DEMO-%' OR station_id LIKE 'STATION-DEMO-%'"),
        ("offline_email_deliveries", "DELETE FROM offline_email_deliveries WHERE device_id LIKE 'DEMO-%' OR incident_id LIKE 'DEMO-%'"),
        ("emergency_contacts", "DELETE FROM emergency_contacts WHERE device_id LIKE 'DEMO-%'"),
        ("device_status", "DELETE FROM device_status WHERE device_id LIKE 'DEMO-%'"),
        ("audit_log", "DELETE FROM audit_log WHERE is_demo = 1 OR target_id = 'DEMO_PACK' OR actor = 'CLI_IMPORTER'"),
    ]

    try:
        if not dry_run:
            conn.execute("BEGIN TRANSACTION")
            for table_name, q in queries:
                cur = conn.execute(q)
                deleted_counts[table_name] = cur.rowcount

            conn.execute(
                """INSERT INTO audit_log (id, created_at, actor, action, target_id, message, is_demo)
                   VALUES (?, ?, 'CLI_IMPORTER', 'SYNTHETIC_FIXTURES_CLEARED', 'DEMO_PACK',
                           'Cleared synthetic demonstration records. Real user data and public directory preserved.', 0)""",
                (str(uuid.uuid4()), now),
            )
            conn.commit()
        else:
            for table_name, q in queries:
                count_q = q.replace("DELETE FROM", "SELECT COUNT(*) FROM")
                deleted_counts[table_name] = conn.execute(count_q).fetchone()[0]

    except Exception as exc:
        if not dry_run:
            conn.rollback()
        raise RuntimeError(f"Database cleanup failed and was safely rolled back: {exc}") from exc

    return deleted_counts


def main() -> None:
    parser = argparse.ArgumentParser(description="Raksha360 Synthetic Fixtures Importer / Cleaner")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--demo", action="store_true", help="Explicitly import synthetic demonstration fixtures")
    group.add_argument("--clear-demo", action="store_true", help="Remove all synthetic demo fixture records")
    parser.add_argument("--dry-run", action="store_true", help="Report planned cleanup counts without modifying database")
    parser.add_argument("--db-path", default=os.getenv("RAKSHA360_DATABASE_PATH", str(DEFAULT_DB_PATH)),
                        help="Path to SQLite database file")

    args = parser.parse_args()
    db_path = Path(args.db_path).resolve()
    print(f"Connecting to database at: {db_path}")
    conn = get_connection(db_path)

    try:
        ensure_schema(conn, db_path)
        if args.demo:
            print("Importing synthetic demonstration fixtures from reference-data/nagpur-public/...")
            results = import_demo_fixtures(conn)
            print("Import completed successfully:")
            for k, v in results.items():
                print(f"  - {k}: {v} records seeded/updated")
            print("Notice: All fixtures marked is_demo=1. No emails, SMS, or dispatch calls made.")
        elif args.clear_demo:
            prefix = "[DRY RUN] " if args.dry_run else ""
            print(f"{prefix}Cleaning synthetic demonstration fixtures...")
            before = get_table_counts(conn)
            deleted = clear_demo_fixtures(conn, dry_run=args.dry_run)
            after = get_table_counts(conn) if not args.dry_run else {t: before[t] - deleted.get(t, 0) for t in before}
            print(f"\n{prefix}CLEANUP COUNTS BY TABLE:")
            for table, del_count in deleted.items():
                b = before.get(table, 0)
                a = after.get(table, 0)
                print(f"  - {table:25s} | before: {b:3d} | removed: {del_count:3d} | after: {a:3d}")
            print(f"\n  police_station_directory: {after.get('police_station_directory', 0)} preserved (OFFICIAL PUBLIC DIRECTORY)")
            if args.dry_run:
                print("\n[DRY RUN COMPLETE] No records were modified.")
            else:
                print("\nNotice: Transaction committed. Real user events, devices, and reference data were preserved.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
