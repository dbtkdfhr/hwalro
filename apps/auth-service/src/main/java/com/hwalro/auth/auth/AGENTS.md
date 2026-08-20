# 인증 도메인 지침

- 로그인, 토큰 재발급, 로그아웃 변경 시 JWT 클레임과 Refresh Token 쿠키 흐름을 함께 확인한다.
- 비밀번호는 BCrypt 등 검증 가능한 해시만 저장·비교하며, 평문과 로그 노출을 금지한다.
- Refresh Token 쿠키의 `HttpOnly`, `Secure`, `SameSite`, 경로 설정을 보안 검토 없이 완화하지 않는다.
- JWT의 발급자·사용자 ID·역할 클레임은 regulation-service와 simulation-service가 소비하는 계약이다. 클레임명·형식 변경은 세 서비스의 검증 로직을 함께 변경할 때만 수행한다.
