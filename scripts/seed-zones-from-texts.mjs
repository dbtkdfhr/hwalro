/**
 * 도면의 매장 이름 텍스트(`layout_texts`)에서 구역(`layout_zones`) 초안을 기계로 만든다.
 *
 * HANDOFF-SCRUM-133.md §2의 방식 그대로다.
 *   1. 각 매장 텍스트 점에서 상하좌우로 광선을 쏴 벽(`walls` + `outside_walls`)에 막히는 지점을 경계로 잡는다.
 *   2. 이웃 매장 점을 삼키지 않도록, 겹치는 쌍은 두 점 사이 중간에서 잘라 겹침을 0으로 만든다.
 *   3. 구역 안에 중심이 들어오는 기둥·구조물을 소속(`layout_zone_members`)으로 붙인다.
 *      벽은 두 매장이 공유하는 경계라 자동 배정에서 제외한다(§5).
 *
 * 자동 생성은 초안일 뿐이다. 이웃 텍스트가 없어 벽까지 크게 번진 구역이 나오므로 사람이 에디터에서 다듬어야 한다.
 *
 * 사용법:
 *   node scripts/seed-zones-from-texts.mjs                 # 미리보기만 (DB를 건드리지 않는다)
 *   node scripts/seed-zones-from-texts.mjs --apply         # 삽입
 *   node scripts/seed-zones-from-texts.mjs --apply --replace   # 같은 이름의 기존 자동 구역을 지우고 다시 삽입
 *   node scripts/seed-zones-from-texts.mjs --version 7 --apply # 다른 도면 버전
 */
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const argv = process.argv.slice(2);
const apply = argv.includes('--apply');
const replace = argv.includes('--replace');
const versionIndex = argv.indexOf('--version');
const layoutVersionId = versionIndex === -1 ? 1 : Number(argv[versionIndex + 1]);

if (!Number.isInteger(layoutVersionId) || layoutVersionId <= 0) {
  throw new Error(`--version 값이 올바르지 않습니다: ${argv[versionIndex + 1]}`);
}

/** 벽에 딱 붙이지 않고 남기는 여유. 경계가 벽과 겹쳐 보이는 것을 막는다. */
const WALL_MARGIN = 0.05;
/** 광선의 최대 길이. 도면 대각선보다 길면 충분하다. */
const RAY_LIMIT = 400;
const EPSILON = 1e-6;

function runMysql(sql, { stdinSql = null } = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    const args = [
      'compose',
      'exec',
      '-T',
      'mysql',
      'sh',
      '-c',
      stdinSql === null
        ? `mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B --raw hwalro_simulation -e ${JSON.stringify(sql)}`
        : 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD"',
    ];
    const child = spawn('docker', args, {
      cwd: repositoryRoot,
      stdio: [stdinSql === null ? 'ignore' : 'pipe', 'pipe', 'pipe'],
    });

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
    if (stdinSql !== null) {
      child.stdin.end(stdinSql);
    }

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

/**
 * JSON_ARRAYAGG로 한 줄만 받아 온다. TSV 이스케이프(줄바꿈 등)를 직접 풀지 않아도 된다.
 * 컨테이너의 mysql 설정이 "PAGER set to stdout" 같은 안내를 stdout에 먼저 뱉으므로 JSON 줄만 골라낸다.
 * --raw가 없으면 배치 출력이 JSON 안의 백슬래시를 한 번 더 이스케이프해 줄바꿈이 리터럴 "
"으로 들어온다.
 */
async function queryJson(sql) {
  const line = (await runMysql(sql))
    .split('\n')
    .map((candidate) => candidate.trim())
    .filter((candidate) => candidate !== '')
    .find((candidate) => candidate.startsWith('[') || candidate === 'NULL');
  if (line === undefined || line === 'NULL') {
    return [];
  }
  return JSON.parse(line);
}

const number = (value) => Number(value);

const [texts, walls, outsideWalls, pillars, fabrics, existingZones, existingMembers] = await Promise.all([
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT('id', id, 'text', text, 'x', x, 'y', y))
     FROM layout_texts WHERE layout_version_id = ${layoutVersionId}`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT('id', id, 'x1', start_x, 'y1', start_y, 'x2', end_x, 'y2', end_y))
     FROM walls WHERE layout_version_id = ${layoutVersionId}`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT('id', id, 'x1', start_x, 'y1', start_y, 'x2', end_x, 'y2', end_y))
     FROM outside_walls WHERE layout_version_id = ${layoutVersionId}`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT('id', id, 'x1', start_x, 'y1', start_y, 'x2', end_x, 'y2', end_y))
     FROM pillars WHERE layout_version_id = ${layoutVersionId}`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT('id', id, 'x1', start_x, 'y1', start_y, 'x2', end_x, 'y2', end_y))
     FROM fabrics WHERE layout_version_id = ${layoutVersionId}`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT('id', id, 'name', name))
     FROM layout_zones WHERE layout_version_id = ${layoutVersionId}`,
  ),
  queryJson(
    `SELECT JSON_ARRAYAGG(JSON_OBJECT('pillarId', pillar_id, 'fabricId', fabric_id))
     FROM layout_zone_members WHERE layout_version_id = ${layoutVersionId}`,
  ),
]);

