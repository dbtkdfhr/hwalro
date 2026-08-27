import { ChevronRight } from 'lucide-react';
import { memo, useEffect, useRef, useState, type Dispatch, type DragEvent } from 'react';
import type { LayoutZone } from '../api/layoutMetadataApi';
import type { EditorAction } from '../state/editorReducer';
import type { EditorState, Vec2 } from '../types';
import { autoScrollStep } from '../utils/dragAutoScroll';
import { dropPositionAt, type DropPosition } from '../utils/layerDrop';
import { buildLayerMenu, type LayerMenuEntry } from '../utils/layerMenu';
import { rectCenter } from '../utils/geometry';
import type { LayerElement } from '../utils/zoneMembership';
import { groupElementsByZone } from '../utils/zoneMembership';
import { LayerKindIcon, type LayerIconKind } from './LayerKindIcon';
import { LayerTreeRow } from './LayerTreeRow';

const DND_MIME = 'application/x-hwalro-layer';
const DND_ZONE_MIME = 'application/x-hwalro-zone';

interface LayersPanelProps {
  state: EditorState;
  dispatch: Dispatch<EditorAction>;
  zones: LayoutZone[];
  selectedZoneId: number | null;
  onSelectZone: (zoneId: number | null) => void;
  employeeNameById: Record<number, string>;
  /** 잠긴 도면 버전에서는 순서가 도면 저장으로만 바뀌므로 드래그를 막고 이유를 표시한다. */
  orderLocked: boolean;
  /** false면 선택만 허용하고 순서와 구역 소속을 바꾸지 않는다. */
  membershipEditable?: boolean;
  onChangeMembership: (element: LayerElement, targetZoneId: number | null) => void;
  onGroupSelectionIntoZone: () => void;
  onMoveZoneOrder: (draggedZoneId: number, targetZoneId: number, position: DropPosition) => void;
  onCenterPoint: (point: Vec2) => void;
}

const groupClassName = 'mt-3 first:mt-0';

const ICON_KIND_BY_MEMBER: Record<LayerElement['kind'], LayerIconKind> = {
  WALL: 'wall',
  PILLAR: 'pillar',
  FABRIC: 'fabric',
};

type LayerGroupKey = 'zones' | 'common' | 'facilities';

interface LayerGroupHeaderProps {
  label: string;
  expanded: boolean;
  controls: string;
  onToggle: () => void;
}

function LayerGroupHeader({ label, expanded, controls, onToggle }: LayerGroupHeaderProps) {
  return (
    <h3>
      <button
        type="button"
        className="layout-layer-group__toggle"
        aria-expanded={expanded}
        aria-controls={controls}
        onClick={onToggle}
      >
        <ChevronRight aria-hidden className={expanded ? 'is-expanded' : ''} />
        <span>{label}</span>
      </button>
    </h3>
  );
}

interface DraggedLayer {
  kind: LayerElement['kind'];
  backendId: number | null;
  clientId: string;
}

function dragPayload(element: LayerElement): string {
  return JSON.stringify({
    kind: element.kind,
    backendId: element.backendId,
    clientId: element.id,
  });
}

function parseDragPayload(event: DragEvent): DraggedLayer | null {
  const raw = event.dataTransfer.getData(DND_MIME);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as DraggedLayer;
    return { kind: parsed.kind, backendId: parsed.backendId, clientId: parsed.clientId };
  } catch {
    return null;
  }
}

