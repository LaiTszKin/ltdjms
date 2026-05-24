import { CommandLocalizations } from '@ltdjms/shared';
import { AdminPanelSlashCommand } from '../../panel/admin/definitions/AdminPanelSlashCommand.js';
import {
  BalanceSlashCommand,
  AdjustBalanceSlashCommand,
  DiceGame1SlashCommand,
  DiceGame2SlashCommand,
  GameTokenAdjustSlashCommand,
  DiceGame1ConfigSlashCommand,
  DiceGame2ConfigSlashCommand,
  CurrencyConfigSlashCommand,
} from './EconomySlashCommands.js';
import { DispatchPanelSlashCommand } from './DispatchSlashCommands.js';

/**
 * A slash command definition suitable for Discord API registration.
 */
export interface SlashCommandDefinition {
  name: string;
  description: string;
  options?: unknown[];
  defaultMemberPermissions?: string | null;
  nameLocalizations?: Record<string, string>;
  descriptionLocalizations?: Record<string, string>;
}

/** /shop — open the guild shop (available to all members). */
const ShopSlashCommand: SlashCommandDefinition = {
  name: 'shop',
  description: '開啟商城',
  defaultMemberPermissions: null,
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['shop'],
  descriptionLocalizations: CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['shop'],
};

/**
 * Core slash command definitions owned by admin and downstream packages (economy, shop, dispatch).
 * User-panel commands are composed at the app layer to avoid admin → user-panel coupling.
 */
const CORE_COMMAND_DEFINITIONS: SlashCommandDefinition[] = [
  AdminPanelSlashCommand,
  BalanceSlashCommand,
  AdjustBalanceSlashCommand,
  DiceGame1SlashCommand,
  DiceGame2SlashCommand,
  GameTokenAdjustSlashCommand,
  DiceGame1ConfigSlashCommand,
  DiceGame2ConfigSlashCommand,
  CurrencyConfigSlashCommand,
  ShopSlashCommand,
  DispatchPanelSlashCommand,
];

/**
 * Registers slash commands with the Discord API via a REST client.
 * Matches Java SlashCommandRegistrar.
 */
export class SlashCommandRegistrar {
  /**
   * Returns admin-owned command definitions (excludes user-panel package commands).
   */
  static getCoreDefinitions(): SlashCommandDefinition[] {
    return [...CORE_COMMAND_DEFINITIONS];
  }

  /**
   * Returns all command definitions that should be registered.
   * @deprecated Prefer app-layer composition via getAllSlashCommandDefinitions().
   */
  static getAllDefinitions(): SlashCommandDefinition[] {
    return [...CORE_COMMAND_DEFINITIONS];
  }

  /**
   * Registers commands globally via the provided REST client.
   * Typically called with discord.js REST.put().
   */
  static async registerAll(
    applicationId: string,
    restPut: (route: string, body: unknown) => Promise<unknown>,
    guildId?: string,
    definitions: SlashCommandDefinition[] = CORE_COMMAND_DEFINITIONS,
  ): Promise<{ success: boolean; message: string }> {
    return SlashCommandRegistrar.registerDefinitions(definitions, applicationId, restPut, guildId);
  }

  /**
   * Registers the given command definitions with the Discord API.
   */
  static async registerDefinitions(
    definitions: SlashCommandDefinition[],
    applicationId: string,
    restPut: (route: string, body: unknown) => Promise<unknown>,
    guildId?: string,
  ): Promise<{ success: boolean; message: string }> {
    try {
      const payload = definitions.map((def) => ({
        name: def.name,
        name_localizations: def.nameLocalizations ?? {},
        description: def.description,
        description_localizations: def.descriptionLocalizations ?? {},
        options: def.options ?? [],
        default_member_permissions: def.defaultMemberPermissions ?? null,
      }));

      const route = guildId
        ? `/applications/${applicationId}/guilds/${guildId}/commands`
        : `/applications/${applicationId}/commands`;

      const result = await restPut(route, payload);
      const count = Array.isArray(result) ? result.length : 0;

      return {
        success: true,
        message: `Successfully registered ${count} commands${guildId ? ` in guild ${guildId}` : ' globally'}`,
      };
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err);
      return {
        success: false,
        message: `Failed to register commands: ${errorMessage}`,
      };
    }
  }
}
