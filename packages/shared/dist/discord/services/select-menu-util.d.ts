import { StringSelectMenuBuilder, type StringSelectMenuOptionBuilder, ActionRowBuilder } from 'discord.js';
/**
 * Splits options into multiple StringSelectMenuBuilder instances,
 * each with at most MAX_OPTIONS_PER_MENU options.
 * All menus share the same customId.
 */
export declare function splitSelectMenus(options: StringSelectMenuOptionBuilder[], customId: string, placeholder?: string): StringSelectMenuBuilder[];
/**
 * Splits options and wraps each menu in an ActionRow.
 */
export declare function buildSelectRows(options: StringSelectMenuOptionBuilder[], customId: string, placeholder?: string): ActionRowBuilder<StringSelectMenuBuilder>[];
