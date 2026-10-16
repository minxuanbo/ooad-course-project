#!/usr/bin/env python3
"""One-shot, non-destructive import into the EMPTY project-local MySQL rbac database."""
import datetime
import hashlib
import json
from pathlib import Path
import sqlite3
import sys
from local_mysql import ROOT, LOCAL, client

TABLES = ["departments", "users", "applications", "permissions", "roles",
          "user_roles", "role_permissions", "sessions", "authorization_logs",
          "authorization_changes", "handovers"]


def normalize(value, column):
    if value is not None and column.endswith("_at"):
        dt = datetime.datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        if dt.tzinfo:
            dt = dt.astimezone(datetime.timezone.utc).replace(tzinfo=None)
        return dt.isoformat(sep=" ", timespec="milliseconds")
    return value


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    return "CONVERT(X'" + str(value).encode("utf-8").hex() + "' USING utf8mb4)"


def digest(rows):
    encoded = []
    for row in rows:
        encoded.append(json.dumps(row, ensure_ascii=False, separators=(",", ":")))
    return hashlib.sha256("\n".join(sorted(encoded)).encode()).hexdigest()


def main():
    source = Path(sys.argv[1] if len(sys.argv) > 1 else ROOT / "data/rbac.db").resolve()
    if not source.is_file():
        sys.exit("Source SQLite file not found. No data changed.")
    config = json.loads((LOCAL / "credentials.json").read_text())
    def run(sql):
        return client(sql, "rbac", config["rbac"], "rbac")
    # Import against the historical layout first; never insert old columns into V2.
    if int(run("SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='companies';").strip()):
        sys.exit("Target already has V2 tables. Use a separate empty legacy target; refusing to overwrite.")
    run((ROOT / "scripts/migrations/001-legacy-mysql.sql").read_text())
    for table in TABLES:
        if int(run(f"SELECT COUNT(*) FROM {table};").strip()):
            sys.exit("Target is not empty: " + table + ". Refusing to overwrite.")
    db = sqlite3.connect(source.as_uri() + "?mode=ro", uri=True)
    backup_dir = LOCAL / "backups"
    backup_dir.mkdir(exist_ok=True)
    backup = backup_dir / ("rbac-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S-%f") + ".db")
    with sqlite3.connect(backup) as out:
        db.backup(out)
    backup.chmod(0o600)
    sql = ["SET time_zone='+00:00';", "START TRANSACTION;"]
    expected = {}
    for table in TABLES:
        columns = [r[1] for r in db.execute(f"PRAGMA table_info({table})")]
        if not columns:
            sys.exit("Source schema missing table: " + table)
        rows = []
        for record in db.execute(f"SELECT * FROM {table}"):
            rows.append([normalize(v, c) for c, v in zip(columns, record)])
        expected[table] = (columns, rows)
        # Preserve IDs; insert parents before children without disabling foreign keys.
        if table == "departments":
            pending = rows[:]
            ordered, inserted = [], set()
            parent = columns.index("parent_id")
            key = columns.index("id")
            while pending:
                progress = False
                for row in pending[:]:
                    if row[parent] is None or row[parent] in inserted:
                        ordered.append(row)
                        inserted.add(row[key])
                        pending.remove(row)
                        progress = True
                if not progress:
                    sys.exit("Invalid department parent chain; no import committed.")
            rows = ordered
        names = ",".join(" `" + c + "`" for c in columns)
        for start in range(0, len(rows), 200):
            values = []
            for row in rows[start:start + 200]:
                values.append("(" + ",".join(literal(v) for v in row) + ")")
            sql.append(f"INSERT INTO {table} ({names}) VALUES " + ",".join(values) + ";")
    sql.append("COMMIT;")
    # mysql batch exits on error; disconnect rolls back this InnoDB transaction.
    run("\n".join(sql))
    report = ["Migration verification (all columns, UTC milliseconds):"]
    for table, (columns, source_rows) in expected.items():
        selected = ",".join("`" + c + "`" for c in columns)
        raw = run(f"SELECT JSON_ARRAY({selected}) FROM {table};")
        actual = []
        for line in raw.splitlines():
            actual.append([normalize(v, c) for c, v in zip(columns, json.loads(line))])
        if digest(source_rows) != digest(actual):
            sys.exit("Verification mismatch: " + table + ". Source/backup retained; inspect target before use.")
        report.append(f"{table}: {len(actual)} rows; SHA-256 content match")
    report.append("Source retained unchanged. Backup: " + str(backup))
    (LOCAL / "migration-report.txt").write_text("\n".join(report) + "\n")
    print("\n".join(report))
    from normalize_mysql import migrate
    migrate("rbac", apply=True)


if __name__ == "__main__":
    main()
