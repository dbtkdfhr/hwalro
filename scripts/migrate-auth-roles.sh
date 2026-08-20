#!/bin/bash
set -e

AUTH_DB_PASSWORD="${AUTH_DB_PASSWORD:-hwalro_auth}"

docker exec -i hwalro-mysql mysql -uhwalro_auth -p"${AUTH_DB_PASSWORD}" hwalro_auth \
  < apps/auth-service/src/main/resources/db/dml.sql

echo "roles DML applied to existing hwalro_auth volume"
