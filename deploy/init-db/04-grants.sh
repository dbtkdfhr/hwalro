#!/bin/bash
# Creates service-specific users on the external production RDS instance.
set -Eeuo pipefail

: "${RDS_HOST:?RDS_HOST is required}"
: "${RDS_ADMIN_USERNAME:?RDS_ADMIN_USERNAME is required}"
: "${RDS_ADMIN_PASSWORD:?RDS_ADMIN_PASSWORD is required}"
: "${AUTH_DB_PASSWORD:?AUTH_DB_PASSWORD is required}"
: "${SIMULATION_DB_PASSWORD:?SIMULATION_DB_PASSWORD is required}"
: "${REGULATION_DB_PASSWORD:?REGULATION_DB_PASSWORD is required}"

RDS_PORT="${RDS_PORT:-3306}"

validate_sql_password() {
    local name="$1"
    local value="$2"

    if [[ "$value" == *"'"* || "$value" == *"\\"* || "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
        echo "${name} must not contain single quotes, backslashes, or line breaks." >&2
        exit 1
    fi
}

validate_sql_password AUTH_DB_PASSWORD "$AUTH_DB_PASSWORD"
validate_sql_password SIMULATION_DB_PASSWORD "$SIMULATION_DB_PASSWORD"
validate_sql_password REGULATION_DB_PASSWORD "$REGULATION_DB_PASSWORD"

export MYSQL_PWD="$RDS_ADMIN_PASSWORD"

mysql \
    --protocol=TCP \
    --host="$RDS_HOST" \
    --port="$RDS_PORT" \
    --user="$RDS_ADMIN_USERNAME" \
    --ssl-mode=REQUIRED <<SQL
CREATE USER IF NOT EXISTS 'hwalro_auth'@'%' IDENTIFIED BY '${AUTH_DB_PASSWORD}';
ALTER USER 'hwalro_auth'@'%' IDENTIFIED BY '${AUTH_DB_PASSWORD}';

CREATE USER IF NOT EXISTS 'hwalro_simulation'@'%' IDENTIFIED BY '${SIMULATION_DB_PASSWORD}';
ALTER USER 'hwalro_simulation'@'%' IDENTIFIED BY '${SIMULATION_DB_PASSWORD}';

CREATE USER IF NOT EXISTS 'hwalro_regulation'@'%' IDENTIFIED BY '${REGULATION_DB_PASSWORD}';
ALTER USER 'hwalro_regulation'@'%' IDENTIFIED BY '${REGULATION_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON hwalro_auth.* TO 'hwalro_auth'@'%';
GRANT ALL PRIVILEGES ON hwalro_simulation.* TO 'hwalro_simulation'@'%';
GRANT ALL PRIVILEGES ON hwalro_regulation.* TO 'hwalro_regulation'@'%';

FLUSH PRIVILEGES;
SQL

echo "Service-specific DB users created"
unset MYSQL_PWD
