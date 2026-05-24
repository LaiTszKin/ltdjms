import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { PermissionFlagsBits, type Guild, type GuildMember } from 'discord.js';
import { ToolExecutionContext } from '../ToolExecutionContext.js';
import { ToolCallerAuthorizationGuard } from '../ToolCallerAuthorizationGuard.js';

/** UT-AG-025 — ToolCallerAuthorizationGuardTest.java */
describe('UT-AG-025 authorization guard parity', () => {
  const TEST_USER_ID = '222222222222222222';
  let guard: ToolCallerAuthorizationGuard;

  beforeEach(() => {
    guard = new ToolCallerAuthorizationGuard();
    ToolExecutionContext.run({ guildId: '1', channelId: '2', userId: TEST_USER_ID }, () => undefined);
  });

  afterEach(() => {
    // context cleared automatically after run scope
  });

  function mockGuild(options: {
    ownerId?: string;
    admin?: boolean;
    memberInCache?: boolean;
    fetchAdmin?: boolean;
    fetchThrows?: boolean;
  }): Guild {
    const member = {
      permissions: {
        has: vi.fn((perm: bigint) =>
          perm === PermissionFlagsBits.Administrator ? (options.admin ?? false) : false,
        ),
      },
    } as unknown as GuildMember;

    return {
      id: '123456789012345678',
      ownerId: options.ownerId ?? '999999999999999999',
      members: {
        cache: {
          has: vi.fn(() => options.memberInCache ?? true),
          get: vi.fn(() => (options.memberInCache ?? true ? member : undefined)),
        },
        fetch: vi.fn(async () => {
          if (options.fetchThrows) {
            throw new Error('api down');
          }
          if (options.fetchAdmin) {
            return { permissions: { has: () => true } } as GuildMember;
          }
          return member;
        }),
      },
    } as unknown as Guild;
  }

  it('rejects non-admin caller', async () => {
    await ToolExecutionContext.run({ guildId: '1', channelId: '2', userId: TEST_USER_ID }, async () => {
      const error = await guard.validateAdministrator(mockGuild({ admin: false }), 'TestTool');
      expect(error).toBe('你沒有權限使用此工具。');
    });
  });

  it('allows admin caller', async () => {
    await ToolExecutionContext.run({ guildId: '1', channelId: '2', userId: TEST_USER_ID }, async () => {
      const error = await guard.validateAdministrator(mockGuild({ admin: true }), 'TestTool');
      expect(error).toBeNull();
    });
  });

  it('allows member retrieved from API', async () => {
    await ToolExecutionContext.run({ guildId: '1', channelId: '2', userId: TEST_USER_ID }, async () => {
      const error = await guard.validateAdministrator(
        mockGuild({ memberInCache: false, fetchAdmin: true }),
        'TestTool',
      );
      expect(error).toBeNull();
    });
  });

  it('rejects when context missing userId', async () => {
    const error = await guard.validateAdministrator(mockGuild({ admin: true }), 'TestTool');
    expect(error).toContain('上下文');
  });

  it('rejects when member retrieval fails', async () => {
    await ToolExecutionContext.run({ guildId: '1', channelId: '2', userId: TEST_USER_ID }, async () => {
      const error = await guard.validateAdministrator(
        mockGuild({ memberInCache: false, fetchThrows: true }),
        'TestTool',
      );
      expect(error).toContain('成員');
    });
  });

  it('allows guild owner without admin permission', async () => {
    await ToolExecutionContext.run({ guildId: '1', channelId: '2', userId: TEST_USER_ID }, async () => {
      const error = await guard.validateAdministrator(
        mockGuild({ ownerId: TEST_USER_ID, admin: false }),
        'TestTool',
      );
      expect(error).toBeNull();
    });
  });
});
