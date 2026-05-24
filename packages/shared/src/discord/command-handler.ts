import { type DiscordInteraction } from './domain/discord-interaction.js';
import { type DiscordContext } from './domain/discord-context.js';

/**
 * Interface for slash command handlers.
 */
export interface CommandHandler {
  readonly commandName: string;
  execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}

/**
 * Interface for interaction handlers (buttons, select menus, modals).
 */
export interface InteractionHandler {
  readonly customIdPrefix: string;
  execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
