import {
  type CommandInteraction,
  type ButtonInteraction,
  type ModalSubmitInteraction,
  type EmbedBuilder,
} from 'discord.js';
import { type DiscordInteraction } from '../domain/discord-interaction.js';

/**
 * Discord.js implementation of DiscordInteraction.
 * Wraps CommandInteraction / ButtonInteraction / ModalSubmitInteraction.
 */
export class DiscordJsInteraction implements DiscordInteraction {
  private acknowledged: boolean;

  constructor(
    private readonly interaction:
      | CommandInteraction
      | ButtonInteraction
      | ModalSubmitInteraction,
  ) {
    this.acknowledged = interaction.replied || interaction.deferred;
  }

  getGuildId(): string {
    return this.interaction.guildId ?? '0';
  }

  getUserId(): string {
    return this.interaction.user.id;
  }

  getChannelId(): string {
    return this.interaction.channelId ?? '0';
  }

  isEphemeral(): boolean {
    return false;
  }

  async reply(message: string): Promise<void> {
    if (this.acknowledged) {
      await this.interaction.followUp(message);
    } else {
      await this.interaction.reply(message);
    }
    this.acknowledged = true;
  }

  async replyEmbed(embed: unknown): Promise<void> {
    const discordEmbed = embed as EmbedBuilder;
    if (this.acknowledged) {
      await this.interaction.followUp({ embeds: [discordEmbed] });
    } else {
      await this.interaction.reply({ embeds: [discordEmbed] });
    }
    this.acknowledged = true;
  }

  async editEmbed(embed: unknown): Promise<void> {
    const discordEmbed = embed as EmbedBuilder;
    await this.interaction.editReply({ embeds: [discordEmbed] });
  }

  async deferReply(): Promise<void> {
    if (!this.acknowledged) {
      await this.interaction.deferReply();
      this.acknowledged = true;
    }
  }

  getHook(): unknown {
    return this.interaction;
  }

  isAcknowledged(): boolean {
    return this.acknowledged;
  }
}
