import {
  Application,
  Container,
  FillGradient,
  Graphics,
  Particle,
  ParticleContainer,
  Rectangle,
  Text as PixiText,
  type Texture,
} from 'pixi.js';
import type {
  Bounds,
  DetectedBottleneck,
  HeatmapData,
  RiskZone,
  SimulationResultSummaryViewModel,
  SimulationResultViewModel,
} from '../types';
import { interpolatePositions, selectFramePair } from '../utils/playback';
import { FLOOR_LABEL_SOURCE_FONT_SIZE, getFloorLabelPresentation } from './floorLabelPresentation';
import { composeHeatmapTrail } from './heatmapTrail';

export interface PixiCameraTransform {
  scale: number;
  offsetX: number;
  offsetY: number;
}

export interface PixiSimulationScene {
  app: Application;
  world: Container;
  heatmapLayer: Graphics;
  hazardLayer: Graphics;
  hazardGradient: FillGradient;
  bottleneckLayer: Graphics;
  riskLayer: Graphics;
  agentLayer: ParticleContainer<Particle>;
  particles: Particle[];
  interpolationBuffer: Float32Array;
  agentTexture: Texture;
  floorLabels: PixiText[];
  lastHeatmapTime: number | null;
}

export function createCameraTransform(
  width: number,
  height: number,
  worldWidth: number,
  worldHeight: number,
  zoom: number,
  panX: number,
  panY: number,
): PixiCameraTransform {
  const padding = 34;
  const fitScale = Math.max(
    0.1,
    Math.min((width - padding * 2) / worldWidth, (height - padding * 2) / worldHeight),
  );
  return {
    scale: fitScale * zoom,
    offsetX: (width - worldWidth * fitScale) / 2 + panX,
    offsetY: (height - worldHeight * fitScale) / 2 + panY,
  };
}

export function pixiScreenToWorld(x: number, y: number, transform: PixiCameraTransform) {
  return {
    x: (x - transform.offsetX) / transform.scale,
    y: (y - transform.offsetY) / transform.scale,
  };
}

type PixiSceneConfig = Pick<
  SimulationResultSummaryViewModel,
  'drawing' | 'hazardZones' | 'totalPeople'
>;

function traceBoundary(graphics: Graphics, points: PixiSceneConfig['drawing']['outsideBoundary']) {
  const first = points[0];
  if (!first) return graphics;
  graphics.moveTo(first.x, first.y);
  for (const point of points.slice(1)) graphics.lineTo(point.x, point.y);
  return graphics.closePath();
}

function addRotatedRectangle(
  container: Container,
  rectangle: PixiSceneConfig['drawing']['pillars'][number],
  fillColor: number,
  strokeColor: number,
) {
  const x = Math.min(rectangle.startX, rectangle.endX);
  const y = Math.min(rectangle.startY, rectangle.endY);
  const width = Math.abs(rectangle.endX - rectangle.startX);
  const height = Math.abs(rectangle.endY - rectangle.startY);
  const graphic = new Graphics()
    .rect(-width / 2, -height / 2, width, height)
    .fill({ color: fillColor })
    .stroke({ color: strokeColor, width: 0.18 });
  graphic.position.set(x + width / 2, y + height / 2);
  graphic.rotation = ((rectangle.rotation ?? 0) * Math.PI) / 180;
  container.addChild(graphic);
}

