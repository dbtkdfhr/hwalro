import type { SimulationDrawing } from '../../simulations/types';

interface DrawingElementsProps {
  drawing: SimulationDrawing;
  changedFabricIds?: ReadonlySet<number>;
}

export function DrawingElements({ drawing, changedFabricIds }: DrawingElementsProps) {
  return (
    <>
      <rect width={drawing.width} height={drawing.height} className="plan-preview__floor" />
      {drawing.outsideBoundary.length > 2 && (
        <polygon
          points={drawing.outsideBoundary.map((point) => `${point.x},${point.y}`).join(' ')}
          className="plan-preview__boundary"
        />
      )}
      {drawing.walls.map((wall) => (
        <line
          key={`${wall.name}-${wall.startX}-${wall.startY}`}
          x1={wall.startX}
          y1={wall.startY}
          x2={wall.endX}
          y2={wall.endY}
          className="plan-preview__wall"
        />
      ))}
      {drawing.pillars.map((pillar) => (
        <rect
          key={`${pillar.name}-${pillar.startX}-${pillar.startY}`}
          x={pillar.startX}
          y={pillar.startY}
          width={pillar.endX - pillar.startX}
          height={pillar.endY - pillar.startY}
          transform={`rotate(${pillar.rotation} ${(pillar.startX + pillar.endX) / 2} ${(pillar.startY + pillar.endY) / 2})`}
          className="plan-preview__pillar"
        />
      ))}
      {drawing.fabrics.map((fabric) => (
        <rect
          key={fabric.id}
          x={fabric.startX}
          y={fabric.startY}
          width={fabric.endX - fabric.startX}
          height={fabric.endY - fabric.startY}
          transform={`rotate(${fabric.rotation} ${(fabric.startX + fabric.endX) / 2} ${(fabric.startY + fabric.endY) / 2})`}
          className={
            changedFabricIds?.has(fabric.id)
              ? 'plan-preview__fabric is-changed'
              : 'plan-preview__fabric'
          }
        />
      ))}
      {drawing.exits.map((exit) => (
        <line
          key={exit.id}
          x1={exit.startX}
          y1={exit.startY}
          x2={exit.endX}
          y2={exit.endY}
          className="plan-preview__exit-line"
        />
      ))}
    </>
  );
}

interface Props {
  drawing: SimulationDrawing;
  label: string;
  changedFabricIds?: ReadonlySet<number>;
}

export function LayoutDiffCanvas({ drawing, label, changedFabricIds }: Props) {
  return (
    <figure className="plan-preview">
      <div className="plan-preview__viewport">
        <svg viewBox={`0 0 ${drawing.width} ${drawing.height}`} role="img" aria-label={label}>
          <DrawingElements drawing={drawing} changedFabricIds={changedFabricIds} />
        </svg>
      </div>
      <figcaption>{label}</figcaption>
    </figure>
  );
}
