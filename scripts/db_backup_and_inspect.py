import sqlite3
import shutil
import datetime
import os
import sys

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    src = os.path.join(repo_root, "backend", "data", "raksha360.sqlite3")
    if not os.path.exists(src):
        print(f"Error: Database not found at {src}")
        sys.exit(1)

    ts = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d_%H%M%S")
    dst = os.path.join(repo_root, "backend", "data", f"raksha360.sqlite3.backup_{ts}")

    # Create timestamped backup
    shutil.copy2(src, dst)
    print(f"BACKUP_PATH: {dst}")

    # Verify backup opens and check integrity
    conn = sqlite3.connect(dst)
    cur = conn.cursor()
    integrity = cur.execute("PRAGMA integrity_check").fetchone()[0]
    print(f"INTEGRITY_CHECK: {integrity}")
    if integrity != "ok":
        print("Error: Integrity check failed!")
        sys.exit(1)

    tables = [r[0] for r in cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall()]
    print(f"\nTABLES_AUDIT (Total tables: {len(tables)}):")
    for t in tables:
        count = cur.execute(f'SELECT count(*) FROM "{t}"').fetchone()[0]
        cols = [c[1] for c in cur.execute(f'PRAGMA table_info("{t}")').fetchall()]
        has_is_demo = 'is_demo' in cols
        demo_count = 0
        if has_is_demo:
            demo_count = cur.execute(f'SELECT count(*) FROM "{t}" WHERE is_demo = 1').fetchone()[0]
        
        # Check for DEMO IDs if applicable
        demo_id_count = 0
        primary_col = cols[0] if cols else None
        if primary_col:
            demo_id_count = cur.execute(f'SELECT count(*) FROM "{t}" WHERE "{primary_col}" LIKE "DEMO-%" OR "{primary_col}" LIKE "%-demo%"').fetchone()[0]
            
        print(f"  {t:30s} | total: {count:4d} | is_demo=1: {demo_count:4d} | demo_id_match: {demo_id_count:4d}")
    
    conn.close()

if __name__ == "__main__":
    main()
