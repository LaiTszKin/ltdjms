import { describe, it, expect, vi } from 'vitest';
import { ChannelType } from 'discord.js';
import { CreateChannelTool } from '../CreateChannelTool.js';
import { CreateCategoryTool } from '../CreateCategoryTool.js';
import { CreateRoleTool } from '../CreateRoleTool.js';
import { ListChannelsTool } from '../ListChannelsTool.js';
import { ListCategoriesTool } from '../ListCategoriesTool.js';
import { ListRolesTool } from '../ListRolesTool.js';
import { GetChannelPermissionsTool } from '../GetChannelPermissionsTool.js';
import { GetCategoryPermissionsTool } from '../GetCategoryPermissionsTool.js';
import { GetRolePermissionsTool } from '../GetRolePermissionsTool.js';
import { ModifyChannelPermissionsTool } from '../ModifyChannelPermissionsTool.js';
import { ModifyCategoryPermissionsTool } from '../ModifyCategoryPermissionsTool.js';
import { ModifyRolePermissionsTool } from '../ModifyRolePermissionsTool.js';
import { SendMessagesTool } from '../SendMessagesTool.js';
import { SearchMessagesTool } from '../SearchMessagesTool.js';
import { ManageMessageTool } from '../ManageMessageTool.js';
import { MoveChannelTool } from '../MoveChannelTool.js';
import { DeleteDiscordResourceTool } from '../DeleteDiscordResourceTool.js';
import { PermissionParser } from '../PermissionParser.js';
import {
  TEST_CHANNEL_ID,
  TEST_GUILD_ID,
  TEST_ROLE_ID,
  TEST_USER_ID,
  createMockAuthGuard,
  createMockGuild,
  createTextChannelMock,
  withToolContext,
} from './tool-test-helpers.js';

const permissionParser = new PermissionParser();

/** UT-AG-001 — LangChain4jCreateChannelToolTest.java */
describe('UT-AG-001 create-channel tool parity', () => {
  it('creates channel successfully', async () => {
    const guild = createMockGuild();
    const created = createTextChannelMock('test-channel');
    vi.mocked(guild.channels.create).mockResolvedValue(created as never);

    const tool = new CreateChannelTool(createMockAuthGuard(), permissionParser);
    const result = await withToolContext(() => tool.execute({ name: 'test-channel' }, guild));

    expect(result).toContain('test-channel');
    expect(result).toContain(created.id);
  });

  it('rejects unauthorized caller', async () => {
    const tool = new CreateChannelTool(createMockAuthGuard({ admin: false }), permissionParser);
    const result = await withToolContext(() => tool.execute({ name: 'test' }, createMockGuild()));
    expect(result).toBe('你沒有權限使用此工具。');
  });
});

/** UT-AG-002 */
describe('UT-AG-002 create-category tool parity', () => {
  it('creates category successfully', async () => {
    const guild = createMockGuild();
    const created = { id: 'cat-1', name: 'cat', type: ChannelType.GuildCategory };
    vi.mocked(guild.channels.create).mockResolvedValue(created as never);
    const tool = new CreateCategoryTool(createMockAuthGuard(), permissionParser);
    const result = await withToolContext(() => tool.execute({ name: 'cat' }, guild));
    expect(result).toContain('cat');
  });
});

/** UT-AG-003 */
describe('UT-AG-003 create-role tool parity', () => {
  it('creates role successfully', async () => {
    const guild = createMockGuild();
    vi.mocked(guild.roles.create).mockResolvedValue({ id: TEST_ROLE_ID, name: 'mod' } as never);
    const tool = new CreateRoleTool(createMockAuthGuard());
    const result = await withToolContext(() => tool.execute({ name: 'mod' }, guild));
    expect(result).toContain('mod');
  });
});

/** UT-AG-004 */
describe('UT-AG-004 list-channels tool parity', () => {
  it('lists channels', async () => {
    const guild = createMockGuild();
    const ch = createTextChannelMock('general');
    guild.channels.cache.set(ch.id, ch as never);
    const tool = new ListChannelsTool(createMockAuthGuard());
    const result = await withToolContext(() => tool.execute({}, guild));
    expect(result.toLowerCase()).toMatch(/general|頻道/);
  });
});

/** UT-AG-005 */
describe('UT-AG-005 list-categories tool parity', () => {
  it('lists categories', async () => {
    const guild = createMockGuild();
    guild.channels.cache.set('c1', {
      id: 'c1',
      name: 'Cat',
      type: ChannelType.GuildCategory,
    } as never);
    const tool = new ListCategoriesTool(createMockAuthGuard());
    const result = await withToolContext(() => tool.execute({}, guild));
    expect(result.toLowerCase()).toMatch(/cat|分類/);
  });
});

