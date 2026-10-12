"""
Raksha360 Database Abstraction Layer.
Supports both PostgreSQL (via psycopg 3 with connection pooling) and SQLite (for local development/testing).

Production Target: Supabase Free PostgreSQL via connection pooler (IPv4 compatible).
Development Target: SQLite (WAL mode, isolated files).
"""

from __future__ import annotations

import json
import os
import re
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Generator

try:
    import psycopg
    from psycopg_pool import ConnectionPool
    HAS_PSYCOPG = True
except ImportError:
    psycopg = None
    ConnectionPool = None
    HAS_PSYCOPG = False


ROOT_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = Path(os.getenv("RAKSHA360_DATA_DIR", str(ROOT_DIR / "data"))).resolve()
DATA_DIR.mkdir(parents=True, exist_ok=True)
DEFAULT_SQLITE_PATH = Path(os.getenv("RAKSHA360_DATABASE_PATH", str(DATA_DIR / "raksha360.sqlite3"))).resolve()
DB_PATH = DEFAULT_SQLITE_PATH
_pg_pool: Any = None


def get_sqlite_path() -> Path:
    import sys
    main_mod = sys.modules.get("backend.app.main")
    if main_mod and hasattr(main_mod, "DB_PATH"):
        return Path(main_mod.DB_PATH).resolve()
    return Path(os.getenv("RAKSHA360_DATABASE_PATH", str(DB_PATH))).resolve()


def get_pg_schema() -> str:
    """Return the configured PostgreSQL search_path schema, if any."""
    schema = os.getenv("RAKSHA360_PG_SCHEMA", "").strip()
    if schema:
        if not re.match(r'^[a-zA-Z0-9_]+$', schema):
            raise ValueError(f"Invalid RAKSHA360_PG_SCHEMA name: {schema}")
    return schema


def _configure_pg_connection(conn: Any) -> None:
    """Configure each connection created by the connection pool to use the isolated schema and UTF-8 encoding."""
    schema = get_pg_schema()
    if schema:
        conn.execute(f'SET search_path TO "{schema}", public')
    conn.execute("SET client_encoding TO 'UTF8'")
    conn.commit()


def get_database_url() -> str:
    url = os.getenv("DATABASE_URL", "").strip()
    if url.startswith("postgres://"):
        url = "postgresql://" + url[len("postgres://"):]
    schema = get_pg_schema()
    if schema and url and "search_path" not in url:
        sep = "&" if "?" in url else "?"
        url = f"{url}{sep}options=-c%20search_path%3D{schema},public"
    return url


def is_postgres() -> bool:
    url = get_database_url()
    return url.startswith("postgresql://") or url.startswith("postgres://")


def get_engine_name() -> str:
    return "postgresql" if is_postgres() else "sqlite"


class DatabaseRow(dict):
    """
    Unified row representation compatible with sqlite3.Row:
    - Supports dictionary key access: row['name'], row.get('name')
    - Supports dict conversion: dict(row)
    - Supports integer index access: row[0], row[1]
    """
    def __init__(self, mapping: dict[str, Any], values: tuple[Any, ...] | list[Any] | None = None) -> None:
        super().__init__(mapping)
        self._values = tuple(mapping.values()) if values is None else tuple(values)

    def __getitem__(self, key: Any) -> Any:
        if isinstance(key, int):
            return self._values[key]
        return super().__getitem__(key)


class EmptyCursor:
    """Mock cursor for statements (like PRAGMA on PostgreSQL) that require no execution."""
    def __init__(self) -> None:
        self.description = None
        self.rowcount = 0

    def fetchone(self) -> None:
        return None

    def fetchall(self) -> list[Any]:
        return []

    def __iter__(self) -> Generator[Any, None, None]:
        return
        yield


class PostgresCursorWrapper:
    """Wraps a psycopg cursor to produce DatabaseRow instances and provide a sqlite-compatible cursor API."""
    def __init__(self, cur: Any) -> None:
        self._cur = cur
        self.description = cur.description
        self.rowcount = cur.rowcount

    def fetchone(self) -> DatabaseRow | None:
        row = self._cur.fetchone()
        if row is None or self._cur.description is None:
            return None
        cols = [desc[0] for desc in self._cur.description]
        return DatabaseRow(dict(zip(cols, row)), row)

    def fetchall(self) -> list[DatabaseRow]:
        rows = self._cur.fetchall()
        if not rows or self._cur.description is None:
            return []
        cols = [desc[0] for desc in self._cur.description]
        return [DatabaseRow(dict(zip(cols, r)), r) for r in rows]

    def __iter__(self) -> Generator[DatabaseRow, None, None]:
        for item in self.fetchall():
            yield item