/** 도면의 모든 요소를 Figma식 계층으로 보여준다. 소속은 드래그 앤 드롭과 컨텍스트 메뉴로 정한다. */
export const LayersPanel = memo(
  function LayersPanel({
    state,
    dispatch,
    zones,
    selectedZoneId,
    onSelectZone,
    employeeNameById,
    orderLocked,
    membershipEditable = true,
    onChangeMembership,
    onGroupSelectionIntoZone,
    onMoveZoneOrder,
    onCenterPoint,
  }: LayersPanelProps) {
    const scrollRef = useRef<HTMLElement>(null);
    const autoScrollRef = useRef<number | null>(null);
    const autoScrollStepRef = useRef(0);
    const draggedLayerRef = useRef<DraggedLayer | null>(null);
    const draggedZoneIdRef = useRef<number | null>(null);
    const [expandedGroups, setExpandedGroups] = useState<Record<LayerGroupKey, boolean>>({
      zones: true,
      common: true,
      facilities: true,
    });
    const [collapsedZoneIds, setCollapsedZoneIds] = useState<Set<number>>(() => new Set());
    const [commonDropActive, setCommonDropActive] = useState(false);
    const { doc, selection } = state;
    const grouped = groupElementsByZone(doc.walls, doc.pillars, doc.fabrics, zones);
    const allMembers = [...grouped.zones.flatMap((group) => group.members), ...grouped.common];
    const ownerZoneByClientId = new Map<string, number>();
    for (const group of grouped.zones) {
      for (const member of group.members) {
        ownerZoneByClientId.set(member.id, group.zone.zoneId);
      }
    }

    const stopAutoScroll = () => {
      if (autoScrollRef.current !== null) {
        window.cancelAnimationFrame(autoScrollRef.current);
        autoScrollRef.current = null;
      }
    };

    /** 드래그 중 포인터가 목록 위/아래 끝에 닿으면 목록을 굴려 화면 밖 위치로도 옮길 수 있게 한다. */
    const driveAutoScroll = (clientY: number) => {
      const container = scrollRef.current;
      if (!container) return;
      autoScrollStepRef.current = autoScrollStep(clientY, container.getBoundingClientRect());
      if (autoScrollStepRef.current === 0) {
        stopAutoScroll();
        return;
      }
      if (autoScrollRef.current !== null) return;
      const tick = () => {
        const target = scrollRef.current;
        const dragging = draggedLayerRef.current !== null || draggedZoneIdRef.current !== null;
        if (!target || !dragging || autoScrollStepRef.current === 0) {
          autoScrollRef.current = null;
          return;
        }
        target.scrollBy(0, autoScrollStepRef.current);
        autoScrollRef.current = window.requestAnimationFrame(tick);
      };
      autoScrollRef.current = window.requestAnimationFrame(tick);
    };

    useEffect(() => stopAutoScroll, []);

    const resolveDragged = (
      event: DragEvent,
    ): { parsed: DraggedLayer; element: LayerElement } | null => {
      const parsed = parseDragPayload(event);
      if (!parsed) {
        return null;
      }
      const element = allMembers.find((member) => member.id === parsed.clientId);
      return element ? { parsed, element } : null;
    };

    const selectedMembers = grouped.zones
      .flatMap((group) => group.members)
      .concat(grouped.common)
      .filter(
        (member) =>
          (member.kind === 'WALL' && selection.wallIds.includes(member.id)) ||
          (member.kind === 'PILLAR' && selection.pillarIds.includes(member.id)) ||
          (member.kind === 'FABRIC' && selection.fabricIds.includes(member.id)),
      );
    const canGroupSelected =
      membershipEditable &&
      selectedMembers.length > 0 &&
      selectedMembers.every((member) =>
        doc.walls
          .concat(doc.pillars, doc.fabrics)
          .some((element) => element.id === member.id && element.backendId !== null),
      );

    const selectElement = (element: LayerElement, additive: boolean) => {
      dispatch({
        type: 'selectAt',
        wallId: element.kind === 'WALL' ? element.id : null,
        outsideWallId: null,
        exitId: null,
        textId: null,
        pillarId: element.kind === 'PILLAR' ? element.id : null,
        fabricId: element.kind === 'FABRIC' ? element.id : null,
        additive,
      });
    };

    const centerMember = (element: LayerElement) => {
      const source =
        element.kind === 'WALL' ? doc.walls : element.kind === 'PILLAR' ? doc.pillars : doc.fabrics;
      const geometry = source.find((candidate) => candidate.id === element.id);
      if (geometry) onCenterPoint(rectCenter(geometry));
    };

    const memberMenu = (element: LayerElement): LayerMenuEntry[] =>
      buildLayerMenu({
        element,
        zones,
        canGroupSelected,
        onChangeMembership,
        onGroupSelectionIntoZone,
      });

    const commonGroupDrop = (event: DragEvent) => {
      const dragged = resolveDragged(event);
      if (dragged) {
        onChangeMembership(dragged.element, null);
      }
      draggedLayerRef.current = null;
      setCommonDropActive(false);
    };

    const toggleGroup = (group: LayerGroupKey) => {
      setExpandedGroups((current) => ({ ...current, [group]: !current[group] }));
    };

    const toggleZone = (zoneId: number) => {
      setCollapsedZoneIds((current) => {
        const next = new Set(current);
        if (next.has(zoneId)) next.delete(zoneId);
        else next.add(zoneId);
        return next;
      });
    };

    const facilityRow = (
      kind: LayerIconKind,
      key: string,
      label: string,
      selected: boolean,
      ariaLabel: string,
      onSelect: () => void,
      center: Vec2,
    ) => (
      <LayerTreeRow
        key={key}
        label={label}
        icon={<LayerKindIcon kind={kind} />}
        selected={selected}
        ariaLabel={ariaLabel}
        onSelect={() => onSelect()}
        onDoubleClick={() => onCenterPoint(center)}
      />
    );

    return (
      <section
        ref={scrollRef}
        aria-label="도면 계층"
        className="layout-layers-panel min-h-0 flex-1 overflow-y-auto px-3 py-3"
        onDragOver={membershipEditable ? (event) => driveAutoScroll(event.clientY) : undefined}
        onDragLeave={stopAutoScroll}
        onDrop={stopAutoScroll}
        onDragEnd={stopAutoScroll}
      >
        {orderLocked ? (
          <p className="mb-2 rounded-md border border-panel-divider bg-panel-soft px-2 py-1.5 text-xs text-text-muted">
            잠긴 버전에서는 소속만 바꿀 수 있고 표시 순서는 도면 저장으로만 바뀝니다.
          </p>
        ) : null}

        <div className={groupClassName}>
          <LayerGroupHeader
            label="구역"
            expanded={expandedGroups.zones}
            controls="layout-layer-zones"
            onToggle={() => toggleGroup('zones')}
          />
          {expandedGroups.zones ? (
            <div id="layout-layer-zones">
              {grouped.zones.length === 0 ? (
                <p className="px-2 py-2 text-xs text-text-muted">
                  아직 구역이 없습니다. 구역 도구로 사각형을 그리거나 요소를 묶어 만드세요.
                </p>
              ) : (
                <ul className="mt-1">
                  {grouped.zones.map(({ zone, members }) => (
                    <li key={zone.zoneId}>
                      <ul>
                        <LayerTreeRow
                          label={zone.name}
                          icon={<LayerKindIcon kind="zone" />}
                          detail={
                            zone.assignedUserId === null
                              ? '미배정'
                              : (employeeNameById[zone.assignedUserId] ??
                                `직원 #${zone.assignedUserId}`)
                          }
                          selected={zone.zoneId === selectedZoneId}
                          ariaLabel={`${zone.name} 구역 선택`}
                          expanded={!collapsedZoneIds.has(zone.zoneId)}
                          onToggleExpanded={() => toggleZone(zone.zoneId)}
                          onSelect={(additive) => {
                            if (!additive) {
                              onSelectZone(zone.zoneId);
                            }
                          }}
                          onDoubleClick={() =>
                            onCenterPoint({
                              x: zone.rect.x + zone.rect.width / 2,
                              y: zone.rect.y + zone.rect.height / 2,
                            })
                          }
                          draggable={membershipEditable && !orderLocked}
                          onDragStart={(event) => {
                            event.dataTransfer.setData(DND_ZONE_MIME, String(zone.zoneId));
                            event.dataTransfer.effectAllowed = 'move';
                            draggedZoneIdRef.current = zone.zoneId;
                          }}
                          onDragEnd={() => {
                            draggedZoneIdRef.current = null;
                          }}
                          dropFeedbackFor={(event) => {
                            const activeZoneId = draggedZoneIdRef.current;
                            if (activeZoneId !== null && activeZoneId !== zone.zoneId) {
                              return dropPositionAt(
                                event.clientY,
                                event.currentTarget.getBoundingClientRect(),
                              );
                            }
                            return draggedLayerRef.current === null ? null : 'inside';
                          }}
                          onDrop={(event, feedback) => {
                            const activeZoneId = draggedZoneIdRef.current;
                            if (
                              activeZoneId !== null &&
                              activeZoneId !== zone.zoneId &&
                              feedback !== 'inside'
                            ) {
                              onMoveZoneOrder(activeZoneId, zone.zoneId, feedback);
                              draggedZoneIdRef.current = null;
                              return;
                            }
                            const dragged = resolveDragged(event);
                            if (dragged) {
                              onChangeMembership(dragged.element, zone.zoneId);
                            }
                            draggedLayerRef.current = null;
                          }}
                        />
                        {!collapsedZoneIds.has(zone.zoneId)
                          ? members.map((member) => (
                              <LayerTreeRow
                                key={member.id}
                                label={member.name}
                                icon={<LayerKindIcon kind={ICON_KIND_BY_MEMBER[member.kind]} />}
                                selected={
                                  (member.kind === 'WALL' &&
                                    selection.wallIds.includes(member.id)) ||
                                  (member.kind === 'PILLAR' &&
                                    selection.pillarIds.includes(member.id)) ||
                                  (member.kind === 'FABRIC' &&
                                    selection.fabricIds.includes(member.id))
                                }
                                indent
                                ariaLabel={`${member.name} 선택`}
                                onSelect={(additive) => selectElement(member, additive)}
                                onDoubleClick={() => centerMember(member)}
                                draggable={membershipEditable && !orderLocked}
                                onDragStart={(event) => {
                                  event.dataTransfer.setData(DND_MIME, dragPayload(member));
                                  event.dataTransfer.effectAllowed = 'move';
                                  draggedLayerRef.current = {
                                    kind: member.kind,
                                    backendId: member.backendId,
                                    clientId: member.id,
                                  };
                                }}
                                onDragEnd={() => {
                                  draggedLayerRef.current = null;
                                }}
                                dropFeedbackFor={(event) => {
                                  const activeLayer = draggedLayerRef.current;
                                  if (activeLayer === null || activeLayer.clientId === member.id) {
                                    return null;
                                  }
                                  return dropPositionAt(
                                    event.clientY,
                                    event.currentTarget.getBoundingClientRect(),
                                  );
                                }}
                                onDrop={(event, feedback) => {
                                  const dragged = resolveDragged(event);
                                  if (!dragged) {
                                    return;
                                  }
                                  if (feedback !== 'inside') {
                                    dispatch({
                                      type: 'reorderElements',
                                      draggedId: dragged.parsed.clientId,
                                      targetId: member.id,
                                      position: feedback,
                                    });
                                  }
                                  onChangeMembership(
                                    dragged.element,
                                    ownerZoneByClientId.get(member.id) ?? null,
                                  );
                                  draggedLayerRef.current = null;
                                }}
                                menuItems={membershipEditable ? memberMenu(member) : undefined}
                              />
                            ))
                          : null}
                        {!collapsedZoneIds.has(zone.zoneId) && members.length === 0 ? (
                          <li className="pl-6 pr-2 py-1 text-[11px] text-text-muted">비어 있음</li>
                        ) : null}
                      </ul>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ) : null}
        </div>

        <div className={groupClassName}>
          <LayerGroupHeader
            label="공통"
            expanded={expandedGroups.common}
            controls="layout-layer-common"
            onToggle={() => toggleGroup('common')}
          />
          {expandedGroups.common ? (
            <ul
              id="layout-layer-common"
              className="mt-1 rounded-md"
              onDragOver={
                membershipEditable
                  ? (event) => {
                      if (event.dataTransfer.types.includes(DND_MIME)) {
                        event.preventDefault();
                      }
                    }
                  : undefined
              }
              onDrop={membershipEditable ? commonGroupDrop : undefined}
            >
              {grouped.common.length === 0 ? (
                <li
                  className={`layout-layer-empty-drop ${commonDropActive ? 'is-active' : ''}`}
                  onDragEnter={(event) => {
                    if (draggedLayerRef.current === null) return;
                    event.preventDefault();
                    setCommonDropActive(true);
                  }}
                  onDragOver={(event) => {
                    if (draggedLayerRef.current === null) return;
                    event.preventDefault();
                    setCommonDropActive(true);
                  }}
                  onDragLeave={(event) => {
                    const rect = event.currentTarget.getBoundingClientRect();
                    if (
                      event.clientX <= rect.left ||
                      event.clientX >= rect.right ||
                      event.clientY <= rect.top ||
                      event.clientY >= rect.bottom
                    ) {
                      setCommonDropActive(false);
                    }
                  }}
                  onDrop={(event) => {
                    event.stopPropagation();
                    commonGroupDrop(event);
                  }}
                >
                  {commonDropActive ? (
                    <span
                      className="layout-layer-insertion layout-layer-insertion--before"
                      aria-hidden
                    />
                  ) : null}
                  <span>
                    {commonDropActive
                      ? '공통 영역의 첫 위치에 놓기'
                      : '구역에 속하지 않은 벽·기둥·구조물이 없습니다.'}
                  </span>
                </li>
              ) : null}
              {grouped.common.map((member) => (
                <LayerTreeRow
                  key={member.id}
                  label={member.name}
                  icon={<LayerKindIcon kind={ICON_KIND_BY_MEMBER[member.kind]} />}
                  selected={
                    (member.kind === 'WALL' && selection.wallIds.includes(member.id)) ||
                    (member.kind === 'PILLAR' && selection.pillarIds.includes(member.id)) ||
                    (member.kind === 'FABRIC' && selection.fabricIds.includes(member.id))
                  }
                  ariaLabel={`${member.name} 선택`}
                  onSelect={(additive) => selectElement(member, additive)}
                  onDoubleClick={() => centerMember(member)}
                  draggable={membershipEditable && !orderLocked}
                  onDragStart={(event) => {
                    event.dataTransfer.setData(DND_MIME, dragPayload(member));
                    event.dataTransfer.effectAllowed = 'move';
                    draggedLayerRef.current = {
                      kind: member.kind,
                      backendId: member.backendId,
                      clientId: member.id,
                    };
                  }}
                  onDragEnd={() => {
                    draggedLayerRef.current = null;
                  }}
                  dropFeedbackFor={(event) => {
                    const activeLayer = draggedLayerRef.current;
                    if (activeLayer === null || activeLayer.clientId === member.id) {
                      return null;
                    }
                    return dropPositionAt(
                      event.clientY,
                      event.currentTarget.getBoundingClientRect(),
                    );
                  }}
                  onDrop={(event, feedback) => {
                    const dragged = resolveDragged(event);
                    if (!dragged) return;
                    if (feedback !== 'inside') {
                      dispatch({
                        type: 'reorderElements',
                        draggedId: dragged.parsed.clientId,
                        targetId: member.id,
                        position: feedback,
                      });
                    }
                    onChangeMembership(dragged.element, null);
                    draggedLayerRef.current = null;
                  }}
                  menuItems={membershipEditable ? memberMenu(member) : undefined}
                />
              ))}
            </ul>
          ) : null}
        </div>

        <div className={groupClassName}>
          <LayerGroupHeader
            label="공용 시설"
            expanded={expandedGroups.facilities}
            controls="layout-layer-facilities"
            onToggle={() => toggleGroup('facilities')}
          />
          {expandedGroups.facilities ? (
            <ul id="layout-layer-facilities" className="mt-1">
              {doc.exits.map((exit) =>
                facilityRow(
                  'exit',
                  exit.id,
                  exit.name,
                  selection.exitIds.includes(exit.id),
                  `${exit.name} 선택`,
                  () =>
                    dispatch({
                      type: 'selectAt',
                      wallId: null,
                      outsideWallId: null,
                      exitId: exit.id,
                      textId: null,
                      pillarId: null,
                      fabricId: null,
                      additive: false,
                    }),
                  rectCenter(exit),
                ),
              )}
              {doc.outsideWalls.map((wall) =>
                facilityRow(
                  'outsideWall',
                  wall.id,
                  wall.name,
                  selection.outsideWallIds.includes(wall.id),
                  `${wall.name} 선택`,
                  () =>
                    dispatch({
                      type: 'selectAt',
                      wallId: null,
                      outsideWallId: wall.id,
                      exitId: null,
                      textId: null,
                      pillarId: null,
                      fabricId: null,
                      additive: false,
                    }),
                  rectCenter(wall),
                ),
              )}
              {doc.layoutTexts.map((text) =>
                facilityRow(
                  'text',
                  text.id,
                  text.text.split('\n')[0] || '텍스트',
                  selection.textIds.includes(text.id),
                  `텍스트 ${text.text.slice(0, 10)} 선택`,
                  () =>
                    dispatch({
                      type: 'selectAt',
                      wallId: null,
                      outsideWallId: null,
                      exitId: null,
                      textId: text.id,
                      pillarId: null,
                      fabricId: null,
                      additive: false,
                    }),
                  { x: text.x, y: text.y },
                ),
              )}
            </ul>
          ) : null}
        </div>
      </section>
    );
  },
  (prev, next) =>
    prev.state.doc === next.state.doc &&
    prev.state.selection === next.state.selection &&
    prev.zones === next.zones &&
    prev.selectedZoneId === next.selectedZoneId &&
    prev.employeeNameById === next.employeeNameById &&
    prev.orderLocked === next.orderLocked &&
    prev.membershipEditable === next.membershipEditable &&
    prev.dispatch === next.dispatch &&
    prev.onSelectZone === next.onSelectZone &&
    prev.onChangeMembership === next.onChangeMembership &&
    prev.onGroupSelectionIntoZone === next.onGroupSelectionIntoZone &&
    prev.onMoveZoneOrder === next.onMoveZoneOrder &&
    prev.onCenterPoint === next.onCenterPoint,
);
