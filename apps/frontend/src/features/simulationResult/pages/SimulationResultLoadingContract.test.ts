import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('simulation result loading contract', () => {
  it('재생 청크 초기 로딩은 공통 문구만 표시하고 이동 버튼을 노출하지 않는다', () => {
    const source = readFileSync(new URL('./SimulationResultPage.tsx', import.meta.url), 'utf8');
    const loadingStart = source.indexOf('if (chunks.loading && (!result || !currentFrame))');
    const errorStart = source.indexOf(
      'if (chunks.error || !result || !currentFrame)',
      loadingStart,
    );
    const loadingBranch = source.slice(loadingStart, errorStart);

    expect(loadingStart).toBeGreaterThan(-1);
    expect(errorStart).toBeGreaterThan(loadingStart);
    expect(loadingBranch).toContain('message={SIMULATION_RESULT_LOADING_MESSAGE}');
    expect(loadingBranch).not.toContain('actions=');
    expect(loadingBranch).not.toContain('뒤로');
  });
});
