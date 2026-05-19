import {
  type CommandInteraction,
  type ButtonInteraction,
  type ModalSubmitInteraction,
} from 'discord.js';
import { type DiscordContext } from '../domain/discord-context.js';

/**
 * Discord.js implementation of DiscordContext.
 * Wraps a discord.js interaction to provide guild/user/channel context.
 */
export class DiscordJsContext implements DiscordContext {
  constructor(
    private readonly interaction:
      | CommandInteraction
      | ButtonInteraction
      | ModalSubmitInteraction,
  ) {}

  getGuildId(): number {
    return this.interaction.guildId ? Number(this.interaction.guildId) : 0;
  }

  getUserId(): number {
    return Number(this.interaction.user.id);
  }

  getChannelId(): number {
    return this.interaction.channelId
      ? Number(this.interaction.channelId)
      : 0;
  }

  getUserMention(): string {
    return `<@${this.interaction.user.id}>`;
  }

  getOption(name: string): string | null {
    if (!('options' in this.interaction)) {
      return null;
    }
    const option = (this.interaction.options as Record<string, unknown>).get
      ? (this.interaction.options as any).get(name)
      : null;
    return option?.value?.toString() ?? null;
  }

  getOptionAsString(name: string): string | null {
    if (!('options' in this.interaction)) {
      return null;
    }
    const option = (this.interaction.options as any).get(name);
    if (option && typeof option.value === 'string') {
      return option.value;
    }
    return null;
  }

  getOptionAsNumber(name: string): number | null {
    if (!('options' in this.interaction)) {
      return null;
    }
    const option = (this.interaction.options as any).get(name);
    if (option && typeof option.value === 'number') {
      return option.value;
    }
    return null;
  }
}
