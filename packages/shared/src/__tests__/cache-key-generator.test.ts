import { describe, it, expect } from 'vitest';
import { DefaultCacheKeyGenerator } from '../infra/cache/cache-key-generator.js';

describe('DefaultCacheKeyGenerator', () => {
  const generator = new DefaultCacheKeyGenerator();

  it('balanceKey returns cache:balance:{guildId}:{userId}', () => {
    const key = generator.balanceKey('123', '456');
    expect(key).toBe('cache:balance:123:456');
  });

  it('gameTokenKey returns cache:gametoken:{guildId}:{userId}', () => {
    const key = generator.gameTokenKey('123', '456');
    expect(key).toBe('cache:gametoken:123:456');
  });

  it('balanceKey handles snowflake-length IDs', () => {
    const guildId = '123456789012345678';
    const userId = '876543210987654321';
    const key = generator.balanceKey(guildId, userId);
    expect(key).toBe(`cache:balance:${guildId}:${userId}`);
  });

  it('gameTokenKey handles snowflake-length IDs', () => {
    const guildId = '123456789012345678';
    const userId = '876543210987654321';
    const key = generator.gameTokenKey(guildId, userId);
    expect(key).toBe(`cache:gametoken:${guildId}:${userId}`);
  });

  it('balanceKey and gameTokenKey produce distinct keys for same IDs', () => {
    const balanceKey = generator.balanceKey('1', '2');
    const tokenKey = generator.gameTokenKey('1', '2');
    expect(balanceKey).not.toBe(tokenKey);
    expect(balanceKey).toContain('balance');
    expect(tokenKey).toContain('gametoken');
  });
});
