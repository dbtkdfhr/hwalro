ALTER TABLE fabrics
    ADD COLUMN movement_policy VARCHAR(20) NOT NULL DEFAULT 'WITHIN_ZONE' AFTER rotation;

ALTER TABLE fabrics
    DROP CHECK ck_fabrics_max_movement_distance,
    DROP COLUMN movable,
    DROP COLUMN max_movement_distance,
    DROP COLUMN rotation_locked,
    DROP COLUMN keep_against_wall,
    ADD CONSTRAINT ck_fabrics_movement_policy
        CHECK (movement_policy IN ('FREE', 'WITHIN_ZONE', 'FIXED'));
