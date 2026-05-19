import { describe, it, expect } from 'vitest';
import { EscortDispatchOrderNumberGenerator } from '../../src/domain/order-number-generator.js';

describe('EscortDispatchOrderNumberGenerator', () => {
  const VALID_CHARS = /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]+$/;

  it('should generate a number in the format ESC-YYYYMMDD-XXXXXX', () => {
    const generator = new EscortDispatchOrderNumberGenerator(() => new Date('2026-05-20T12:00:00Z').getTime());
    const orderNumber = generator.generate();

    expect(orderNumber).toMatch(/^ESC-\d{8}-[A-Z0-9]{6}$/);
  });

  it('should use the date from the clock', () => {
    const generator = new EscortDispatchOrderNumberGenerator(() => new Date('2026-01-15T00:00:00Z').getTime());
    const orderNumber = generator.generate();

    expect(orderNumber).toMatch(/^ESC-20260115-[A-Z0-9]{6}$/);
  });

  it('should only contain allowed characters in the suffix', () => {
    const generator = new EscortDispatchOrderNumberGenerator();

    // Generate multiple numbers to check character set consistency
    for (let i = 0; i < 100; i++) {
      const orderNumber = generator.generate();
      const suffix = orderNumber.split('-')[2];
      expect(suffix).toMatch(VALID_CHARS);
    }
  });

  it('should exclude confusing characters (I, O, 0, 1)', () => {
    const generator = new EscortDispatchOrderNumberGenerator();

    for (let i = 0; i < 100; i++) {
      const orderNumber = generator.generate();
      const suffix = orderNumber.split('-')[2];
      expect(suffix).not.toContain('I');
      expect(suffix).not.toContain('O');
      expect(suffix).not.toContain('0');
      expect(suffix).not.toContain('1');
    }
  });

  it('should generate 6-character suffixes', () => {
    const generator = new EscortDispatchOrderNumberGenerator();

    for (let i = 0; i < 100; i++) {
      const orderNumber = generator.generate();
      const suffix = orderNumber.split('-')[2];
      expect(suffix).toHaveLength(6);
    }
  });

  it('should generate unique numbers on repeated calls', () => {
    const generator = new EscortDispatchOrderNumberGenerator();
    const generated = new Set<string>();

    for (let i = 0; i < 1000; i++) {
      const orderNumber = generator.generate();
      generated.add(orderNumber);
    }

    // With 1000 samples on same date, should have high probability of uniqueness
    // due to 6 chars from 30-char alphabet = 30^6 = 729,000,000 combinations
    expect(generated.size).toBeGreaterThan(900);
  });

  it('should accept a custom clock function', () => {
    const mockClock = () => new Date('2025-12-25T10:30:00Z').getTime();
    const generator = new EscortDispatchOrderNumberGenerator(mockClock);

    const orderNumber = generator.generate();
    expect(orderNumber).toMatch(/^ESC-20251225-[A-Z0-9]{6}$/);
  });
});
