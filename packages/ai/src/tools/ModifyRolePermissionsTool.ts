import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import {
  escapeJson,
  normalizeOptionalName,
  parsePermissionNames,
  parseSnowflakeId,
  permissionListToJson,
  permissionNamesFromBits,
} from './permission-modify-helper.js';

export const ModifyRolePermissionsParamsSchema = z.object({
  roleId: z.string(),
  newName: z.string().optional(),
  permissionsToAdd: z.array(z.string()).optional(),
  permissionsToRemove: z.array(z.string()).optional(),
});

export type ModifyRolePermissionsParams = z.infer<typeof ModifyRolePermissionsParamsSchema>;

/**
 * Modifies permissions for a specific role.
 * Tool name: modify_role_permissions
 */
export class ModifyRolePermissionsTool {
  readonly name = 'modify_role_permissions';
  readonly description = '修改指定身分組的權限設定';
  readonly schema = ModifyRolePermissionsParamsSchema;

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(params: ModifyRolePermissionsParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    const roleId = parseSnowflakeId(params.roleId);
    if (!roleId) {
      return this.buildErrorResponse('roleId 未提供');
    }

    const role = guild.roles.cache.get(roleId);
    if (!role) {
      return this.buildErrorResponse('找不到指定的角色');
    }

    const normalizedName = normalizeOptionalName(params.newName);
    if (params.newName !== undefined && normalizedName === null) {
      return this.buildErrorResponse('新的角色名稱不能為空白');
    }
    if (normalizedName && normalizedName.length > 100) {
      return this.buildErrorResponse(`角色名稱不能超過 100 字（當前: ${normalizedName.length}）`);
    }

    const hasPermissionChanges =
      (params.permissionsToAdd?.length ?? 0) > 0 || (params.permissionsToRemove?.length ?? 0) > 0;
    const hasRename = normalizedName !== null;

    if (!hasPermissionChanges && !hasRename) {
      return this.buildErrorResponse('未指定任何權限或名稱修改操作');
    }

    try {
      const beforePermissions = permissionNamesFromBits(role.permissions.bitfield);
      let afterPermissions = beforePermissions;

      if (hasPermissionChanges) {
        let nextPermissions = role.permissions.bitfield;
        if (params.permissionsToAdd?.length) {
          nextPermissions |= parsePermissionNames(params.permissionsToAdd);
        }
        if (params.permissionsToRemove?.length) {
          nextPermissions &= ~parsePermissionNames(params.permissionsToRemove);
        }
        afterPermissions = permissionNamesFromBits(nextPermissions);
        await role.setPermissions(nextPermissions, '透過 AI Agent 修改身分組權限');
      }

      if (hasRename) {
        await role.setName(normalizedName!, '透過 AI Agent 修改身分組名稱');
      }

      const effectiveName = hasRename ? normalizedName! : role.name;
      const message =
        hasRename && hasPermissionChanges
          ? '角色名稱與權限修改成功'
          : hasRename
            ? '角色名稱修改成功'
            : '角色權限修改成功';

      let json = `{\n  "success": true,\n  "message": "${message}",\n  "role": {\n    "id": "${role.id}",\n    "name": "${escapeJson(effectiveName)}"\n  },\n  "renamed": ${hasRename},\n  "permissionsUpdated": ${hasPermissionChanges}`;

      if (hasPermissionChanges) {
        json += `,\n  "before": {\n    "permissions": ${permissionListToJson(beforePermissions)},\n    "count": ${beforePermissions.length},\n    "raw": "${role.permissions.bitfield.toString()}"\n  },\n  "after": {\n    "permissions": ${permissionListToJson(afterPermissions)},\n    "count": ${afterPermissions.length},\n    "raw": "${role.permissions.bitfield.toString()}"\n  }`;
      }

      json += '\n}';
      return json;
    } catch (error) {
      return this.buildErrorResponse(
        `修改失敗: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }

  private buildErrorResponse(error: string): string {
    return `{\n  "success": false,\n  "error": "${escapeJson(error)}"\n}`;
  }
}
