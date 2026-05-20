import { z } from 'zod';
export const ModifyRolePermissionsParamsSchema = z.object({
    roleId: z.string(),
    permissions: z.array(z.object({
        id: z.string(),
        type: z.enum(['role', 'member']),
        allow: z.string().optional(),
        deny: z.string().optional(),
    })),
});
/**
 * Modifies permissions for a specific role.
 * Tool name: modify_role_permissions
 */
export class ModifyRolePermissionsTool {
    authGuard;
    name = 'modify_role_permissions';
    description = '修改指定身分組的權限設定';
    schema = ModifyRolePermissionsParamsSchema;
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
            // Process permission changes
            for (const perm of params.permissions) {
                if (perm.allow) {
                    const bits = BigInt(perm.allow);
                    await role.setPermissions(bits, `透過 AI Agent 修改身分組權限 - allow`);
                }
                if (perm.deny) {
                    // deny is handled through the permissions set
                    const currentPerms = role.permissions;
                    const denyBits = BigInt(perm.deny);
                    const newPerms = currentPerms.remove(denyBits);
                    await role.setPermissions(newPerms, `透過 AI Agent 修改身分組權限 - deny`);
                }
            }
            return `已成功修改身分組 ${role.name} 的權限設定`;
        }
        catch (error) {
            return `修改身分組權限失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=ModifyRolePermissionsTool.js.map