import { EmbedBuilder } from 'discord.js';
import { paginateEmbedView } from '../services/embed-pagination.js';
/**
 * Mock implementation of DiscordEmbedBuilder for testing.
 * Records all builder calls for test assertions.
 * Matches Java MockDiscordEmbedBuilder.
 */
export class MockDiscordEmbedBuilder {
    MAX_TITLE_LENGTH = 256;
    MAX_DESCRIPTION_LENGTH = 4096;
    MAX_FIELD_NAME_LENGTH = 256;
    MAX_FIELD_VALUE_LENGTH = 1024;
    MAX_FIELDS = 25;
    MAX_FOOTER_LENGTH = 2048;
    ELLIPSIS = '...';
    _title;
    _description;
    _color;
    _fields = [];
    _footer;
    setTitle(title) {
        this._title =
            title.length > this.MAX_TITLE_LENGTH
                ? this.truncate(title, this.MAX_TITLE_LENGTH)
                : title;
        return this;
    }
    setDescription(description) {
        this._description =
            description.length > this.MAX_DESCRIPTION_LENGTH
                ? description.slice(0, this.MAX_DESCRIPTION_LENGTH)
                : description;
        return this;
    }
    setColor(color) {
        this._color = color;
        return this;
    }
    addField(name, value, inline) {
        if (this._fields.length >= this.MAX_FIELDS) {
            return this;
        }
        this._fields.push({
            name: name.length > this.MAX_FIELD_NAME_LENGTH
                ? this.truncate(name, this.MAX_FIELD_NAME_LENGTH)
                : name,
            value: value.length > this.MAX_FIELD_VALUE_LENGTH
                ? this.truncate(value, this.MAX_FIELD_VALUE_LENGTH)
                : value,
            inline,
        });
        return this;
    }
    setFooter(text) {
        this._footer =
            text.length > this.MAX_FOOTER_LENGTH
                ? this.truncate(text, this.MAX_FOOTER_LENGTH)
                : text;
        return this;
    }
    build() {
        const builder = new EmbedBuilder();
        if (this._title)
            builder.setTitle(this._title);
        if (this._description)
            builder.setDescription(this._description);
        if (this._color)
            builder.setColor(this._color);
        if (this._footer)
            builder.setFooter({ text: this._footer });
        for (const f of this._fields) {
            builder.addFields({ name: f.name, value: f.value, inline: f.inline });
        }
        return builder.toJSON();
    }
    buildPaginated(data) {
        return paginateEmbedView(data, this, (str, maxLen) => this.truncate(str, maxLen));
    }
    // ---- Test helper getters ----
    getTitle() {
        return this._title;
    }
    getDescription() {
        return this._description;
    }
    getColor() {
        return this._color;
    }
    getFields() {
        return [...this._fields];
    }
    getFooter() {
        return this._footer;
    }
    reset() {
        this._title = undefined;
        this._description = undefined;
        this._color = undefined;
        this._fields.length = 0;
        this._footer = undefined;
    }
    truncate(str, maxLength) {
        if (str.length <= maxLength)
            return str;
        return str.slice(0, maxLength - this.ELLIPSIS.length) + this.ELLIPSIS;
    }
}
//# sourceMappingURL=mock-discord-embed-builder.js.map