import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';

/**
 * Interface for slash command handlers.
 * Each slash command registers with a unique commandName.
 */
export interface CommandHandler {
  /** The slash command name (e.g., "admin-panel", "user-panel"). */
  readonly commandName: string;

  /**
   * Executes the command handler.
   * @param interaction - The Discord interaction to respond to
   * @param context - The Discord context with extracted options
   */
  execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}

/**
 * Interface for interaction handlers (buttons, select menus, modals).
 * Matched by customId prefix.
 */
export interface InteractionHandler {
  /** The customId prefix used to route interactions to this handler. */
  readonly customIdPrefix: string;

  /**
   * Executes the interaction handler.
   * @param interaction - The Discord interaction to respond to
   * @param context - The Discord context
   */
  execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
