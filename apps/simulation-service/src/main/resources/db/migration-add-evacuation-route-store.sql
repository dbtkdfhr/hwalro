-- Existing RDS databases do not execute db/schema.sql because DB_INIT_MODE is never.
-- Apply this migration once before deploying the persistent evacuation route cache.
USE hwalro_simulation;

CREATE TABLE IF NOT EXISTS evacuation_route_store (
    cache_key CHAR(64) NOT NULL,
    layout_id BIGINT UNSIGNED NOT NULL,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    result_payload JSON NOT NULL,
    computed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_evacuation_route_store PRIMARY KEY (cache_key),
    INDEX idx_ers_version (layout_version_id),
    INDEX idx_ers_computed_at (computed_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
