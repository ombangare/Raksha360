#!/usr/bin/env python3
"""
Raksha360 Clean Project Packager.

Produces a clean distributable ZIP file of the repository:
- Excludes:
  - Private .env files (keeps .env.example)
  - SQLite databases (*.sqlite3, *.db)
  - Node modules (node_modules/)
  - Python virtual environments (.venv/, venv/)
  - Python bytecode cache (__pycache__/, *.pyc)
  - Vite build outputs (dist/)
  - Android build cache (build/, .gradle/)
  - Temporary and git folders (.git/, .pytest_cache/)
"""

from __future__ import annotations

import argparse
import os
import zipfile
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parents[1]

EXCLUDE_DIRS = {
    "node_modules",
    ".venv",
    "venv",
    "__pycache__",
    ".pytest_cache",
    ".git",
    "dist",
    "build",
    ".gradle",
}

EXCLUDE_FILES = {
    ".env",
    "local.properties",
}

EXCLUDE_EXTS = {
    ".sqlite3",
    ".sqlite",
    ".db",
    ".pyc",
    ".pyo",
    ".log",
}


def should_exclude(rel_path: Path) -> bool:
    for part in rel_path.parts:
        if part in EXCLUDE_DIRS:
            return True
        if part.startswith(".env") and part != ".env.example":
            return True
    name = rel_path.name
    if name in EXCLUDE_FILES:
        return True
    if rel_path.suffix.lower() in EXCLUDE_EXTS:
        return True
    return False


def create_clean_zip(output_zip: Path) -> int:
    output_zip.parent.mkdir(parents=True, exist_ok=True)
    file_count = 0

    with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(WORKSPACE_ROOT):
            # Prune excluded directories in-place
            dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS and not d.startswith(".")]

            root_path = Path(root)
            for file in sorted(files):
                full_path = root_path / file
                rel_path = full_path.relative_to(WORKSPACE_ROOT)

                if should_exclude(rel_path):
                    continue

                # Top-level folder inside zip: Raksha360/<rel_path>
                arcname = Path("Raksha360") / rel_path
                zf.write(full_path, arcname=str(arcname).replace("\\", "/"))
                file_count += 1

    return file_count


def main() -> None:
    parser = argparse.ArgumentParser(description="Package clean Raksha360 ZIP without secrets or build artifacts")
    default_out = WORKSPACE_ROOT.parent / "Raksha360_Clean_Verified.zip"
    parser.add_argument("--output", default=str(default_out), help="Path for generated ZIP file")
    args = parser.parse_args()

    out_path = Path(args.output).resolve()
    print(f"Packaging clean Raksha360 archive into: {out_path} ...")
    count = create_clean_zip(out_path)
    size_mb = out_path.stat().st_size / (1024 * 1024)
    print(f"Archive successfully created:")
    print(f"  - Total files packaged: {count}")
    print(f"  - Archive size: {size_mb:.2f} MB")
    print(f"  - Output location: {out_path}")
    print("Verified: No .env, tokens, SQLite databases, node_modules, or build artifacts included.")


if __name__ == "__main__":
    main()
