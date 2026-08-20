# Regulation MyBatis Mapper 지침

- SQL 변경 시 Java Mapper, DTO/도메인, `db/schema.sql`을 함께 확인한다.
- 사용자 입력은 `#{}` 바인딩으로만 전달하고, 역할 필터·정렬·페이지네이션을 누락하지 않는다.
- 외부 사용자·도면·시뮬레이션 식별자를 타 서비스 테이블 조인이나 FK로 바꾸지 않는다.
