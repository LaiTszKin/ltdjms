import { EmbedBuilder, type APIEmbed } from 'discord.js';
import { type EmbedView } from '../domain/embed-view.js';

/**
 * Limits configuration for embed pagination.
 * Mirrors the constants on DiscordEmbedBuilder implementations.
 */
export interface EmbedLimits {
  readonly MAX_TITLE_LENGTH: number;
  readonly MAX_DESCRIPTION_LENGTH: number;
  readonly MAX_FIELD_NAME_LENGTH: number;
  readonly MAX_FIELD_VALUE_LENGTH: number;
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
export function paginateEmbedView(
  data: EmbedView,
  limits: EmbedLimits,
  truncate: (str: string, maxLength: number) => string,
): APIEmbed[] {
  const embeds: APIEmbed[] = [];

  const title = data.title
    ? data.title.length > limits.MAX_TITLE_LENGTH
      ? truncate(data.title, limits.MAX_TITLE_LENGTH)
      : data.title
    : undefined;

  const footer = data.footer
    ? data.footer.length > limits.MAX_FOOTER_LENGTH
      ? truncate(data.footer, limits.MAX_FOOTER_LENGTH)
      : data.footer
    : undefined;

  const description = data.description;

  // Paginate by description
  if (description && description.length > limits.MAX_DESCRIPTION_LENGTH) {
    const totalPages = Math.ceil(
      description.length / limits.MAX_DESCRIPTION_LENGTH,
    );
    for (let i = 0; i < totalPages; i++) {
      const start = i * limits.MAX_DESCRIPTION_LENGTH;
      const end = Math.min(
        start + limits.MAX_DESCRIPTION_LENGTH,
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
    if (totalAdded > 0 && totalAdded % limits.MAX_FIELDS === 0) {
      embeds.push(builder.toJSON());
      pageIndex++;
      builder = new EmbedBuilder();
      if (title) {
        builder.setTitle(
          data.fields.length > limits.MAX_FIELDS
            ? `${title} (${pageIndex + 1})`
            : title,
        );
      }
      if (data.color) builder.setColor(data.color);
      if (footer) builder.setFooter({ text: footer });
    }
    builder.addFields({
      name: truncate(field.name, limits.MAX_FIELD_NAME_LENGTH),
      value: truncate(field.value, limits.MAX_FIELD_VALUE_LENGTH),
      inline: field.inline,
    });
    totalAdded++;
  }
  embeds.push(builder.toJSON());

  return embeds;
}
