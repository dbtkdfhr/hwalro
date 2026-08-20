SET NAMES utf8mb4;

-- 신규 개발 DB에 안전 체크리스트 예시 구역, 템플릿, 점검 이력을 적재한다.
-- 개발용 test 사용자(ID 1)를 점검 담당자로 사용한다.
-- simulation_result_id and inspector_id are external identifiers owned by other services.

USE hwalro_regulation;

INSERT IGNORE INTO inspection_areas (id, name, description) VALUES
    (1, '더현대서울 지하 1층', '식품관 및 공용 통로 점검 구역'),
    (2, '더현대서울 지하 2층', '팝업·전시 운영 구역'),
    (3, '더현대서울 1층', '주 출입구 및 행사 운영 구역');

INSERT IGNORE INTO checklist_templates (id, inspection_area_id, version, status) VALUES
    (1, 1, 1, 'ACTIVE'),
    (2, 2, 1, 'ACTIVE'),
    (3, 3, 1, 'ACTIVE');

INSERT IGNORE INTO checklist_template_items
    (id, checklist_template_id, title, criterion, category, display_order) VALUES
    (1, 1, '피난 통로 적치물 여부', '공용 통로에 이동을 방해하는 적치물이 없어야 함', 'EVACUATION', 1),
    (2, 1, '방화문 개폐 상태', '방화문이 정상적으로 닫히고 고정 장치가 없어야 함', 'FIRE', 2),
    (3, 1, '유도등 점등 상태', '피난 유도등이 정상 점등되어야 함', 'GUIDANCE', 3),
    (4, 2, '피난 통로 최소 폭 확보', '도면 기준 2.4m 이상 확보', 'EVACUATION', 1),
    (5, 2, '비상구 전면 장애물 여부', '비상구 전면에 장애물이 없어야 함', 'EVACUATION', 2),
    (6, 2, '구조물 전도·고정 상태', '현장 시공 후 고정 상태 확인', 'STRUCTURE', 3),
    (7, 2, '안내 표지 가시성', '관람 동선 기준 안내 표지가 식별되어야 함', 'GUIDANCE', 4),
    (8, 2, '위험 구역 접근 통제', '차단선 및 안내 배치 확인', 'CONTROL', 5),
    (9, 2, '개선 배치 재시뮬레이션', '조치 완료 후 시뮬레이션 결과 확인', 'SIMULATION', 6),
    (10, 3, '주 출입구 접근성', '주 출입구의 유효 폭과 접근 동선을 확보해야 함', 'EVACUATION', 1),
    (11, 3, '외부 대피 장소 연결', '대피 동선이 외부 집결지까지 단절되지 않아야 함', 'EVACUATION', 2),
    (12, 3, '임시 구조물 고정 상태', '행사 구조물이 전도되지 않도록 고정되어야 함', 'STRUCTURE', 3),
    (13, 3, '안전 안내 인력 배치', '혼잡 구간에 안전 안내 인력을 배치해야 함', 'CONTROL', 4);

INSERT IGNORE INTO safety_inspections
    (id, inspection_area_id, checklist_template_id, simulation_result_id, inspector_id, status, comment,
     created_at, updated_at, completed_at) VALUES
    (1, 2, 2, 18, 1, 'DRAFT', '부적합 항목 조치 완료 후 다시 확인',
     '2026-08-04 09:00:00.000000', '2026-08-04 11:20:00.000000', NULL),
    (2, 2, 2, 15, 1, 'COMPLETED', '안내 표지 위치를 조정하고 최종 확인함',
     '2026-07-20 10:00:00.000000', '2026-07-20 13:30:00.000000', '2026-07-20 13:30:00.000000'),
    (3, 1, 1, NULL, 1, 'COMPLETED', '식품관 공용 통로와 방화문 상태를 점검하고 조치를 완료했습니다.',
     '2026-07-15 10:00:00.000000', '2026-07-15 11:40:00.000000', '2026-07-15 11:40:00.000000'),
    (4, 3, 3, NULL, 1, 'DRAFT', '행사 구조물 고정 상태와 출입구 안내 표지를 추가 확인 중입니다.',
     '2026-08-05 09:30:00.000000', '2026-08-05 10:10:00.000000', NULL);

