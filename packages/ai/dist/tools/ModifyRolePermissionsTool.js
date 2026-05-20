import { PermissionFlagsBits } from 'discord.js';
import { z } from 'zod';
export const ModifyRolePermissionsParamsSchema = z.object({
    roleId: z.string(),
    permissions: z.array(z.object({
        allow: z.string().optional(),
        deny: z.string().optional(),
        allowSet: z.array(z.string()).optional(),
        denySet: z.array(z.string()).optional(),
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
            // Accumulate all allow/deny bits first, then apply once (P1-26 fix)
            let allowBits = BigInt(0);
            let denyBits = BigInt(0);
            for (const perm of params.permissions) {
                if (perm.allow) {
                    allowBits |= BigInt(perm.allow);
                }
                if (perm.deny) {
                    denyBits |= BigInt(perm.deny);
                }
                if (perm.allowSet) {
                    for (const name of perm.allowSet) {
                        const key = name.toUpperCase();
                        const bit = PermissionFlagsBits[key];
                        if (bit !== undefined)
                            allowBits |= bit;
                    }
                }
                if (perm.denySet) {
                    for (const name of perm.denySet) {
                        const key = name.toUpperCase();
                        const bit = PermissionFlagsBits[key];
                        if (bit !== undefined)
                            denyBits |= bit;
                    }
                }
            }
            // Apply accumulated permissions in a single call
            let finalPerms = role.permissions;
            if (allowBits > BigInt(0)) {
                finalPerms = finalPerms.add(allowBits);
            }
            if (denyBits > BigInt(0)) {
                finalPerms = finalPerms.remove(denyBits);
            }
            await role.setPermissions(finalPerms, '透過 AI Agent 修改身分組權限');
            return `已成功修改身分組 ${role.name} 的權限設定`;
        }
        catch (error) {
            return `修改身分組權限失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=ModifyRolePermissionsTool.js.map