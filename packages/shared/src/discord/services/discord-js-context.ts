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

  getGuildId(): string {
    return this.interaction.guildId ?? '0';
  }

  getUserId(): string {
    return this.interaction.user.id;
  }

  getChannelId(): string {
    return this.interaction.channelId ?? '0';
  }

  getUserMention(): string {
    return `<@${this.interaction.user.id}>`;
  }

  private hasOptions(): boolean {
    return 'options' in this.interaction;
  }

  getOption(name: string): string | null {
    if (!this.hasOptions()) {
      return null;
    }
    const opts = (this.interaction as any).options;
    const option = opts.get ? opts.get(name) : null;
    return option?.value?.toString() ?? null;
  }

  getOptionAsString(name: string): string | null {
    if (!this.hasOptions()) {
      return null;
    }
    const option = (this.interaction as any).options.get(name);
    if (option && typeof option.value === 'string') {
      return option.value;
    }
    return null;
  }

  getOptionAsNumber(name: string): number | null {
    if (!this.hasOptions()) {
      return null;
    }
    const option = (this.interaction as any).options.get(name);
    if (option && typeof option.value === 'number') {
      return option.value;
    }
    return null;
  }

  getOptionAsUser(name: string): unknown | null {
    if (!this.hasOptions()) {
      return null;
    }
    const option = (this.interaction as any).options.getUser?.(name);
    return option ?? null;
  }
}
