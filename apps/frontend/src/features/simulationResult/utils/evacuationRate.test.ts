import { describe, expect, it } from 'vitest';
import { calculateEvacuationRate } from './evacuationRate';

describe('calculateEvacuationRate', () => {
  it.each([
    { evacuatedPeople: 0, totalPeople: 0, expected: 0 },
    { evacuatedPeople: 50, totalPeople: 101, expected: 50 },
    { evacuatedPeople: 4_997, totalPeople: 5_000, expected: 99 },
    { evacuatedPeople: 4_998, totalPeople: 5_000, expected: 99 },
    { evacuatedPeople: 5_000, totalPeople: 5_000, expected: 100 },
    { evacuatedPeople: -1, totalPeople: 100, expected: 0 },
    { evacuatedPeople: 101, totalPeople: 100, expected: 100 },
  ])(
    'returns $expected% for $evacuatedPeople of $totalPeople people',
    ({ evacuatedPeople, totalPeople, expected }) => {
      expect(calculateEvacuationRate(evacuatedPeople, totalPeople)).toBe(expected);
    },
  );
});
