import { PermissionFlagsBits } from 'discord.js';
import { z } from 'zod';
export const CreateRoleParamsSchema = z.object({
    name: z.string().min(1).max(100),
    color: z.string().optional(),
    permissions: z
        .array(z.object({
        id: z.string(),
        type: z.enum(['role', 'member']),
        allow: z.string().optional(),
        deny: z.string().optional(),
        allowSet: z.array(z.string()).optional(),
        denySet: z.array(z.string()).optional(),
    }))
        .optional(),
});
/**
 * Creates a role in the guild.
 * Tool name: create_role
 */
export class CreateRoleTool {
    authGuard;
    name = 'create_role';
    description = '在伺服器中創建一個新的身分組';
    schema = CreateRoleParamsSchema;
    constructor(authGuard) {
        this.authGuard = authGuard;
    }
    async execute(params, guild) {
        const authError = await this.authGuard.validateAdministrator(guild, this.name);
        if (authError)
            return authError;
        try {
            const roleOptions = {
                name: params.name,
                reason: '透過 AI Agent 創建身分組',
            };
            if (params.color) {
                roleOptions.color = params.color;
            }
            const role = await guild.roles.create(roleOptions);
            // Apply permissions after role creation (P1-25 fix)
            if (params.permissions && params.permissions.length > 0) {
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
                // Start from default permissions, apply allow and deny
                let finalPerms = role.permissions;
                if (allowBits > BigInt(0)) {
                    finalPerms = finalPerms.add(allowBits);
                }
                if (denyBits > BigInt(0)) {
                    finalPerms = finalPerms.remove(denyBits);
                }
                await role.setPermissions(finalPerms, '透過 AI Agent 設定身分組權限');
            }
            return `已成功創建身分組「${role.name}」(ID: ${role.id})`;
        }
        catch (error) {
            return `創建身分組失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=CreateRoleTool.js.map