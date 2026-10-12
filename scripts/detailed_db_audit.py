import sqlite3
import json
import os

DB_PATH = "backend/data/raksha360.sqlite3"

def audit():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    tables = [r[0] for r in cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall()]

    report = {}

    for t in tables:
        rows = cur.execute(f'SELECT * FROM "{t}"').fetchall()
        report[t] = {
            "total": len(rows),
            "definite_demo": [],
            "ambiguous": [],
            "genuine_or_reference": []
        }
        for r in rows:
            d = dict(r)
            # Classification logic
            is_demo = d.get("is_demo")
            first_val = str(list(d.values())[0]) if d else ""
            device_id = str(d.get("device_id") or "")
            source = str(d.get("source") or "")
            actor = str(d.get("actor") or "")
            target_id = str(d.get("target_id") or "")

            item_summary = {k: d[k] for k in list(d.keys())[:3]}

            if t == "police_station_directory":
                # Reference data
                report[t]["genuine_or_reference"].append((item_summary, "Official public directory entry"))
            elif is_demo == 1 or first_val.startswith("DEMO-") or "SYNTHETIC" in source:
                report[t]["definite_demo"].append((item_summary, f"is_demo={is_demo}, ID={first_val}, source={source}"))
            elif first_val.startswith("OFFLINE-") and ("DEMO-" in str(d.get("device_session_id") or "") or "DEMO-" in str(d.get("details_json") or "")):
                report[t]["definite_demo"].append((item_summary, f"Generated offline incident from DEMO device: {d.get('device_session_id')}"))
            elif device_id.startswith("DEMO-"):
                report[t]["definite_demo"].append((item_summary, f"Associated with demo device {device_id}"))
            elif t == "audit_log" and (target_id == "DEMO_PACK" or "DEMO" in actor or "Synthetic" in str(d.get("message") or "")):
                report[t]["definite_demo"].append((item_summary, "Demo audit trail"))
            elif t == "audit_log":
                report[t]["genuine_or_reference"].append((item_summary, "Operational audit log entry"))
            else:
                # Ambiguous or genuine
                report[t]["genuine_or_reference"].append((item_summary, "Live user/operational record"))

    print("=== DRY RUN CLEANUP REPORT ===")
    for t, data in report.items():
        print(f"\nTable: {t}")
        print(f"  Total records: {data['total']}")
        print(f"  Definite demo records: {len(data['definite_demo'])}")
        for item, reason in data["definite_demo"]:
            print(f"    - [DEMO] {item} -> Reason: {reason}")
        print(f"  Ambiguous records: {len(data['ambiguous'])}")
        for item, reason in data["ambiguous"]:
            print(f"    - [AMBIGUOUS] {item} -> Reason: {reason}")
        print(f"  Genuine/Reference records: {len(data['genuine_or_reference'])}")
        for item, reason in data["genuine_or_reference"][:5]:
            print(f"    - [KEEP] {item} -> Reason: {reason}")
        if len(data["genuine_or_reference"]) > 5:
            print(f"    ... and {len(data['genuine_or_reference']) - 5} more preserved records.")

if __name__ == "__main__":
    import sys
    sys.stdout.reconfigure(encoding='utf-8')
    audit()