if (texts.length === 0) {
  throw new Error(`도면 버전 ${layoutVersionId}에 매장 이름 텍스트가 없습니다.`);
}

const blockers = [...walls, ...outsideWalls].map((wall) => ({
  ax: number(wall.x1),
  ay: number(wall.y1),
  bx: number(wall.x2),
  by: number(wall.y2),
}));

const cross = (x1, y1, x2, y2) => x1 * y2 - y1 * x2;

/** 점 (px,py)에서 방향 (dx,dy)로 쏜 광선이 벽에 막히는 거리. 막히지 않으면 RAY_LIMIT. */
function castRay(px, py, dx, dy) {
  let best = RAY_LIMIT;
  for (const { ax, ay, bx, by } of blockers) {
    const sx = bx - ax;
    const sy = by - ay;
    const denominator = cross(dx, dy, sx, sy);
    const qpx = ax - px;
    const qpy = ay - py;

    if (Math.abs(denominator) < EPSILON) {
      // 광선과 벽이 평행하다. 같은 직선 위에 있을 때만 끝점이 막는다.
      if (Math.abs(cross(qpx, qpy, dx, dy)) >= EPSILON) {
        continue;
      }
      for (const [ex, ey] of [
        [ax, ay],
        [bx, by],
      ]) {
        const t = (ex - px) * dx + (ey - py) * dy;
        if (t > EPSILON && t < best) {
          best = t;
        }
      }
      continue;
    }

    const t = cross(qpx, qpy, sx, sy) / denominator;
    const u = cross(qpx, qpy, dx, dy) / denominator;
    if (t > EPSILON && u >= -EPSILON && u <= 1 + EPSILON && t < best) {
      best = t;
    }
  }
  return best;
}

const zones = texts.map((text) => {
  const px = number(text.x);
  const py = number(text.y);
  return {
    textId: text.id,
    name: String(text.text).replace(/\s*\n\s*/g, ' ').trim(),
    px,
    py,
    left: px - castRay(px, py, -1, 0) + WALL_MARGIN,
    right: px + castRay(px, py, 1, 0) - WALL_MARGIN,
    top: py - castRay(px, py, 0, -1) + WALL_MARGIN,
    bottom: py + castRay(px, py, 0, 1) - WALL_MARGIN,
  };
});

// 이웃 매장 점을 삼키지 않도록 겹치는 쌍을 두 점 사이 중간에서 자른다.
// 사각형은 줄어들기만 하므로 반복은 반드시 멈춘다.
let passes = 0;
for (; passes < 200; passes += 1) {
  let changed = false;
  for (let i = 0; i < zones.length; i += 1) {
    for (let j = i + 1; j < zones.length; j += 1) {
      const a = zones[i];
      const b = zones[j];
      const overlapX = Math.min(a.right, b.right) - Math.max(a.left, b.left);
      const overlapY = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
      if (overlapX <= EPSILON || overlapY <= EPSILON) {
        continue;
      }

      const deltaX = a.px - b.px;
      const deltaY = a.py - b.py;
      if (Math.abs(deltaX) >= Math.abs(deltaY)) {
        const middle = (a.px + b.px) / 2;
        const [leftZone, rightZone] = deltaX >= 0 ? [b, a] : [a, b];
        leftZone.right = Math.min(leftZone.right, middle);
        rightZone.left = Math.max(rightZone.left, middle);
      } else {
        const middle = (a.py + b.py) / 2;
        const [upperZone, lowerZone] = deltaY >= 0 ? [b, a] : [a, b];
        upperZone.bottom = Math.min(upperZone.bottom, middle);
        lowerZone.top = Math.max(lowerZone.top, middle);
      }
      changed = true;
    }
  }
  if (!changed) {
    break;
  }
}

// 컬럼이 decimal(12,4)다. 그냥 반올림하면 맞닿은 경계가 0.1mm씩 겹쳐 "겹침 있음"으로 잡힌다.
// 시작 변은 올리고 끝 변은 내려 항상 안쪽으로만 맞춘다.
const SCALE = 10000;
const roundIn = (value) => Math.ceil(value * SCALE) / SCALE;
const roundOut = (value) => Math.floor(value * SCALE) / SCALE;

// 이름이 겹치는 매장(팝업, E/S)은 검수할 때 구분되도록 번호를 붙인다.
const nameCounts = new Map();
for (const zone of zones) {
  nameCounts.set(zone.name, (nameCounts.get(zone.name) ?? 0) + 1);
}
const nameSeen = new Map();

const rows = zones.map((zone, index) => {
  let name = zone.name;
  if ((nameCounts.get(name) ?? 0) > 1) {
    const seen = (nameSeen.get(name) ?? 0) + 1;
    nameSeen.set(name, seen);
    name = `${name} ${seen}`;
  }
  const x = roundIn(zone.left);
  const y = roundIn(zone.top);
  return {
    name,
    x,
    y,
    width: roundOut(zone.right) - x,
    height: roundOut(zone.bottom) - y,
    displayOrder: index + 1,
  };
});

