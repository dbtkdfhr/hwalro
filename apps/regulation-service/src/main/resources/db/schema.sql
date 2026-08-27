SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS hwalro_regulation
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE hwalro_regulation;

CREATE TABLE IF NOT EXISTS risks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_id BIGINT UNSIGNED NOT NULL,
    layout_version_id BIGINT UNSIGNED NULL,
    assignee_id BIGINT UNSIGNED NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    start_x DECIMAL(12, 4),
    start_y DECIMAL(12, 4),
    end_x DECIMAL(12, 4),
    end_y DECIMAL(12, 4),
    severity VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_risks PRIMARY KEY (id),
    INDEX idx_risks_layout_id (layout_id),
    INDEX idx_risks_assignee_id (assignee_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Law articles attached to a risk for reference; only identifiers are stored, not content snapshots.
CREATE TABLE IF NOT EXISTS risk_attached_laws (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    risk_id BIGINT UNSIGNED NOT NULL,
    law_serial_number VARCHAR(30) NOT NULL,
    law_article_number VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_risk_attached_laws PRIMARY KEY (id),
    CONSTRAINT uk_risk_attached_laws_risk_law UNIQUE (risk_id, law_serial_number, law_article_number),
    CONSTRAINT fk_risk_attached_laws_risk FOREIGN KEY (risk_id) REFERENCES risks (id) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX idx_risk_attached_laws_risk_id (risk_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- An independently managed area selected for regulation-service safety inspections.
CREATE TABLE IF NOT EXISTS inspection_areas (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    layout_id BIGINT UNSIGNED NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inspection_areas PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Each area can have multiple checklist versions; only one version is activated by the application.
CREATE TABLE IF NOT EXISTS checklist_templates (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    inspection_area_id BIGINT UNSIGNED NOT NULL,
    version INT UNSIGNED NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_checklist_templates PRIMARY KEY (id),
    CONSTRAINT uk_checklist_templates_area_version
        UNIQUE (inspection_area_id, version),
    CONSTRAINT chk_checklist_templates_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT fk_checklist_templates_area
        FOREIGN KEY (inspection_area_id) REFERENCES inspection_areas (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS checklist_template_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    checklist_template_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    criterion TEXT NULL,
    category VARCHAR(30) NOT NULL,
    display_order INT UNSIGNED NOT NULL,
    CONSTRAINT pk_checklist_template_items PRIMARY KEY (id),
    CONSTRAINT uk_checklist_template_items_order
        UNIQUE (checklist_template_id, display_order),
    CONSTRAINT fk_checklist_template_items_template
        FOREIGN KEY (checklist_template_id) REFERENCES checklist_templates (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- One row represents one inspection run selected from an area's inspection history screen.
CREATE TABLE IF NOT EXISTS safety_inspections (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    inspection_area_id BIGINT UNSIGNED NOT NULL,
    checklist_template_id BIGINT UNSIGNED NOT NULL,
    simulation_result_id BIGINT UNSIGNED NULL,
    layout_id BIGINT UNSIGNED NULL,
    layout_version_id BIGINT UNSIGNED NULL,
    snapshot_image MEDIUMBLOB NULL,
    inspector_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    comment TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    CONSTRAINT pk_safety_inspections PRIMARY KEY (id),
    CONSTRAINT chk_safety_inspections_status
        CHECK (status IN ('DRAFT', 'COMPLETED')),
    CONSTRAINT fk_safety_inspections_area
        FOREIGN KEY (inspection_area_id) REFERENCES inspection_areas (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_safety_inspections_template
        FOREIGN KEY (checklist_template_id) REFERENCES checklist_templates (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    INDEX idx_safety_inspections_area_updated
        (inspection_area_id, updated_at),
    INDEX idx_safety_inspections_simulation_result
        (simulation_result_id),
    INDEX idx_safety_inspections_inspector
        (inspector_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Snapshot columns preserve the wording and order used when an inspection was started.
CREATE TABLE IF NOT EXISTS safety_inspection_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    safety_inspection_id BIGINT UNSIGNED NOT NULL,
    template_item_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    criterion TEXT NULL,
    category VARCHAR(30) NOT NULL,
    display_order INT UNSIGNED NOT NULL,
    result VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    comment TEXT NULL,
    marker_x DECIMAL(6, 5) NULL,
    marker_y DECIMAL(6, 5) NULL,
    checked_at DATETIME(6) NULL,
    CONSTRAINT pk_safety_inspection_items PRIMARY KEY (id),
    CONSTRAINT uk_safety_inspection_items_template_item
        UNIQUE (safety_inspection_id, template_item_id),
    CONSTRAINT uk_safety_inspection_items_order
        UNIQUE (safety_inspection_id, display_order),
    CONSTRAINT chk_safety_inspection_items_result
        CHECK (result IN ('PENDING', 'PASS', 'REVIEW_REQUIRED', 'FAIL')),
    CONSTRAINT fk_safety_inspection_items_inspection
        FOREIGN KEY (safety_inspection_id) REFERENCES safety_inspections (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_safety_inspection_items_template_item
        FOREIGN KEY (template_item_id) REFERENCES checklist_template_items (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS reports (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    author_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NULL,
    ai_summary LONGTEXT NULL,
    generation_request LONGTEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT '초안',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT chk_reports_status
        CHECK (status IN ('AI 작성 중', '생성 실패', '초안', '작성 중', '완료')),
    INDEX idx_reports_author_id (author_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS report_simulations (
    report_id BIGINT UNSIGNED NOT NULL,
    simulation_result_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT pk_report_simulations PRIMARY KEY (report_id, simulation_result_id),
    CONSTRAINT fk_report_simulations_report
        FOREIGN KEY (report_id) REFERENCES reports (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    INDEX idx_report_simulations_simulation_result_id (simulation_result_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS manuals (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(30) NOT NULL,
    CONSTRAINT pk_manuals PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
