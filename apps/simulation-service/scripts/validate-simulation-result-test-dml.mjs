import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const dmlPath = resolve(scriptDirectory, '../src/main/resources/db/simulation-result-test-dml.sql');
const sql = await readFile(dmlPath, 'utf8');

for (const requiredFragment of [
  "WHERE login_id = 'test'",
  'VALUES (9100, 9100, @test_user_id',
  'termination_reason, frame_interval_seconds',
  "'ALL_EVACUATED'",
  'USE hwalro_regulation;',
  'INSERT INTO risks',
]) {
  if (!sql.includes(requiredFragment)) {
    throw new Error(`Missing required integrated fixture fragment: ${requiredFragment}`);
  }
}
const payloads = [...sql.matchAll(/CAST\('((?:''|[^'])*)' AS JSON\)/g)].map((match) =>
  match[1].replaceAll("''", "'"),
);
const parsedPayloads = payloads.map((payload) => JSON.parse(payload));
const timelineChunks = parsedPayloads.filter(
  (payload) => payload.positionOrder === 'AGENT_ID_ASC_XY_FLAT',
);
const heatmapChunks = parsedPayloads.filter((payload) => payload.grid?.valueOrder === 'ROW_MAJOR');
const timelineFrames = timelineChunks.flatMap((payload) => payload.frames);
const heatmapFrames = heatmapChunks.flatMap((payload) => payload.frames);

if (timelineChunks.length !== 7 || timelineFrames.length !== 67) {
  throw new Error('Expected 7 timeline chunks containing 67 frames');
}
if (heatmapChunks.length !== 7 || heatmapFrames.length !== 67) {
  throw new Error('Expected 7 heatmap chunks containing 67 frames');
}
if (timelineChunks.some((payload) => payload.agentCount !== 100)) {
  throw new Error('Every timeline chunk must describe 100 agents');
}
if (timelineFrames.some((frame) => frame.positions.length !== 200)) {
  throw new Error('Every timeline frame must contain 200 x/y values');
}
if (heatmapFrames.some((frame) => frame.values.length !== 680)) {
  throw new Error('Every heatmap frame must contain a 34 x 20 density grid');
}

console.log(
  JSON.stringify({
    jsonPayloads: parsedPayloads.length,
    timelineChunks: timelineChunks.length,
    timelineFrames: timelineFrames.length,
    heatmapChunks: heatmapChunks.length,
    heatmapFrames: heatmapFrames.length,
    agentCount: timelineChunks[0].agentCount,
  }),
);