const centerOf = (element) => ({
  x: (number(element.x1) + number(element.x2)) / 2,
  y: (number(element.y1) + number(element.y2)) / 2,
});

// 이미 다른 구역에 속한 요소는 건드리지 않는다. 소속은 요소당 하나뿐이라(unique key)
// 덮어쓰면 사람이 손으로 붙여 둔 소속을 조용히 빼앗는다.
const ownedPillarIds = new Set(existingMembers.map((member) => member.pillarId).filter(Boolean));
const ownedFabricIds = new Set(existingMembers.map((member) => member.fabricId).filter(Boolean));

const memberships = [];
let skippedOwned = 0;
for (const [kind, elements, owned] of [
  ['pillar', pillars, ownedPillarIds],
  ['fabric', fabrics, ownedFabricIds],
]) {
  for (const element of elements) {
    if (owned.has(element.id)) {
      skippedOwned += 1;
      continue;
    }
    const { x, y } = centerOf(element);
    const hit = rows.findIndex(
      (row) => x >= row.x && x <= row.x + row.width && y >= row.y && y <= row.y + row.height,
    );
    if (hit !== -1) {
      memberships.push({ kind, elementId: element.id, zoneIndex: hit });
    }
  }
}

const tiny = rows.filter((row) => row.width < 0.5 || row.height < 0.5);
const largest = [...rows].sort((a, b) => b.width * b.height - a.width * a.height).slice(0, 5);

console.log(`도면 버전 ${layoutVersionId}`);
console.log(`  매장 텍스트 ${texts.length}개 → 구역 ${rows.length}개 (겹침 해소 ${passes}회 반복)`);
console.log(`  벽 ${walls.length} + 외각벽 ${outsideWalls.length} · 기둥 ${pillars.length} · 구조물 ${fabrics.length}`);
console.log(`  소속 배정: 기둥 ${memberships.filter((m) => m.kind === 'pillar').length}개, 구조물 ${memberships.filter((m) => m.kind === 'fabric').length}개`);
console.log(`  이미 다른 구역에 속해 건너뛴 요소 ${skippedOwned}개`);
console.log(`  기존 구역 ${existingZones.length}개: ${existingZones.map((z) => z.name).join(', ') || '없음'}`);
console.log(`  가장 큰 구역 5개: ${largest.map((r) => `${r.name} ${Math.round(r.width * r.height)}m²`).join(', ')}`);
if (tiny.length > 0) {
  console.log(`  ⚠ 0.5m 미만으로 눌린 구역 ${tiny.length}개: ${tiny.map((r) => r.name).join(', ')}`);
}

if (!apply) {
  console.log('\n미리보기만 했다. 실제로 넣으려면 --apply 를 붙여라.');
  process.exit(0);
}

const quote = (value) => `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;

const statements = ['SET NAMES utf8mb4;', 'USE hwalro_simulation;', 'START TRANSACTION;'];

if (replace) {
  const names = rows.map((row) => quote(row.name)).join(', ');
  statements.push(
    `DELETE FROM layout_zones WHERE layout_version_id = ${layoutVersionId} AND name IN (${names});`,
  );
}

statements.push('SET @seed_base := NULL;');
rows.forEach((row, index) => {
  statements.push(
    `INSERT INTO layout_zones (layout_version_id, name, zone_type, x, y, width, height, display_order)` +
      ` VALUES (${layoutVersionId}, ${quote(row.name)}, 'WORK', ${row.x}, ${row.y}, ${row.width}, ${row.height}, ${row.displayOrder});`,
  );
  if (index === 0) {
    statements.push('SET @seed_base := LAST_INSERT_ID();');
  }
  statements.push(`SET @zone_${index} := LAST_INSERT_ID();`);
});

for (const { kind, elementId, zoneIndex } of memberships) {
  const column = kind === 'pillar' ? 'pillar_id' : 'fabric_id';
  statements.push(
    `INSERT INTO layout_zone_members (layout_version_id, zone_id, ${column})` +
      ` VALUES (${layoutVersionId}, @zone_${zoneIndex}, ${elementId});`,
  );
}

statements.push('COMMIT;');

await runMysql(null, { stdinSql: `${statements.join('\n')}\n` });

const [after] = await queryJson(
  `SELECT JSON_ARRAYAGG(JSON_OBJECT('zones', z, 'members', m)) FROM (
     SELECT (SELECT COUNT(*) FROM layout_zones WHERE layout_version_id = ${layoutVersionId}) AS z,
            (SELECT COUNT(*) FROM layout_zone_members WHERE layout_version_id = ${layoutVersionId}) AS m
   ) t`,
);
console.log(`\n삽입 완료. 도면 버전 ${layoutVersionId}: 구역 ${after.zones}개, 소속 ${after.members}건.`);
