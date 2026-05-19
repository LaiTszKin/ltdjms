import {
  StringSelectMenuBuilder,
  type StringSelectMenuOptionBuilder,
  ActionRowBuilder,
  type StringSelectMenuComponent,
} from 'discord.js';

/**
 * Utility for splitting select menus that exceed Discord's 25-option limit.
 * Matches Java SelectMenuUtil.
 */

const MAX_OPTIONS_PER_MENU = 25;

/**
 * Splits options into multiple StringSelectMenuBuilder instances,
 * each with at most MAX_OPTIONS_PER_MENU options.
 * All menus share the same customId.
 */
export function splitSelectMenus(
  options: StringSelectMenuOptionBuilder[],
  customId: string,
  placeholder?: string,
): StringSelectMenuBuilder[] {
  if (options.length === 0) return [];

  const menus: StringSelectMenuBuilder[] = [];

  for (let i = 0; i < options.length; i += MAX_OPTIONS_PER_MENU) {
    const chunk = options.slice(i, i + MAX_OPTIONS_PER_MENU);
    const menu = new StringSelectMenuBuilder()
      .setCustomId(customId)
      .setPlaceholder(placeholder || 'Select an option')
      .addOptions(chunk);
    menus.push(menu);
  }

  return menus;
}

/**
 * Splits options and wraps each menu in an ActionRow.
 */
export function buildSelectRows(
  options: StringSelectMenuOptionBuilder[],
  customId: string,
  placeholder?: string,
): ActionRowBuilder<StringSelectMenuBuilder>[] {
  return splitSelectMenus(options, customId, placeholder).map((menu) =>
    new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(menu),
  );
}