def convert_query_to_postgres(sql: str) -> str:
    """
    Converts SQLite ? parameter placeholders to PostgreSQL %s placeholders safely,
    ignoring ? inside string literals, quoted identifiers, and comments.
    Also maps SQLite-specific scalar functions like multi-argument MAX(a, b) to GREATEST(a, b).
    """
    out: list[str] = []
    i = 0
    n = len(sql)
    while i < n:
        char = sql[i]
        # Single-quoted string literal
        if char == "'":
            out.append(char)
            i += 1
            while i < n:
                curr = sql[i]
                out.append(curr)
                i += 1
                if curr == "'":
                    if i < n and sql[i] == "'":
                        out.append("'")
                        i += 1
                    else:
                        break
        # Double-quoted identifier
        elif char == '"':
            out.append(char)
            i += 1
            while i < n:
                curr = sql[i]
                out.append(curr)
                i += 1
                if curr == '"':
                    break
        # Line comment -- ...
        elif char == '-' and i + 1 < n and sql[i+1] == '-':
            while i < n and sql[i] != '\n':
                out.append(sql[i])
                i += 1
        # Block comment /* ... */
        elif char == '/' and i + 1 < n and sql[i+1] == '*':
            out.append(sql[i:i+2])
            i += 2
            while i < n:
                if sql[i] == '*' and i + 1 < n and sql[i+1] == '/':
                    out.append("*/")
                    i += 2
                    break
                out.append(sql[i])
                i += 1
        # Standalone parameter placeholder ?
        elif char == '?':
            out.append('%s')
            i += 1
        else:
            out.append(char)
            i += 1

    converted = "".join(out)
    # Adjust COLLATE NOCASE to standard case-insensitive LOWER() if encountered
    converted = re.sub(r'ORDER BY name COLLATE NOCASE', 'ORDER BY LOWER(name)', converted, flags=re.IGNORECASE)
    return converted


class PostgresWrapper:
    """Wraps a psycopg connection and provides an execution interface matching sqlite3."""
    def __init__(self, conn: Any) -> None:
        self._conn = conn

    def execute(self, sql: str, params: Any = None) -> Any:
        clean = sql.strip()
        if clean.upper().startswith("PRAGMA"):
            return EmptyCursor()
        pg_sql = convert_query_to_postgres(clean)
        cur = self._conn.cursor()
        if params is not None:
            cur.execute(pg_sql, params)
        else:
            cur.execute(pg_sql)
        return PostgresCursorWrapper(cur)

    def executemany(self, sql: str, params_seq: Any) -> Any:
        clean = sql.strip()
        if clean.upper().startswith("PRAGMA"):
            return EmptyCursor()
        pg_sql = convert_query_to_postgres(clean)
        cur = self._conn.cursor()
        cur.executemany(pg_sql, params_seq)
        return PostgresCursorWrapper(cur)

    def executescript(self, script: str) -> None:
        cur = self._conn.cursor()
        cur.execute(script)

    def commit(self) -> None:
        self._conn.commit()

    def rollback(self) -> None:
        self._conn.rollback()


def get_db_pool() -> Any:
    global _pg_pool
    if not HAS_PSYCOPG:
        raise RuntimeError("psycopg and psycopg_pool must be installed to use PostgreSQL.")
    if _pg_pool is None:
        db_url = get_database_url()
        if not db_url:
            raise RuntimeError("DATABASE_URL must be configured to connect to PostgreSQL.")
        _pg_pool = ConnectionPool(
            conninfo=db_url,
            min_size=1,
            max_size=5,
            timeout=10.0,
            max_idle=300.0,
            open=True,
            configure=_configure_pg_connection,
        )
    return _pg_pool


def close_db_pool() -> None:
    global _pg_pool
    if _pg_pool is not None:
        try:
            _pg_pool.close()
        except Exception:
            pass
        _pg_pool = None


@contextmanager
def db() -> Generator[Any, None, None]:
    """
    Context manager yielding a database connection wrapper.
    Commits on successful exit; rolls back and raises on exceptions.
    """
    if is_postgres():
        pool = get_db_pool()
        with pool.connection() as conn:
            wrapper = PostgresWrapper(conn)
            try:
                yield wrapper
                conn.commit()
            except Exception:
                conn.rollback()
                raise
    else:
        conn = sqlite3.connect(get_sqlite_path(), timeout=15)
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
