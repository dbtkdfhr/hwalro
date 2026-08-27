#!/bin/bash
set -Eeuo pipefail

: "${AUTH_DB_URL:?AUTH_DB_URL is required}"
: "${AUTH_DB_PASSWORD:?AUTH_DB_PASSWORD is required}"
: "${SIMULATION_DB_URL:?SIMULATION_DB_URL is required}"
: "${SIMULATION_DB_PASSWORD:?SIMULATION_DB_PASSWORD is required}"
: "${REGULATION_DB_URL:?REGULATION_DB_URL is required}"
: "${REGULATION_DB_PASSWORD:?REGULATION_DB_PASSWORD is required}"

parse_jdbc_url() {
    local jdbc_url="$1"
    local authority

    if [[ "$jdbc_url" != jdbc:mysql://* ]]; then
        echo "Unsupported MySQL JDBC URL" >&2
        return 1
    fi

    authority="${jdbc_url#jdbc:mysql://}"
    authority="${authority%%/*}"
    DB_HOST="${authority%%:*}"

    if [[ "$authority" == *:* ]]; then
        DB_PORT="${authority##*:}"
    else
        DB_PORT=3306
    fi

    if [[ -z "$DB_HOST" || ! "$DB_PORT" =~ ^[0-9]+$ ]]; then
        echo "Invalid MySQL host or port in JDBC URL" >&2
        return 1
    fi
}

query_database() {
    local jdbc_url="$1"
    local username="$2"
    local password="$3"
    local database="$4"
    local query="$5"

    parse_jdbc_url "$jdbc_url"
    MYSQL_PWD="$password" mysql \
        --protocol=TCP \
        --host="$DB_HOST" \
        --port="$DB_PORT" \
        --user="$username" \
        --database="$database" \
        --ssl-mode=REQUIRED \
        --default-character-set=utf8mb4 \
        --batch \
        --skip-column-names \
        --execute="$query"
}

assert_no_missing_requirements() {
    local label="$1"
    local jdbc_url="$2"
    local username="$3"
    local password="$4"
    local database="$5"
    local query="$6"
    local missing

    if ! missing="$(query_database "$jdbc_url" "$username" "$password" "$database" "$query")"; then
        echo "RDS preflight could not query ${label}." >&2
        exit 1
    fi

    if [[ -n "$missing" ]]; then
        echo "RDS preflight failed for ${label}. Missing requirements:" >&2
        printf '%s\n' "$missing" >&2
        exit 1
    fi
}

assert_no_missing_requirements \
    "auth schema" \
    "$AUTH_DB_URL" \
    "${AUTH_DB_USERNAME:-hwalro_auth}" \
    "$AUTH_DB_PASSWORD" \
    "hwalro_auth" \
    "SELECT requirement
       FROM (
           SELECT 'role.GENERAL_EMPLOYEE' AS requirement,
                  EXISTS(SELECT 1 FROM roles WHERE role_name = 'GENERAL_EMPLOYEE') AS is_present
       ) checks
      WHERE is_present = 0;"

assert_no_missing_requirements \
    "simulation schema" \
    "$SIMULATION_DB_URL" \
    "${SIMULATION_DB_USERNAME:-hwalro_simulation}" \
    "$SIMULATION_DB_PASSWORD" \
    "hwalro_simulation" \
    "SELECT requirement
       FROM (
           SELECT 'table.layout_zones' AS requirement, EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'layout_zones') AS is_present
           UNION ALL SELECT 'table.layout_zone_members', EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'layout_zone_members')
           UNION ALL SELECT 'table.evacuation_route_store', EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'evacuation_route_store')
           UNION ALL SELECT 'fabrics.movable', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'fabrics' AND column_name = 'movable')
           UNION ALL SELECT 'fabrics.max_movement_distance', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'fabrics' AND column_name = 'max_movement_distance')
           UNION ALL SELECT 'fabrics.rotation_locked', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'fabrics' AND column_name = 'rotation_locked')
           UNION ALL SELECT 'fabrics.keep_against_wall', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'fabrics' AND column_name = 'keep_against_wall')
           UNION ALL SELECT 'fabrics.display_order', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'fabrics' AND column_name = 'display_order')
           UNION ALL SELECT 'walls.display_order', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'walls' AND column_name = 'display_order')
           UNION ALL SELECT 'pillars.display_order', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pillars' AND column_name = 'display_order')
           UNION ALL SELECT 'columns.structure_constraints', 7 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND ((table_name = 'fabrics' AND ((column_name IN ('movable', 'rotation_locked', 'keep_against_wall') AND column_type = 'tinyint(1)' AND is_nullable = 'NO') OR (column_name = 'max_movement_distance' AND column_type = 'decimal(12,4)' AND is_nullable = 'YES') OR (column_name = 'display_order' AND column_type = 'int' AND is_nullable = 'NO'))) OR (table_name IN ('walls', 'pillars') AND column_name = 'display_order' AND column_type = 'int' AND is_nullable = 'NO')))
           UNION ALL SELECT 'columns.layout_zones', 12 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'layout_zones' AND ((column_name IN ('id', 'layout_version_id') AND column_type = 'bigint unsigned' AND is_nullable = 'NO') OR (column_name = 'name' AND column_type = 'varchar(200)' AND is_nullable = 'NO') OR (column_name = 'zone_type' AND column_type = 'varchar(40)' AND is_nullable = 'NO') OR (column_name IN ('x', 'y', 'width', 'height') AND column_type = 'decimal(12,4)' AND is_nullable = 'NO') OR (column_name IN ('assigned_user_id', 'default_exit_id') AND column_type = 'bigint unsigned' AND is_nullable = 'YES') OR (column_name = 'display_order' AND column_type = 'int' AND is_nullable = 'NO') OR (column_name = 'created_at' AND column_type = 'datetime(6)' AND is_nullable = 'NO')))
           UNION ALL SELECT 'columns.layout_zone_members', 6 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'layout_zone_members' AND ((column_name IN ('id', 'layout_version_id', 'zone_id') AND column_type = 'bigint unsigned' AND is_nullable = 'NO') OR (column_name IN ('wall_id', 'pillar_id', 'fabric_id') AND column_type = 'bigint unsigned' AND is_nullable = 'YES')))
           UNION ALL SELECT 'columns.evacuation_route_store', 5 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'evacuation_route_store' AND ((column_name = 'cache_key' AND column_type = 'char(64)' AND is_nullable = 'NO') OR (column_name IN ('layout_id', 'layout_version_id') AND column_type = 'bigint unsigned' AND is_nullable = 'NO') OR (column_name = 'result_payload' AND column_type = 'json' AND is_nullable = 'NO') OR (column_name = 'computed_at' AND column_type = 'datetime(6)' AND is_nullable = 'NO')))
           UNION ALL SELECT 'constraint.uk_fabrics_id_version', EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'fabrics' AND constraint_name = 'uk_fabrics_id_version' AND constraint_type = 'UNIQUE')
           UNION ALL SELECT 'constraint.uk_walls_id_version', EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'walls' AND constraint_name = 'uk_walls_id_version' AND constraint_type = 'UNIQUE')
           UNION ALL SELECT 'constraint.uk_pillars_id_version', EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'pillars' AND constraint_name = 'uk_pillars_id_version' AND constraint_type = 'UNIQUE')
           UNION ALL SELECT 'constraints.layout_zone_integrity', 17 = (SELECT COUNT(DISTINCT constraint_name) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND constraint_name IN ('uk_fabrics_id_version', 'ck_fabrics_max_movement_distance', 'uk_walls_id_version', 'uk_pillars_id_version', 'uk_layout_zones_id_version', 'uk_layout_zones_version_name', 'ck_layout_zones_extent', 'fk_layout_zones_layout_version', 'fk_layout_zones_default_exit', 'uk_layout_zone_members_wall', 'uk_layout_zone_members_pillar', 'uk_layout_zone_members_fabric', 'ck_layout_zone_members_exactly_one', 'fk_layout_zone_members_zone', 'fk_layout_zone_members_wall', 'fk_layout_zone_members_pillar', 'fk_layout_zone_members_fabric'))
           UNION ALL SELECT 'primary-keys.new-simulation-tables', 3 = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name IN ('layout_zones', 'layout_zone_members', 'evacuation_route_store') AND constraint_type = 'PRIMARY KEY')
           UNION ALL SELECT 'indexes.evacuation_route_store', 2 = (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'evacuation_route_store' AND index_name IN ('idx_ers_version', 'idx_ers_computed_at'))
       ) checks
      WHERE is_present = 0;"

