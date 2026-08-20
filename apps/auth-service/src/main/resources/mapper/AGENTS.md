# Auth MyBatis Mapper 지침

- SQL 변경 시 Java Mapper, 도메인/응답 DTO, `db/schema.sql`을 함께 확인한다.
- 사용자 입력은 항상 `#{}` 바인딩으로 전달한다.
- 사용자·역할 조회는 로그인 식별과 JWT 역할 구성에 사용되므로 조인·정렬·중복 제거의 의미를 바꾸지 않는다.
