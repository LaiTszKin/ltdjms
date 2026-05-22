import { describe, it, expect, vi } from 'vitest';
import {
  EscortDispatchOrderNumberGenerator,
  generateUniqueOrderNumber,
} from '../../domain/order-number-generator.js';

describe('EscortDispatchOrderNumberGenerator', () => {
  describe('generate', () => {
    it('should produce a number matching ESC-YYYYMMDD-XXXXXX format', () => {
      // Use fixed clock: 2026-05-21 12:00:00 UTC
      const fixedClock = () => new Date('2026-05-21T12:00:00Z').getTime();
      const generator = new EscortDispatchOrderNumberGenerator(fixedClock);

      const number = generator.generate();
      expect(number).toMatch(/^ESC-20260521-[A-Z0-9]{6}$/);
    });

    it('should use the provided clock function for date part', () => {
      // 2026-01-01
      const janClock = () => new Date('2026-01-01T00:00:00Z').getTime();
      const generator = new EscortDispatchOrderNumberGenerator(janClock);

      const number = generator.generate();
      expect(number).toMatch(/^ESC-20260101-[A-Z0-9]{6}$/);
    });

    it('should use the provided random function for suffix', () => {
      // Deterministic random: always returns 0, so all chars will be 'A'
      const fixedClock = () => new Date('2026-05-21T12:00:00Z').getTime();
      const deterministicRandom = () => 0;
      const generator = new EscortDispatchOrderNumberGenerator(fixedClock, deterministicRandom);

      const number = generator.generate();
      expect(number).toBe('ESC-20260521-AAAAAA');
    });

    it('should only use valid alphanumeric chars (excludes I, O, 0, 1)', () => {
      const fixedClock = () => new Date('2026-05-21T12:00:00Z').getTime();
      // Return each index to produce every possible char
      let callCount = 0;
      const rotatingRandom = (_min: number, max: number) => {
        const idx = callCount % max;
        callCount++;
        return idx;
      };

      const generator = new EscortDispatchOrderNumberGenerator(fixedClock, rotatingRandom);

      // Collect all unique chars generated
      const chars = new Set<string>();
      for (let i = 0; i < 5; i++) {
        const num = generator.generate();
        for (const ch of num.slice(-6)) {
          chars.add(ch);
        }
      }

      // Verify no excluded characters appear
      const validSet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
      for (const ch of chars) {
        expect(validSet).toContain(ch);
      }
      // Verify excluded chars are not present
      expect(chars.has('I')).toBe(false);
      expect(chars.has('O')).toBe(false);
      expect(chars.has('0')).toBe(false);
      expect(chars.has('1')).toBe(false);
    });

    it('should produce unique numbers on successive calls', () => {
      const fixedClock = () => new Date('2026-05-21T12:00:00Z').getTime();
      const generator = new EscortDispatchOrderNumberGenerator(fixedClock);

      const numbers = new Set<string>();
      for (let i = 0; i < 100; i++) {
        numbers.add(generator.generate());
      }

      // With 6 random chars from 30 char set, 100 iterations should all be unique
      expect(numbers.size).toBe(100);
    });
  });
});

describe('generateUniqueOrderNumber', () => {
  it('should return a unique order number when no collision', async () => {
    const generator = new EscortDispatchOrderNumberGenerator(() =>
      new Date('2026-05-21T12:00:00Z').getTime(),
    );
    // No collision: always returns false
    const existsFn = vi.fn().mockResolvedValue(false);

    const number = await generateUniqueOrderNumber(generator, existsFn);
    expect(number).toMatch(/^ESC-20260521-[A-Z0-9]{6}$/);
    expect(existsFn).toHaveBeenCalledTimes(1);
  });

  it('should retry on collision and succeed', async () => {
    const generator = new EscortDispatchOrderNumberGenerator(() =>
      new Date('2026-05-21T12:00:00Z').getTime(),
    );
    // First call returns true (collision), second returns false (success)
    const existsFn = vi.fn().mockResolvedValueOnce(true).mockResolvedValueOnce(false);

    const number = await generateUniqueOrderNumber(generator, existsFn);
    expect(number).toMatch(/^ESC-20260521-[A-Z0-9]{6}$/);
    expect(existsFn).toHaveBeenCalledTimes(2);
  });

  it('should throw when max retries exhausted', async () => {
    const generator = new EscortDispatchOrderNumberGenerator(() =>
      new Date('2026-05-21T12:00:00Z').getTime(),
    );
    // Always collides
    const existsFn = vi.fn().mockResolvedValue(true);

    await expect(generateUniqueOrderNumber(generator, existsFn, 3)).rejects.toThrow(
      'Unable to generate unique order number after retries',
    );
    expect(existsFn).toHaveBeenCalledTimes(3);
  });

  it('should handle custom max retries', async () => {
    const generator = new EscortDispatchOrderNumberGenerator(() =>
      new Date('2026-05-21T12:00:00Z').getTime(),
    );
    const existsFn = vi.fn().mockResolvedValue(true);

    // With 5 retries, should fail after 5 attempts
    await expect(generateUniqueOrderNumber(generator, existsFn, 5)).rejects.toThrow(
      'Unable to generate unique order number',
    );
    expect(existsFn).toHaveBeenCalledTimes(5);
  });

  it('should propagate exceptions from existsFn', async () => {
    const generator = new EscortDispatchOrderNumberGenerator(() =>
      new Date('2026-05-21T12:00:00Z').getTime(),
    );
    const existsFn = vi.fn().mockRejectedValue(new Error('DB error'));

    await expect(generateUniqueOrderNumber(generator, existsFn)).rejects.toThrow('DB error');
  });
});
