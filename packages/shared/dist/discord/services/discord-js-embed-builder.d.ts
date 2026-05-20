import { type APIEmbed } from 'discord.js';
import { type DiscordEmbedBuilder } from '../domain/discord-embed-builder.js';
import { type EmbedView } from '../domain/embed-view.js';
import pino from 'pino';
/**
 * Discord.js implementation of DiscordEmbedBuilder.
 * Wraps discord.js EmbedBuilder with automatic length enforcement.
 */
export declare class DiscordJsEmbedBuilder implements DiscordEmbedBuilder {
    readonly MAX_TITLE_LENGTH = 256;
    readonly MAX_DESCRIPTION_LENGTH = 4096;
    readonly MAX_FIELD_NAME_LENGTH = 256;
    readonly MAX_FIELD_VALUE_LENGTH = 1024;
    readonly MAX_FIELDS = 25;
    readonly MAX_FOOTER_LENGTH = 2048;
    readonly ELLIPSIS = "...";
    private readonly embed;
    private fieldCount;
    private readonly logger;
    constructor(logger?: pino.Logger);
    setTitle(title: string): DiscordEmbedBuilder;
    setDescription(description: string): DiscordEmbedBuilder;
    setColor(color: number): DiscordEmbedBuilder;
    addField(name: string, value: string, inline: boolean): DiscordEmbedBuilder;
    setFooter(text: string): DiscordEmbedBuilder;
    build(): APIEmbed;
    buildPaginated(data: EmbedView): APIEmbed[];
    private truncate;
}
