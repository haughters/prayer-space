import { describe, it, expect } from 'vitest';
import { formatDate, validatePrayerText } from './utils';

describe('formatDate', () => {
  it('formats ISO date strings correctly', () => {
    const formatted = formatDate('2026-07-03T15:11:42.604595Z');
    expect(formatted).toBe('Jul 3, 2026');
  });

  it('returns empty string for null/undefined/invalid inputs', () => {
    expect(formatDate(null)).toBe('');
    expect(formatDate(undefined)).toBe('');
    expect(formatDate('invalid-date')).toBe('');
  });
});

describe('validatePrayerText', () => {
  it('validates correct prayer text length', () => {
    expect(validatePrayerText('This is a valid prayer length.')).toBe(true);
  });

  it('rejects short prayer text', () => {
    expect(validatePrayerText('Short')).toBe(false);
  });

  it('rejects empty or null text', () => {
    expect(validatePrayerText(null)).toBe(false);
    expect(validatePrayerText('')).toBe(false);
  });

  it('rejects extremely long text', () => {
    const longText = 'a'.repeat(2001);
    expect(validatePrayerText(longText)).toBe(false);
  });
});
