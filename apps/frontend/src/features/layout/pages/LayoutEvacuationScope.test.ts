import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('evacuation route feature scope', () => {
  it('keeps evacuation routes on the employee page and out of the layout workspace', () => {
    const layoutPageSource = readFileSync(new URL('./LayoutPage.tsx', import.meta.url), 'utf8');
    const layoutCanvasSource = readFileSync(
      new URL('../components/LayoutCanvas.tsx', import.meta.url),
      'utf8',
    );
    const employeePageSource = readFileSync(
      new URL('../../zones/pages/EvacuationPage.tsx', import.meta.url),
      'utf8',
    );
    const employeeOverlaySource = readFileSync(
      new URL('../../zones/components/EvacuationRouteOverlay.tsx', import.meta.url),
      'utf8',
    );

    expect(layoutPageSource).not.toContain('EvacuationRoutePanel');
    expect(layoutPageSource).not.toContain('.evacuationRoutes(');
    expect(layoutCanvasSource).not.toContain('EvacuationRouteOverlay');
    expect(employeePageSource).toContain('zoneApi.evacuationRoutes');
    expect(employeePageSource).toContain('EvacuationRouteOverlay');
    expect(employeeOverlaySource).toContain('export function EvacuationRouteOverlay');
  });
});
