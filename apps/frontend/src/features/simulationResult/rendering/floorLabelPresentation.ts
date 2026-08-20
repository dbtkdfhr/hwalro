import { PX_PER_METER } from '../../layout/utils/geometry';
import { MIN_TEXT_SCREEN_PX, TEXT_FONT_PX } from '../../layout/utils/hitTest';

export const FLOOR_LABEL_SOURCE_FONT_SIZE = TEXT_FONT_PX;
export const FLOOR_LABEL_WORLD_SCALE = 1 / PX_PER_METER;

export function getFloorLabelPresentation(cameraScale: number) {
  const screenFontSize = FLOOR_LABEL_SOURCE_FONT_SIZE * FLOOR_LABEL_WORLD_SCALE * cameraScale;
  return {
    sourceFontSize: FLOOR_LABEL_SOURCE_FONT_SIZE,
    labelScale: FLOOR_LABEL_WORLD_SCALE,
    screenFontSize,
    visible: screenFontSize >= MIN_TEXT_SCREEN_PX,
  };
}
