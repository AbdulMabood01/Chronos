import { describe, it, expect } from 'vitest';
import { formatDate } from './dates';

describe('calendar date formatting', () => {
  it('preserves calendar dates west of UTC', () => {
    process.env.TZ = 'America/Chicago';
    expect(formatDate('2026-09-10')).toBe('Sep 10, 2026');
    expect(formatDate('2026-03-08')).toBe('Mar 08, 2026');
  });
  it('honors the offset on timestamps', () => {
    process.env.TZ = 'America/Chicago';
    expect(formatDate('2026-09-10T01:00:00Z')).toBe('Sep 09, 2026');
  });
  it('handles missing or invalid values', () => {
    expect(formatDate(null)).toBe('-');
    expect(formatDate('invalid')).toBe('-');
  });
});
