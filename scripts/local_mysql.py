#!/usr/bin/env python3
"""Start a project-private MySQL 8 server; generate local-only credentials."""
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / ".local" / "mysql"
DATA = LOCAL / "data"
SOCKET = LOCAL / "mysql.sock"
PORT = 3307


def client(sql, user="root", password="", database=None):
    command = [shutil.which("mysql"), "--no-defaults", "--protocol=SOCKET",
               "--socket=" + str(SOCKET), "--user=" + user, "--default-character-set=utf8mb4",
               "--batch", "--raw", "--skip-column-names"]
    if database:
        command.append(database)
    env = dict(os.environ, MYSQL_PWD=password)
    result = subprocess.run(command, input=sql, text=True, capture_output=True, env=env)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout


def main():
    if not shutil.which("mysql") or not shutil.which("mysqld"):
        sys.exit("Install MySQL 8.0.16+ or 8.4 and put mysql/mysqld on PATH.")
    LOCAL.mkdir(parents=True, exist_ok=True)
    LOCAL.chmod(0o700)
    credentials = LOCAL / "credentials.json"
    if not DATA.exists():
        with (LOCAL / "initialize.log").open("w") as log:
            subprocess.run([shutil.which("mysqld"), "--no-defaults", "--initialize-insecure",
                            "--datadir=" + str(DATA)], stdout=log, stderr=log, check=True)
    config = None
    if credentials.exists():
        config = json.loads(credentials.read_text())
    try:
        client("SELECT 1;", password=config["root"] if config else "")
    except RuntimeError:
        # Bind only loopback; X Protocol is disabled. No system service is changed.
        log = (LOCAL / "server.log").open("a")
        subprocess.Popen([shutil.which("mysqld"), "--no-defaults", "--datadir=" + str(DATA),
                          "--socket=" + str(SOCKET), "--port=" + str(PORT),
                          "--bind-address=127.0.0.1", "--mysqlx=OFF",
                          "--pid-file=" + str(LOCAL / "mysql.pid"),
                          "--log-error=" + str(LOCAL / "mysql-error.log"),
                          "--default-time-zone=+00:00"], stdout=log, stderr=log, start_new_session=True)
        for _ in range(60):
            try:
                client("SELECT 1;", password=config["root"] if config else "")
                break
            except RuntimeError:
                time.sleep(0.5)
        else:
            sys.exit("MySQL failed to start; inspect .local/mysql/mysql-error.log.")
    if not config:
        config = {"root": secrets.token_hex(24), "rbac": secrets.token_hex(24),
                  "rbac_test": secrets.token_hex(24)}
        # Persist before bootstrap so credentials are recoverable if setup is interrupted.
        credentials.write_text(json.dumps(config))
        credentials.chmod(0o600)
        sql = ""
        for name in ("rbac", "rbac_test"):
            sql += f"CREATE DATABASE IF NOT EXISTS {name} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;"
            sql += f"CREATE USER IF NOT EXISTS '{name}'@'localhost' IDENTIFIED BY '{config[name]}';"
            sql += f"GRANT ALL PRIVILEGES ON {name}.* TO '{name}'@'localhost';"
        sql += f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{config['root']}';"
        client(sql)
    props = LOCAL / "application-local.properties"
    props.write_text(
        "RBAC_DB_PASSWORD=" + config["rbac"] + "\n" +
        "RBAC_TEST_DB_PASSWORD=" + config["rbac_test"] + "\n")
    props.chmod(0o600)
    print("Project MySQL ready: 127.0.0.1:3307; databases: rbac, rbac_test.")
    print("Generated secrets are stored only in ignored .local/mysql/ (mode 600).")


if __name__ == "__main__":
    main()
