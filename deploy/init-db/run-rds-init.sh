#!/bin/bash
set -Eeuo pipefail

: "${RDS_HOST:?RDS_HOST is required}"
: "${RDS_ADMIN_USERNAME:?RDS_ADMIN_USERNAME is required}"
: "${RDS_ADMIN_PASSWORD:?RDS_ADMIN_PASSWORD is required}"
: "${AUTH_DB_PASSWORD:?AUTH_DB_PASSWORD is required}"
: "${SIMULATION_DB_PASSWORD:?SIMULATION_DB_PASSWORD is required}"
: "${REGULATION_DB_PASSWORD:?REGULATION_DB_PASSWORD is required}"

RDS_PORT="${RDS_PORT:-3306}"
INIT_DIR=/init-db

if [[ ! "$RDS_PORT" =~ ^[0-9]+$ ]] || ((RDS_PORT < 1 || RDS_PORT > 65535)); then
    echo "RDS_PORT must be a number between 1 and 65535." >&2
    exit 1
fi

export MYSQL_PWD="$RDS_ADMIN_PASSWORD"

MYSQL_ARGS=(
    --protocol=TCP
    --host="$RDS_HOST"
    --port="$RDS_PORT"
    --user="$RDS_ADMIN_USERNAME"
    --ssl-mode=REQUIRED
    --default-character-set=utf8mb4
    --show-warnings
)

run_sql_file() {
    local file_name="$1"
    local file_path="${INIT_DIR}/${file_name}"

    if [[ ! -f "$file_path" ]]; then
        echo "Missing initialization file: ${file_path}" >&2
        exit 1
    fi

    echo "Running ${file_name}"
    mysql "${MYSQL_ARGS[@]}" < "$file_path"
}

echo "Checking encrypted connection to RDS"
mysql "${MYSQL_ARGS[@]}" --execute='SELECT 1;'

run_sql_file 01-auth-schema.sql
run_sql_file 02-simulation-schema.sql
run_sql_file 03-regulation-schema.sql

echo "Running 04-grants.sh"
bash "${INIT_DIR}/04-grants.sh"

run_sql_file 05-auth-dml.sql
run_sql_file 06-safety-check-dml.sql
run_sql_file 07-density-threshold-dml.sql

unset MYSQL_PWD
echo "RDS initialization completed"
