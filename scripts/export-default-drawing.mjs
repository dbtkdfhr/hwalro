/**
 * 검수를 마친 도면 버전을 통째로 읽어 기본 도면 데이터 JSON으로 내보낸다.
 *
 * 구역만 가져오지 않고 도면 전체를 다시 내보낸다. 검수 중 벽이나 기물을 고쳤어도 어긋나지 않게 하기 위함이다.
 *
 * 구역 소속과 구역의 기본 비상구는 ID가 아니라 **배열 인덱스**로 적는다. 기본 도면으로 새 도면을 만들 때
 * 요소는 새 ID를 받으므로 ID를 적어 두면 아무 의미가 없다. 인덱스가 맞아떨어지려면 각 배열이
 * `display_order ASC, id ASC` 순서여야 한다 — `find*IdsByVersionId`가 돌려주는 순서와 같아야 한다.
 *
 * 사용법:
 *   node scripts/export-default-drawing.mjs --version-id 90
 *   node scripts/export-default-drawing.mjs --version-id 90 --out apps/simulation-service/src/main/resources/drawings/default-drawing-v9.json
 */
import { spawn } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const argv = process.argv.slice(2);
const readOption = (name) => {
  const index = argv.indexOf(name);
  return index === -1 ? null : argv[index + 1];
};

const layoutVersionId = Number(readOption('--version-id'));
if (!Number.isInteger(layoutVersionId) || layoutVersionId <= 0) {
  throw new Error('--version-id 에 내보낼 도면 버전의 ID를 줘라. (layout_versions.id, version 번호가 아니다)');
}
const outputPath = resolve(
  repositoryRoot,
  readOption('--out') ?? 'apps/simulation-service/src/main/resources/drawings/default-drawing-v9.json',
);

function runMysql(sql) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(
      'docker',
      [
        'compose',
        'exec',
        '-T',
        'mysql',
        'sh',
        '-c',
        `mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B --raw hwalro_simulation -e ${JSON.stringify(sql)}`,
      ],
      { cwd: repositoryRoot, stdio: ['ignore', 'pipe', 'pipe'] },
    );

    let stdout = '';
    let stderr = '';
    child.stdout.setEncoding('utf8');
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
    });
    child.stderr.setEncoding('utf8');
    child.stderr.on('data', (chunk) => {
      stderr += chunk;
    });
    child.once('error', (error) => {
      rejectPromise(new Error('docker compose exec 실행에 실패했습니다.', { cause: error }));
    });
    child.once('exit', (code) => {
      if (code !== 0) {
        rejectPromise(new Error(`MySQL 실행이 실패했습니다. exitCode=${code}\n${stderr}`));
        return;
      }
      resolvePromise(stdout);
    });
  });
}

async function queryJson(sql) {
  const line = (await runMysql(sql))
    .split('\n')
    .map((candidate) => candidate.trim())
    .filter((candidate) => candidate !== '')
    .find((candidate) => candidate.startsWith('[') || candidate.startsWith('{') || candidate === 'NULL');
  if (line === undefined || line === 'NULL') {
    return [];
  }
  return JSON.parse(line);
}

/** decimal(12,4)로 저장된 값의 꼬리 0을 떼어 낸다. 5.4400 → 5.44 */
const decimal = (value) => (value === null || value === undefined ? null : Number(value));

const ELEMENT_COLUMNS = `'name', name, 'startX', start_x, 'startY', start_y, 'endX', end_x, 'endY', end_y`;
/** find*IdsByVersionId 와 같은 순서여야 인덱스 참조가 맞는다. */
const ELEMENT_ORDER = 'ORDER BY display_order ASC, id ASC';

const [meta] = await queryJson(
  `SELECT JSON_ARRAYAGG(JSON_OBJECT('title', l.title, 'width', f.width, 'height', f.height, 'version', v.version))
   FROM layout_versions v
   JOIN layouts l ON l.id = v.layout_id
   JOIN floor_plans f ON f.id = l.floor_plan_id
   WHERE v.id = ${layoutVersionId}`,
);
if (!meta) {
  throw new Error(`도면 버전 ${layoutVersionId}을(를) 찾지 못했습니다.`);
}

