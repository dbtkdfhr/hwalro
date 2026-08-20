import { describe, expect, it } from 'vitest';
import { getFloorLabelPresentation } from './floorLabelPresentation';

describe('getFloorLabelPresentation', () => {
  it('scales the screen font size with the Pixi camera', () => {
    const normal = getFloorLabelPresentation(7);
    const zoomed = getFloorLabelPresentation(14);

    expect(normal.screenFontSize).toBeCloseTo(11);
    expect(zoomed.screenFontSize).toBeCloseTo(22);
    expect(normal.sourceFontSize).toBe(11);
    expect(normal.labelScale).toBeCloseTo(1 / 7);
    expect(zoomed.labelScale).toBeCloseTo(1 / 7);
  });

  it('hides labels that become smaller than the drawing editor threshold', () => {
    expect(getFloorLabelPresentation(2).visible).toBe(false);
    expect(getFloorLabelPresentation(7).visible).toBe(true);
  });
});
