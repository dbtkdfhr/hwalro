import { readFile } from 'node:fs/promises';

const mirroredFiles = [
  [
    'apps/auth-service/src/main/resources/db/schema.sql',
    'deploy/init-db/01-auth-schema.sql',
  ],
  [
    'apps/simulation-service/src/main/resources/db/schema.sql',
    'deploy/init-db/02-simulation-schema.sql',
  ],
  [
    'apps/regulation-service/src/main/resources/db/schema.sql',
    'deploy/init-db/03-regulation-schema.sql',
  ],
  [
    'apps/auth-service/src/main/resources/db/dml.sql',
    'deploy/init-db/05-auth-dml.sql',
  ],
  [
    'apps/regulation-service/src/main/resources/db/safety-check-dml.sql',
    'deploy/init-db/06-safety-check-dml.sql',
  ],
  [
    'apps/simulation-service/src/main/resources/db/density-threshold-dml.sql',
    'deploy/init-db/07-density-threshold-dml.sql',
  ],
];

const normalize = (contents) => contents.replaceAll('\r\n', '\n').trimEnd();

const failures = [];

for (const [sourcePath, deploymentPath] of mirroredFiles) {
  const [source, deployment] = await Promise.all([
    readFile(sourcePath, 'utf8'),
    readFile(deploymentPath, 'utf8'),
  ]);

  if (normalize(source) !== normalize(deployment)) {
    failures.push(`${deploymentPath} is out of sync with ${sourcePath}`);
  }
}

const compose = await readFile('deploy/docker-compose.prod.yml', 'utf8');
const simulationStart = compose.indexOf('\n  simulation-service:');
const regulationStart = compose.indexOf('\n  regulation-service:', simulationStart);
const simulationBlock = compose.slice(simulationStart, regulationStart);

if (simulationStart < 0 || regulationStart < 0) {
  failures.push('Unable to find the simulation-service deployment block');
} else {
  const activeSimulationLines = simulationBlock
    .split(/\r?\n/u)
    .filter((line) => !line.trimStart().startsWith('#'));
  const authServiceUrlLines = activeSimulationLines.filter((line) =>
    line.includes('AUTH_SERVICE_URL:'),
  );

  if (
    authServiceUrlLines.length !== 1 ||
    !authServiceUrlLines[0].includes('AUTH_SERVICE_URL: http://auth-service:8080')
  ) {
    failures.push('simulation-service must define exactly one Docker auth-service URL');
  }
}

const migrationContracts = [
  {
    path: 'apps/auth-service/src/main/resources/db/migration-add-general-employee-role.sql',
    fragments: ['SET NAMES utf8mb4', "VALUES ('GENERAL_EMPLOYEE', '일반 직원')"],
  },
  {
    path: 'apps/simulation-service/src/main/resources/db/migration-add-layout-zones-and-structure-constraints.sql',
    fragments: [
      'USE hwalro_simulation',
      'ADD COLUMN movable BOOLEAN',
      'ADD COLUMN max_movement_distance',
      'ADD COLUMN rotation_locked BOOLEAN',
      'ADD COLUMN keep_against_wall BOOLEAN',
      'ADD COLUMN display_order INT',
      'ADD CONSTRAINT uk_fabrics_id_version',
      'ADD CONSTRAINT uk_walls_id_version',
      'ADD CONSTRAINT uk_pillars_id_version',
      'CONSTRAINT ck_fabrics_max_movement_distance',
      'CREATE TABLE IF NOT EXISTS layout_zones',
      'CONSTRAINT fk_layout_zones_default_exit',
      'CREATE TABLE IF NOT EXISTS layout_zone_members',
      'CONSTRAINT ck_layout_zone_members_exactly_one',
      'CONSTRAINT fk_layout_zone_members_fabric',
    ],
  },
  {
    path: 'apps/simulation-service/src/main/resources/db/migration-add-evacuation-route-store.sql',
    fragments: [
      'USE hwalro_simulation',
      'CREATE TABLE IF NOT EXISTS evacuation_route_store',
      'result_payload JSON NOT NULL',
      'INDEX idx_ers_version',
      'INDEX idx_ers_computed_at',
    ],
  },
  {
    path: 'apps/regulation-service/src/main/resources/db/migration-add-checklist-drawing-link.sql',
    fragments: [
      'ALTER TABLE inspection_areas',
      'ADD COLUMN layout_id BIGINT UNSIGNED NULL',
      'ALTER TABLE safety_inspections',
      'ADD COLUMN layout_version_id BIGINT UNSIGNED NULL',
      'ADD COLUMN snapshot_image MEDIUMBLOB',
      'ALTER TABLE safety_inspection_items',
      'ADD COLUMN marker_x',
      'ADD COLUMN marker_y',
    ],
  },
  {
    path: 'apps/regulation-service/src/main/resources/db/migration-backfill-inspection-layout.sql',
    fragments: ['UPDATE safety_inspections inspection', 'SET inspection.layout_id = area.layout_id'],
  },
  {
    path: 'apps/regulation-service/src/main/resources/db/migration-add-risks-layout-reference.sql',
    fragments: [
      'ALTER TABLE risks',
      'ADD COLUMN layout_id BIGINT UNSIGNED NULL',
      'ADD COLUMN layout_version_id BIGINT UNSIGNED NULL',
      'ADD INDEX idx_risks_layout_id',
    ],
  },
  {
    path: 'apps/regulation-service/src/main/resources/db/migration-risks-layout-only.sql',
    fragments: [
      'DELETE FROM risks',
      'DROP COLUMN simulation_result_id',
      'MODIFY COLUMN layout_id BIGINT UNSIGNED NOT NULL',
    ],
  },
];

