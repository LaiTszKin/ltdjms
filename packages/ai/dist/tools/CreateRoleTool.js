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
            return `已成功創建身分組「${role.name}」(ID: ${role.id})`;
        }
        catch (error) {
            return `創建身分組失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=CreateRoleTool.js.map