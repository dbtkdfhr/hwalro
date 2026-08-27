-- 체크리스트를 도면과 연결한다.
-- 구역에 도면을 연결하고, 점검 시작 시 도면 스냅샷(PNG)을 함께 저장해 이력이 도면에 쌓이게 한다.
-- marker_x/marker_y는 점검 항목을 도면 위 위치(0~1 정규화)에 표시하기 위한 값이다.
USE hwalro_regulation;

ALTER TABLE inspection_areas
    ADD COLUMN layout_id BIGINT UNSIGNED NULL AFTER description;

ALTER TABLE safety_inspections
    ADD COLUMN layout_id BIGINT UNSIGNED NULL AFTER simulation_result_id,
    ADD COLUMN layout_version_id BIGINT UNSIGNED NULL AFTER layout_id,
    ADD COLUMN snapshot_image MEDIUMBLOB NULL AFTER layout_version_id;

ALTER TABLE safety_inspection_items
    ADD COLUMN marker_x DECIMAL(6, 5) NULL AFTER comment,
    ADD COLUMN marker_y DECIMAL(6, 5) NULL AFTER marker_x;
