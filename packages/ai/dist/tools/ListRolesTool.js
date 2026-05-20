import { z } from 'zod';
/**
 * Lists all roles in the guild.
 * Tool name: list_roles
 */
export class ListRolesTool {
    authGuard;
    name = 'list_roles';
    description = '列出伺服器中的所有身分組';
    schema = z.object({});
    constructor(authGuard) {
        this.authGuard = authGuard;
    }
    async execute(_params, guild) {
        const authError = await this.authGuard.validateAdministrator(guild, this.name);
        if (authError)
            return authError;
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
        }
        catch (error) {
            return `列出身分組失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=ListRolesTool.js.map