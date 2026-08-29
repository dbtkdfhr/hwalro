# V1.5 이후 운영 RDS 마이그레이션

이 문서는 `feat: 활로(HWALRO) V1.5`가 배포된 기존 RDS를 이후 코드에 맞게 변경하는 순서를 설명한다.
애플리케이션의 `DB_INIT_MODE`는 운영에서 `never`이므로 Jenkins 배포만으로 아래 변경이 적용되지 않는다.

## 실행 전

1. RDS 스냅샷을 생성한다.
2. 대상이 `hwalro_auth`, `hwalro_simulation`, `hwalro_regulation` 운영 데이터베이스인지 확인한다.
3. 각 SQL 파일은 한 번만 실행한다. `ALTER TABLE ... ADD COLUMN`이 포함되어 있어 재실행용 스크립트가 아니다.

## 배포 전 추가형 마이그레이션

아래 순서대로 실행한다. 이 단계는 기존 컬럼을 제거하지 않으므로 V1.5 코드가 실행 중인 상태에서도 적용할 수 있다.

1. `apps/auth-service/src/main/resources/db/migration-add-general-employee-role.sql`
2. `apps/simulation-service/src/main/resources/db/migration-add-layout-zones-and-structure-constraints.sql`
3. `apps/simulation-service/src/main/resources/db/migration-replace-structure-constraints-with-movement-policy.sql`
4. `apps/simulation-service/src/main/resources/db/migration-add-evacuation-route-store.sql`
5. `apps/regulation-service/src/main/resources/db/migration-add-checklist-drawing-link.sql`
6. `apps/regulation-service/src/main/resources/db/migration-backfill-inspection-layout.sql`
7. `apps/regulation-service/src/main/resources/db/migration-add-risks-layout-reference.sql`

3번은 새 `movement_policy` 컬럼과 제약조건만 추가하고 기존 이동 제약 컬럼은 유지한다.
따라서 실행 중인 이전 버전과 V2.5가 전환되는 동안 두 버전이 각각 필요한 컬럼을 사용할 수 있다.

그다음 `main` 머지를 통해 Jenkins 배포를 실행한다.
Jenkins는 실제 RDS를 읽기 전용으로 점검하며, 위 역할·테이블·컬럼·인덱스가 빠져 있으면
기존 백엔드와 프론트엔드를 유지한 채 배포를 중단한다.

## 배포 후 파괴적 정리

새 버전 배포와 기본 기능 확인이 끝난 뒤 다음 파일을 순서대로 실행한다.

1. `apps/regulation-service/src/main/resources/db/migration-risks-layout-only.sql`
2. `apps/simulation-service/src/main/resources/db/migration-drop-legacy-structure-constraints.sql`

첫 번째 파일은 `simulation_result_id`가 있거나 `layout_id`가 없는 기존 주의 항목을 삭제하고
`risks.simulation_result_id` 컬럼을 제거한다. 보존해야 할 데이터가 있다면 실행 전에 별도 변환 SQL을 작성해야 한다.

두 번째 파일은 V2.5에서 사용하지 않는 `movable`, `max_movement_distance`,
`rotation_locked`, `keep_against_wall` 컬럼과 기존 CHECK 제약조건을 제거한다.

## 적용 확인

```sql
SELECT role_name
FROM hwalro_auth.roles
WHERE role_name = 'GENERAL_EMPLOYEE';

SHOW TABLES FROM hwalro_simulation LIKE 'layout_zones';
SHOW TABLES FROM hwalro_simulation LIKE 'layout_zone_members';
SHOW TABLES FROM hwalro_simulation LIKE 'evacuation_route_store';

SHOW COLUMNS FROM hwalro_simulation.fabrics LIKE 'movement_policy';
SELECT constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema = 'hwalro_simulation'
  AND table_name = 'fabrics'
  AND constraint_name = 'ck_fabrics_movement_policy';
SHOW COLUMNS FROM hwalro_simulation.walls LIKE 'display_order';
SHOW COLUMNS FROM hwalro_simulation.pillars LIKE 'display_order';

SHOW COLUMNS FROM hwalro_regulation.inspection_areas LIKE 'layout_id';
SHOW COLUMNS FROM hwalro_regulation.safety_inspections LIKE 'snapshot_image';
SHOW COLUMNS FROM hwalro_regulation.safety_inspection_items LIKE 'marker_x';
SHOW COLUMNS FROM hwalro_regulation.risks LIKE 'layout_id';
```
