import { type DiscordInteraction } from '@ltdjms/shared';

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
