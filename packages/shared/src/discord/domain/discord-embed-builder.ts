import { type EmbedView } from './embed-view.js';

/**
 * Discord Embed builder abstraction with automatic length enforcement.
 * Matches Java DiscordEmbedBuilder interface.
 *
 * Discord API length limits:
 * - Title: 256 chars
 * - Description: 4096 chars
 * - Field Name: 256 chars
 * - Field Value: 1024 chars
 * - Fields: 25 max
 * - Footer: 2048 chars
 */
export interface DiscordEmbedBuilder {
  readonly MAX_TITLE_LENGTH: number;
  readonly MAX_DESCRIPTION_LENGTH: number;
  readonly MAX_FIELD_NAME_LENGTH: number;
  readonly MAX_FIELD_VALUE_LENGTH: number;
  readonly MAX_FIELDS: number;
  readonly MAX_FOOTER_LENGTH: number;
  readonly ELLIPSIS: string;

  /** Sets the embed title (truncated to 256 chars). */
  setTitle(title: string): DiscordEmbedBuilder;

  /** Sets the embed description (truncated to 4096 chars). */
  setDescription(description: string): DiscordEmbedBuilder;

  /** Sets the embed color. */
  setColor(color: number): DiscordEmbedBuilder;

  /** Adds a field (truncated, max 25 fields). */
  addField(name: string, value: string, inline: boolean): DiscordEmbedBuilder;

  /** Sets the footer text (truncated to 2048 chars). */
  setFooter(text: string): DiscordEmbedBuilder;

  /** Builds a single embed. */
  build(): unknown;

  /** Builds multiple embeds with automatic pagination. */
  buildPaginated(data: EmbedView): unknown[];
}
