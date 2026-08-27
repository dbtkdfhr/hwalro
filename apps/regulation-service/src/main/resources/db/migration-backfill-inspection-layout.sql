-- 기존 점검 행에 구역에 연결된 도면을 승계한다.
-- 도면 연결 기능 추가 이전에 생성된 점검도 스냅샷 대상이 되도록 한다.
USE hwalro_regulation;

UPDATE safety_inspections inspection
JOIN inspection_areas area ON area.id = inspection.inspection_area_id
SET inspection.layout_id = area.layout_id
WHERE inspection.layout_id IS NULL
  AND area.layout_id IS NOT NULL;
