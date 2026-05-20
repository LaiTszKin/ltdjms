import { type APIEmbed } from 'discord.js';
import { type DiscordEmbedBuilder } from '../domain/discord-embed-builder.js';
import { type EmbedView, type FieldView } from '../domain/embed-view.js';
/**
 * Mock implementation of DiscordEmbedBuilder for testing.
 * Records all builder calls for test assertions.
 * Matches Java MockDiscordEmbedBuilder.
 */
export declare class MockDiscordEmbedBuilder implements DiscordEmbedBuilder {
    readonly MAX_TITLE_LENGTH = 256;
    readonly MAX_DESCRIPTION_LENGTH = 4096;
    readonly MAX_FIELD_NAME_LENGTH = 256;
    readonly MAX_FIELD_VALUE_LENGTH = 1024;
    readonly MAX_FIELDS = 25;
    readonly MAX_FOOTER_LENGTH = 2048;
    readonly ELLIPSIS = "...";
    private _title;
    private _description;
    private _color;
    private readonly _fields;
    private _footer;
    setTitle(title: string): DiscordEmbedBuilder;
    setDescription(description: string): DiscordEmbedBuilder;
    setColor(color: number): DiscordEmbedBuilder;
    addField(name: string, value: string, inline: boolean): DiscordEmbedBuilder;
    setFooter(text: string): DiscordEmbedBuilder;
    build(): APIEmbed;
    buildPaginated(data: EmbedView): APIEmbed[];
    getTitle(): string | undefined;
    getDescription(): string | undefined;
    getColor(): number | undefined;
    getFields(): FieldView[];
    getFooter(): string | undefined;
    reset(): void;
    private truncate;
}
