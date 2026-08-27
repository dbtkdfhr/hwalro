-- 위험 예상 항목을 도면 기준으로만 소유하도록 전환하는 수동 마이그레이션이다.
USE hwalro_regulation;

DELETE FROM risks
WHERE simulation_result_id IS NOT NULL
   OR layout_id IS NULL;

ALTER TABLE risks
    DROP INDEX idx_risks_simulation_result_id,
    DROP COLUMN simulation_result_id,
    MODIFY COLUMN layout_id BIGINT UNSIGNED NOT NULL;
