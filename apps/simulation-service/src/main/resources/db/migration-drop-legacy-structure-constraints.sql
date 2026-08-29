USE hwalro_simulation;

-- V2.5 애플리케이션 배포와 기본 기능 확인이 끝난 뒤 한 번만 실행한다.
ALTER TABLE fabrics
    DROP CHECK ck_fabrics_max_movement_distance,
    DROP COLUMN movable,
    DROP COLUMN max_movement_distance,
    DROP COLUMN rotation_locked,
    DROP COLUMN keep_against_wall;
