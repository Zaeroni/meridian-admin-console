import { describe, expect, it } from 'vitest';
import { formatHours, formatMoney, shortDay } from './format';

describe('formatHours', () => {
  it('formats short, day and mixed waits', () => {
    expect(formatHours(null)).toBe('');
    expect(formatHours(0)).toBe('<1h');
    expect(formatHours(5)).toBe('5h');
    expect(formatHours(24)).toBe('1d');
    expect(formatHours(30)).toBe('1d 6h');
    expect(formatHours(72)).toBe('3d');
  });
});

describe('chart helpers', () => {
  it('shortens ISO dates without padding', () => {
    expect(shortDay('2026-11-06')).toBe('11/6');
    expect(shortDay('2026-01-16')).toBe('1/16');
  });

  it('formats cents as dollars', () => {
    expect(formatMoney(153345)).toContain('1,533.45');
  });
});
