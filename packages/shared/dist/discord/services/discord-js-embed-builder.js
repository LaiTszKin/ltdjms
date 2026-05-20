import { EmbedBuilder } from 'discord.js';
import pino from 'pino';
import { paginateEmbedView } from './embed-pagination.js';
/**
 * Discord.js implementation of DiscordEmbedBuilder.
 * Wraps discord.js EmbedBuilder with automatic length enforcement.
 */
export class DiscordJsEmbedBuilder {
    MAX_TITLE_LENGTH = 256;
    MAX_DESCRIPTION_LENGTH = 4096;
    MAX_FIELD_NAME_LENGTH = 256;
    MAX_FIELD_VALUE_LENGTH = 1024;
    MAX_FIELDS = 25;
    MAX_FOOTER_LENGTH = 2048;
    ELLIPSIS = '...';
    embed = new EmbedBuilder();
    fieldCount = 0;
    logger;
    constructor(logger) {
        this.logger = logger ?? pino({ level: 'warn' });
    }
    setTitle(title) {
        this.embed.setTitle(title.length > this.MAX_TITLE_LENGTH
            ? this.truncate(title, this.MAX_TITLE_LENGTH)
            : title);
        return this;
    }
    setDescription(description) {
        this.embed.setDescription(description.length > this.MAX_DESCRIPTION_LENGTH
            ? description.slice(0, this.MAX_DESCRIPTION_LENGTH)
            : description);
        return this;
    }
    setColor(color) {
        this.embed.setColor(color);
        return this;
    }
    addField(name, value, inline) {
        if (this.fieldCount >= this.MAX_FIELDS) {
            return this;
        }
        this.embed.addFields({
            name: name.length > this.MAX_FIELD_NAME_LENGTH
                ? this.truncate(name, this.MAX_FIELD_NAME_LENGTH)
                : name,
            value: value.length > this.MAX_FIELD_VALUE_LENGTH
                ? this.truncate(value, this.MAX_FIELD_VALUE_LENGTH)
                : value,
            inline,
        });
        this.fieldCount++;
        return this;
    }
    setFooter(text) {
        this.embed.setFooter({
            text: text.length > this.MAX_FOOTER_LENGTH
                ? this.truncate(text, this.MAX_FOOTER_LENGTH)
                : text,
        });
        return this;
    }
    build() {
        return this.embed.toJSON();
    }
    buildPaginated(data) {
        return paginateEmbedView(data, this, (str, maxLen) => this.truncate(str, maxLen));
    }
    truncate(str, maxLength) {
        if (str.length <= maxLength)
            return str;
        this.logger.warn({ originalLength: str.length, maxLength, ellipsis: this.ELLIPSIS }, 'Embed field truncated');
        return str.slice(0, maxLength - this.ELLIPSIS.length) + this.ELLIPSIS;
    }
}
//# sourceMappingURL=discord-js-embed-builder.js.map