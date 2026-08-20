-- 위험 예상 항목 목록 확인용 수동 DML이다. 자동 실행하지 않는다.
-- 401~412번 위험 예상 항목을 추가하거나 갱신한다.

USE hwalro_regulation;

INSERT INTO risks (
    id,
    simulation_result_id,
    assignee_id,
    title,
    description,
    start_x,
    start_y,
    end_x,
    end_y,
    severity,
    status,
    created_at
) VALUES
    (401, NULL, 1, '중앙 통로 밀집도 증가', '점심 시간대 중앙 통로에 체류 인원이 집중되어 보행 폭이 감소할 가능성이 있습니다.', 12.5000, 8.2000, 26.8000, 8.2000, '높음', '조치 중', NOW(6) - INTERVAL 1 HOUR),
    (402, NULL, 2, '비상 출구 앞 적치물 확인', '후면 비상 출구 앞에 홍보 배너와 물품 상자가 놓여 있어 대피 시 제거가 필요합니다.', 42.1000, 18.6000, 45.0000, 18.6000, '높음', '임시저장', NOW(6) - INTERVAL 3 HOUR),
    (403, NULL, 3, '체험존 대기열 통로 침범', '체험존 대기열이 주 통로까지 확장될 수 있어 안내선 보강이 필요합니다.', 18.4000, 31.7000, 29.5000, 31.7000, '보통', '조치 중', NOW(6) - INTERVAL 5 HOUR),
    (404, NULL, 1, '안내 표지 시인성 부족', '교차 구간의 대피 방향 표지가 조명에 가려 멀리서 확인하기 어렵습니다.', 33.3000, 12.4000, 35.1000, 12.4000, '보통', '완료', NOW(6) - INTERVAL 1 DAY),
    (405, NULL, 2, '임시 판매대 모서리 충돌 위험', '판매대 끝부분이 보행 동선과 가까워 혼잡 시간대 충돌 가능성이 있습니다.', 9.8000, 22.0000, 11.2000, 22.0000, '낮음', '임시저장', NOW(6) - INTERVAL 1 DAY - INTERVAL 2 HOUR),
    (406, NULL, 3, '지하 출입구 조도 저하', '지하 출입구 계단 구간의 조도가 낮아 야간 이동 시 주의가 필요합니다.', 51.7000, 6.3000, 54.6000, 6.3000, '보통', '조치 중', NOW(6) - INTERVAL 2 DAY),
    (407, NULL, 1, '소화기 접근 동선 차단', '소화기 앞에 대기 안내판이 설치되어 비상 시 즉시 접근하기 어렵습니다.', 27.6000, 16.8000, 29.0000, 16.8000, '높음', '완료', NOW(6) - INTERVAL 2 DAY - INTERVAL 3 HOUR),
    (408, NULL, 2, '휴게 공간 대피 동선 혼재', '휴게 좌석이 비상 대피 동선과 가까워 혼잡 시 이동을 방해할 수 있습니다.', 37.2000, 29.1000, 47.9000, 29.1000, '보통', '임시저장', NOW(6) - INTERVAL 3 DAY),
    (409, NULL, 3, '출입구 우산 보관대 미끄럼 위험', '우천 시 출입구 주변 바닥이 젖을 수 있어 흡수 매트 배치가 필요합니다.', 4.6000, 5.1000, 7.8000, 5.1000, '낮음', '조치 중', NOW(6) - INTERVAL 3 DAY - INTERVAL 2 HOUR),
    (410, NULL, 1, '공연장 후면 출구 안내 부족', '객석 후면의 보조 출구 위치를 알리는 안내가 부족합니다.', 61.3000, 24.5000, 64.1000, 24.5000, '보통', '완료', NOW(6) - INTERVAL 4 DAY),
    (411, NULL, 2, '계산대 대기선 교차', '계산대 대기선과 입장 대기선이 교차해 보행 흐름이 지연될 수 있습니다.', 22.7000, 40.8000, 38.9000, 40.8000, '높음', '조치 중', NOW(6) - INTERVAL 5 DAY),
    (412, NULL, 3, '비상 방송 스피커 음량 점검', '후면 휴게 구역에서 비상 방송 음량이 충분한지 현장 점검이 필요합니다.', 48.5000, 35.6000, 55.8000, 35.6000, '낮음', '임시저장', NOW(6) - INTERVAL 6 DAY)
ON DUPLICATE KEY UPDATE
    simulation_result_id = VALUES(simulation_result_id),
    assignee_id = VALUES(assignee_id),
    title = VALUES(title),
    description = VALUES(description),
    start_x = VALUES(start_x),
    start_y = VALUES(start_y),
    end_x = VALUES(end_x),
    end_y = VALUES(end_y),
    severity = VALUES(severity),
    status = VALUES(status);

SELECT id, assignee_id, title, severity, status, created_at
FROM risks
WHERE id BETWEEN 401 AND 412
ORDER BY id DESC;
