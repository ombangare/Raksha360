import sqlite3
import shutil
import datetime
import os
import sys

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    db_url = os.getenv("DATABASE_URL", "").strip()

    if db_url.startswith("postgres://") or db_url.startswith("postgresql://"):
        try:
            import psycopg
        except ImportError:
            print("Error: psycopg is required to inspect PostgreSQL. Install with: pip install psycopg")
            sys.exit(1)

        norm_url = "postgresql://" + db_url[len("postgres://"):] if db_url.startswith("postgres://") else db_url
        ts = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d_%H%M%S")
        print(f"DATABASE_TARGET: PostgreSQL / Supabase")
        print(f"BACKUP_PROCEDURE:")
        print(f"  To export a full SQL backup from Supabase, run:")
        print(f"  pg_dump \"$DATABASE_URL\" -F p -v -f \"raksha360_backup_{ts}.sql\"")
        print(f"  To restore:")
        print(f"  psql \"$DATABASE_URL\" -f \"raksha360_backup_{ts}.sql\"")

        try:
            conn = psycopg.connect(norm_url)
            cur = conn.cursor()
            cur.execute("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                ORDER BY table_name;
            """)
            tables = [r[0] for r in cur.fetchall()]
            print(f"\nTABLES_AUDIT (Total public tables: {len(tables)}):")
            for t in tables:
                cur.execute(f'SELECT count(*) FROM "{t}"')
                count = cur.fetchone()[0]
                cur.execute(f"SELECT column_name FROM information_schema.columns WHERE table_name = '{t}' AND table_schema = 'public'")
                cols = [c[0] for c in cur.fetchall()]
                demo_count = 0
                if "is_demo" in cols:
                    cur.execute(f'SELECT count(*) FROM "{t}" WHERE is_demo = 1')
                    demo_count = cur.fetchone()[0]
                print(f"  {t:30s} | total: {count:4d} | is_demo=1: {demo_count:4d}")
            conn.close()
            return
        except Exception as exc:
            print(f"PostgreSQL connection error: {exc}")
            sys.exit(1)

    data_dir = os.getenv("RAKSHA360_DATA_DIR", os.path.join(repo_root, "backend", "data"))
    default_db = os.path.join(data_dir, "raksha360.sqlite3")
    src = os.getenv("RAKSHA360_DATABASE_PATH", default_db)
    if not os.path.exists(src):
        print(f"Error: Database not found at {src}")
        sys.exit(1)

    ts = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d_%H%M%S")
    dst = f"{src}.backup_{ts}"

    # Use SQLite Online Backup API for atomic, WAL-safe snapshot
    src_conn = sqlite3.connect(src)
    dst_conn = sqlite3.connect(dst)
    with dst_conn:
        src_conn.backup(dst_conn)
    src_conn.close()
    dst_conn.close()
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
