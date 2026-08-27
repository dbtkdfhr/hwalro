import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { SimulationViewToggle } from './SimulationViewToggle';

describe('SimulationViewToggle', () => {
  it('exposes both modes and the current pressed state', () => {
    const html = renderToStaticMarkup(<SimulationViewToggle mode="three" onChange={vi.fn()} />);

    expect(html).toContain('2D');
    expect(html).toContain('3D');
    expect(html).not.toContain('2.5D');
    expect(html).toContain('aria-pressed="true"');
    expect(html).toContain('드래그해 회전');
  });
});
