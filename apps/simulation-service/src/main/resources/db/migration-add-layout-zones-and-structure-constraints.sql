-- 2026-08-22: additive migration for existing simulation databases.
-- Adds version-owned operational Zones, Zone membership across walls, pillars
-- and fabrics with display ordering, and layout placement exclusions.
-- New installs get all of this from db/schema.sql; existing databases must
-- apply this file once. Run the statements in order: the composite foreign keys
-- in layout_zone_members require uk_walls_id_version, uk_pillars_id_version
-- and uk_fabrics_id_version to exist first.
USE hwalro_simulation;

ALTER TABLE fabrics
    ADD COLUMN movable BOOLEAN NOT NULL DEFAULT TRUE AFTER rotation,
    ADD COLUMN max_movement_distance DECIMAL(12, 4) NULL AFTER movable,
    ADD COLUMN rotation_locked BOOLEAN NOT NULL DEFAULT FALSE AFTER max_movement_distance,
    ADD COLUMN keep_against_wall BOOLEAN NOT NULL DEFAULT FALSE AFTER rotation_locked,
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER keep_against_wall,
    ADD CONSTRAINT uk_fabrics_id_version UNIQUE (id, layout_version_id),
    ADD CONSTRAINT ck_fabrics_max_movement_distance
        CHECK (max_movement_distance IS NULL OR max_movement_distance > 0);

ALTER TABLE walls
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER end_y,
    ADD CONSTRAINT uk_walls_id_version UNIQUE (id, layout_version_id);

ALTER TABLE pillars
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER rotation,
    ADD CONSTRAINT uk_pillars_id_version UNIQUE (id, layout_version_id);

CREATE TABLE IF NOT EXISTS layout_zones (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(200) NOT NULL,
    zone_type VARCHAR(40) NOT NULL,
    x DECIMAL(12, 4) NOT NULL,
    y DECIMAL(12, 4) NOT NULL,
    width DECIMAL(12, 4) NOT NULL,
    height DECIMAL(12, 4) NOT NULL,
    assigned_user_id BIGINT UNSIGNED NULL,
    default_exit_id BIGINT UNSIGNED NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_layout_zones PRIMARY KEY (id),
    CONSTRAINT uk_layout_zones_id_version UNIQUE (id, layout_version_id),
    CONSTRAINT uk_layout_zones_version_name UNIQUE (layout_version_id, name),
    CONSTRAINT ck_layout_zones_extent CHECK (width > 0 AND height > 0),
    CONSTRAINT fk_layout_zones_layout_version
        FOREIGN KEY (layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_layout_zones_default_exit
        FOREIGN KEY (default_exit_id, layout_version_id)
        REFERENCES layout_exits (id, layout_version_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    INDEX idx_layout_zones_assigned_user (assigned_user_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS layout_zone_members (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    zone_id BIGINT UNSIGNED NOT NULL,
    wall_id BIGINT UNSIGNED NULL,
    pillar_id BIGINT UNSIGNED NULL,
    fabric_id BIGINT UNSIGNED NULL,
    CONSTRAINT pk_layout_zone_members PRIMARY KEY (id),
    CONSTRAINT uk_layout_zone_members_wall UNIQUE (wall_id, layout_version_id),
    CONSTRAINT uk_layout_zone_members_pillar UNIQUE (pillar_id, layout_version_id),
    CONSTRAINT uk_layout_zone_members_fabric UNIQUE (fabric_id, layout_version_id),
    CONSTRAINT ck_layout_zone_members_exactly_one
        CHECK ((wall_id IS NOT NULL) + (pillar_id IS NOT NULL) + (fabric_id IS NOT NULL) = 1),
    CONSTRAINT fk_layout_zone_members_zone
        FOREIGN KEY (zone_id, layout_version_id)
        REFERENCES layout_zones (id, layout_version_id)
        ON UPDATE RESTRICT
        ON DELETE CASCADE,
    CONSTRAINT fk_layout_zone_members_wall
        FOREIGN KEY (wall_id, layout_version_id)
        REFERENCES walls (id, layout_version_id)
        ON UPDATE RESTRICT
        ON DELETE CASCADE,
    CONSTRAINT fk_layout_zone_members_pillar
        FOREIGN KEY (pillar_id, layout_version_id)
        REFERENCES pillars (id, layout_version_id)
        ON UPDATE RESTRICT
        ON DELETE CASCADE,
    CONSTRAINT fk_layout_zone_members_fabric
        FOREIGN KEY (fabric_id, layout_version_id)
        REFERENCES fabrics (id, layout_version_id)
        ON UPDATE RESTRICT
        ON DELETE CASCADE,
    INDEX idx_layout_zone_members_zone (zone_id, layout_version_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 배치 제외 영역은 EXCLUSION 유형 구역으로 흡수했다. 이전에 이 스크립트로 만든 테이블을 정리한다.
DROP TABLE IF EXISTS layout_placement_exclusions;