INSERT IGNORE INTO safety_inspection_items
    (id, safety_inspection_id, template_item_id, title, criterion, category, display_order,
     result, comment, checked_at) VALUES
    (1, 1, 4, '피난 통로 최소 폭 확보', '도면 기준 2.4m 이상 확보', 'EVACUATION', 1,
     'PASS', NULL, '2026-08-04 10:10:00.000000'),
    (2, 1, 5, '비상구 전면 장애물 여부', '비상구 전면에 장애물이 없어야 함', 'EVACUATION', 2,
     'PASS', '장애물 없음', '2026-08-04 10:20:00.000000'),
    (3, 1, 6, '구조물 전도·고정 상태', '현장 시공 후 고정 상태 확인', 'STRUCTURE', 3,
     'REVIEW_REQUIRED', '현장 사진 추가 필요', '2026-08-04 10:40:00.000000'),
    (4, 1, 7, '안내 표지 가시성', '관람 동선 기준 안내 표지가 식별되어야 함', 'GUIDANCE', 4,
     'FAIL', '관람 동선 기준 표지가 가려짐', '2026-08-04 10:50:00.000000'),
    (5, 1, 8, '위험 구역 접근 통제', '차단선 및 안내 배치 확인', 'CONTROL', 5,
     'PASS', NULL, '2026-08-04 11:00:00.000000'),
    (6, 1, 9, '개선 배치 재시뮬레이션', '조치 완료 후 시뮬레이션 결과 확인', 'SIMULATION', 6,
     'PENDING', '조치 완료 후 실행', NULL),
    (7, 2, 4, '피난 통로 최소 폭 확보', '도면 기준 2.4m 이상 확보', 'EVACUATION', 1,
     'PASS', NULL, '2026-07-20 11:00:00.000000'),
    (8, 2, 5, '비상구 전면 장애물 여부', '비상구 전면에 장애물이 없어야 함', 'EVACUATION', 2,
     'PASS', NULL, '2026-07-20 11:10:00.000000'),
    (9, 2, 6, '구조물 전도·고정 상태', '현장 시공 후 고정 상태 확인', 'STRUCTURE', 3,
     'PASS', NULL, '2026-07-20 11:20:00.000000'),
    (10, 2, 7, '안내 표지 가시성', '관람 동선 기준 안내 표지가 식별되어야 함', 'GUIDANCE', 4,
     'PASS', '표지 위치 조정 완료', '2026-07-20 12:50:00.000000'),
    (11, 2, 8, '위험 구역 접근 통제', '차단선 및 안내 배치 확인', 'CONTROL', 5,
     'PASS', NULL, '2026-07-20 13:00:00.000000'),
    (12, 2, 9, '개선 배치 재시뮬레이션', '조치 완료 후 시뮬레이션 결과 확인', 'SIMULATION', 6,
     'PASS', '시뮬레이션 결과 확인', '2026-07-20 13:20:00.000000'),
    (13, 3, 1, '피난 통로 적치물 여부', '공용 통로에 이동을 방해하는 적치물이 없어야 함', 'EVACUATION', 1,
     'PASS', '통로 적치물 없음', '2026-07-15 10:30:00.000000'),
    (14, 3, 2, '방화문 개폐 상태', '방화문이 정상적으로 닫히고 고정 장치가 없어야 함', 'FIRE', 2,
     'PASS', '자동 폐쇄 상태 정상', '2026-07-15 10:50:00.000000'),
    (15, 3, 3, '유도등 점등 상태', '피난 유도등이 정상 점등되어야 함', 'GUIDANCE', 3,
     'PASS', '유도등 점등 확인', '2026-07-15 11:20:00.000000'),
    (16, 4, 10, '주 출입구 접근성', '주 출입구의 유효 폭과 접근 동선을 확보해야 함', 'EVACUATION', 1,
     'PASS', '출입구 유효 폭 확보', '2026-08-05 09:40:00.000000'),
    (17, 4, 11, '외부 대피 장소 연결', '대피 동선이 외부 집결지까지 단절되지 않아야 함', 'EVACUATION', 2,
     'PENDING', '외부 집결지 안내 표지 확인 예정', NULL),
    (18, 4, 12, '임시 구조물 고정 상태', '행사 구조물이 전도되지 않도록 고정되어야 함', 'STRUCTURE', 3,
     'REVIEW_REQUIRED', '고정 볼트 체결 사진 검토 필요', '2026-08-05 09:55:00.000000'),
    (19, 4, 13, '안전 안내 인력 배치', '혼잡 구간에 안전 안내 인력을 배치해야 함', 'CONTROL', 4,
     'FAIL', '출입구 혼잡 시간대 안내 인력이 부족함', '2026-08-05 10:05:00.000000');

SELECT area.id,
       area.name,
       inspection.id AS inspection_id,
       inspection.status,
       inspection.inspector_id,
       inspection.created_at
FROM inspection_areas area
LEFT JOIN safety_inspections inspection ON inspection.inspection_area_id = area.id
ORDER BY area.id, inspection.created_at DESC;
