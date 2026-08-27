-- 주의 항목을 시뮬레이션 결과가 아닌 도면 기준으로도 소유할 수 있게 한다.
-- layout_version_id는 주의 항목이 표시된 도면 버전을 남기는 이력 참조다.
USE hwalro_regulation;

ALTER TABLE risks
    ADD COLUMN layout_id BIGINT UNSIGNED NULL AFTER simulation_result_id,
    ADD COLUMN layout_version_id BIGINT UNSIGNED NULL AFTER layout_id,
    ADD INDEX idx_risks_layout_id (layout_id);
