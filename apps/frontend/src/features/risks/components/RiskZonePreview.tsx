import { SimulationMinimap } from '../../simulations/components/SimulationMinimap';
import type { RiskDrawingContext } from '../types/risks';

interface ZoneBounds {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

interface Props {
  drawing: RiskDrawingContext['drawing'];
  zone: ZoneBounds;
  width?: number;
  height?: number;
}

export function RiskZonePreview({ drawing, zone, width = 240, height = 160 }: Props) {
  return (
    <SimulationMinimap
      drawing={drawing}
      highlights={[
        {
          x: Math.min(zone.startX, zone.endX),
          y: Math.min(zone.startY, zone.endY),
          width: Math.abs(zone.endX - zone.startX),
          height: Math.abs(zone.endY - zone.startY),
        },
      ]}
      ariaLabel="주의 항목이 표시된 도면 구역 미리보기"
      width={width}
      height={height}
      className="block"
    />
  );
}
