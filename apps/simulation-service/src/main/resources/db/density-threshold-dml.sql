-- 시스템 전체에서 사용하는 밀집도 기준 기본값입니다.
-- CREATE TABLE은 기존 Docker 볼륨에서도 pnpm run infra로 적용되도록 중복 선언합니다.

USE hwalro_simulation;

CREATE TABLE IF NOT EXISTS density_threshold_settings (
    id TINYINT UNSIGNED NOT NULL,
    threshold_value DECIMAL(8, 3) NOT NULL,
    unit VARCHAR(30) NOT NULL DEFAULT 'PERSON_PER_M2',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_density_threshold_settings PRIMARY KEY (id),
    CONSTRAINT chk_density_threshold_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_density_threshold_settings_positive CHECK (threshold_value > 0),
    CONSTRAINT chk_density_threshold_settings_unit CHECK (unit = 'PERSON_PER_M2')
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO density_threshold_settings (id, threshold_value, unit)
VALUES (1, 3.000, 'PERSON_PER_M2')
ON DUPLICATE KEY UPDATE id = 1;