function drawFloorPlan(result: PixiSceneConfig) {
  const { drawing } = result;
  const baseLayer = new Graphics();
  const structureLayer = new Container();
  const lineLayer = new Graphics();
  const labels: PixiText[] = [];
  baseLayer.rect(0, 0, drawing.width, drawing.height).fill({ color: 0xf3f7f6 });
  traceBoundary(baseLayer, drawing.outsideBoundary).fill({ color: 0xffffff });
  traceBoundary(lineLayer, drawing.outsideBoundary).stroke({ color: 0x355b55, width: 0.45 });
  for (const wall of drawing.walls) {
    lineLayer
      .moveTo(wall.startX, wall.startY)
      .lineTo(wall.endX, wall.endY)
      .stroke({ color: 0x506663, width: 0.4, cap: 'round' });
  }
  structureLayer.addChild(lineLayer);
  for (const pillar of drawing.pillars) {
    addRotatedRectangle(structureLayer, pillar, 0xdce5e3, 0x839793);
  }
  for (const fabric of drawing.fabrics) {
    addRotatedRectangle(structureLayer, fabric, 0xe8efed, 0xa0afac);
  }
  for (const text of drawing.layoutTexts) {
    const label = new PixiText({
      text: text.text,
      style: {
        fontFamily: 'Arial, sans-serif',
        fontSize: FLOOR_LABEL_SOURCE_FONT_SIZE,
        fill: 0x637773,
      },
      resolution: Math.max(2, (window.devicePixelRatio || 1) * 2),
    });
    label.position.set(text.x, text.y);
    labels.push(label);
    structureLayer.addChild(label);
  }
  const exitLayer = new Graphics();
  for (const exit of drawing.exits) {
    exitLayer
      .moveTo(exit.startX, exit.startY)
      .lineTo(exit.endX, exit.endY)
      .stroke({ color: 0x078f7e, width: 1, cap: 'round' });
  }
  structureLayer.addChild(exitLayer);
  return { baseLayer, structureLayer, labels };
}

function createAgentTexture(app: Application) {
  const source = new Graphics().circle(8, 8, 7).fill({ color: 0x0e9182 });
  const texture = app.renderer.generateTexture(source);
  source.destroy();
  return texture;
}

function drawDashedCircle(layer: Graphics, centerX: number, centerY: number, radius: number) {
  const circumference = Math.PI * 2 * radius;
  const segmentCount = Math.min(512, Math.max(12, Math.round(circumference / 2.25)));
  const segmentAngle = (Math.PI * 2) / segmentCount;
  const dashAngle = segmentAngle * 0.58;
  for (let index = 0; index < segmentCount; index += 1) {
    const startAngle = index * segmentAngle;
    layer.moveTo(centerX + Math.cos(startAngle) * radius, centerY + Math.sin(startAngle) * radius);
    layer.arc(centerX, centerY, radius, startAngle, startAngle + dashAngle);
  }
  layer.stroke({ color: 0xef7777, alpha: 1, width: 0.32 });
}

function drawHazardZones(result: PixiSceneConfig) {
  const layer = new Graphics();
  const gradient = new FillGradient({
    type: 'radial',
    center: { x: 0.5, y: 0.5 },
    innerRadius: 0,
    outerCenter: { x: 0.5, y: 0.5 },
    outerRadius: 0.5,
    colorStops: [
      { offset: 0, color: 'rgba(177, 32, 32, 0.58)' },
      { offset: 0.5, color: 'rgba(225, 75, 75, 0.28)' },
      { offset: 1, color: 'rgba(239, 119, 119, 0.08)' },
    ],
    textureSpace: 'local',
  });
  for (const hazard of result.hazardZones) {
    layer.circle(hazard.centerX, hazard.centerY, hazard.radius).fill(gradient);
    drawDashedCircle(layer, hazard.centerX, hazard.centerY, hazard.radius);
  }
  return { layer, gradient };
}

export async function createPixiSimulationScene(
  host: HTMLDivElement,
  result: PixiSceneConfig,
): Promise<PixiSimulationScene> {
  const app = new Application();
  await app.init({
    width: Math.max(1, host.clientWidth),
    height: Math.max(1, host.clientHeight),
    backgroundAlpha: 0,
    antialias: true,
    autoDensity: true,
    resolution: window.devicePixelRatio || 1,
    preference: 'webgl',
    autoStart: false,
  });
  app.canvas.className = 'simulation-pixi-canvas';
  app.canvas.setAttribute('aria-hidden', 'true');
  host.appendChild(app.canvas);

  const world = new Container();
  const heatmapLayer = new Graphics();
  const hazards = drawHazardZones(result);
  const bottleneckLayer = new Graphics();
  const riskLayer = new Graphics();
  const floorPlan = drawFloorPlan(result);
  const agentTexture = createAgentTexture(app);
  const particles = Array.from(
    { length: result.totalPeople },
    () =>
      new Particle({
        texture: agentTexture,
        anchorX: 0.5,
        anchorY: 0.5,
        scaleX: 0.052,
        scaleY: 0.052,
        x: -10_000,
        y: -10_000,
      }),
  );
  const agentLayer = new ParticleContainer<Particle>({
    texture: agentTexture,
    particles,
    boundsArea: new Rectangle(0, 0, result.drawing.width, result.drawing.height),
    dynamicProperties: {
      position: true,
      rotation: false,
      vertex: false,
      color: false,
    },
  });
  agentLayer.update();
  world.addChild(
    floorPlan.baseLayer,
    heatmapLayer,
    floorPlan.structureLayer,
    agentLayer,
    hazards.layer,
    bottleneckLayer,
    riskLayer,
  );
  app.stage.addChild(world);

  return {
    app,
    world,
    heatmapLayer,
    hazardLayer: hazards.layer,
    hazardGradient: hazards.gradient,
    bottleneckLayer,
    riskLayer,
    agentLayer,
    particles,
    interpolationBuffer: new Float32Array(result.totalPeople * 2),
    agentTexture,
    floorLabels: floorPlan.labels,
    lastHeatmapTime: null,
  };
}

