-- Run after schema.sql has initialized hwalro_simulation.
-- The fixture is rolled back so it can be safely rerun against a development database.
USE hwalro_simulation;

DROP PROCEDURE IF EXISTS verify_improvement_proposal_schema;

DELIMITER //

CREATE PROCEDURE verify_improvement_proposal_schema()
BEGIN
    DECLARE duplicate_proposal_order_blocked BOOLEAN DEFAULT FALSE;
    DECLARE duplicate_proposal_simulation_blocked BOOLEAN DEFAULT FALSE;
    DECLARE duplicate_simulation_link_blocked BOOLEAN DEFAULT FALSE;
    DECLARE missing_source_simulation_blocked BOOLEAN DEFAULT FALSE;

    INSERT INTO floor_plans (name, width, height)
    VALUES ('Improvement proposal verification floor', 100, 80);
    SET @floor_plan_id = LAST_INSERT_ID();

    INSERT INTO layouts (floor_plan_id, created_by, title)
    VALUES (@floor_plan_id, 1, 'Improvement proposal verification layout');
    SET @layout_id = LAST_INSERT_ID();

    INSERT INTO layout_versions (layout_id, version, status)
    VALUES (@layout_id, 1, 'DRAFT');
    SET @source_layout_version_id = LAST_INSERT_ID();

    INSERT INTO layout_versions (layout_id, version, status)
    VALUES (@layout_id, 2, 'DRAFT');
    SET @saved_layout_version_id = LAST_INSERT_ID();

    UPDATE layouts
    SET current_version_id = @saved_layout_version_id
    WHERE id = @layout_id;

    INSERT INTO simulations (layout_version_id, created_by, title, status)
    VALUES (@source_layout_version_id, 1, 'Source simulation', 'COMPLETED');
    SET @source_simulation_id = LAST_INSERT_ID();

    INSERT INTO improvement_proposals (
        source_simulation_id,
        saved_layout_version_id,
        proposal_order,
        proposal_type,
        title,
        change_data,
        change_summary,
        saved_at
    ) VALUES (
        @source_simulation_id,
        @saved_layout_version_id,
        1,
        'MINIMAL',
        'Minimum change proposal',
        JSON_OBJECT('relocateFacilityIds', JSON_ARRAY(1)),
        JSON_OBJECT('summary', 'Move one facility'),
        CURRENT_TIMESTAMP(6)
    );
    SET @proposal_a_id = LAST_INSERT_ID();

    INSERT INTO improvement_proposals (
        source_simulation_id,
        proposal_order,
        proposal_type,
        title,
        change_data,
        change_summary
    ) VALUES
        (@source_simulation_id, 2, 'BALANCED', 'Balanced proposal', JSON_OBJECT(), JSON_OBJECT()),
        (@source_simulation_id, 3, 'MAXIMUM', 'Maximum proposal', JSON_OBJECT(), JSON_OBJECT());
    SELECT id INTO @proposal_b_id
    FROM improvement_proposals
    WHERE source_simulation_id = @source_simulation_id
      AND proposal_order = 2;

    INSERT INTO simulations (layout_version_id, parent_simulation_id, created_by, title, status)
    VALUES (@saved_layout_version_id, @source_simulation_id, 1, 'Verified simulation', 'COMPLETED');
    SET @verification_simulation_id = LAST_INSERT_ID();

    INSERT INTO proposal_simulations (improvement_proposal_id, simulation_id, source_simulation_id)
    VALUES (@proposal_a_id, @verification_simulation_id, @source_simulation_id);

    -- Each block catches the expected constraint violation and records that it occurred.
    BEGIN
        DECLARE CONTINUE HANDLER FOR 1062 SET duplicate_proposal_order_blocked = TRUE;
        INSERT INTO improvement_proposals (
            source_simulation_id, proposal_order, proposal_type, title, change_data, change_summary
        ) VALUES (@source_simulation_id, 1, 'MINIMAL', 'Duplicate order', JSON_OBJECT(), JSON_OBJECT());
    END;

    BEGIN
        DECLARE CONTINUE HANDLER FOR 1062 SET duplicate_proposal_simulation_blocked = TRUE;
        INSERT INTO proposal_simulations (improvement_proposal_id, simulation_id, source_simulation_id)
        VALUES (@proposal_a_id, @source_simulation_id, @source_simulation_id);
    END;

    BEGIN
        DECLARE CONTINUE HANDLER FOR 1062 SET duplicate_simulation_link_blocked = TRUE;
        INSERT INTO proposal_simulations (improvement_proposal_id, simulation_id, source_simulation_id)
        VALUES (@proposal_b_id, @verification_simulation_id, @source_simulation_id);
    END;

    BEGIN
        DECLARE CONTINUE HANDLER FOR 1452 SET missing_source_simulation_blocked = TRUE;
        INSERT INTO improvement_proposals (
            source_simulation_id, proposal_order, proposal_type, title, change_data, change_summary
        ) VALUES (999999, 1, 'MINIMAL', 'Missing source', JSON_OBJECT(), JSON_OBJECT());
    END;

    IF NOT (
        duplicate_proposal_order_blocked
        AND duplicate_proposal_simulation_blocked
        AND duplicate_simulation_link_blocked
        AND missing_source_simulation_blocked
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Improvement proposal integrity verification failed';
    END IF;

    SELECT
        source_simulation.id AS source_simulation_id,
        proposal.id AS proposal_id,
        proposal.title,
        verification_simulation.id AS verification_simulation_id,
        verification_simulation.status AS verification_status
    FROM improvement_proposals proposal
    JOIN simulations source_simulation ON source_simulation.id = proposal.source_simulation_id
    LEFT JOIN proposal_simulations proposal_simulation
        ON proposal_simulation.improvement_proposal_id = proposal.id
    LEFT JOIN simulations verification_simulation
        ON verification_simulation.id = proposal_simulation.simulation_id
    WHERE proposal.source_simulation_id = @source_simulation_id
    ORDER BY proposal.proposal_order;
END //

DELIMITER ;

START TRANSACTION;
CALL verify_improvement_proposal_schema();
ROLLBACK;

DROP PROCEDURE verify_improvement_proposal_schema;