const [walls, outsideWalls, pillars, fabrics, exits, layoutTexts, zones, members] = await Promise.all([
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT(${ELEMENT_COLUMNS}, 'displayOrder', display_order))
     FROM (SELECT * FROM walls WHERE layout_version_id = ${layoutVersionId} ${ELEMENT_ORDER}) t`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT(${ELEMENT_COLUMNS}))
     FROM (SELECT * FROM outside_walls WHERE layout_version_id = ${layoutVersionId} ORDER BY id ASC) t`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT(${ELEMENT_COLUMNS}, 'rotation', rotation, 'displayOrder', display_order))
     FROM (SELECT * FROM pillars WHERE layout_version_id = ${layoutVersionId} ${ELEMENT_ORDER}) t`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT(${ELEMENT_COLUMNS}, 'rotation', rotation, 'displayOrder', display_order))
     FROM (SELECT * FROM fabrics WHERE layout_version_id = ${layoutVersionId} ${ELEMENT_ORDER}) t`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT(${ELEMENT_COLUMNS}, 'id', id))
     FROM (SELECT * FROM layout_exits WHERE layout_version_id = ${layoutVersionId} ORDER BY id ASC) t`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT('text', text, 'x', x, 'y', y))
     FROM (SELECT * FROM layout_texts WHERE layout_version_id = ${layoutVersionId} ORDER BY id ASC) t`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT('id', id, 'name', name, 'zoneType', zone_type, 'x', x, 'y', y,
                                      'width', width, 'height', height, 'displayOrder', display_order,
                                      'defaultExitId', default_exit_id, 'assignedUserId', assigned_user_id))
     FROM (SELECT * FROM layout_zones WHERE layout_version_id = ${layoutVersionId} ORDER BY display_order ASC, id ASC) t`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT('zoneId', zone_id, 'wallId', wall_id, 'pillarId', pillar_id, 'fabricId', fabric_id))
     FROM (SELECT * FROM layout_zone_members WHERE layout_version_id = ${layoutVersionId} ORDER BY zone_id ASC, id ASC) t`,
  ),
]);

// 요소 ID → 배열 인덱스. 소속과 기본 비상구는 이 인덱스로만 적는다.
const indexOf = (rows, idKey = 'id') => new Map(rows.map((row, index) => [row[idKey], index]));
const exitIndexes = indexOf(exits);

// 벽·기둥·구조물은 ID가 서로 겹치므로 인덱스 맵도 종류별로 나눈다.
const wallIds = await queryJson(
  `SELECT JSON_ARRAYAGG(id) FROM (SELECT id FROM walls WHERE layout_version_id = ${layoutVersionId} ${ELEMENT_ORDER}) t`,
);
const pillarIds = await queryJson(
  `SELECT JSON_ARRAYAGG(id) FROM (SELECT id FROM pillars WHERE layout_version_id = ${layoutVersionId} ${ELEMENT_ORDER}) t`,
);
const fabricIds = await queryJson(
  `SELECT JSON_ARRAYAGG(id) FROM (SELECT id FROM fabrics WHERE layout_version_id = ${layoutVersionId} ${ELEMENT_ORDER}) t`,
);
const elementIndexes = {
  WALL: new Map(wallIds.map((id, index) => [id, index])),
  PILLAR: new Map(pillarIds.map((id, index) => [id, index])),
  FABRIC: new Map(fabricIds.map((id, index) => [id, index])),
};

const membersByZone = new Map(zones.map((zone) => [zone.id, []]));
let assignedUserCount = 0;
for (const member of members) {
  const [kind, elementId] =
    member.wallId !== null
      ? ['WALL', member.wallId]
      : member.pillarId !== null
        ? ['PILLAR', member.pillarId]
        : ['FABRIC', member.fabricId];
  const index = elementIndexes[kind].get(elementId);
  if (index === undefined) {
    throw new Error(`소속이 가리키는 ${kind} ${elementId}을(를) 버전 ${layoutVersionId}에서 찾지 못했습니다.`);
  }
  const bucket = membersByZone.get(member.zoneId);
  if (bucket === undefined) {
    throw new Error(`소속이 가리키는 구역 ${member.zoneId}을(를) 버전 ${layoutVersionId}에서 찾지 못했습니다.`);
  }
  bucket.push({ kind, index });
}

