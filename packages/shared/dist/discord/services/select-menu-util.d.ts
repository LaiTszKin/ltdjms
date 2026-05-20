import { StringSelectMenuBuilder, type StringSelectMenuOptionBuilder, ActionRowBuilder } from 'discord.js';
/**
 * Splits options into multiple StringSelectMenuBuilder instances,
 * each with at most MAX_OPTIONS_PER_MENU options.
 * All menus share the same customId.
 */
export declare function splitSelectMenus(options: StringSelectMenuOptionBuilder[], customId: string, placeholder?: string): StringSelectMenuBuilder[];
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
export declare function splitSelectMenusGeneric<T>(items: T[], builderCallback: (builder: StringSelectMenuBuilder, item: T) => void, customId: string, placeholder?: string): StringSelectMenuBuilder[];
/**
 * Splits options and wraps each menu in an ActionRow.
 */
export declare function buildSelectRows(options: StringSelectMenuOptionBuilder[], customId: string, placeholder?: string): ActionRowBuilder<StringSelectMenuBuilder>[];
