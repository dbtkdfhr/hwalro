import { useState } from 'react';
import { MousePointer, SquarePlus } from 'lucide-react';

import { CanvasWorkspacePanel, useCollapsibleWorkspacePanel } from '../../../components/workspace';
import type { SimulationDrawing, SimulationRect } from '../../simulations/types';
import type { ForbiddenZone, SearchConstraints } from '../api/layoutSearchApi';
import { ConstraintEditor, type ConstraintEditorTool } from './ConstraintEditor';

const WALL_CONTACT_EPSILON = 0.05;

function pointToSegmentDistance(
  px: number,
  py: number,
  ax: number,
  ay: number,
  bx: number,
  by: number,
): number {
  const dx = bx - ax;
  const dy = by - ay;
  const lengthSq = dx * dx + dy * dy;
  const t =
    lengthSq === 0 ? 0 : Math.min(Math.max(((px - ax) * dx + (py - ay) * dy) / lengthSq, 0), 1);
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}

function segmentDistance(
  ax: number,
  ay: number,
  bx: number,
  by: number,
  cx: number,
  cy: number,
  dx: number,
  dy: number,
): number {
  return Math.min(
    pointToSegmentDistance(ax, ay, cx, cy, dx, dy),
    pointToSegmentDistance(bx, by, cx, cy, dx, dy),
    pointToSegmentDistance(cx, cy, ax, ay, bx, by),
    pointToSegmentDistance(dx, dy, ax, ay, bx, by),
  );
}

function fabricCorners(fabric: SimulationRect): Array<[number, number]> {
  const minX = Math.min(fabric.startX, fabric.endX);
  const maxX = Math.max(fabric.startX, fabric.endX);
  const minY = Math.min(fabric.startY, fabric.endY);
  const maxY = Math.max(fabric.startY, fabric.endY);
  const cx = (minX + maxX) / 2;
  const cy = (minY + maxY) / 2;
  const rad = (fabric.rotation * Math.PI) / 180;
  const cos = Math.cos(rad);
  const sin = Math.sin(rad);
  const corners: Array<[number, number]> = [
    [minX, minY],
    [maxX, minY],
    [maxX, maxY],
    [minX, maxY],
  ];
  return corners.map(([x, y]) => [
    cx + (x - cx) * cos - (y - cy) * sin,
    cy + (x - cx) * sin + (y - cy) * cos,
  ]);
}

function touchesWall(fabric: SimulationRect, drawing: SimulationDrawing): boolean {
  const segments: Array<[number, number, number, number]> = [];
  for (const wall of drawing.walls) {
    segments.push([wall.startX, wall.startY, wall.endX, wall.endY]);
  }
  const boundary = drawing.outsideBoundary;
  if (boundary.length >= 2) {
    for (let i = 0; i < boundary.length; i += 1) {
      const start = boundary[i];
      const end = boundary[(i + 1) % boundary.length];
      segments.push([start.x, start.y, end.x, end.y]);
    }
  }
  const corners = fabricCorners(fabric);
  for (let i = 0; i < corners.length; i += 1) {
    const [ax, ay] = corners[i];
    const [bx, by] = corners[(i + 1) % corners.length];
    for (const [cx, cy, dx, dy] of segments) {
      if (segmentDistance(ax, ay, bx, by, cx, cy, dx, dy) <= WALL_CONTACT_EPSILON) {
        return true;
      }
    }
  }
  return false;
}

const RADIUS_SLIDER_MAX = 5;
const RADIUS_SLIDER_STEP = 0.5;

function radiusToSlider(radius: number | undefined): number {
  if (radius === undefined) return RADIUS_SLIDER_MAX;
  return Math.min(radius, RADIUS_SLIDER_MAX);
}

function radiusLabel(radius: number | undefined): string {
  if (radius === undefined) return '자유';
  if (radius === 0) return '고정';
  return `${Math.round(radius * 10) / 10}m`;
}

