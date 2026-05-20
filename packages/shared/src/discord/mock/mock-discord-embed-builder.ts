import { EmbedBuilder, type APIEmbed } from 'discord.js';
import { type DiscordEmbedBuilder } from '../domain/discord-embed-builder.js';
import { type EmbedView, type FieldView } from '../domain/embed-view.js';
import { paginateEmbedView } from '../services/embed-pagination.js';

/**
 * Mock implementation of DiscordEmbedBuilder for testing.
 * Records all builder calls for test assertions.
 * Matches Java MockDiscordEmbedBuilder.
 */
export class MockDiscordEmbedBuilder implements DiscordEmbedBuilder {
  readonly MAX_TITLE_LENGTH = 256;
  readonly MAX_DESCRIPTION_LENGTH = 4096;
  readonly MAX_FIELD_NAME_LENGTH = 256;
  readonly MAX_FIELD_VALUE_LENGTH = 1024;
  readonly MAX_FIELDS = 25;
  readonly MAX_FOOTER_LENGTH = 2048;
  readonly ELLIPSIS = '...';

  private _title: string | undefined;
  private _description: string | undefined;
  private _color: number | undefined;
  private readonly _fields: FieldView[] = [];
  private _footer: string | undefined;

  setTitle(title: string): DiscordEmbedBuilder {
    this._title =
      title.length > this.MAX_TITLE_LENGTH
        ? this.truncate(title, this.MAX_TITLE_LENGTH)
        : title;
    return this;
  }

  setDescription(description: string): DiscordEmbedBuilder {
    this._description =
      description.length > this.MAX_DESCRIPTION_LENGTH
        ? description.slice(0, this.MAX_DESCRIPTION_LENGTH)
        : description;
    return this;
  }

  setColor(color: number): DiscordEmbedBuilder {
    this._color = color;
    return this;
  }

  addField(name: string, value: string, inline: boolean): DiscordEmbedBuilder {
    if (this._fields.length >= this.MAX_FIELDS) {
      return this;
    }
    this._fields.push({
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
    return this;
  }

  setFooter(text: string): DiscordEmbedBuilder {
    this._footer =
      text.length > this.MAX_FOOTER_LENGTH
        ? this.truncate(text, this.MAX_FOOTER_LENGTH)
        : text;
    return this;
  }

  build(): APIEmbed {
    const builder = new EmbedBuilder();
    if (this._title) builder.setTitle(this._title);
    if (this._description) builder.setDescription(this._description);
    if (this._color) builder.setColor(this._color);
    if (this._footer) builder.setFooter({ text: this._footer });
    for (const f of this._fields) {
      builder.addFields({ name: f.name, value: f.value, inline: f.inline });
    }
    return builder.toJSON();
  }

  buildPaginated(data: EmbedView): APIEmbed[] {
    return paginateEmbedView(data, this, (str, maxLen) => this.truncate(str, maxLen));
  }

  // ---- Test helper getters ----

  getTitle(): string | undefined {
    return this._title;
  }

  getDescription(): string | undefined {
    return this._description;
  }

  getColor(): number | undefined {
    return this._color;
  }

  getFields(): FieldView[] {
    return [...this._fields];
  }

  getFooter(): string | undefined {
    return this._footer;
  }

  reset(): void {
    this._title = undefined;
    this._description = undefined;
    this._color = undefined;
    this._fields.length = 0;
    this._footer = undefined;
  }

  private truncate(str: string, maxLength: number): string {
    if (str.length <= maxLength) return str;
    return str.slice(0, maxLength - this.ELLIPSIS.length) + this.ELLIPSIS;
  }
}
