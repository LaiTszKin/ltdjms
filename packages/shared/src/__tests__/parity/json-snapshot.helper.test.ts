import { describe, it, expect } from 'vitest';
import {
  assertEmbedParity,
  assertJsonParity,
  normalizeEmbedForSnapshot,
  normalizeValue,
} from './json-snapshot.js';

/** UT-ED-001: JSON snapshot parity helper */
describe('json-snapshot helper (UT-ED-001)', () => {
  it('normalizeEmbedForSnapshot strips volatile embed fields and sorts keys', () => {
    const embed = {
      description: 'Hello',
      title: 'Shop',
      timestamp: '2026-05-24T00:00:00.000Z',
      color: 0x5865f2,
      fields: [{ name: 'Price', value: '100', inline: true }],
    };

    expect(normalizeEmbedForSnapshot(embed)).toEqual({
      color: 0x5865f2,
      description: 'Hello',
      fields: [{ inline: true, name: 'Price', value: '100' }],
      title: 'Shop',
    });
  });

  it('normalizeValue sorts object keys recursively', () => {
    expect(normalizeValue({ b: 2, a: { d: 4, c: 3 } })).toEqual({
      a: { c: 3, d: 4 },
      b: 2,
    });
  });

  it('assertJsonParity passes for structurally equal oracle payloads', () => {
    const oracle = { buttons: { rows: [[{ customId: 'shop_buy', label: 'Buy' }]] } };
    const actual = { buttons: { rows: [[{ label: 'Buy', customId: 'shop_buy' }]] } };

    expect(() => assertJsonParity(actual, oracle)).not.toThrow();
  });

  it('assertJsonParity fails when oracle differs', () => {
    expect(() => assertJsonParity({ id: 'a' }, { id: 'b' })).toThrow();
  });

  it('assertEmbedParity compares normalized embeds', () => {
    const oracle = { title: '🏪 商店', description: 'empty', color: 5793266 };
    const actual = {
      title: '🏪 商店',
      description: 'empty',
      color: 5793266,
      timestamp: 'ignored',
    };

    expect(() => assertEmbedParity(actual, oracle)).not.toThrow();
  });
});
