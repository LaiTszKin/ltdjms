import { describe, it, expect, vi } from 'vitest';
import { safeSnowflakeToNumber } from '../utils/snowflake.js';

describe('safeSnowflakeToNumber', () => {
  it('should convert small snowflakes within safe integer range', () => {
    const result = safeSnowflakeToNumber('12345');
    expect(result).toBe(12345);
  });

  it('should convert snowflakes at MAX_SAFE_INTEGER boundary', () => {
    const maxSafe = String(Number.MAX_SAFE_INTEGER);
    expect(safeSnowflakeToNumber(maxSafe)).toBe(Number.MAX_SAFE_INTEGER);
  });

  it('should warn (not throw) for snowflakes exceeding safe integer range', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const largeSnowflake = '1444689832226980012';

    const result = safeSnowflakeToNumber(largeSnowflake);

    // Should still return a number (with precision loss)
    expect(typeof result).toBe('number');
    expect(result).not.toBeNaN();
    // Warning should have been issued
    expect(warnSpy).toHaveBeenCalledTimes(1);
    expect(warnSpy.mock.calls[0][0]).toContain('loses precision');

    warnSpy.mockRestore();
  });

  it('should warn for typical 19-digit Discord snowflakes', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const typicalSnowflake = '588344488624259073';

    safeSnowflakeToNumber(typicalSnowflake);

    expect(warnSpy).toHaveBeenCalledTimes(1);
    expect(warnSpy.mock.calls[0][0]).toContain('loses precision');

    warnSpy.mockRestore();
  });
});