function heatColor(density: number, maxDensity: number) {
  const ratio = Math.min(1, density / maxDensity);
  const low = { red: 255, green: 212, blue: 71 };
  const medium = { red: 255, green: 138, blue: 61 };
  const high = { red: 239, green: 63, blue: 50 };
  const start = ratio < 0.5 ? low : medium;
  const end = ratio < 0.5 ? medium : high;
  const progress = ratio < 0.5 ? ratio * 2 : (ratio - 0.5) * 2;
  const red = Math.round(start.red + (end.red - start.red) * progress);
  const green = Math.round(start.green + (end.green - start.green) * progress);
  const blue = Math.round(start.blue + (end.blue - start.blue) * progress);
  return (red << 16) | (green << 8) | blue;
}

function updateHeatmap(layer: Graphics, heatmap: HeatmapData, timeSeconds: number) {
  const frame = selectFramePair(heatmap.frames, timeSeconds).previous;
  const trailValues = composeHeatmapTrail(
    heatmap.frames,
    frame.timeSeconds,
    heatmap.rows * heatmap.columns,
  );
  const visibleCells: Array<{
    centerX: number;
    centerY: number;
    color: number;
    ratio: number;
  }> = [];
  const maxDensity = Math.max(1, heatmap.maxDensity);
  layer.clear();
  for (let row = 0; row < heatmap.rows; row += 1) {
    for (let column = 0; column < heatmap.columns; column += 1) {
      const density = trailValues[row * heatmap.columns + column];
      if (density <= 0) continue;
      visibleCells.push({
        centerX: heatmap.originX + (column + 0.5) * heatmap.cellWidth,
        centerY: heatmap.originY + (row + 0.5) * heatmap.cellHeight,
        color: heatColor(density, maxDensity),
        ratio: Math.min(1, density / maxDensity),
      });
    }
  }
  const cellRadius = Math.max(heatmap.cellWidth, heatmap.cellHeight);
  for (const cell of visibleCells) {
    layer
      .circle(cell.centerX, cell.centerY, cellRadius * 1.2)
      .fill({ color: cell.color, alpha: 0.035 + cell.ratio * 0.145 });
  }
  for (const cell of visibleCells) {
    layer
      .circle(cell.centerX, cell.centerY, cellRadius * 0.82)
      .fill({ color: cell.color, alpha: 0.07 + cell.ratio * 0.31 });
  }
  return frame.timeSeconds;
}

function drawBounds(
  layer: Graphics,
  bounds: Bounds,
  strokeColor: number,
  fillColor: number,
  fillAlpha: number,
) {
  layer
    .rect(bounds.x, bounds.y, bounds.width, bounds.height)
    .fill({ color: fillColor, alpha: fillAlpha })
    .stroke({ color: strokeColor, alpha: 1, width: 0.7 });
}

function drawDashedLine(
  layer: Graphics,
  startX: number,
  startY: number,
  endX: number,
  endY: number,
  dashLength = 2,
  gapLength = 1.25,
) {
  const length = Math.hypot(endX - startX, endY - startY);
  if (length === 0) return;
  const directionX = (endX - startX) / length;
  const directionY = (endY - startY) / length;
  for (let offset = 0; offset < length; offset += dashLength + gapLength) {
    const dashEnd = Math.min(offset + dashLength, length);
    layer
      .moveTo(startX + directionX * offset, startY + directionY * offset)
      .lineTo(startX + directionX * dashEnd, startY + directionY * dashEnd);
  }
}

