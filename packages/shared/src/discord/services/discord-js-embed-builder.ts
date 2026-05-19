import { EmbedBuilder, type APIEmbed } from 'discord.js';
import { type DiscordEmbedBuilder } from '../domain/discord-embed-builder.js';
import { type EmbedView } from '../domain/embed-view.js';

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
    const embeds: APIEmbed[] = [];

    const title = data.title
      ? data.title.length > this.MAX_TITLE_LENGTH
        ? this.truncate(data.title, this.MAX_TITLE_LENGTH)
        : data.title
      : undefined;

    const footer = data.footer
      ? data.footer.length > this.MAX_FOOTER_LENGTH
        ? this.truncate(data.footer, this.MAX_FOOTER_LENGTH)
        : data.footer
      : undefined;

    const description = data.description;

    // Paginate by description
    if (description && description.length > this.MAX_DESCRIPTION_LENGTH) {
      const totalPages = Math.ceil(
        description.length / this.MAX_DESCRIPTION_LENGTH,
      );
      for (let i = 0; i < totalPages; i++) {
        const start = i * this.MAX_DESCRIPTION_LENGTH;
        const end = Math.min(
          start + this.MAX_DESCRIPTION_LENGTH,
          description.length,
        );
        const builder = new EmbedBuilder();
        builder.setDescription(description.slice(start, end));
        if (title) {
          builder.setTitle(
            totalPages > 1 ? `${title} (${i + 1}/${totalPages})` : title,
          );
        }
        if (data.color) builder.setColor(data.color);
        if (footer) builder.setFooter({ text: footer });
        embeds.push(builder.toJSON());
      }
      return embeds;
    }

    // No fields
    if (!data.fields || data.fields.length === 0) {
      const builder = new EmbedBuilder();
      if (title) builder.setTitle(title);
      if (description) builder.setDescription(description);
      if (data.color) builder.setColor(data.color);
      if (footer) builder.setFooter({ text: footer });
      embeds.push(builder.toJSON());
      return embeds;
    }

    // Fields may need pagination
    let totalAdded = 0;
    let pageIndex = 0;
    let builder = new EmbedBuilder();
    if (title) builder.setTitle(title);
    if (description) builder.setDescription(description);
    if (data.color) builder.setColor(data.color);
    if (footer) builder.setFooter({ text: footer });

    for (const field of data.fields) {
      if (totalAdded > 0 && totalAdded % this.MAX_FIELDS === 0) {
        embeds.push(builder.toJSON());
        pageIndex++;
        builder = new EmbedBuilder();
        if (title) {
          builder.setTitle(
            data.fields.length > this.MAX_FIELDS
              ? `${title} (${pageIndex + 1})`
              : title,
          );
        }
        if (data.color) builder.setColor(data.color);
        if (footer) builder.setFooter({ text: footer });
      }
      builder.addFields({
        name: field.name,
        value: field.value,
        inline: field.inline,
      });
      totalAdded++;
    }
    embeds.push(builder.toJSON());

    return embeds;
  }

  private truncate(str: string, maxLength: number): string {
    if (str.length <= maxLength) return str;
    return str.slice(0, maxLength - this.ELLIPSIS.length) + this.ELLIPSIS;
  }
}
