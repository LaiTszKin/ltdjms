import { type DiscordInteraction } from './domain/discord-interaction.js';

/**
 * Ensures the interaction has been deferred.
 * Safe to call multiple times — the DiscordInteraction abstraction
 * checks isAcknowledged() before deferring.
 */
export async function ensureDeferred(interaction: DiscordInteraction): Promise<void> {
  if (!interaction.isAcknowledged()) {
    interaction.makeEphemeral();
    await interaction.deferReply();
  }
}
