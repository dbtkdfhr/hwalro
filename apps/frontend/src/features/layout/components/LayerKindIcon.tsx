import { Armchair, BrickWall, Columns3, DoorOpen, Frame, SquareDashed, Type } from 'lucide-react';

/** 계층 목록에서 한눈에 종류를 알아보게 하는 아이콘. 구역은 Figma의 Frame처럼 # 모양을 쓴다. */
export type LayerIconKind = 'zone' | 'wall' | 'pillar' | 'fabric' | 'text' | 'exit' | 'outsideWall';

const ICONS = {
  zone: Frame,
  wall: BrickWall,
  pillar: Columns3,
  fabric: Armchair,
  text: Type,
  exit: DoorOpen,
  outsideWall: SquareDashed,
} as const;

export function LayerKindIcon({ kind }: { kind: LayerIconKind }) {
  const Icon = ICONS[kind];
  return <Icon aria-hidden className="layout-layer-row__icon" />;
}
