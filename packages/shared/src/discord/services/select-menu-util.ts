import {
  StringSelectMenuBuilder,
  type StringSelectMenuOptionBuilder,
  ActionRowBuilder,
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
 * Generic version of splitSelectMenus that accepts items of any type
 * and a callback to configure each StringSelectMenuBuilder with the item.
 * Useful when menu options are derived from domain objects rather than
 * pre-built StringSelectMenuOptionBuilder instances.
 *
 * @param items - array of items to distribute across menus
 * @param builderCallback - callback that adds the item to the menu builder
 * @param customId - custom ID for all menus
 * @param placeholder - optional placeholder text
 * @returns array of StringSelectMenuBuilder instances
 */
export function splitSelectMenusGeneric<T>(
  items: T[],
  builderCallback: (builder: StringSelectMenuBuilder, item: T) => void,
  customId: string,
  placeholder?: string,
): StringSelectMenuBuilder[] {
  if (items.length === 0) return [];

  const menus: StringSelectMenuBuilder[] = [];

  for (let i = 0; i < items.length; i += MAX_OPTIONS_PER_MENU) {
    const chunk = items.slice(i, i + MAX_OPTIONS_PER_MENU);
    const menu = new StringSelectMenuBuilder()
      .setCustomId(customId)
      .setPlaceholder(placeholder || 'Select an option');

    for (const item of chunk) {
      builderCallback(menu, item);
    }

    menus.push(menu);
  }

  return menus;
}

/**
 * Splits options and wraps each menu in an ActionRow.
 */
function buildSelectRows(
  options: StringSelectMenuOptionBuilder[],
  customId: string,
  placeholder?: string,
): ActionRowBuilder<StringSelectMenuBuilder>[] {
  return splitSelectMenus(options, customId, placeholder).map((menu) =>
    new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(menu),
  );
}
