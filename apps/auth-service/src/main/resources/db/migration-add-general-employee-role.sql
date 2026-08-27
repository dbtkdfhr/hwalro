-- Adds the production role required by the employee zone-assignment flow.
-- Demo users remain in db/dml.sql and are intentionally not created here.
SET NAMES utf8mb4;

USE hwalro_auth;

INSERT IGNORE INTO roles (role_name, description)
VALUES ('GENERAL_EMPLOYEE', '일반 직원');

UPDATE roles
SET description = '일반 직원'
WHERE role_name = 'GENERAL_EMPLOYEE';
