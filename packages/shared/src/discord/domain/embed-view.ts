import { EmbedBuilder, ButtonBuilder } from 'discord.js';

/**
 * Immutable data structure for Embed view data.
 * Provides decoupling from discord.js MessageEmbed.
 * Matches Java EmbedView record.
 */
export interface EmbedView {
  readonly title?: string;
  readonly description?: string;
  readonly color?: number;
  readonly fields?: FieldView[];
  readonly footer?: string;
}

/**
 * Field view for an embed field.
 * Matches Java EmbedView.FieldView record.
 */
export interface FieldView {
  readonly name: string;
  readonly value: string;
  readonly inline: boolean;
}

/**
 * Immutable data structure for Button view data.
 * Matches Java ButtonView record.
 */
export interface ButtonView {
  readonly id: string;
  readonly label: string;
  readonly style: ButtonStyle;
  readonly disabled: boolean;
}

/** Button style enum matching discord.js ButtonStyle. */
export enum ButtonStyle {
  PRIMARY = 1,
  SECONDARY = 2,
  SUCCESS = 3,
  DANGER = 4,
  LINK = 5,
}

/**
 * Creates a ButtonView with length validation on id and label.
 * Discord limits: id max 100 chars, label max 80 chars.
 * Throws if either exceeds the limit.
 */
export function createButtonView(
  id: string,
  label: string,
  style: ButtonStyle = ButtonStyle.PRIMARY,
  disabled = false,
): ButtonView {
  if (id.length > 100) {
    throw new Error(`ButtonView id exceeds 100 character limit: ${id.length} chars`);
  }
  if (label.length > 80) {
    throw new Error(`ButtonView label exceeds 80 character limit: ${label.length} chars`);
  }
  return { id, label, style, disabled } as ButtonView;
}

/**
 * Converts an EmbedView to a discord.js EmbedBuilder.
 * Pure data transformation — no side effects.
 * Matches spec R8.5 requirement for paginated embed construction support.
 */
export function toDiscordJsEmbed(view: EmbedView): EmbedBuilder {
  const embed = new EmbedBuilder();
  if (view.title) embed.setTitle(view.title);
  if (view.description) embed.setDescription(view.description);
  if (view.color !== undefined) embed.setColor(view.color);
  if (view.footer) embed.setFooter({ text: view.footer });
  if (view.fields) {
    embed.addFields(
      view.fields.map((f) => ({ name: f.name, value: f.value, inline: f.inline })),
    );
  }
  return embed;
}

/**
 * Converts a ButtonView to a discord.js ButtonBuilder.
 * Pure data transformation — no side effects.
 * Matches spec R8.5 requirement for paginated embed construction support.
 */
export function toDiscordJsButton(view: ButtonView): ButtonBuilder {
  return new ButtonBuilder()
    .setCustomId(view.id)
    .setLabel(view.label)
    .setStyle(view.style as unknown as number)
    .setDisabled(view.disabled);
}
