import { memo } from 'react';
import { Circle, Group, Line, Rect, Text as KonvaText } from 'react-konva';
import type { Exit, Fabric, LayoutText, OutsideWall, Pillar, Wall } from '../types';
import { PX_PER_METER, rectCenter } from '../utils/geometry';
import {
  MIN_TEXT_SCREEN_PX,
  ROTATE_HANDLE_OFFSET_PX,
  TEXT_FONT_PX,
  textWorldBox,
} from '../utils/hitTest';
import { ACCENT_ALPHA_8, CANVAS_COLORS, FONT_UI } from '../utils/colors';

export const MINOR_STEP = 50;
export const MAJOR_STEP = 250;

interface GridLine {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

interface GridLayerProps {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
  zoom: number;
}

export const GridLayer = memo(function GridLayer({ minX, minY, maxX, maxY, zoom }: GridLayerProps) {
  const strokeWidth = 1 / (zoom * PX_PER_METER);
  const minor: GridLine[] = [];
  if (MINOR_STEP * zoom * PX_PER_METER >= 6) {
    for (let x = Math.floor(minX / MINOR_STEP) * MINOR_STEP; x <= maxX; x += MINOR_STEP) {
      minor.push({ x1: x, y1: minY, x2: x, y2: maxY });
    }
    for (let y = Math.floor(minY / MINOR_STEP) * MINOR_STEP; y <= maxY; y += MINOR_STEP) {
      minor.push({ x1: minX, y1: y, x2: maxX, y2: y });
    }
  }
  const major: GridLine[] = [];
  for (let x = Math.floor(minX / MAJOR_STEP) * MAJOR_STEP; x <= maxX; x += MAJOR_STEP) {
    major.push({ x1: x, y1: minY, x2: x, y2: maxY });
  }
  for (let y = Math.floor(minY / MAJOR_STEP) * MAJOR_STEP; y <= maxY; y += MAJOR_STEP) {
    major.push({ x1: minX, y1: y, x2: maxX, y2: y });
  }
  return (
    <>
      {minor.map((line, i) => (
        <Line
          key={`m${i}`}
          points={[line.x1, line.y1, line.x2, line.y2]}
          stroke={CANVAS_COLORS.gridMinor}
          strokeWidth={strokeWidth}
        />
      ))}
      {major.map((line, i) => (
        <Line
          key={`M${i}`}
          points={[line.x1, line.y1, line.x2, line.y2]}
          stroke={CANVAS_COLORS.gridMajor}
          strokeWidth={strokeWidth}
        />
      ))}
    </>
  );
});

interface WallViewProps {
  wall: Wall | OutsideWall;
  selected: boolean;
  s: (px: number) => number;
  color?: string;
  thick?: boolean;
  problem?: boolean;
}

export const WallView = memo(function WallView({
  wall,
  selected,
  s,
  color,
  thick,
  problem,
}: WallViewProps) {
  let stroke = color ?? CANVAS_COLORS.ink;
  if (selected) {
    stroke = CANVAS_COLORS.accent;
  }
  if (problem) {
    stroke = CANVAS_COLORS.problem;
  }
  let strokeWidth = thick ? s(3) : s(2);
  if (selected) {
    strokeWidth = s(2.5);
  }
  if (problem) {
    strokeWidth = s(3.5);
  }
  return (
    <Group>
      <Line
        points={[wall.startX, wall.startY, wall.endX, wall.endY]}
        stroke={stroke}
        strokeWidth={strokeWidth}
      />
      {selected && (
        <Group>
          <Circle
            x={wall.startX}
            y={wall.startY}
            radius={s(5)}
            fill={CANVAS_COLORS.canvas}
            stroke={CANVAS_COLORS.accent}
            strokeWidth={s(1.5)}
          />
          <Circle
            x={wall.endX}
            y={wall.endY}
            radius={s(5)}
            fill={CANVAS_COLORS.canvas}
            stroke={CANVAS_COLORS.accent}
            strokeWidth={s(1.5)}
          />
          <Circle x={wall.startX} y={wall.startY} radius={s(3)} fill={CANVAS_COLORS.accent} />
          <Circle x={wall.endX} y={wall.endY} radius={s(3)} fill={CANVAS_COLORS.accent} />
        </Group>
      )}
    </Group>
  );
});

export const OutsideWallView = memo(function OutsideWallView(props: WallViewProps) {
  return <WallView {...props} color={CANVAS_COLORS.outsideWall} thick />;
});

interface ExitViewProps {
  exit: Exit;
  selected: boolean;
  s: (px: number) => number;
  problem?: boolean;
}

export const ExitView = memo(function ExitView({ exit, selected, s, problem }: ExitViewProps) {
  let color: string = CANVAS_COLORS.exit;
  if (selected) {
    color = CANVAS_COLORS.exitStrong;
  }
  if (problem) {
    color = CANVAS_COLORS.problem;
  }
  let strokeWidth = s(2.5);
  if (selected) {
    strokeWidth = s(3);
  }
  if (problem) {
    strokeWidth = s(3.5);
  }
  return (
    <Group>
      <Line
        points={[exit.startX, exit.startY, exit.endX, exit.endY]}
        stroke={color}
        strokeWidth={strokeWidth}
        shadowColor={color}
        shadowBlur={s(22)}
        shadowOpacity={0.5}
        shadowOffset={{ x: 0, y: 0 }}
      />
      {selected && (
        <Group>
          <Circle
            x={exit.startX}
            y={exit.startY}
            radius={s(5)}
            fill={CANVAS_COLORS.canvas}
            stroke={CANVAS_COLORS.exitStrong}
            strokeWidth={s(1.5)}
          />
          <Circle
            x={exit.endX}
            y={exit.endY}
            radius={s(5)}
            fill={CANVAS_COLORS.canvas}
            stroke={CANVAS_COLORS.exitStrong}
            strokeWidth={s(1.5)}
          />
          <Circle x={exit.startX} y={exit.startY} radius={s(3)} fill={CANVAS_COLORS.exitStrong} />
          <Circle x={exit.endX} y={exit.endY} radius={s(3)} fill={CANVAS_COLORS.exitStrong} />
        </Group>
      )}
    </Group>
  );
});

interface RectViewProps {
  selected: boolean;
  s: (px: number) => number;
  fill: string;
  stroke: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
  problem?: boolean;
}

function RectShape({
  selected,
  s,
  fill,
  stroke,
  startX,
  startY,
  endX,
  endY,
  rotation,
  problem,
}: RectViewProps) {
  const minX = Math.min(startX, endX);
  const minY = Math.min(startY, endY);
  const width = Math.abs(endX - startX);
  const height = Math.abs(endY - startY);
  const center = rectCenter({ startX, startY, endX, endY });
  let rectStroke = stroke;
  let rectStrokeWidth = selected ? s(2) : s(1);
  let rectFill = fill;
  if (problem) {
    rectFill = CANVAS_COLORS.problemFill;
    rectStroke = CANVAS_COLORS.problem;
    rectStrokeWidth = s(2.5);
  }
  return (
    <Group x={center.x} y={center.y} rotation={rotation}>
      <Rect
        x={minX - center.x}
        y={minY - center.y}
        width={width}
        height={height}
        fill={rectFill}
        stroke={rectStroke}
        strokeWidth={rectStrokeWidth}
      />
      {selected && (
        <Group>
          <Circle
            x={startX - center.x}
            y={startY - center.y}
            radius={s(5)}
            fill={CANVAS_COLORS.canvas}
            stroke={CANVAS_COLORS.accent}
            strokeWidth={s(1.5)}
          />
          <Circle
            x={endX - center.x}
            y={endY - center.y}
            radius={s(5)}
            fill={CANVAS_COLORS.canvas}
            stroke={CANVAS_COLORS.accent}
            strokeWidth={s(1.5)}
          />
          <Circle
            x={startX - center.x}
            y={startY - center.y}
            radius={s(3)}
            fill={CANVAS_COLORS.accent}
          />
          <Circle
            x={endX - center.x}
            y={endY - center.y}
            radius={s(3)}
            fill={CANVAS_COLORS.accent}
          />
          <Circle
            x={0}
            y={minY - s(ROTATE_HANDLE_OFFSET_PX) - center.y}
            radius={s(5)}
            fill={CANVAS_COLORS.canvas}
            stroke={CANVAS_COLORS.accent}
            strokeWidth={s(1.5)}
          />
        </Group>
      )}
    </Group>
  );
}

interface PillarViewProps {
  pillar: Pillar;
  selected: boolean;
  s: (px: number) => number;
  problem?: boolean;
}

export const PillarView = memo(function PillarView({
  pillar,
  selected,
  s,
  problem,
}: PillarViewProps) {
  return (
    <RectShape
      selected={selected}
      s={s}
      problem={problem}
      fill={CANVAS_COLORS.pillarFill}
      stroke={selected ? CANVAS_COLORS.accent : CANVAS_COLORS.pillarStroke}
      startX={pillar.startX}
      startY={pillar.startY}
      endX={pillar.endX}
      endY={pillar.endY}
      rotation={pillar.rotation}
    />
  );
});

interface FabricViewProps {
  fabric: Fabric;
  selected: boolean;
  s: (px: number) => number;
  problem?: boolean;
}

export const FabricView = memo(function FabricView({
  fabric,
  selected,
  s,
  problem,
}: FabricViewProps) {
  return (
    <RectShape
      selected={selected}
      s={s}
      problem={problem}
      fill={selected ? CANVAS_COLORS.fabricSelectedFill : CANVAS_COLORS.fabricFill}
      stroke={selected ? CANVAS_COLORS.accent : CANVAS_COLORS.fabricStroke}
      startX={fabric.startX}
      startY={fabric.startY}
      endX={fabric.endX}
      endY={fabric.endY}
      rotation={fabric.rotation}
    />
  );
});

interface TextViewProps {
  text: LayoutText;
  selected: boolean;
  zoom: number;
}

export const TextView = memo(function TextView({ text, selected, zoom }: TextViewProps) {
  if (TEXT_FONT_PX * zoom < MIN_TEXT_SCREEN_PX) {
    return null;
  }
  const s = (px: number) => px / (zoom * PX_PER_METER);
  const box = textWorldBox(text);
  return (
    <Group>
      {selected && (
        <Rect
          x={box.x - s(TEXT_FONT_PX) / 4}
          y={box.y - s(TEXT_FONT_PX) / 4}
          width={box.w + s(TEXT_FONT_PX) / 2}
          height={box.h + s(TEXT_FONT_PX) / 2}
          fill={ACCENT_ALPHA_8}
          stroke={CANVAS_COLORS.accent}
          strokeWidth={s(1)}
          dash={[s(4), s(3)]}
        />
      )}
      <KonvaText
        x={text.x}
        y={text.y}
        text={text.text}
        fontSize={TEXT_FONT_PX / PX_PER_METER}
        fontFamily={FONT_UI}
        fill={selected ? CANVAS_COLORS.accent : CANVAS_COLORS.ink}
      />
    </Group>
  );
});
