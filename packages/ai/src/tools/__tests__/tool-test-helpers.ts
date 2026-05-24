import { vi } from 'vitest';
import { ChannelType, PermissionFlagsBits, type Guild, type GuildMember } from 'discord.js';
import { ToolExecutionContext } from '../ToolExecutionContext.js';
import { ToolCallerAuthorizationGuard } from '../ToolCallerAuthorizationGuard.js';

export const TEST_GUILD_ID = '123456789012345678';
export const TEST_CHANNEL_ID = '987654321098765432';
export const TEST_USER_ID = '222222222222222222';
export const TEST_ROLE_ID = '111111111111111111';

export function setupToolContext(): void {
  ToolExecutionContext.run(
    { guildId: TEST_GUILD_ID, channelId: TEST_CHANNEL_ID, userId: TEST_USER_ID },
    () => undefined,
  );
}

export function withToolContext<T>(fn: () => T | Promise<T>): T | Promise<T> {
  return ToolExecutionContext.run(
    { guildId: TEST_GUILD_ID, channelId: TEST_CHANNEL_ID, userId: TEST_USER_ID },
    fn,
  );
}

function createCacheLike(entries: Array<[string, unknown]>) {
  const map = new Map(entries);
  return Object.assign(map, {
    filter: (predicate: (value: unknown, key: string) => boolean) =>
      [...map.entries()].filter(([key, value]) => predicate(value, key)).map(([, value]) => value),
    map: <T>(mapper: (value: unknown) => T) => [...map.values()].map(mapper),
  });
}

export function createMockGuild(options?: {
  ownerId?: string;
  admin?: boolean;
  memberFetchFails?: boolean;
}): Guild {
  const member = {
    permissions: {
      has: vi.fn((perm: bigint) =>
        perm === PermissionFlagsBits.Administrator ? (options?.admin ?? true) : false,
      ),
    },
  } as unknown as GuildMember;

  const guild = {
    id: TEST_GUILD_ID,
    ownerId: options?.ownerId ?? '999999999999999999',
    members: {
      cache: {
        has: vi.fn((id: string) => id === TEST_USER_ID && !options?.memberFetchFails),
        get: vi.fn((id: string) => (id === TEST_USER_ID ? member : undefined)),
      },
      fetch: vi.fn(async (id: string) => {
        if (options?.memberFetchFails) {
          throw new Error('Member not found');
        }
        if (id === TEST_USER_ID) {
          return member;
        }
        throw new Error('Member not found');
      }),
    },
    channels: {
      cache: createCacheLike([]),
      create: vi.fn(),
    },
    roles: {
      cache: createCacheLike([]),
      create: vi.fn(),
    },
  } as unknown as Guild;

  return guild;
}

export function createMockAuthGuard(options?: { admin?: boolean }): ToolCallerAuthorizationGuard {
  const guard = new ToolCallerAuthorizationGuard();
  vi.spyOn(guard, 'validateAdministrator').mockImplementation(async (guild, toolName) => {
    if (options?.admin === false) {
      return '你沒有權限使用此工具。';
    }
    void guild;
    void toolName;
    return null;
  });
  return guard;
}

export function createTextChannelMock(name: string, id = TEST_CHANNEL_ID) {
  return {
    id,
    name,
    type: ChannelType.GuildText,
    parentId: null,
    permissionOverwrites: { cache: new Map(), edit: vi.fn() },
    delete: vi.fn(),
    send: vi.fn(),
  };
}
