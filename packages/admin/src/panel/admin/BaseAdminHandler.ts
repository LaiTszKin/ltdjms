import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type InteractionHandler } from '../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { type AdminPanelSessionData } from '../../session/types.js';
import { BotErrorHandler } from '../../commands/infra/BotErrorHandler.js';

/**
 * Abstract base class for admin panel interaction handlers.
 * Provides common admin permission checking, session access, and deferred reply.
 *
 * @abstract
 * @implements InteractionHandler
 */
export abstract class BaseAdminHandler implements InteractionHandler {
  abstract readonly customIdPrefix: string;

  constructor(
    protected readonly sessionManager: AdminPanelSessionManager,
    protected readonly errorHandler: BotErrorHandler,
  ) {}

  abstract execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void>;

  /**
   * Checks whether the user has ADMINISTRATOR permission or is the guild owner.
   * Uses the raw Discord interaction from getHook().
   */
  protected checkAdminPermission(interaction: DiscordInteraction): boolean {
    try {
      const raw = interaction.getHook() as {
        memberPermissions?: { has(permission: bigint): boolean };
        guild?: { ownerId: string };
      };
      const userId = String(interaction.getUserId());

      // Discord PermissionFlagsBits.Administrator is 8n
      if (raw.memberPermissions?.has(8n)) {
        return true;
      }

      if (raw.guild?.ownerId === userId) {
        return true;
      }
    } catch {
      // Fall through to denial
    }
    return false;
  }

  /**
   * Retrieves the current admin panel session for the interaction user.
   * Returns null if no active session exists.
   */
  protected getSession(
    interaction: DiscordInteraction,
  ): AdminPanelSessionData | null {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();
    return this.sessionManager.getSession(guildId, userId);
  }

  /**
   * Ensures the interaction has been deferred.
   * Safe to call multiple times — the DiscordInteraction abstraction
   * checks isAcknowledged() before deferring.
   */
  protected async ensureDeferred(
    interaction: DiscordInteraction,
  ): Promise<void> {
    if (!interaction.isAcknowledged()) {
      await interaction.deferReply();
    }
  }
}
