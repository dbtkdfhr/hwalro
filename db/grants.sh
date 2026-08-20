#!/bin/bash
# Executed directly by the Linux MySQL entrypoint; Git enforces LF via .gitattributes.
set -e

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'hwalro_auth'@'%' IDENTIFIED BY '${AUTH_DB_PASSWORD:-hwalro_auth}';
CREATE USER IF NOT EXISTS 'hwalro_simulation'@'%' IDENTIFIED BY '${SIMULATION_DB_PASSWORD:-hwalro_simulation}';
CREATE USER IF NOT EXISTS 'hwalro_regulation'@'%' IDENTIFIED BY '${REGULATION_DB_PASSWORD:-hwalro_regulation}';

GRANT ALL PRIVILEGES ON hwalro_auth.* TO 'hwalro_auth'@'%';
GRANT ALL PRIVILEGES ON hwalro_simulation.* TO 'hwalro_simulation'@'%';
GRANT ALL PRIVILEGES ON hwalro_regulation.* TO 'hwalro_regulation'@'%';

FLUSH PRIVILEGES;
SQL

echo "Service-specific DB users created"
