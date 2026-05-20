import { z } from 'zod';
export const GetRolePermissionsParamsSchema = z.object({
    roleId: z.string(),
});
/**
 * Gets permissions for a specific role.
 * Tool name: get_role_permissions
 */
export class GetRolePermissionsTool {
    authGuard;
    name = 'get_role_permissions';
    description = '獲取指定身分組的權限設定';
    schema = GetRolePermissionsParamsSchema;
    constructor(authGuard) {
        this.authGuard = authGuard;
    }
    async execute(params, guild) {
        const authError = await this.authGuard.validateAdministrator(guild, this.name);
        if (authError)
            return authError;
        try {
            const role = guild.roles.cache.get(params.roleId);
            if (!role) {
                return `找不到身分組 ${params.roleId}`;
            }
            return JSON.stringify({
                roleId: params.roleId,
                roleName: role.name,
                color: role.hexColor,
                permissions: role.permissions.toArray(),
                position: role.position,
            }, null, 2);
        }
        catch (error) {
            return `獲取身分組權限失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=GetRolePermissionsTool.js.map