/** UT-AG-006 */
describe('UT-AG-006 list-roles tool parity', () => {
  it('lists roles', async () => {
    const guild = createMockGuild();
    guild.roles.cache.set(TEST_ROLE_ID, { id: TEST_ROLE_ID, name: 'Admin' } as never);
    const tool = new ListRolesTool(createMockAuthGuard());
    const result = await withToolContext(() => tool.execute({}, guild));
    expect(result.toLowerCase()).toMatch(/admin|身分/);
  });
});

/** UT-AG-007 */
describe('UT-AG-007 get-channel-permissions tool parity', () => {
  it('returns channel permissions successfully', async () => {
    const guild = createMockGuild();
    const channel = createTextChannelMock('general');
    channel.permissionOverwrites.cache.set(TEST_ROLE_ID, {
      id: TEST_ROLE_ID,
      type: 0,
      allow: { toArray: () => ['ViewChannel'] },
      deny: { toArray: () => [] },
    });
    guild.channels.cache.set(channel.id, channel as never);

    const tool = new GetChannelPermissionsTool(createMockAuthGuard());
    const result = await withToolContext(() => tool.execute({ channelId: TEST_CHANNEL_ID }, guild));
    expect(result).toContain(TEST_CHANNEL_ID);
    expect(result).toContain('general');
  });

  it('returns not found for missing channel', async () => {
    const tool = new GetChannelPermissionsTool(createMockAuthGuard());
    const result = await withToolContext(() =>
      tool.execute({ channelId: 'missing' }, createMockGuild()),
    );
    expect(result).toMatch(/找不到|不存在|not found/i);
  });
});

/** UT-AG-008 */
describe('UT-AG-008 get-category-permissions tool parity', () => {
  it('returns category permissions successfully', async () => {
    const guild = createMockGuild();
    const category = {
      id: 'cat-1',
      name: 'Category',
      type: ChannelType.GuildCategory,
      permissionOverwrites: {
        cache: {
          map: () => [],
        },
      },
    };
    guild.channels.cache.set(category.id, category as never);

    const tool = new GetCategoryPermissionsTool(createMockAuthGuard());
    const result = await withToolContext(() => tool.execute({ categoryId: 'cat-1' }, guild));
    expect(result).toContain('cat-1');
    expect(result).toContain('Category');
  });

  it('returns not found for missing category', async () => {
    const tool = new GetCategoryPermissionsTool(createMockAuthGuard());
    const result = await withToolContext(() =>
      tool.execute({ categoryId: 'missing' }, createMockGuild()),
    );
    expect(result).toMatch(/找不到|不存在|not found/i);
  });
});

/** UT-AG-009 */
describe('UT-AG-009 get-role-permissions tool parity', () => {
  it('returns role permissions successfully', async () => {
    const guild = createMockGuild();
    guild.roles.cache.set(TEST_ROLE_ID, {
      id: TEST_ROLE_ID,
      name: 'Admin',
      hexColor: '#ff0000',
      permissions: { toArray: () => ['Administrator'] },
      position: 1,
    } as never);

    const tool = new GetRolePermissionsTool(createMockAuthGuard());
    const result = await withToolContext(() => tool.execute({ roleId: TEST_ROLE_ID }, guild));
    expect(result).toContain(TEST_ROLE_ID);
    expect(result).toContain('Admin');
  });

  it('returns not found for missing role', async () => {
    const tool = new GetRolePermissionsTool(createMockAuthGuard());
    const result = await withToolContext(() =>
      tool.execute({ roleId: 'missing' }, createMockGuild()),
    );
    expect(result).toMatch(/找不到|不存在|not found/i);
  });
});

/** UT-AG-010 */
describe('UT-AG-010 modify-channel-permissions tool parity', () => {
  it('modifies channel permissions successfully', async () => {
    const guild = createMockGuild();
    const channel = {
      ...createTextChannelMock('general'),
      permissionOverwrites: {
        cache: new Map(),
        create: vi.fn().mockResolvedValue(undefined),
      },
    };
    guild.channels.cache.set(channel.id, channel as never);

    const tool = new ModifyChannelPermissionsTool(createMockAuthGuard(), permissionParser);
    const result = await withToolContext(() =>
      tool.execute(
        {
          channelId: TEST_CHANNEL_ID,
          permissions: [{ id: TEST_ROLE_ID, type: 'role', allowSet: ['ViewChannel'] }],
        },
        guild,
      ),
    );
    expect(result).toContain('已成功修改');
    expect(channel.permissionOverwrites.create).toHaveBeenCalled();
  });

  it('rejects unauthorized caller', async () => {
    const tool = new ModifyChannelPermissionsTool(
      createMockAuthGuard({ admin: false }),
      permissionParser,
    );
    const result = await withToolContext(() =>
      tool.execute({ channelId: TEST_CHANNEL_ID, permissions: [] }, createMockGuild()),
    );
    expect(result).toBe('你沒有權限使用此工具。');
  });
});

