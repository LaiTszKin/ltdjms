import { describe, it, expect } from 'vitest';
import { MockDiscordContext } from '../mock-discord-context.js';

describe('MockDiscordContext', () => {
  it('returns configured guild/user/channel ids', () => {
    const ctx = new MockDiscordContext('123', '456', '789', '<@456>');
    expect(ctx.getGuildId()).toBe('123');
    expect(ctx.getUserId()).toBe('456');
    expect(ctx.getChannelId()).toBe('789');
    expect(ctx.getUserMention()).toBe('<@456>');
  });

  it('validates positive ids', () => {
    expect(() => new MockDiscordContext('', '456', '789', '<@456>')).toThrow(
      'guildId must be a valid non-zero id',
    );
    expect(() => new MockDiscordContext('123', '', '789', '<@456>')).toThrow(
      'userId must be a valid non-zero id',
    );
    expect(() => new MockDiscordContext('123', '456', '', '<@456>')).toThrow(
      'channelId must be a valid non-zero id',
    );
  });

  it('validates non-empty user mention', () => {
    expect(() => new MockDiscordContext('123', '456', '789', '')).toThrow(
      'userMention must not be empty',
    );
  });

  it('getOption returns null for unknown option', () => {
    const ctx = new MockDiscordContext(1, 2, 3, '<@2>');
    expect(ctx.getOption('unknown')).toBeNull();
  });

  it('setOption/getOption roundtrip', () => {
    const ctx = new MockDiscordContext(1, 2, 3, '<@2>');
    ctx.setOption('amount', 100);
    expect(ctx.getOption('amount')).toBe('100');
    expect(ctx.getOptionAsNumber('amount')).toBe(100);
  });

  it('getOptionAsString works', () => {
    const ctx = new MockDiscordContext(1, 2, 3, '<@2>');
    ctx.setOption('name', 'Alice');
    expect(ctx.getOptionAsString('name')).toBe('Alice');
  });

  it('clearOption removes option', () => {
    const ctx = new MockDiscordContext(1, 2, 3, '<@2>');
    ctx.setOption('amount', 50);
    expect(ctx.hasOption('amount')).toBe(true);
    ctx.clearOption('amount');
    expect(ctx.hasOption('amount')).toBe(false);
    expect(ctx.getOptionCount()).toBe(0);
  });

  it('clearAllOptions removes all options', () => {
    const ctx = new MockDiscordContext(1, 2, 3, '<@2>');
    ctx.setOption('a', '1');
    ctx.setOption('b', '2');
    expect(ctx.getOptionCount()).toBe(2);
    ctx.clearAllOptions();
    expect(ctx.getOptionCount()).toBe(0);
  });
});
