import {
  CommandInteraction,
  PermissionFlagsBits,
  type ButtonInteraction,
  type ModalSubmitInteraction,
  type EmbedBuilder,
  ModalBuilder,
} from 'discord.js';
import { type DiscordInteraction } from '../domain/discord-interaction.js';

/**
 * Discord.js implementation of DiscordInteraction.
 * Wraps CommandInteraction / ButtonInteraction / ModalSubmitInteraction.
 */
export class DiscordJsInteraction implements DiscordInteraction {
  private acknowledged: boolean;
  private _ephemeral: boolean;

  constructor(
    private readonly interaction:
      | CommandInteraction
      | ButtonInteraction
      | ModalSubmitInteraction,
    ephemeral?: boolean,
  ) {
    this.acknowledged = interaction.replied || interaction.deferred;
    // CommandInteraction has an ephemeral property; others default to false
    this._ephemeral = ephemeral ?? (interaction instanceof CommandInteraction ? (interaction.ephemeral ?? false) : false);
  }

  getGuildId(): string {
    return this.interaction.guildId ?? '0';
  }

  getUserId(): string {
    return this.interaction.user.id;
  }

  isEphemeral(): boolean {
    return this._ephemeral;
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

  getCustomId(): string {
    if ('customId' in this.interaction) {
      return (this.interaction as { customId: string }).customId;
    }
    return '';
  }

  isAcknowledged(): boolean {
    return this.acknowledged;
  }

  getChannelId(): string {
    return this.interaction.channelId ?? '0';
  }

  isAdministrator(): boolean {
    if (this.interaction.memberPermissions?.has(PermissionFlagsBits.Administrator)) {
      return true;
    }
    if (this.interaction.guild?.ownerId === this.interaction.user.id) {
      return true;
    }
    return false;
  }

  hasPermission(permission: bigint): boolean {
    return this.interaction.memberPermissions?.has(permission) ?? false;
  }

  async showModal(modal: unknown): Promise<void> {
    // ModalSubmitInteraction does not support showModal; only CommandInteraction and ButtonInteraction do.
    if ('showModal' in this.interaction) {
      await (this.interaction as CommandInteraction | ButtonInteraction).showModal(
        modal as ModalBuilder,
      );
    }
  }

  getSelectedValues(): string[] {
    if ('values' in this.interaction) {
      return (this.interaction as { values: string[] }).values;
    }
    return [];
  }

  getTextInputValue(customId: string): string {
    if ('fields' in this.interaction) {
      return (this.interaction as { fields: { getTextInputValue: (id: string) => string } }).fields.getTextInputValue(customId);
    }
    return '';
  }

  getGuildName(): string | null {
    return this.interaction.guild?.name ?? null;
  }

  getChannelName(channelId: string): string | null {
    const guild = this.interaction.guild;
    if (!guild) return null;
    const channel = guild.channels.cache.get(channelId);
    return channel?.name ?? null;
  }

  isButton(): boolean {
    // Prefer discord.js built-in method when available, fall back to duck-typing
    // for robustness against discord.js version changes.
    const interaction = this.interaction as unknown as Record<string, unknown>;
    if (typeof interaction.isButton === 'function') {
      return (interaction as { isButton: () => boolean }).isButton();
    }
    return 'customId' in this.interaction && !('fields' in this.interaction);
  }

  isModalSubmit(): boolean {
    // Prefer discord.js built-in method when available, fall back to duck-typing.
    const interaction = this.interaction as unknown as Record<string, unknown>;
    if (typeof interaction.isModalSubmit === 'function') {
      return (interaction as { isModalSubmit: () => boolean }).isModalSubmit();
    }
    return 'fields' in this.interaction;
  }

  async replyWithComponents(
    embed: unknown,
    components: unknown[],
  ): Promise<{ channelId: string; id: string } | null> {
    const discordEmbed = embed as EmbedBuilder;
    // Callers pass ActionRowBuilder<T>[] at runtime; cast through any for discord.js type compatibility
    const opts: Record<string, unknown> = { embeds: [discordEmbed], components };
    if (this.acknowledged) {
      await this.interaction.followUp(opts as Record<string, unknown>);
    } else {
      await this.interaction.reply(opts as Record<string, unknown>);
      this.acknowledged = true;
    }

    try {
      const replyMsg = await this.interaction.fetchReply();
      if (replyMsg && 'channelId' in replyMsg && 'id' in replyMsg) {
        return {
          channelId: String(replyMsg.channelId),
          id: String(replyMsg.id),
        };
      }
    } catch {
      // Non-critical: reply metadata unavailable
    }
    return null;
  }

  async editWithComponents(embed: unknown, components: unknown[]): Promise<void> {
    const opts: Record<string, unknown> = { embeds: [embed as EmbedBuilder], components };
    await this.interaction.editReply(opts as Record<string, unknown>);
  }
}
