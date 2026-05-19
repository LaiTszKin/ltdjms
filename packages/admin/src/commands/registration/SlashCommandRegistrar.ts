import { AdminPanelSlashCommand } from '../../panel/admin/definitions/AdminPanelSlashCommand.js';
import { UserPanelSlashCommand } from '../../panel/user/definitions/UserPanelSlashCommand.js';

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

/**
 * Aggregate of all slash command definitions across all packages.
 * Used by the registration script to bulk-register commands with Discord API.
 */
const ALL_COMMAND_DEFINITIONS: SlashCommandDefinition[] = [
  // Admin commands
  AdminPanelSlashCommand,
  UserPanelSlashCommand,
];

/**
 * Registers slash commands with the Discord API via a REST client.
 * Matches Java SlashCommandRegistrar.
 */
export class SlashCommandRegistrar {
  /**
   * Returns all command definitions that should be registered.
   */
  static getAllDefinitions(): SlashCommandDefinition[] {
    return [...ALL_COMMAND_DEFINITIONS];
  }

  /**
   * Registers all commands globally via the provided REST client.
   * Typically called with discord.js REST.put().
   * @param applicationId - Discord application ID
   * @param restPut - Function that calls REST.put() with the given route and body
   * @param guildId - Optional guild ID for development (guild-specific registration)
   */
  static async registerAll(
    applicationId: string,
    restPut: (route: string, body: unknown) => Promise<unknown>,
    guildId?: string,
  ): Promise<{ success: boolean; message: string }> {
    try {
      const definitions = ALL_COMMAND_DEFINITIONS.map((def) => ({
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

      const result = await restPut(route, definitions);
      const count = Array.isArray(result) ? result.length : 0;

      return {
        success: true,
        message: `Successfully registered ${count} commands${guildId ? ` in guild ${guildId}` : ' globally'}`,
      };
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : String(err);
      return {
        success: false,
        message: `Failed to register commands: ${errorMessage}`,
      };
    }
  }
}