/** UT-AG-011 */
describe('UT-AG-011 modify-category-permissions tool parity', () => {
  it('modifies category permissions successfully', async () => {
    const guild = createMockGuild();
    const category = {
      id: 'cat-1',
      name: 'Category',
      type: ChannelType.GuildCategory,
      permissionOverwrites: { create: vi.fn().mockResolvedValue(undefined) },
    };
    guild.channels.cache.set(category.id, category as never);

    const tool = new ModifyCategoryPermissionsTool(createMockAuthGuard(), permissionParser);
    const result = await withToolContext(() =>
      tool.execute(
        {
          categoryId: 'cat-1',
          permissions: [{ id: TEST_ROLE_ID, type: 'role', allowSet: ['ViewChannel'] }],
        },
        guild,
      ),
    );
    expect(result).toContain('已成功修改');
    expect(category.permissionOverwrites.create).toHaveBeenCalled();
  });

  it('rejects unauthorized caller', async () => {
    const tool = new ModifyCategoryPermissionsTool(
      createMockAuthGuard({ admin: false }),
      permissionParser,
    );
    const result = await withToolContext(() =>
      tool.execute({ categoryId: '1', permissions: [] }, createMockGuild()),
    );
    expect(result).toBe('你沒有權限使用此工具。');
  });
});

/** UT-AG-012 */
describe('UT-AG-012 modify-role-permissions tool parity', () => {
  it('modifies role permissions successfully', async () => {
    const guild = createMockGuild();
    const permissions = {
      add: vi.fn(function (this: typeof permissions) {
        return this;
      }),
      remove: vi.fn(function (this: typeof permissions) {
        return this;
      }),
    };
    const role = {
      id: TEST_ROLE_ID,
      name: 'Mod',
      permissions,
      setPermissions: vi.fn().mockResolvedValue(undefined),
    };
    guild.roles.cache.set(TEST_ROLE_ID, role as never);

    const tool = new ModifyRolePermissionsTool(createMockAuthGuard());
    const result = await withToolContext(() =>
      tool.execute({ roleId: TEST_ROLE_ID, permissions: [{ allowSet: ['ViewChannel'] }] }, guild),
    );
    expect(result).toContain('已成功修改');
    expect(role.setPermissions).toHaveBeenCalled();
  });

  it('rejects unauthorized caller', async () => {
    const tool = new ModifyRolePermissionsTool(createMockAuthGuard({ admin: false }));
    const result = await withToolContext(() =>
      tool.execute({ roleId: TEST_ROLE_ID, permissions: 'VIEW_CHANNEL' }, createMockGuild()),
    );
    expect(result).toBe('你沒有權限使用此工具。');
  });
});

/** UT-AG-013 */
describe('UT-AG-013 send-messages tool parity', () => {
  it('sends messages successfully', async () => {
    const guild = createMockGuild();
    const channel = {
      ...createTextChannelMock('general'),
      isTextBased: () => true,
      send: vi.fn().mockResolvedValue(undefined),
    };
    guild.channels.cache.set(channel.id, channel as never);

    const tool = new SendMessagesTool(createMockAuthGuard());
    const result = await withToolContext(() =>
      tool.execute({ channelIds: [TEST_CHANNEL_ID], message: 'hello' }, guild),
    );
    expect(result).toContain('已發送');
    expect(channel.send).toHaveBeenCalledWith('hello');
  });

  it('rejects unauthorized caller', async () => {
    const tool = new SendMessagesTool(createMockAuthGuard({ admin: false }));
    const result = await withToolContext(() =>
      tool.execute({ channelIds: [TEST_CHANNEL_ID], message: 'hi' }, createMockGuild()),
    );
    expect(result).toBe('你沒有權限使用此工具。');
  });
});

