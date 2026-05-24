import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';

/**
 * Interface for slash command handlers.
 * Each slash command registers with a unique commandName.
 */
export interface CommandHandler {
  /** The slash command name (e.g., "user-panel", "redeem-code"). */
  readonly commandName: string;

  execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}

/**
 * Interface for interaction handlers (buttons, select menus, modals).
 * Matched by customId prefix.
 */
export interface InteractionHandler {
  readonly customIdPrefix: string;

  execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
