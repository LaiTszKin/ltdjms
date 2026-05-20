import { EmbedBuilder, type APIEmbed } from 'discord.js';
import { type DiscordEmbedBuilder } from '../domain/discord-embed-builder.js';
import { type EmbedView } from '../domain/embed-view.js';
import pino, { type Logger } from 'pino';
import { paginateEmbedView } from './embed-pagination.js';

/**
 * Discord.js implementation of DiscordEmbedBuilder.
 * Wraps discord.js EmbedBuilder with automatic length enforcement.
 */
export class DiscordJsEmbedBuilder implements DiscordEmbedBuilder {
  readonly MAX_TITLE_LENGTH = 256;
  readonly MAX_DESCRIPTION_LENGTH = 4096;
  readonly MAX_FIELD_NAME_LENGTH = 256;
  readonly MAX_FIELD_VALUE_LENGTH = 1024;
  readonly MAX_FIELDS = 25;
  readonly MAX_FOOTER_LENGTH = 2048;
  readonly ELLIPSIS = '...';

  private readonly embed: EmbedBuilder = new EmbedBuilder();
  private fieldCount = 0;
  private readonly logger: Logger;

  constructor(logger?: Logger) {
    this.logger = logger ?? pino({ level: 'warn' });
  }

  setTitle(title: string): DiscordEmbedBuilder {
    this.embed.setTitle(
      title.length > this.MAX_TITLE_LENGTH
        ? this.truncate(title, this.MAX_TITLE_LENGTH)
        : title,
    );
    return this;
  }

  setDescription(description: string): DiscordEmbedBuilder {
    this.embed.setDescription(
      description.length > this.MAX_DESCRIPTION_LENGTH
        ? description.slice(0, this.MAX_DESCRIPTION_LENGTH)
        : description,
    );
    return this;
  }

  setColor(color: number): DiscordEmbedBuilder {
    this.embed.setColor(color);
    return this;
  }

  addField(name: string, value: string, inline: boolean): DiscordEmbedBuilder {
    if (this.fieldCount >= this.MAX_FIELDS) {
      return this;
    }
    this.embed.addFields({
      name:
        name.length > this.MAX_FIELD_NAME_LENGTH
          ? this.truncate(name, this.MAX_FIELD_NAME_LENGTH)
          : name,
      value:
        value.length > this.MAX_FIELD_VALUE_LENGTH
          ? this.truncate(value, this.MAX_FIELD_VALUE_LENGTH)
          : value,
      inline,
    });
    this.fieldCount++;
    return this;
  }

  setFooter(text: string): DiscordEmbedBuilder {
    this.embed.setFooter({
      text:
        text.length > this.MAX_FOOTER_LENGTH
          ? this.truncate(text, this.MAX_FOOTER_LENGTH)
          : text,
    });
    return this;
  }

  build(): APIEmbed {
    return this.embed.toJSON();
  }

  buildPaginated(data: EmbedView): APIEmbed[] {
    return paginateEmbedView(data, this, (str, maxLen) => this.truncate(str, maxLen));
  }

  private truncate(str: string, maxLength: number): string {
    if (str.length <= maxLength) return str;
    this.logger.warn(
      { originalLength: str.length, maxLength, ellipsis: this.ELLIPSIS },
      'Embed field truncated',
    );
    return str.slice(0, maxLength - this.ELLIPSIS.length) + this.ELLIPSIS;
  }
}
