import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const serviceDirectory = resolve(scriptDirectory, '..');
const drawingPath = resolve(serviceDirectory, 'src/main/resources/drawings/default-drawing.json');
const outputPath = resolve(
  serviceDirectory,
  'src/main/resources/db/simulation-result-test-dml.sql',
);

const TOTAL_PEOPLE = 100;
const DURATION_SECONDS = 264;
const FRAME_STEP_SECONDS = 4;
const INACTIVE_POSITION = -10_000;
const HEATMAP_COLUMNS = 34;
const HEATMAP_ROWS = 20;
const HEATMAP_CELL_SIZE = 5;
const FRAMES_PER_CHUNK = 10;

function mulberry32(seed) {
  return () => {
    let value = (seed += 0x6d2b79f5);
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4_294_967_296;
  };
}

function round(value, scale = 4) {
  return Number(value.toFixed(scale));
}

function escapeSql(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function jsonSql(value) {
  return `CAST(${escapeSql(JSON.stringify(value))} AS JSON)`;
}

function chunk(values, size) {
  const chunks = [];
  for (let index = 0; index < values.length; index += size) {
    chunks.push(values.slice(index, index + size));
  }
  return chunks;
}

function createAgentData() {
  const random = mulberry32(20260807);
  const agents = Array.from({ length: TOTAL_PEOPLE }, (_, index) => {
    const southExit = random() > 0.42;
    return {
      id: index + 1,
      startX: round(23 + random() * 116),
      startY: round(20 + random() * 62),
      exitX: southExit ? 84.95 : 146.2,
      exitY: southExit ? 99.2 : 44.7,
      delay: round(random() * 20),
      finishTime: round(120 + random() * 144),
    };
  });

  const frames = [];
  for (let time = 0; time <= DURATION_SECONDS; time += FRAME_STEP_SECONDS) {
    const positions = [];
    let activeAgentCount = 0;
    for (const agent of agents) {
      if (time >= agent.finishTime) {
        positions.push(INACTIVE_POSITION, INACTIVE_POSITION);
        continue;
      }

      activeAgentCount += 1;
      const travelDuration = Math.max(1, agent.finishTime - agent.delay);
      const progress = Math.min(1, Math.max(0, (time - agent.delay) / travelDuration));
      const eased = progress * progress * (3 - 2 * progress);
      const curve = Math.sin(progress * Math.PI) * (agent.id % 2 === 0 ? 4 : -4);
      positions.push(
        round(agent.startX + (agent.exitX - agent.startX) * eased),
        round(agent.startY + (agent.exitY - agent.startY) * eased + curve),
      );
    }

    frames.push({
      timeSeconds: time,
      activeAgentCount,
      evacuatedCount: TOTAL_PEOPLE - activeAgentCount,
      positions,
    });
  }

  return { agents, frames };
}

function createHeatmapFrames() {
  const frames = [];
  for (let time = 0; time <= DURATION_SECONDS; time += FRAME_STEP_SECONDS) {
    const pulse = Math.sin((time / DURATION_SECONDS) * Math.PI);
    const peaks = [
      { x: 0.59 + Math.sin(time / 30) * 0.035, y: 0.48, power: 4.8 * pulse },
      { x: 0.82, y: 0.74 + Math.cos(time / 38) * 0.04, power: 3.9 * pulse },
    ];
    const values = [];
    for (let row = 0; row < HEATMAP_ROWS; row += 1) {
      for (let column = 0; column < HEATMAP_COLUMNS; column += 1) {
        const x = column / HEATMAP_COLUMNS;
        const y = row / HEATMAP_ROWS;
        let density = 0;
        for (const peak of peaks) {
          const dx = x - peak.x;
          const dy = y - peak.y;
          density += peak.power * Math.exp(-(dx * dx + dy * dy) / 0.008);
        }
        values.push(round(density));
      }
    }
    frames.push({ timeSeconds: time, values });
  }
  return frames;
}

function insertRows(table, columns, rows) {
  return `INSERT INTO ${table} (${columns.join(', ')}) VALUES\n${rows
    .map((row) => `(${row.join(', ')})`)
    .join(',\n')};`;
}

const drawing = JSON.parse(await readFile(drawingPath, 'utf8'));
const { agents, frames: timelineFrames } = createAgentData();
const heatmapFrames = createHeatmapFrames();

if (agents.length !== TOTAL_PEOPLE) {
  throw new Error(`Expected ${TOTAL_PEOPLE} agents, received ${agents.length}`);
}
if (timelineFrames.length !== 67 || heatmapFrames.length !== 67) {
  throw new Error('Expected 67 timeline frames and 67 heatmap frames');
}
if (timelineFrames.some((frame) => frame.positions.length !== TOTAL_PEOPLE * 2)) {
  throw new Error('Every timeline frame must contain one x/y pair per agent');
}
if (timelineFrames.at(-1)?.evacuatedCount !== TOTAL_PEOPLE) {
  throw new Error('All fixture agents must be evacuated in the final frame');
}
if (heatmapFrames.some((frame) => frame.values.length !== HEATMAP_COLUMNS * HEATMAP_ROWS)) {
  throw new Error('Every heatmap frame must match the configured grid dimensions');
}
if (drawing.walls.length === 0 || drawing.exits.length < 2) {
  throw new Error('The default drawing must contain walls and at least two exits');
}

const wallRows = drawing.walls.map((wall) => [
  '9100',
  escapeSql(wall.name),
  wall.startX,
  wall.startY,
  wall.endX,
  wall.endY,
]);
const exitRows = drawing.exits.map((exit, index) => [
  String(9101 + index),
  '9100',
  escapeSql(exit.name),
  exit.startX,
  exit.startY,
  exit.endX,
  exit.endY,
]);
const textRows = drawing.layoutTexts.map((item) => ['9100', escapeSql(item.text), item.x, item.y]);

const simulations = [
  { id: 9201, parentId: null, resultId: 9301, seed: 20260807, duration: 264, density: 4.8 },
  { id: 9202, parentId: 9201, resultId: 9302, seed: 20260808, duration: 221, density: 4.2 },
  { id: 9203, parentId: 9201, resultId: 9303, seed: 20260809, duration: 233, density: 4.4 },
  { id: 9204, parentId: 9201, resultId: 9304, seed: 20260810, duration: 218, density: 4.0 },
];

const metricRows = simulations.flatMap((simulation) => [
  [
    simulation.resultId,
    escapeSql('SECOND'),
    escapeSql('SIMULATION_DURATION_SECONDS'),
    simulation.duration,
  ],
  [
    simulation.resultId,
    escapeSql('SECOND'),
    escapeSql('TOTAL_EVACUATION_TIME_SECONDS'),
    simulation.duration,
  ],
  [simulation.resultId, escapeSql('PERSON_PER_M2'), escapeSql('MAX_DENSITY'), simulation.density],
  [simulation.resultId, escapeSql('PERSON'), escapeSql('TOTAL_PEOPLE'), TOTAL_PEOPLE],
  [simulation.resultId, escapeSql('PERSON'), escapeSql('EVACUATED_PEOPLE'), TOTAL_PEOPLE],
  [
    simulation.resultId,
    escapeSql('COUNT'),
    escapeSql('BOTTLENECK_COUNT'),
    simulation.id === 9201 ? 2 : 1,
  ],
]);

const timelineRows = chunk(timelineFrames, FRAMES_PER_CHUNK).map((frames, sequence) => [
  '9301',
  sequence,
  jsonSql({
    coordinateSystem: 'FLOOR_PLAN',
    coordinateUnit: 'METER',
    positionOrder: 'AGENT_ID_ASC_XY_FLAT',
    inactivePositionValue: INACTIVE_POSITION,
    agentCount: TOTAL_PEOPLE,
    frames,
  }),
]);

const heatmapRows = chunk(heatmapFrames, FRAMES_PER_CHUNK).map((frames, sequence) => [
  '9301',
  sequence,
  jsonSql({
    coordinateSystem: 'FLOOR_PLAN',
    coordinateUnit: 'METER',
    threshold: { value: 3.5, unit: 'PERSON_PER_M2' },
    grid: {
      originX: 0,
      originY: 0,
      cellSize: HEATMAP_CELL_SIZE,
      rows: HEATMAP_ROWS,
      columns: HEATMAP_COLUMNS,
      valueOrder: 'ROW_MAJOR',
    },
    frames,
  }),
]);

const initialState = {
  coordinateSystem: 'FLOOR_PLAN',
  coordinateUnit: 'METER',
  agents: agents.map((agent) => ({ id: agent.id, x: agent.startX, y: agent.startY })),
};

const sql = `-- Generated by scripts/generate-simulation-result-test-dml.mjs.
-- Re-run with: node scripts/generate-simulation-result-test-dml.mjs
-- Run with a MySQL account that can access all three hwalro schemas.

USE hwalro_auth;

SET @test_user_id := (
    SELECT user_id
    FROM users
    WHERE login_id = 'test'
    LIMIT 1
);

CREATE TEMPORARY TABLE simulation_result_fixture_assertions (
    assertion_name VARCHAR(100) NOT NULL,
    condition_met TINYINT NOT NULL,
    CONSTRAINT chk_simulation_result_fixture_assertion CHECK (condition_met = 1)
);

INSERT INTO simulation_result_fixture_assertions (assertion_name, condition_met)
VALUES ('test 사용자가 먼저 생성되어 있어야 합니다.', IF(@test_user_id IS NULL, 0, 1));

DROP TEMPORARY TABLE simulation_result_fixture_assertions;

START TRANSACTION;

USE hwalro_regulation;

DELETE FROM risks WHERE id IN (9401, 9402);

USE hwalro_simulation;

DELETE FROM improvement_proposals WHERE source_simulation_id IN (9201, 9202, 9203, 9204);
DELETE FROM simulations WHERE parent_simulation_id = 9201;
DELETE FROM simulations WHERE id = 9201;
UPDATE layouts SET current_version_id = NULL WHERE id = 9100;
DELETE FROM layouts WHERE id = 9100;
DELETE FROM floor_plans WHERE id = 9100;

INSERT INTO floor_plans (id, name, image_url, width, height)
VALUES (9100, ${escapeSql(drawing.name)}, NULL, ${drawing.width}, ${drawing.height});

INSERT INTO layouts (id, floor_plan_id, created_by, current_version_id, title, description, created_at)
VALUES (9100, 9100, @test_user_id, NULL, ${escapeSql('더현대 서울 B2 · 팝업 행사장')},
        ${escapeSql('시뮬레이션 결과 화면 DB 연동용 테스트 배치')}, '2026-08-07 09:00:00.000000');

INSERT INTO layout_versions (id, layout_id, version, status, optimistic_lock, created_at)
VALUES (9100, 9100, 1, 'PUBLISHED', 0, '2026-08-07 09:00:00.000000');

${insertRows('walls', ['layout_version_id', 'name', 'start_x', 'start_y', 'end_x', 'end_y'], wallRows)}

${insertRows('layout_exits', ['id', 'layout_version_id', 'name', 'start_x', 'start_y', 'end_x', 'end_y'], exitRows)}

${insertRows('layout_texts', ['layout_version_id', 'text', 'x', 'y'], textRows)}

UPDATE layouts SET current_version_id = 9100 WHERE id = 9100;

${insertRows(
  'simulations',
  [
    'id',
    'layout_version_id',
    'parent_simulation_id',
    'created_by',
    'status',
    'created_at',
    'requested_at',
    'started_at',
    'finished_at',
  ],
  simulations.map((simulation, index) => {
    const minute = String(index * 10).padStart(2, '0');
    return [
      simulation.id,
      '9100',
      simulation.parentId ?? 'NULL',
      '@test_user_id',
      escapeSql('COMPLETED'),
      escapeSql(`2026-08-07 09:${minute}:00.000000`),
      escapeSql(`2026-08-07 09:${minute}:05.000000`),
      escapeSql(`2026-08-07 09:${minute}:10.000000`),
      escapeSql(`2026-08-07 09:${minute}:20.000000`),
    ];
  }),
)}

${insertRows(
  'simulation_options',
  ['simulation_id', 'random_seed', 'total_people', 'walking_speed', 'reaction_time'],
  simulations.map((simulation) => [simulation.id, simulation.seed, TOTAL_PEOPLE, 1.3, 2.0]),
)}

INSERT INTO simulation_initial_states (simulation_id, agent_positions)
VALUES (9201, ${jsonSql(initialState)});

${insertRows(
  'simulation_exits',
  ['simulation_id', 'layout_exit_id', 'layout_version_id'],
  simulations.flatMap((simulation) =>
    drawing.exits.map((_, index) => [simulation.id, 9101 + index, '9100']),
  ),
)}

${insertRows(
  'simulation_results',
  [
    'id',
    'simulation_id',
    'engine_version',
    'termination_reason',
    'frame_interval_seconds',
    'created_at',
  ],
  simulations.map((simulation, index) => [
    simulation.resultId,
    simulation.id,
    escapeSql('jupedsim-fixture-1.0'),
    escapeSql('ALL_EVACUATED'),
    4.0,
    escapeSql(`2026-08-07 10:${String(index * 10).padStart(2, '0')}:00.000000`),
  ]),
)}

${insertRows('simulation_metrics', ['simulation_result_id', 'unit', 'metric_type', 'metric_value'], metricRows)}

${insertRows('timelines', ['simulation_result_id', 'chunk_sequence', 'frame_data'], timelineRows)}

${insertRows('heatmaps', ['simulation_result_id', 'chunk_sequence', 'density_data'], heatmapRows)}

${insertRows(
  'detected_bottlenecks',
  [
    'simulation_result_id',
    'bottleneck_order',
    'start_time_seconds',
    'end_time_seconds',
    'peak_density',
    'threshold_value',
    'geometry',
    'analysis_version',
  ],
  [
    [
      9301,
      1,
      68,
      140,
      4.8,
      3.5,
      jsonSql({ type: 'RECTANGLE', name: '중앙 연결 통로', x: 93, y: 36, width: 31, height: 27 }),
      escapeSql('fixture-1.0'),
    ],
    [
      9301,
      2,
      148,
      206,
      4.1,
      3.5,
      jsonSql({ type: 'RECTANGLE', name: '남측 출구 통로', x: 132, y: 66, width: 25, height: 24 }),
      escapeSql('fixture-1.0'),
    ],
    [
      9302,
      1,
      74,
      126,
      4.2,
      3.5,
      jsonSql({ type: 'RECTANGLE', name: '중앙 연결 통로', x: 93, y: 36, width: 28, height: 24 }),
      escapeSql('fixture-1.0'),
    ],
    [
      9303,
      1,
      82,
      151,
      4.4,
      3.5,
      jsonSql({
        type: 'RECTANGLE',
        name: '중앙 집기 인접 통로',
        x: 88,
        y: 34,
        width: 29,
        height: 25,
      }),
      escapeSql('fixture-1.0'),
    ],
    [
      9304,
      1,
      66,
      118,
      4.0,
      3.5,
      jsonSql({ type: 'RECTANGLE', name: '남측 출구 통로', x: 132, y: 66, width: 22, height: 21 }),
      escapeSql('fixture-1.0'),
    ],
  ],
)}

USE hwalro_regulation;

INSERT INTO risks (
    id,
    simulation_result_id,
    assignee_id,
    title,
    description,
    start_x,
    start_y,
    end_x,
    end_y,
    severity,
    status,
    created_at
) VALUES
(
    9401,
    9301,
    @test_user_id,
    '중앙 행사 집기 인접 구역',
    '사용자가 결과 화면에서 지정한 위험 예상 구역 테스트 데이터',
    88.0000,
    34.0000,
    117.0000,
    59.0000,
    'HIGH',
    'OPEN',
    '2026-08-07 10:30:00.000000'
),
(
    9402,
    9301,
    @test_user_id,
    '남측 출구 대기 구역',
    '남측 출구 앞 대기열 발생 가능성을 확인하기 위한 테스트 데이터',
    132.0000,
    66.0000,
    157.0000,
    90.0000,
    'MEDIUM',
    'OPEN',
    '2026-08-07 10:31:00.000000'
);

COMMIT;
`;

await writeFile(outputPath, sql, 'utf8');
console.log(
  `Generated ${outputPath} (${TOTAL_PEOPLE} agents, ${timelineFrames.length} timeline frames, ${heatmapFrames.length} heatmap frames)`,
);
