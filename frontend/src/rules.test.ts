import { describe, expect, it } from 'vitest';
import { isDanger, reasonRequired, transitionLabel } from './rules';

describe('reasonRequired', () => {
  it('asks for a reason on the same moves the API does', () => {
    expect(reasonRequired('IN_REVIEW', 'NEEDS_MORE_INFO')).toBe(true);
    expect(reasonRequired('IN_REVIEW', 'APPROVED')).toBe(true);
    expect(reasonRequired('IN_REVIEW', 'REJECTED')).toBe(true);
    expect(reasonRequired('LIVE', 'SUSPENDED')).toBe(true);
    expect(reasonRequired('SUSPENDED', 'LIVE')).toBe(true);
  });

  it('does not ask for one on routine moves', () => {
    expect(reasonRequired('SUBMITTED', 'IN_REVIEW')).toBe(false);
    expect(reasonRequired('NEEDS_MORE_INFO', 'IN_REVIEW')).toBe(false);
    expect(reasonRequired('APPROVED', 'LIVE')).toBe(false);
  });
});

describe('transitionLabel', () => {
  it('words the same target differently depending on where it came from', () => {
    expect(transitionLabel('SUBMITTED', 'IN_REVIEW')).toBe('Start review');
    expect(transitionLabel('NEEDS_MORE_INFO', 'IN_REVIEW')).toBe('Resume review');
    expect(transitionLabel('APPROVED', 'LIVE')).toBe('Go live');
    expect(transitionLabel('SUSPENDED', 'LIVE')).toBe('Reinstate');
  });

  it('marks rejecting and suspending as dangerous', () => {
    expect(isDanger('REJECTED')).toBe(true);
    expect(isDanger('SUSPENDED')).toBe(true);
    expect(isDanger('APPROVED')).toBe(false);
  });
});