const drawing = {
  name: meta.title,
  width: decimal(meta.width),
  height: decimal(meta.height),
  walls: walls.map((wall) => ({
    name: wall.name,
    startX: decimal(wall.startX),
    startY: decimal(wall.startY),
    endX: decimal(wall.endX),
    endY: decimal(wall.endY),
    displayOrder: wall.displayOrder,
  })),
  outsideWalls: outsideWalls.map((wall) => ({
    name: wall.name,
    startX: decimal(wall.startX),
    startY: decimal(wall.startY),
    endX: decimal(wall.endX),
    endY: decimal(wall.endY),
  })),
  pillars: pillars.map((pillar) => ({
    name: pillar.name,
    startX: decimal(pillar.startX),
    startY: decimal(pillar.startY),
    endX: decimal(pillar.endX),
    endY: decimal(pillar.endY),
    rotation: decimal(pillar.rotation),
    displayOrder: pillar.displayOrder,
  })),
  fabrics: fabrics.map((fabric) => ({
    name: fabric.name,
    startX: decimal(fabric.startX),
    startY: decimal(fabric.startY),
    endX: decimal(fabric.endX),
    endY: decimal(fabric.endY),
    rotation: decimal(fabric.rotation),
    displayOrder: fabric.displayOrder,
  })),
  exits: exits.map((exit) => ({
    name: exit.name,
    startX: decimal(exit.startX),
    startY: decimal(exit.startY),
    endX: decimal(exit.endX),
    endY: decimal(exit.endY),
  })),
  layoutTexts: layoutTexts.map((text) => ({
    text: text.text,
    x: decimal(text.x),
    y: decimal(text.y),
  })),
  // 담당 직원 배정은 내보내지 않는다. 사용자 ID는 환경마다 다르고, 남의 계정에 구역이 붙으면 곤란하다.
  zones: zones.map((zone) => {
    if (zone.assignedUserId !== null) {
      assignedUserCount += 1;
    }
    const defaultExitIndex = zone.defaultExitId === null ? null : exitIndexes.get(zone.defaultExitId);
    if (zone.defaultExitId !== null && defaultExitIndex === undefined) {
      throw new Error(`구역 "${zone.name}"의 기본 비상구 ${zone.defaultExitId}을(를) 찾지 못했습니다.`);
    }
    return {
      name: zone.name,
      zoneType: zone.zoneType,
      x: decimal(zone.x),
      y: decimal(zone.y),
      width: decimal(zone.width),
      height: decimal(zone.height),
      displayOrder: zone.displayOrder,
      defaultExitIndex: defaultExitIndex ?? null,
      members: membersByZone.get(zone.id),
    };
  }),
};

await writeFile(outputPath, `${JSON.stringify(drawing, null, 2)}\n`, 'utf8');

const memberCount = drawing.zones.reduce((total, zone) => total + zone.members.length, 0);
console.log(`도면 버전 ${layoutVersionId} (version ${meta.version}) → ${outputPath}`);
console.log(
  `  벽 ${drawing.walls.length} · 외각벽 ${drawing.outsideWalls.length} · 기둥 ${drawing.pillars.length} · ` +
    `구조물 ${drawing.fabrics.length} · 비상구 ${drawing.exits.length} · 텍스트 ${drawing.layoutTexts.length}`,
);
console.log(
  `  구역 ${drawing.zones.length} · 소속 ${memberCount} · 기본 비상구 지정 ${drawing.zones.filter((zone) => zone.defaultExitIndex !== null).length}`,
);
if (assignedUserCount > 0) {
  console.log(`  담당 직원이 배정된 구역 ${assignedUserCount}개는 배정을 빼고 내보냈다.`);
}