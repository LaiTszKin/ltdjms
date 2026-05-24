import {
  type DiscordInteraction,
  type DiscordContext,
  ensureDeferred,
  type InteractionHandler,
} from '@ltdjms/shared';
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

  abstract execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;

  /**
   * Checks whether the user has ADMINISTRATOR permission or is the guild owner.
   * Delegates to the unified DiscordInteraction interface.
   */
  protected checkAdminPermission(interaction: DiscordInteraction): boolean {
    return interaction.isAdministrator();
  }

  /**
   * Retrieves the current admin panel session for the interaction user.
   * Returns null if no active session exists.
   */
  protected getSession(interaction: DiscordInteraction): AdminPanelSessionData | null {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();
    return this.sessionManager.getSession(guildId, userId);
  }

  /**
   * Ensures the interaction has been deferred.
   * Safe to call multiple times — the DiscordInteraction abstraction
   * checks isAcknowledged() before deferring.
   */
  protected async ensureDeferred(interaction: DiscordInteraction): Promise<void> {
    return ensureDeferred(interaction);
  }
}
