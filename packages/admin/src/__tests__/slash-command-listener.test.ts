import { describe, it, expect, vi, beforeEach } from 'vitest';
import { EventEmitter } from 'events';
import { SlashCommandListener } from '../commands/infra/SlashCommandListener.js';

/**
 * Builds a minimal mock discord.js interaction that satisfies the duck-typing
 * checks in handleRawInteraction. Only the methods/properties actually accessed
 * by the type-detection logic need to be provided.
 */
interface MockRawInteraction {
  isChatInputCommand: () => boolean;
  isButton: () => boolean;
  isAnySelectMenu: () => boolean;
  isModalSubmit: () => boolean;
  commandName?: string;
  customId?: string;
  // Properties accessed by DiscordJsInteraction / DiscordJsContext constructors
  replied: boolean;
  deferred: boolean;
  guildId: string;
  user: { id: string };
  channelId: string;
  memberPermissions: { has: (perm: bigint) => boolean };
  guild?: { ownerId: string; name?: string; channels?: { cache: Map<string, unknown> } };
}

function createMockClient(): EventEmitter {
  return new EventEmitter();
}

function createMockGateway(client: EventEmitter) {
  return {
    requireReadyClient: () => client,
  };
}

describe('SlashCommandListener type detection', () => {
  let client: EventEmitter;
  let listener: SlashCommandListener;

  beforeEach(() => {
    client = createMockClient();
    listener = new SlashCommandListener();
    listener.listen(createMockGateway(client) as any);
  });

  it('should route chatInput commands to registered command handler', async () => {
    const handler = vi.fn().mockResolvedValue(undefined);
    listener.registerCommand({ commandName: 'test-cmd', execute: handler });

    client.emit('interactionCreate', {
      isChatInputCommand: () => true,
      isButton: () => false,
      isAnySelectMenu: () => false,
      isModalSubmit: () => false,
      commandName: 'test-cmd',
      replied: false,
      deferred: false,
      guildId: '123',
      user: { id: '456' },
      channelId: '789',
      memberPermissions: { has: () => true },
    } satisfies MockRawInteraction);

    await vi.waitFor(() => {
      expect(handler).toHaveBeenCalledTimes(1);
    });
  });

  it('should route button interactions to registered interaction handler', async () => {
    const handler = vi.fn().mockResolvedValue(undefined);
    listener.registerInteractionHandler({ customIdPrefix: 'btn_', execute: handler });

    client.emit('interactionCreate', {
      isChatInputCommand: () => false,
      isButton: () => true,
      isAnySelectMenu: () => false,
      isModalSubmit: () => false,
      customId: 'btn_test',
      replied: false,
      deferred: false,
      guildId: '123',
      user: { id: '456' },
      channelId: '789',
      memberPermissions: { has: () => true },
    } satisfies MockRawInteraction);

    await vi.waitFor(() => {
      expect(handler).toHaveBeenCalledTimes(1);
    });
  });

  it('should route select menu interactions via isAnySelectMenu', async () => {
    const handler = vi.fn().mockResolvedValue(undefined);
    listener.registerInteractionHandler({ customIdPrefix: 'sel_', execute: handler });

    // Simulate a UserSelectMenu interaction — the exact method that was failing
    client.emit('interactionCreate', {
      isChatInputCommand: () => false,
      isButton: () => false,
      isAnySelectMenu: () => true,
      isModalSubmit: () => false,
      customId: 'sel_test',
      replied: false,
      deferred: false,
      guildId: '123',
      user: { id: '456' },
      channelId: '789',
      memberPermissions: { has: () => true },
    } satisfies MockRawInteraction);

    await vi.waitFor(() => {
      expect(handler).toHaveBeenCalledTimes(1);
    });
  });

  it('should route modal submit interactions', async () => {
    const handler = vi.fn().mockResolvedValue(undefined);
    listener.registerInteractionHandler({ customIdPrefix: 'modal_', execute: handler });

    client.emit('interactionCreate', {
      isChatInputCommand: () => false,
      isButton: () => false,
      isAnySelectMenu: () => false,
      isModalSubmit: () => true,
      customId: 'modal_test',
      replied: false,
      deferred: false,
      guildId: '123',
      user: { id: '456' },
      channelId: '789',
      memberPermissions: { has: () => true },
    } satisfies MockRawInteraction);

    await vi.waitFor(() => {
      expect(handler).toHaveBeenCalledTimes(1);
    });
  });

  it('should ignore unknown interaction types without replying', async () => {
    const handler = vi.fn().mockResolvedValue(undefined);
    listener.registerInteractionHandler({ customIdPrefix: 'ignored_', execute: handler });

    // All detection methods return false — should be silently ignored
    client.emit('interactionCreate', {
      isChatInputCommand: () => false,
      isButton: () => false,
      isAnySelectMenu: () => false,
      isModalSubmit: () => false,
      customId: 'ignored_test',
      replied: false,
      deferred: false,
      guildId: '123',
      user: { id: '456' },
      channelId: '789',
      memberPermissions: { has: () => true },
    } satisfies MockRawInteraction);

    // Give event loop time to process, then verify no handler was called
    await new Promise((r) => setTimeout(r, 100));
    expect(handler).not.toHaveBeenCalled();
  });

  it('should prefer isAnySelectMenu over individual isUserSelect/isRoleSelect etc.', () => {
    // This is a structural test ensuring the code uses isAnySelectMenu
    // rather than version-fragile individual type checkers.
    const source = SlashCommandListener.prototype.constructor.toString();
    expect(source).not.toContain('isUserSelect(');
    expect(source).not.toContain('isRoleSelect(');
    expect(source).not.toContain('isMentionableSelect(');
    expect(source).not.toContain('isChannelSelect(');
  });
});
