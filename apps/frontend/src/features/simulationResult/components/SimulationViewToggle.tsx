import type { SimulationViewMode } from '../rendering/simulationViewMode';
import './SimulationViewToggle.css';

interface Props {
  mode: SimulationViewMode;
  onChange: (mode: SimulationViewMode) => void;
}

export function SimulationViewToggle({ mode, onChange }: Props) {
  return (
    <div className="simulation-view-control">
      <div className="simulation-view-toggle" aria-label="시뮬레이션 보기 방식">
        {(['plan', 'three'] as const).map((value) => (
          <button
            key={value}
            type="button"
            aria-pressed={mode === value}
            onClick={() => onChange(value)}
          >
            {value === 'plan' ? '2D' : '3D'}
          </button>
        ))}
      </div>
      {mode === 'three' && <span>드래그해 회전하고 휠로 확대할 수 있습니다.</span>}
    </div>
  );
}
