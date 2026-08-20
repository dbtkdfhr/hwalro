# Simulation MyBatis Mapper 지침

- SQL 변경 시 Java Mapper, DTO/도메인, `db/schema.sql`을 함께 확인한다.
- 사용자 입력은 `#{}` 바인딩만 사용하고, 소유권 필터·정렬·페이지네이션을 누락하지 않는다.
- 도면 버전과 시뮬레이션·개선안 계보를 잇는 조인은 동일한 원본·버전 관계를 보장해야 한다.
