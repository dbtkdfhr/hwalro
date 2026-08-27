# 주의 항목 도메인 지침

- 담당자와 시뮬레이션 결과 ID는 auth-service·simulation-service가 소유한 외부 식별자다. 타 서비스 DB FK나 내부 구현 의존성을 추가하지 않는다.
- ADMIN·OPERATOR·SAFETY_REVIEWER의 조회·수정 범위는 기존 서비스 계층 규칙을 따른다.
- 목록 필터·정렬·페이지네이션을 수정할 때 역할별 데이터 노출 범위를 함께 확인한다.
