# Auth DB 지침

- 이 디렉터리는 auth-service 전용 초기 스키마와 개발용 DML을 관리한다.
- `users`, `roles`, `user_roles` 변경은 인증, JWT 역할 클레임, 사용자 관리 API 영향을 확인한다.
- DML의 비밀번호는 해시값만 사용하며 실제 계정·비밀값을 추가하지 않는다.
- DDL 변경 최종 응답에는 변경 테이블과 직접 관계 테이블을 포함한 Mermaid `erDiagram` 코드 및 백엔드 연계 영향을 제공한다.
