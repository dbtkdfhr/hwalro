# Simulation JWT 지침

- `userId`와 `roles` 클레임명·형식은 auth-service가 발급하고 simulation-service가 소비하는 계약이다. 단독 변경하지 않는다.
- 인증 실패·권한 부족 응답은 기존 인터셉터와 예외 처리 흐름을 유지한다.
- 역할 문자열 변경은 auth-service와 regulation-service의 인가 규칙까지 함께 검토한다.