assert_no_missing_requirements \
    "regulation schema" \
    "$REGULATION_DB_URL" \
    "${REGULATION_DB_USERNAME:-hwalro_regulation}" \
    "$REGULATION_DB_PASSWORD" \
    "hwalro_regulation" \
    "SELECT requirement
       FROM (
           SELECT 'inspection_areas.layout_id' AS requirement, EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'inspection_areas' AND column_name = 'layout_id') AS is_present
           UNION ALL SELECT 'safety_inspections.layout_id', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'safety_inspections' AND column_name = 'layout_id')
           UNION ALL SELECT 'safety_inspections.layout_version_id', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'safety_inspections' AND column_name = 'layout_version_id')
           UNION ALL SELECT 'safety_inspections.snapshot_image', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'safety_inspections' AND column_name = 'snapshot_image')
           UNION ALL SELECT 'safety_inspection_items.marker_x', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'safety_inspection_items' AND column_name = 'marker_x')
           UNION ALL SELECT 'safety_inspection_items.marker_y', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'safety_inspection_items' AND column_name = 'marker_y')
           UNION ALL SELECT 'risks.layout_id', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risks' AND column_name = 'layout_id')
           UNION ALL SELECT 'risks.layout_version_id', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risks' AND column_name = 'layout_version_id')
           UNION ALL SELECT 'columns.regulation_layout_links', 8 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND ((table_name = 'inspection_areas' AND column_name = 'layout_id' AND column_type = 'bigint unsigned' AND is_nullable = 'YES') OR (table_name = 'safety_inspections' AND column_name IN ('layout_id', 'layout_version_id') AND column_type = 'bigint unsigned' AND is_nullable = 'YES') OR (table_name = 'safety_inspections' AND column_name = 'snapshot_image' AND column_type = 'mediumblob' AND is_nullable = 'YES') OR (table_name = 'safety_inspection_items' AND column_name IN ('marker_x', 'marker_y') AND column_type = 'decimal(6,5)' AND is_nullable = 'YES') OR (table_name = 'risks' AND column_name IN ('layout_id', 'layout_version_id') AND column_type = 'bigint unsigned')))
           UNION ALL SELECT 'index.idx_risks_layout_id', EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risks' AND index_name = 'idx_risks_layout_id')
           UNION ALL SELECT 'data.inspection_layout_backfill', NOT EXISTS(SELECT 1 FROM safety_inspections inspection JOIN inspection_areas area ON area.id = inspection.inspection_area_id WHERE inspection.layout_id IS NULL AND area.layout_id IS NOT NULL LIMIT 1)
       ) checks
      WHERE is_present = 0;"

echo "RDS schema preflight passed."
