import { ChevronDown, ChevronUp } from 'lucide-react';
import { Input } from '../../../components/ui';

interface NumberStepperInputProps {
  id?: string;
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  disabled?: boolean;
  onValueChange: (value: number) => void;
}

function getDecimalPlaces(value: number) {
  const [, decimals = ''] = value.toString().split('.');
  return decimals.length;
}

export function normalizeNumberInputText(value: string) {
  return value.replace(/^0+(?=\d)/, '');
}

export function NumberStepperInput({
  id,
  label,
  value,
  min,
  max,
  step,
  disabled = false,
  onValueChange,
}: NumberStepperInputProps) {
  const adjustValue = (direction: 1 | -1) => {
    const precision = getDecimalPlaces(step);
    const nextValue = Math.min(max, Math.max(min, value + step * direction));
    onValueChange(Number(nextValue.toFixed(precision)));
  };

  return (
    <div className="simulation-number-stepper">
      <Input
        id={id}
        type="number"
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={disabled}
        aria-label={id ? undefined : label}
        onChange={(event) => {
          const normalizedValue = normalizeNumberInputText(event.currentTarget.value);
          if (normalizedValue !== event.currentTarget.value) {
            event.currentTarget.value = normalizedValue;
          }
          onValueChange(Number(normalizedValue));
        }}
        className="simulation-number-stepper__input tabular-nums"
      />
      <div className="simulation-number-stepper__controls" aria-hidden={disabled || undefined}>
        <button
          type="button"
          aria-label={`${label} 증가`}
          disabled={disabled || value >= max}
          onClick={() => adjustValue(1)}
        >
          <ChevronUp aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label={`${label} 감소`}
          disabled={disabled || value <= min}
          onClick={() => adjustValue(-1)}
        >
          <ChevronDown aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}
