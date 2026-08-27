import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { ThreeSimulationLoading } from './SimulationPlaybackStage';

describe('ThreeSimulationLoading', () => {
  it('fills the same canvas area as the loaded 3D stage', () => {
    const html = renderToStaticMarkup(<ThreeSimulationLoading />);

    expect(html).toContain('class="simulation-canvas-wrap simulation-three-loading"');
    expect(html).toContain('3D 공간을 준비하고 있습니다.');
  });
});