const runbook = await readFile('deploy/RDS_MIGRATION_V1_5_TO_CURRENT.md', 'utf8');
let previousRunbookIndex = -1;

for (const { path: migrationPath, fragments } of migrationContracts) {
  try {
    const migration = await readFile(migrationPath, 'utf8');
    const executableSql = normalize(migration).replace(/^\s*--.*$/gmu, '');

    for (const requiredFragment of fragments) {
      if (!executableSql.includes(requiredFragment)) {
        failures.push(`${migrationPath} is missing: ${requiredFragment}`);
      }
    }
  } catch (error) {
    if (error?.code === 'ENOENT') {
      failures.push(`${migrationPath} is missing`);
    } else {
      throw error;
    }
  }

  const runbookIndex = runbook.indexOf(`\`${migrationPath}\``);
  if (runbookIndex < 0) {
    failures.push(`${migrationPath} is missing from the RDS migration runbook`);
  } else if (runbookIndex <= previousRunbookIndex) {
    failures.push(`${migrationPath} is out of order in the RDS migration runbook`);
  } else {
    previousRunbookIndex = runbookIndex;
  }
}

const preflight = await readFile('deploy/preflight-rds-schema.sh', 'utf8');
for (const requiredCheck of [
  'role.GENERAL_EMPLOYEE',
  'table.layout_zones',
  'table.layout_zone_members',
  'table.evacuation_route_store',
  'fabrics.movable',
  'walls.display_order',
  'pillars.display_order',
  'columns.structure_constraints',
  'columns.layout_zones',
  'columns.layout_zone_members',
  'columns.evacuation_route_store',
  'constraints.layout_zone_integrity',
  'primary-keys.new-simulation-tables',
  'indexes.evacuation_route_store',
  'inspection_areas.layout_id',
  'safety_inspections.snapshot_image',
  'safety_inspection_items.marker_x',
  'risks.layout_id',
  'columns.regulation_layout_links',
  'index.idx_risks_layout_id',
  'data.inspection_layout_backfill',
]) {
  if (!preflight.includes(`'${requiredCheck}'`)) {
    failures.push(`deploy/preflight-rds-schema.sh is missing check: ${requiredCheck}`);
  }
}

const executablePreflight = preflight.replace(/^\s*#.*$/gmu, '');
if (/\b(?:ALTER|CREATE|DELETE|DROP|INSERT|REPLACE|TRUNCATE|UPDATE)\b/iu.test(executablePreflight)) {
  failures.push('deploy/preflight-rds-schema.sh must remain read-only');
}

const jenkinsfile = await readFile('Jenkinsfile', 'utf8');
const preflightCommandIndex = jenkinsfile.indexOf('bash /preflight-rds-schema.sh');
const backendPullIndex = jenkinsfile.indexOf(
  'docker compose --env-file /opt/hwalro/deploy/.env.prod -f /opt/hwalro/deploy/docker-compose.prod.yml pull',
);
const backendDeployStageIndex = jenkinsfile.indexOf("stage('CD: Deploy Backend with SSM')");
const frontendDeployStageIndex = jenkinsfile.indexOf("stage('CD: Deploy Frontend')");

if (preflightCommandIndex < 0 || backendPullIndex <= preflightCommandIndex) {
  failures.push('Jenkins must run the RDS preflight before pulling deployment containers');
}
if (backendDeployStageIndex < 0 || frontendDeployStageIndex <= backendDeployStageIndex) {
  failures.push('Jenkins must deploy the backend successfully before publishing the frontend');
}

if (failures.length > 0) {
  console.error('Deployment contract verification failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exitCode = 1;
} else {
  console.log('Deployment contract verification passed.');
}
