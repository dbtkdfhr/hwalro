CREATE DATABASE IF NOT EXISTS hwalro_simulation
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

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

CREATE TABLE IF NOT EXISTS floor_plans (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    image_url VARCHAR(2048) NULL,
    width DECIMAL(12, 4) NOT NULL,
    height DECIMAL(12, 4) NOT NULL,
    CONSTRAINT pk_floor_plans PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS layouts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    floor_plan_id BIGINT UNSIGNED NOT NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    current_version_id BIGINT UNSIGNED NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_layouts PRIMARY KEY (id),
    CONSTRAINT fk_layouts_floor_plan
        FOREIGN KEY (floor_plan_id) REFERENCES floor_plans (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    INDEX idx_layouts_created_by (created_by)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS layout_versions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_id BIGINT UNSIGNED NOT NULL,
    version INT UNSIGNED NOT NULL,
    status VARCHAR(30) NOT NULL,
    optimistic_lock INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_layout_versions PRIMARY KEY (id),
    CONSTRAINT uk_layout_versions_layout_version UNIQUE (layout_id, version),
    CONSTRAINT fk_layout_versions_layout
        FOREIGN KEY (layout_id) REFERENCES layouts (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE layouts
    ADD CONSTRAINT fk_layouts_current_version
        FOREIGN KEY (current_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS walls (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(200) NOT NULL,
    start_x DECIMAL(12, 4) NOT NULL,
    start_y DECIMAL(12, 4) NOT NULL,
    end_x DECIMAL(12, 4) NOT NULL,
    end_y DECIMAL(12, 4) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_walls PRIMARY KEY (id),
    CONSTRAINT uk_walls_id_version UNIQUE (id, layout_version_id),
    CONSTRAINT fk_walls_layout_version
        FOREIGN KEY (layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pillars (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(200) NOT NULL,
    start_x DECIMAL(12, 4) NOT NULL,
    start_y DECIMAL(12, 4) NOT NULL,
    end_x DECIMAL(12, 4) NOT NULL,
    end_y DECIMAL(12, 4) NOT NULL,
    rotation DECIMAL(12, 4) NOT NULL DEFAULT 0,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_pillars PRIMARY KEY (id),
    CONSTRAINT uk_pillars_id_version UNIQUE (id, layout_version_id),
    CONSTRAINT ck_pillars_extent CHECK (start_x < end_x AND start_y < end_y),
    CONSTRAINT fk_pillars_layout_version
        FOREIGN KEY (layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS fabrics (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(200) NOT NULL,
    start_x DECIMAL(12, 4) NOT NULL,
    start_y DECIMAL(12, 4) NOT NULL,
    end_x DECIMAL(12, 4) NOT NULL,
    end_y DECIMAL(12, 4) NOT NULL,
    rotation DECIMAL(12, 4) NOT NULL DEFAULT 0,
    movement_policy VARCHAR(20) NOT NULL DEFAULT 'WITHIN_ZONE',
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_fabrics PRIMARY KEY (id),
    -- layout_zone_members가 (fabric_id, layout_version_id) 복합 FK로 참조한다.
    CONSTRAINT uk_fabrics_id_version UNIQUE (id, layout_version_id),
    -- 아래→위로 그린 구조물이 start > end로 저장되면 배치 개선안 탐색이 조용히 후보를 버린다
    -- (Java는 좌표 검증 실패, 엔진은 INVALID_GEOMETRY). DrawingService가 저장 시 정규화하며,
    -- 여기서 한 번 더 막아 회귀 시 즉시 실패하게 한다.
    CONSTRAINT ck_fabrics_extent CHECK (start_x < end_x AND start_y < end_y),
    CONSTRAINT ck_fabrics_movement_policy
        CHECK (movement_policy IN ('FREE', 'WITHIN_ZONE', 'FIXED')),
    CONSTRAINT fk_fabrics_layout_version
        FOREIGN KEY (layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS layout_texts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    text TEXT NOT NULL,
    x DECIMAL(12, 4) NOT NULL,
    y DECIMAL(12, 4) NOT NULL,
    CONSTRAINT pk_layout_texts PRIMARY KEY (id),
    CONSTRAINT fk_layout_texts_layout_version
        FOREIGN KEY (layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS layout_exits (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(200) NOT NULL,
    start_x DECIMAL(12, 4) NOT NULL,
    start_y DECIMAL(12, 4) NOT NULL,
    end_x DECIMAL(12, 4) NOT NULL,
    end_y DECIMAL(12, 4) NOT NULL,
    CONSTRAINT pk_layout_exits PRIMARY KEY (id),
    CONSTRAINT uk_layout_exits_id_version UNIQUE (id, layout_version_id),
    CONSTRAINT fk_layout_exits_layout_version
        FOREIGN KEY (layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS outside_walls (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(200) NOT NULL,
    start_x DECIMAL(12, 4) NOT NULL,
    start_y DECIMAL(12, 4) NOT NULL,
    end_x DECIMAL(12, 4) NOT NULL,
    end_y DECIMAL(12, 4) NOT NULL,
    CONSTRAINT pk_outside_walls PRIMARY KEY (id),
    CONSTRAINT fk_outside_walls_layout_version
        FOREIGN KEY (layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS layout_zones (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(200) NOT NULL,
    zone_type VARCHAR(40) NOT NULL,
    x DECIMAL(12, 4) NOT NULL,
    y DECIMAL(12, 4) NOT NULL,
    width DECIMAL(12, 4) NOT NULL,
    height DECIMAL(12, 4) NOT NULL,
    -- auth-service의 사용자 ID. 서비스 경계를 넘지 않기 위해 FK를 만들지 않고
    -- 배정 시점에 auth-service API로 검증한다.
    assigned_user_id BIGINT UNSIGNED NULL,
    default_exit_id BIGINT UNSIGNED NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_layout_zones PRIMARY KEY (id),
    -- layout_zone_members가 (zone_id, layout_version_id) 복합 FK로 참조한다.
    CONSTRAINT uk_layout_zones_id_version UNIQUE (id, layout_version_id),
    CONSTRAINT uk_layout_zones_version_name UNIQUE (layout_version_id, name),
    CONSTRAINT ck_layout_zones_extent CHECK (width > 0 AND height > 0),
    CONSTRAINT fk_layout_zones_layout_version
        FOREIGN KEY (layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    -- 복합 FK로 다른 도면 버전의 비상구를 참조하지 못하게 막는다. MySQL은 NOT NULL 컬럼이
    -- 포함된 복합 FK에 SET NULL을 허용하지 않으므로 RESTRICT이며, 비상구 삭제 전에
    -- DrawingService가 이 참조를 먼저 NULL로 만든다.
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

CREATE TABLE IF NOT EXISTS simulations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    layout_version_id BIGINT UNSIGNED NOT NULL,
    parent_simulation_id BIGINT UNSIGNED NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    requested_at DATETIME(6) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    failure_message VARCHAR(1000) NULL,
    failure_detail JSON NULL,
    CONSTRAINT pk_simulations PRIMARY KEY (id),
    CONSTRAINT uk_simulations_id_version UNIQUE (id, layout_version_id),
    CONSTRAINT uk_simulations_id_parent UNIQUE (id, parent_simulation_id),
    CONSTRAINT ck_simulations_status CHECK (status IN ('DRAFT', 'REQUESTED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT fk_simulations_layout_version
        FOREIGN KEY (layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_simulations_parent_simulation
        FOREIGN KEY (parent_simulation_id) REFERENCES simulations (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    INDEX idx_simulations_created_by (created_by)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS simulation_options (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    simulation_id BIGINT UNSIGNED NOT NULL,
    random_seed INT NOT NULL,
    model_profile VARCHAR(50) NOT NULL DEFAULT 'SFM_DEFAULT_V2',
    routing_profile VARCHAR(50) NOT NULL DEFAULT 'HAZARD_RADIAL_EXP_V3',
    total_people INT UNSIGNED NOT NULL,
    walking_speed DECIMAL(8, 4) NOT NULL,
    reaction_time DECIMAL(8, 4) NOT NULL DEFAULT 0.5000,
    initial_response_time_mean DECIMAL(8, 4) NOT NULL DEFAULT 0.0000,
    initial_response_time_std_dev DECIMAL(8, 4) NOT NULL DEFAULT 0.0000,
    CONSTRAINT pk_simulation_options PRIMARY KEY (id),
    CONSTRAINT uk_simulation_options_simulation UNIQUE (simulation_id),
    CONSTRAINT fk_simulation_options_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS simulation_initial_states (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    simulation_id BIGINT UNSIGNED NOT NULL,
    agent_positions JSON NOT NULL,
    CONSTRAINT pk_simulation_initial_states PRIMARY KEY (id),
    CONSTRAINT uk_simulation_initial_states_simulation UNIQUE (simulation_id),
    CONSTRAINT fk_simulation_initial_states_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS simulation_exits (
    simulation_id BIGINT UNSIGNED NOT NULL,
    layout_exit_id BIGINT UNSIGNED NOT NULL,
    layout_version_id BIGINT UNSIGNED NOT NULL,

    CONSTRAINT pk_simulation_exits
        PRIMARY KEY (simulation_id, layout_exit_id),

    CONSTRAINT fk_simulation_exits_simulation_version
        FOREIGN KEY (simulation_id, layout_version_id)
        REFERENCES simulations (id, layout_version_id)
        ON UPDATE RESTRICT
        ON DELETE CASCADE,

    CONSTRAINT fk_simulation_exits_layout_exit_version
        FOREIGN KEY (layout_exit_id, layout_version_id)
        REFERENCES layout_exits (id, layout_version_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS hazard_zones (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    simulation_id BIGINT UNSIGNED NOT NULL,
    center_x DECIMAL(12, 4) NOT NULL,
    center_y DECIMAL(12, 4) NOT NULL,
    radius DECIMAL(12, 4) NOT NULL,
    CONSTRAINT pk_hazard_zones PRIMARY KEY (id),
    CONSTRAINT fk_hazard_zones_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS simulation_results (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    simulation_id BIGINT UNSIGNED NOT NULL,
    engine_version VARCHAR(100) NOT NULL,
    termination_reason VARCHAR(30) NOT NULL,
    frame_interval_seconds DECIMAL(8, 3) NOT NULL,
    termination_detail JSON NULL,
    recovery_detail JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_simulation_results PRIMARY KEY (id),
    CONSTRAINT uk_simulation_results_simulation UNIQUE (simulation_id),
    CONSTRAINT fk_simulation_results_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS simulation_metrics (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    simulation_result_id BIGINT UNSIGNED NOT NULL,
    unit VARCHAR(50) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_value DOUBLE NOT NULL,
    CONSTRAINT pk_simulation_metrics PRIMARY KEY (id),
    CONSTRAINT uk_simulation_metrics_result_type UNIQUE (simulation_result_id, metric_type),
    CONSTRAINT fk_simulation_metrics_result
        FOREIGN KEY (simulation_result_id) REFERENCES simulation_results (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS timelines (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    simulation_result_id BIGINT UNSIGNED NOT NULL,
    chunk_sequence INT UNSIGNED NOT NULL,
    frame_data JSON NOT NULL,
    CONSTRAINT pk_timelines PRIMARY KEY (id),
    CONSTRAINT uk_timelines_result_sequence UNIQUE (simulation_result_id, chunk_sequence),
    CONSTRAINT fk_timelines_result
        FOREIGN KEY (simulation_result_id) REFERENCES simulation_results (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS heatmaps (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    simulation_result_id BIGINT UNSIGNED NOT NULL,
    chunk_sequence INT UNSIGNED NOT NULL,
    density_data JSON NOT NULL,
    CONSTRAINT pk_heatmaps PRIMARY KEY (id),
    CONSTRAINT uk_heatmaps_result_sequence UNIQUE (simulation_result_id, chunk_sequence),
    CONSTRAINT fk_heatmaps_result
        FOREIGN KEY (simulation_result_id) REFERENCES simulation_results (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS detected_bottlenecks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    simulation_result_id BIGINT UNSIGNED NOT NULL,
    bottleneck_order INT UNSIGNED NOT NULL,
    start_time_seconds DOUBLE NOT NULL,
    end_time_seconds DOUBLE NOT NULL,
    peak_density DOUBLE NOT NULL,
    threshold_value DOUBLE NOT NULL,
    geometry JSON NOT NULL,
    analysis_version VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_detected_bottlenecks
    PRIMARY KEY (id),

    CONSTRAINT uk_detected_bottlenecks_result_order
    UNIQUE (simulation_result_id, bottleneck_order),

    CONSTRAINT fk_detected_bottlenecks_result
    FOREIGN KEY (simulation_result_id)
    REFERENCES simulation_results (id)
    ON UPDATE CASCADE
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS improvement_proposals (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    source_simulation_id BIGINT UNSIGNED NOT NULL,
    saved_layout_version_id BIGINT UNSIGNED NULL,
    proposal_order INT UNSIGNED NOT NULL,
    proposal_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    change_data JSON NOT NULL,
    change_summary JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    saved_at DATETIME(6) NULL,
    CONSTRAINT pk_improvement_proposals PRIMARY KEY (id),
    CONSTRAINT uk_improvement_proposals_id_source UNIQUE (id, source_simulation_id),
    CONSTRAINT uk_improvement_proposals_source_order UNIQUE (source_simulation_id, proposal_order),
    CONSTRAINT uk_improvement_proposals_saved_layout_version UNIQUE (saved_layout_version_id),
    CONSTRAINT fk_improvement_proposals_source_simulation
        FOREIGN KEY (source_simulation_id) REFERENCES simulations (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_improvement_proposals_saved_layout_version
        FOREIGN KEY (saved_layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS proposal_simulations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    improvement_proposal_id BIGINT UNSIGNED NOT NULL,
    simulation_id BIGINT UNSIGNED NOT NULL,
    source_simulation_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_proposal_simulations PRIMARY KEY (id),
    CONSTRAINT uk_proposal_simulations_proposal UNIQUE (improvement_proposal_id),
    CONSTRAINT uk_proposal_simulations_simulation UNIQUE (simulation_id),
    CONSTRAINT fk_proposal_simulations_improvement_proposal_lineage
        FOREIGN KEY (improvement_proposal_id, source_simulation_id)
        REFERENCES improvement_proposals (id, source_simulation_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_proposal_simulations_simulation_lineage
        FOREIGN KEY (simulation_id, source_simulation_id)
        REFERENCES simulations (id, parent_simulation_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS layout_searches (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    baseline_simulation_id BIGINT UNSIGNED NOT NULL,
    baseline_layout_version_id BIGINT UNSIGNED NOT NULL,
    planner_version VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    baseline_metrics JSON NOT NULL,
    diagnosis JSON NULL,
    budget JSON NOT NULL,
    constraints JSON NULL,
    requested_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    failure_code VARCHAR(50) NULL,
    failure_message VARCHAR(1000) NULL,
    CONSTRAINT pk_layout_searches PRIMARY KEY (id),
    CONSTRAINT ck_layout_searches_status CHECK (status IN
        ('PENDING','DIAGNOSING','GENERATING','VERIFYING','COMPLETED','NO_IMPROVEMENT','FAILED','CANCELLED')),
    CONSTRAINT fk_layout_searches_baseline_simulation
        FOREIGN KEY (baseline_simulation_id) REFERENCES simulations (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_layout_searches_baseline_layout_version
        FOREIGN KEY (baseline_layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    INDEX idx_layout_searches_baseline (baseline_simulation_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS layout_search_candidates (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    study_id BIGINT UNSIGNED NOT NULL,
    parent_candidate_id BIGINT UNSIGNED NULL,
    round_index INT UNSIGNED NOT NULL,
    candidate_order INT UNSIGNED NOT NULL,
    origin_finding_type VARCHAR(40) NOT NULL,
    operator_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    change_set JSON NOT NULL,
    rationale JSON NOT NULL,
    proxy_score DOUBLE NULL,
    metric_delta JSON NULL,
    reject_reason VARCHAR(60) NULL,
    constraints_snapshot JSON NULL,
    adopted_layout_version_id BIGINT UNSIGNED NULL,
    prepared_simulation_id BIGINT UNSIGNED NULL,
    adopted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_layout_search_candidates PRIMARY KEY (id),
    CONSTRAINT uk_layout_search_candidates_order UNIQUE (study_id, round_index, candidate_order),
    CONSTRAINT uk_layout_search_candidates_prepared_simulation UNIQUE (prepared_simulation_id),
    CONSTRAINT ck_layout_search_candidates_status CHECK (status IN
        ('GENERATED','REJECTED_CONSTRAINT','QUEUED','RUNNING','EVALUATED','NOT_IMPROVED','FAILED')),
    CONSTRAINT fk_layout_search_candidates_search
        FOREIGN KEY (study_id) REFERENCES layout_searches (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_layout_search_candidates_parent
        FOREIGN KEY (parent_candidate_id) REFERENCES layout_search_candidates (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_layout_search_candidates_adopted_version
        FOREIGN KEY (adopted_layout_version_id) REFERENCES layout_versions (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_layout_search_candidates_prepared_simulation
        FOREIGN KEY (prepared_simulation_id) REFERENCES simulations (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS layout_search_trials (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    candidate_id BIGINT UNSIGNED NOT NULL,
    engine_version VARCHAR(100) NULL,
    termination_reason VARCHAR(30) NULL,
    metrics JSON NULL,
    total_move_distance DOUBLE NULL,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at DATETIME(6) NULL,
    failure_message VARCHAR(1000) NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 1,
    CONSTRAINT pk_layout_search_trials PRIMARY KEY (id),
    CONSTRAINT uk_layout_search_trials_candidate UNIQUE (candidate_id),
    CONSTRAINT fk_layout_search_trials_candidate
        FOREIGN KEY (candidate_id) REFERENCES layout_search_candidates (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

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