function drawDashedBounds(
  layer: Graphics,
  bounds: Bounds,
  strokeColor: number,
  fillColor: number,
  fillAlpha: number,
) {
  const right = bounds.x + bounds.width;
  const bottom = bounds.y + bounds.height;
  layer.rect(bounds.x, bounds.y, bounds.width, bounds.height).fill({
    color: fillColor,
    alpha: fillAlpha,
  });
  drawDashedLine(layer, bounds.x, bounds.y, right, bounds.y);
  drawDashedLine(layer, right, bounds.y, right, bottom);
  drawDashedLine(layer, right, bottom, bounds.x, bottom);
  drawDashedLine(layer, bounds.x, bottom, bounds.x, bounds.y);
  layer.stroke({ color: strokeColor, alpha: 0.95, width: 0.65 });
}

function updateBottlenecks(
  layer: Graphics,
  bottlenecks: DetectedBottleneck[],
  selectedId: number | null,
  timeSeconds: number,
) {
  layer.clear();
  for (const bottleneck of bottlenecks) {
    const active =
      timeSeconds >= bottleneck.startTimeSeconds && timeSeconds <= bottleneck.endTimeSeconds;
    const fillAlpha = active ? 0.2 : 0.07;
    if (bottleneck.id === selectedId) {
      drawBounds(layer, bottleneck.geometry, 0xe44135, 0xef5734, fillAlpha);
    } else {
      drawDashedBounds(layer, bottleneck.geometry, 0xef6b43, 0xef5734, fillAlpha);
    }
  }
}

function updateRiskZones(layer: Graphics, riskZones: RiskZone[], draftZone: Bounds | null) {
  layer.clear();
  for (const zone of riskZones) {
    drawBounds(layer, zone, 0x5c75d9, 0x5c75d9, 0.1);
  }
  if (draftZone) drawBounds(layer, draftZone, 0x5c75d9, 0x5c75d9, 0.08);
}

export function updatePixiSimulationScene(
  scene: PixiSimulationScene,
  result: SimulationResultViewModel,
  bottlenecks: DetectedBottleneck[],
  currentTimeSeconds: number,
  selectedBottleneckId: number | null,
  showBottlenecks: boolean,
  riskZones: RiskZone[],
  draftZone: Bounds | null,
) {
  const heatmapFrame = selectFramePair(result.heatmap.frames, currentTimeSeconds).previous;
  if (scene.lastHeatmapTime !== heatmapFrame.timeSeconds) {
    scene.lastHeatmapTime = updateHeatmap(scene.heatmapLayer, result.heatmap, currentTimeSeconds);
  }
  const pair = selectFramePair(result.agentFrames, currentTimeSeconds);
  interpolatePositions(
    pair.previous.positions,
    pair.next.positions,
    pair.ratio,
    scene.interpolationBuffer,
  );
  for (let index = 0; index < scene.particles.length; index += 1) {
    const offset = index * 2;
    scene.particles[index].x = scene.interpolationBuffer[offset];
    scene.particles[index].y = scene.interpolationBuffer[offset + 1];
  }
  if (showBottlenecks) {
    updateBottlenecks(scene.bottleneckLayer, bottlenecks, selectedBottleneckId, currentTimeSeconds);
  } else {
    scene.bottleneckLayer.clear();
  }
  updateRiskZones(scene.riskLayer, riskZones, draftZone);
  scene.app.render();
}

export function applyPixiCamera(scene: PixiSimulationScene, transform: PixiCameraTransform) {
  scene.world.scale.set(transform.scale);
  scene.world.position.set(transform.offsetX, transform.offsetY);
  const labelPresentation = getFloorLabelPresentation(transform.scale);
  for (const label of scene.floorLabels) {
    label.scale.set(labelPresentation.labelScale);
    label.visible = labelPresentation.visible;
  }
  scene.app.render();
}

export function destroyPixiSimulationScene(scene: PixiSimulationScene) {
  scene.app.destroy({ removeView: true }, { children: true });
  scene.hazardGradient.destroy();
  scene.agentTexture.destroy(true);
}