function fabricBadges(fabricId: number, constraints: SearchConstraints): string[] {
  const badges: string[] = [];
  const radius = constraints.moveRadii[fabricId];
  if (radius === 0) badges.push('고정');
  else if (radius !== undefined && radius > 0) badges.push(`${Math.round(radius * 10) / 10}m`);
  if (constraints.rotationAllowed[fabricId] === false) badges.push('회전');
  if (constraints.wallAnchored[fabricId] === true) badges.push('벽면');
  return badges;
}

function formatZone(zone: ForbiddenZone): string {
  const width = Math.round(zone.width * 10) / 10;
  const height = Math.round(zone.height * 10) / 10;
  return `${width} × ${height} m`;
}

interface Props {
  drawing: SimulationDrawing;
  constraints: SearchConstraints;
  onChange: (updater: (current: SearchConstraints) => SearchConstraints) => void;
  onStart: (verify: boolean) => void;
  starting: boolean;
}

export function ConstraintInspector({ drawing, constraints, onChange, onStart, starting }: Props) {
  const [verify, setVerify] = useState(false);
  const [selectedFabricId, setSelectedFabricId] = useState<number | null>(null);
  const [tool, setTool] = useState<ConstraintEditorTool>('select');
  const [selectedZoneIndex, setSelectedZoneIndex] = useState<number | null>(null);
  const panel = useCollapsibleWorkspacePanel();

  const selectedFabric =
    selectedFabricId === null
      ? null
      : (drawing.fabrics.find((fabric) => fabric.id === selectedFabricId) ?? null);

  const selectedTouchesWall = selectedFabric !== null && touchesWall(selectedFabric, drawing);

  const setRadius = (fabricId: number, sliderValue: number) => {
    onChange((current) => {
      const next: SearchConstraints = {
        ...current,
        moveRadii: { ...current.moveRadii },
      };
      if (sliderValue >= RADIUS_SLIDER_MAX) {
        delete next.moveRadii[fabricId];
      } else {
        next.moveRadii[fabricId] = Math.round(sliderValue * 10) / 10;
      }
      return next;
    });
  };

  const toggleRotation = (fabricId: number) => {
    onChange((current) => {
      const next: SearchConstraints = {
        ...current,
        rotationAllowed: { ...current.rotationAllowed },
      };
      next.rotationAllowed[fabricId] = !(current.rotationAllowed[fabricId] ?? true);
      return next;
    });
  };

  const toggleWallAnchored = (fabricId: number) => {
    onChange((current) => {
      const next: SearchConstraints = {
        ...current,
        wallAnchored: { ...current.wallAnchored },
      };
      next.wallAnchored[fabricId] = !(current.wallAnchored[fabricId] ?? false);
      return next;
    });
  };

  const addForbiddenZone = (zone: ForbiddenZone) => {
    onChange((current) => ({
      ...current,
      forbiddenZones: [...current.forbiddenZones, zone],
    }));
    setSelectedZoneIndex(constraints.forbiddenZones.length);
  };

  const deleteForbiddenZone = (index: number) => {
    onChange((current) => ({
      ...current,
      forbiddenZones: current.forbiddenZones.filter((_, i) => i !== index),
    }));
    setSelectedZoneIndex((current) => {
      if (current === null) return null;
      if (current === index) return null;
      return current > index ? current - 1 : current;
    });
  };

  const moveForbiddenZone = (index: number, dx: number, dy: number) => {
    onChange((current) => ({
      ...current,
      forbiddenZones: current.forbiddenZones.map((zone, i) =>
        i === index
          ? {
              x: Math.round((zone.x + dx) * 100) / 100,
              y: Math.round((zone.y + dy) * 100) / 100,
              width: zone.width,
              height: zone.height,
            }
          : zone,
      ),
    }));
  };

  return (
    <div className="constraint-inspector" aria-label="구조물 제약 설정">
      <div className="constraint-inspector__canvas">
        <ConstraintEditor
          drawing={drawing}
          constraints={constraints}
          selectedFabricId={selectedFabricId}
          tool={tool}
          onSelectFabric={setSelectedFabricId}
          onAddForbiddenZone={addForbiddenZone}
          onMoveForbiddenZone={moveForbiddenZone}
          selectedZoneIndex={selectedZoneIndex}
          onSelectZone={setSelectedZoneIndex}
        />
      </div>

      <div className="constraint-floating-toolbar">
        <div className="constraint-floating-toolbar__buttons">
          <button
            type="button"
            className={`constraint-floating-tool-button ${tool === 'select' ? 'is-active' : ''}`}
            aria-pressed={tool === 'select'}
            onClick={() => setTool('select')}
          >
            <MousePointer className="h-3.5 w-3.5" />
            <span>선택 도구</span>
          </button>
          <button
            type="button"
            className={`constraint-floating-tool-button ${tool === 'zone' ? 'is-active' : ''}`}
            aria-pressed={tool === 'zone'}
            onClick={() => setTool((current) => (current === 'zone' ? 'select' : 'zone'))}
          >
            <SquarePlus className="h-3.5 w-3.5" />
            <span>금지 영역 그리기</span>
          </button>
        </div>
        <span className="constraint-floating-toolbar__hint">
          {tool === 'zone'
            ? '드래그하여 금지 영역을 그립니다'
            : '구조물을 클릭하여 제약을 지정합니다'}
        </span>
        {tool === 'zone' && (
          <p className="constraint-editor-zone-hint" role="status">
            금지 영역 안으로는 구조물이 이동할 수 없습니다. (최소 0.2m)
          </p>
        )}
      </div>

      {panel.isMinimized ? (
        <button type="button" className="canvas-workspace-panel-restore" onClick={panel.restore}>
          제약 설정 열기
        </button>
      ) : (
        <CanvasWorkspacePanel
          ariaLabel="구조물 제약 설정"
          animate
          className={`constraint-inspector__panel ${panel.isCollapsing ? 'is-collapsing' : ''}`}
        >
          <div className="constraint-panel-header">
            <h3>구조물 제약 설정</h3>
            <button
              type="button"
              className="candidate-panel-close-btn"
              onClick={panel.minimize}
              aria-label="구조물 제약 설정 최소화"
            >
              −
            </button>
          </div>

          <div className="constraint-inspector__panel-scroll">
            <div className="constraint-inspector__group">
              <h3 className="constraint-inspector__group-title">
                구조물 목록
                {drawing.fabrics.length > 0 && <small>{drawing.fabrics.length}개</small>}
              </h3>
              {drawing.fabrics.length === 0 ? (
                <p className="constraint-inspector__empty">구조물이 없습니다.</p>
              ) : (
                <ul className="constraint-inspector__list">
                  {drawing.fabrics.map((fabric) => {
                    const badges = fabricBadges(fabric.id, constraints);
                    const selected = fabric.id === selectedFabricId;
                    return (
                      <li key={fabric.id}>
                        <button
                          type="button"
                          className={`constraint-fabric-row${selected ? ' is-selected' : ''}`}
                          aria-pressed={selected}
                          onClick={() => setSelectedFabricId(fabric.id)}
                        >
                          <span>{fabric.name || `구조물 ${fabric.id}`}</span>
                          {badges.length > 0 && (
                            <span className="constraint-fabric-badges">
                              {badges.map((badge) => (
                                <em key={badge} className="constraint-fabric-badge">
                                  {badge}
                                </em>
                              ))}
                            </span>
                          )}
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>

            {selectedFabric && (
              <div className="constraint-inspector__group">
                <h3 className="constraint-inspector__group-title">
                  {selectedFabric.name || `구조물 ${selectedFabric.id}`} 제약
                </h3>
                <div className="constraint-inspector__detail">
                  <div className="constraint-inspector__controls">
                    <span className="constraint-inspector__label">이동 반경</span>
                    <div className="constraint-inspector__slider-row">
                      <input
                        type="range"
                        min={0}
                        max={RADIUS_SLIDER_MAX}
                        step={RADIUS_SLIDER_STEP}
                        value={radiusToSlider(constraints.moveRadii[selectedFabric.id])}
                        aria-label={`${selectedFabric.name ?? `구조물 ${selectedFabric.id}`} 이동 반경`}
                        onChange={(event) =>
                          setRadius(selectedFabric.id, Number(event.target.value))
                        }
                      />
                      <span className="constraint-inspector__slider-value">
                        {radiusLabel(constraints.moveRadii[selectedFabric.id])}
                      </span>
                    </div>
                    <div className="constraint-inspector__slider-scale" aria-hidden="true">
                      <span>고정</span>
                      <span>{Math.round(RADIUS_SLIDER_MAX * 10) / 10}m</span>
                      <span>자유</span>
                    </div>
                  </div>
                  <div className="constraint-inspector__controls">
                    <label className="constraint-inspector__check">
                      <input
                        type="checkbox"
                        checked={constraints.rotationAllowed[selectedFabric.id] ?? true}
                        onChange={() => toggleRotation(selectedFabric.id)}
                      />
                      회전 허용
                    </label>
                    <label
                      className={`constraint-inspector__check${
                        selectedTouchesWall ? '' : ' is-disabled'
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={constraints.wallAnchored[selectedFabric.id] === true}
                        disabled={!selectedTouchesWall}
                        onChange={() => toggleWallAnchored(selectedFabric.id)}
                      />
                      벽면 접촉 유지
                    </label>
                    {!selectedTouchesWall && (
                      <small className="constraint-inspector__check-hint">
                        벽에 닿아 있는 구조물만 벽면 접촉 유지를 설정할 수 있습니다.
                      </small>
                    )}
                  </div>
                </div>
              </div>
            )}

            <div className="constraint-inspector__group">
              <h3 className="constraint-inspector__group-title">금지 영역</h3>
              {constraints.forbiddenZones.length === 0 ? (
                <p className="constraint-inspector__empty">
                  {tool === 'zone'
                    ? '캔버스에서 드래그하여 금지 영역을 추가하세요.'
                    : '금지 영역이 없습니다.'}
                </p>
              ) : (
                <ul className="constraint-zone-list">
                  {constraints.forbiddenZones.map((zone, index) => (
                    <li
                      key={`${zone.x}-${zone.y}-${index}`}
                      className={`constraint-zone-item${index === selectedZoneIndex ? ' is-selected' : ''}`}
                    >
                      <button
                        type="button"
                        className="constraint-zone-item__select"
                        onClick={() =>
                          setSelectedZoneIndex((current) => (current === index ? null : index))
                        }
                      >
                        <span>금지 영역 {index + 1}</span>
                        <small>{formatZone(zone)}</small>
                      </button>
                      <button
                        type="button"
                        className="constraint-zone-item__delete"
                        aria-label={`금지 영역 ${index + 1} 삭제`}
                        onClick={() => deleteForbiddenZone(index)}
                      >
                        삭제
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <label className={`constraint-inspector__check${starting ? ' is-disabled' : ''}`}>
              <input
                type="checkbox"
                checked={verify}
                disabled={starting}
                onChange={(event) => setVerify(event.target.checked)}
              />
              <span>개선안마다 시뮬레이션으로 확인</span>
            </label>
            <p className="constraint-inspector__check-hint">
              개선 폭을 실측으로 확인하지만 탐색이 훨씬 오래 걸립니다. 끄면 후보만 제안하고, 원하는
              개선안을 골라 직접 실행할 수 있습니다.
            </p>
            <p className="constraint-inspector__note">
              화면을 떠나도 서버에서 탐색이 계속되며 나중에 돌아와 진행 상태를 확인할 수 있습니다.
            </p>
          </div>

          <div className="constraint-panel-footer">
            <button
              type="button"
              className="search-start-button"
              disabled={starting}
              onClick={() => onStart(verify)}
            >
              {starting ? '탐색 준비 중' : '배치 개선안 탐색 시작'}
            </button>
          </div>
        </CanvasWorkspacePanel>
      )}
    </div>
  );
}