/** UT-AG-014 */
describe('UT-AG-014 search-messages tool parity', () => {
  it('finds matching messages successfully', async () => {
    const guild = createMockGuild();
    const channel = {
      ...createTextChannelMock('general'),
      isTextBased: () => true,
      isSendable: () => true,
      messages: {
        fetch: vi.fn().mockResolvedValue({
          filter: () => ({
            first: () => [
              {
                id: 'msg-1',
                content: 'hello world',
                author: { tag: 'user#1' },
                createdAt: new Date('2024-01-01T00:00:00Z'),
              },
            ],
          }),
        }),
      },
    };
    guild.channels.cache.set(channel.id, channel as never);

    const tool = new SearchMessagesTool(createMockAuthGuard());
    const result = await withToolContext(() => tool.execute({ keywords: 'hello' }, guild));
    expect(result).toContain('hello');
  });

  it('rejects unauthorized caller', async () => {
    const tool = new SearchMessagesTool(createMockAuthGuard({ admin: false }));
    const result = await withToolContext(() =>
      tool.execute({ keywords: 'hello' }, createMockGuild()),
    );
    expect(result).toBe('你沒有權限使用此工具。');
  });
});

/** UT-AG-015 */
describe('UT-AG-015 manage-message tool parity', () => {
  it('deletes message successfully', async () => {
    const guild = createMockGuild();
    const channel = {
      ...createTextChannelMock('general'),
      isTextBased: () => true,
      isSendable: () => true,
      messages: {
        fetch: vi.fn().mockResolvedValue({
          pinned: false,
          delete: vi.fn().mockResolvedValue(undefined),
        }),
      },
    };
    guild.channels.cache.set(channel.id, channel as never);

    const tool = new ManageMessageTool(createMockAuthGuard());
    const result = await withToolContext(() =>
      tool.execute({ messageId: 'msg-1', action: 'delete', channelId: TEST_CHANNEL_ID }, guild),
    );
    expect(result).toContain('已成功刪除');
  });

  it('rejects unauthorized caller', async () => {
    const tool = new ManageMessageTool(createMockAuthGuard({ admin: false }));
    const result = await withToolContext(() =>
      tool.execute({ messageId: '1', action: 'delete' }, createMockGuild()),
    );
    expect(result).toBe('你沒有權限使用此工具。');
  });
});

/** UT-AG-016 */
describe('UT-AG-016 move-channel tool parity', () => {
  it('moves channel successfully', async () => {
    const guild = createMockGuild();
    const channel = {
      ...createTextChannelMock('general'),
      setParent: vi.fn().mockResolvedValue(undefined),
    };
    const category = {
      id: 'cat-1',
      name: 'Target',
      type: ChannelType.GuildCategory,
    };
    guild.channels.cache.set(channel.id, channel as never);
    guild.channels.cache.set(category.id, category as never);

    const tool = new MoveChannelTool(createMockAuthGuard());
    const result = await withToolContext(() =>
      tool.execute({ channelId: TEST_CHANNEL_ID, targetCategoryId: 'cat-1' }, guild),
    );
    expect(result).toContain('已將頻道');
    expect(channel.setParent).toHaveBeenCalledWith('cat-1', {
      reason: '透過 AI Agent 移動頻道',
    });
  });

  it('rejects unauthorized caller', async () => {
    const tool = new MoveChannelTool(createMockAuthGuard({ admin: false }));
    const result = await withToolContext(() =>
      tool.execute({ channelId: TEST_CHANNEL_ID, targetCategoryId: '1' }, createMockGuild()),
    );
    expect(result).toBe('你沒有權限使用此工具。');
  });
});

/** UT-AG-017 */
describe('UT-AG-017 delete-discord-resource tool parity', () => {
  it('deletes channel successfully', async () => {
    const guild = createMockGuild();
    const channel = {
      ...createTextChannelMock('general'),
      delete: vi.fn().mockResolvedValue(undefined),
    };
    guild.channels.cache.set(channel.id, channel as never);

    const tool = new DeleteDiscordResourceTool(createMockAuthGuard());
    const result = await withToolContext(() =>
      tool.execute({ resourceType: 'channel', resourceId: TEST_CHANNEL_ID }, guild),
    );
    expect(result).toContain('已成功刪除');
    expect(channel.delete).toHaveBeenCalled();
  });

  it('rejects unauthorized caller', async () => {
    const tool = new DeleteDiscordResourceTool(createMockAuthGuard({ admin: false }));
    const result = await withToolContext(() =>
      tool.execute({ resourceType: 'channel', resourceId: TEST_CHANNEL_ID }, createMockGuild()),
    );
    expect(result).toBe('你沒有權限使用此工具。');
  });
});

// silence unused import warnings for IDs used in mocks
void TEST_GUILD_ID;
void TEST_USER_ID;
