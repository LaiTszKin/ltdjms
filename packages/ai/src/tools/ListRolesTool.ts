import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { TOOL_DESCRIPTIONS } from './tool-descriptions.js';

/**
 * Lists all roles in the guild.
 * Tool name: list_roles
 */
export class ListRolesTool {
  readonly name = 'list_roles';
  readonly description = TOOL_DESCRIPTIONS.list_roles;
  readonly schema = z.object({});

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(_params: unknown, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      const roles = guild.roles.cache
        .filter((r) => r.name !== '@everyone')
        .map((r) => ({
          id: r.id,
          name: r.name,
          color: r.hexColor,
          position: r.position,
          permissions: r.permissions.toArray(),
        }));

      return JSON.stringify(roles, null, 2);
    } catch (error) {
      return `列出身分組失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
