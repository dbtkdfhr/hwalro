USE hwalro_simulation;

ALTER TABLE fabrics
    ADD COLUMN movement_policy VARCHAR(20) NOT NULL DEFAULT 'WITHIN_ZONE' AFTER rotation,
    ADD CONSTRAINT ck_fabrics_movement_policy
        CHECK (movement_policy IN ('FREE', 'WITHIN_ZONE', 'FIXED'));
