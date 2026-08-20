SET NAMES utf8mb4;

START TRANSACTION;

INSERT IGNORE INTO roles (role_name, description) VALUES
('ADMIN', '관리자'),
('OPERATOR', '운영 담당자'),
('SAFETY_REVIEWER', '안전 검토자');

UPDATE roles
SET description = CASE role_name
  WHEN 'ADMIN' THEN '관리자'
  WHEN 'OPERATOR' THEN '운영 담당자'
  WHEN 'SAFETY_REVIEWER' THEN '안전 검토자'
END
WHERE role_name IN ('ADMIN', 'OPERATOR', 'SAFETY_REVIEWER');

INSERT INTO user_roles (user_id, role_id)
SELECT ur.user_id, new_role.role_id
FROM user_roles ur
JOIN roles old_role ON ur.role_id = old_role.role_id AND old_role.role_name = 'USER'
JOIN roles new_role ON new_role.role_name = 'SAFETY_REVIEWER'
WHERE NOT EXISTS (
  SELECT 1
  FROM user_roles existing_role
  WHERE existing_role.user_id = ur.user_id
    AND existing_role.role_id = new_role.role_id
);

DELETE ur
FROM user_roles ur
JOIN roles old_role ON ur.role_id = old_role.role_id AND old_role.role_name = 'USER';

DELETE FROM roles WHERE role_name = 'USER';

INSERT IGNORE INTO users (login_id, password, name, enabled) VALUES
('test', '$2y$10$0DguaN63igiENXyzyn0x3OAmPFc7Q6K0A/SASAgAGTdevBWltAl3q', '테스트', TRUE);

UPDATE users
SET name = '테스트'
WHERE login_id = 'test';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.user_id, r.role_id
FROM users u
JOIN roles r ON r.role_name IN ('OPERATOR', 'SAFETY_REVIEWER')
WHERE u.login_id = 'test';

COMMIT;
