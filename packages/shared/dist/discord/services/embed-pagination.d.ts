import { type APIEmbed } from 'discord.js';
import { type EmbedView } from '../domain/embed-view.js';
/**
 * Limits configuration for embed pagination.
 * Mirrors the constants on DiscordEmbedBuilder implementations.
 */
export interface EmbedLimits {
    readonly MAX_TITLE_LENGTH: number;
    readonly MAX_DESCRIPTION_LENGTH: number;
    readonly MAX_FIELDS: number;
    readonly MAX_FOOTER_LENGTH: number;
    readonly ELLIPSIS: string;
}
/**
 * Pure function that paginates an EmbedView into multiple APIEmbed instances.
 * Handles description overflow and field overflow pagination.
 *
 * Extracted from DiscordJsEmbedBuilder.buildPaginated() and MockDiscordEmbedBuilder.buildPaginated()
 * to eliminate duplication.
 *
 * @param data - the embed view data to paginate
 * @param limits - embed length limits
 * @param truncate - truncation function (allows each caller to inject its own logging)
 * @returns array of APIEmbed instances
 */
export declare function paginateEmbedView(data: EmbedView, limits: EmbedLimits, truncate: (str: string, maxLength: number) => string): APIEmbed[];
