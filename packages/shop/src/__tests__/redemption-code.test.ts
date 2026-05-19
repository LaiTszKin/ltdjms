import { describe, it, expect } from 'vitest';
import {
  createRedemptionCode,
  withRedeemed,
  isRedeemed,
  isExpired,
  isValid,
  isInvalidated,
  withInvalidated,
  belongsToGuild,
  getMaskedCode,
} from '../domain/redemption-code.js';

describe('RedemptionCode', () => {
  it('should create a new code', () => {
    const code = createRedemptionCode('ABC123XYZ789', 1, 123, null);
    expect(code.code).toBe('ABC123XYZ789');
    expect(code.productId).toBe(1);
    expect(code.guildId).toBe(123);
    expect(code.quantity).toBe(1);
    expect(code.id).toBeNull();
    expect(code.redeemedBy).toBeNull();
    expect(code.invalidatedAt).toBeNull();
  });

  it('should uppercase the code', () => {
    const code = createRedemptionCode('abc123', 1, 123, null);
    expect(code.code).toBe('ABC123');
  });

  it('should mark as redeemed', () => {
    const code = createRedemptionCode('TESTCODE', 1, 123, null);
    const redeemed = withRedeemed(code, 456);
    expect(isRedeemed(redeemed)).toBe(true);
    expect(redeemed.redeemedBy).toBe(456);
    expect(redeemed.redeemedAt).toBeInstanceOf(Date);
  });

  it('should throw when redeeming already redeemed code', () => {
    const code = createRedemptionCode('TESTCODE', 1, 123, null);
    const redeemed = withRedeemed(code, 456);
    expect(() => withRedeemed(redeemed, 789)).toThrow('already been redeemed');
  });

  it('should detect expired code', () => {
    const past = new Date(Date.now() - 86400000);
    const code = createRedemptionCode('TESTCODE', 1, 123, past);
    expect(isExpired(code)).toBe(true);
  });

  it('should detect non-expired code', () => {
    const future = new Date(Date.now() + 86400000);
    const code = createRedemptionCode('TESTCODE', 1, 123, future);
    expect(isExpired(code)).toBe(false);
  });

  it('should consider code with no expiry as not expired', () => {
    const code = createRedemptionCode('TESTCODE', 1, 123, null);
    expect(isExpired(code)).toBe(false);
  });

  it('should check validity', () => {
    const code = createRedemptionCode('TESTCODE', 1, 123, null);
    expect(isValid(code)).toBe(true);

    const redeemed = withRedeemed(code, 456);
    expect(isValid(redeemed)).toBe(false);
  });

  it('should invalidate code', () => {
    const code = createRedemptionCode('TESTCODE', 1, 123, null);
    const invalidated = withInvalidated(code);
    expect(isInvalidated(invalidated)).toBe(true);
    expect(invalidated.productId).toBeNull(); // productId set to null on invalidation
  });

  it('should throw when invalidating already invalidated code', () => {
    const code = createRedemptionCode('TESTCODE', 1, 123, null);
    const invalidated = withInvalidated(code);
    expect(() => withInvalidated(invalidated)).toThrow('already been invalidated');
  });

  it('should check guild ownership', () => {
    const code = createRedemptionCode('TESTCODE', 1, 123, null);
    expect(belongsToGuild(code, 123)).toBe(true);
    expect(belongsToGuild(code, 456)).toBe(false);
  });

  it('should show masked code', () => {
    const code = createRedemptionCode('ABCDEFGHIJKLMNOP', 1, 123, null);
    expect(getMaskedCode(code)).toBe('ABCD****MNOP');
  });

  it('should show unmasked code if too short', () => {
    const code = createRedemptionCode('ABCDEFGH', 1, 123, null);
    expect(getMaskedCode(code)).toBe('ABCDEFGH');
  });

  it('should fail validation with negative quantity', () => {
    expect(() => createRedemptionCode('TESTCODE', 1, 123, null, -1)).toThrow();
  });

  it('should fail validation with quantity exceeding 1000', () => {
    expect(() => createRedemptionCode('TESTCODE', 1, 123, null, 1001)).toThrow();
  });
});
