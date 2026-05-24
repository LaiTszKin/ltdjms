import { ChannelType, OverwriteType, type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import {
  applyAllowDenyChanges,
  escapeJson,
  normalizeOptionalName,
  parseSnowflakeId,
  permissionListToJson,
  permissionNamesFromBits,
} from './permission-modify-helper.js';

export const ModifyCategoryPermissionsParamsSchema = z.object({
  categoryId: z.string(),
  targetId: z.string().optional(),
  targetType: z.enum(['member', 'role']).optional(),
  allowToAdd: z.array(z.string()).optional(),
  allowToRemove: z.array(z.string()).optional(),
  denyToAdd: z.array(z.string()).optional(),
  denyToRemove: z.array(z.string()).optional(),
  newName: z.string().optional(),
});

export type ModifyCategoryPermissionsParams = z.infer<typeof ModifyCategoryPermissionsParamsSchema>;

/**
 * Modifies permission overwrites for a specific category.
 * Tool name: modify_category_permissions
 */
export class ModifyCategoryPermissionsTool {
  readonly name = 'modify_category_permissions';
  readonly description = '修改指定分類的權限設定';
  readonly schema = ModifyCategoryPermissionsParamsSchema;

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(params: ModifyCategoryPermissionsParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    const categoryId = parseSnowflakeId(params.categoryId);
    if (!categoryId) {
      return this.buildErrorResponse('categoryId 未提供');
    }

    const normalizedName = normalizeOptionalName(params.newName);
    if (params.newName !== undefined && normalizedName === null) {
      return this.buildErrorResponse('新的類別名稱不能為空白');
    }
    if (normalizedName && normalizedName.length > 100) {
      return this.buildErrorResponse(`類別名稱不能超過 100 字（當前: ${normalizedName.length}）`);
    }

    const hasPermissionChanges =
      (params.allowToAdd?.length ?? 0) > 0 ||
      (params.allowToRemove?.length ?? 0) > 0 ||
      (params.denyToAdd?.length ?? 0) > 0 ||
      (params.denyToRemove?.length ?? 0) > 0;
    const hasRename = normalizedName !== null;

    if (!hasPermissionChanges && !hasRename) {
      return this.buildErrorResponse('未指定任何權限或名稱修改操作');
    }

    const category = guild.channels.cache.get(categoryId);
    if (!category || category.type !== ChannelType.GuildCategory) {
      return this.buildErrorResponse('找不到指定的類別');
    }

    const targetId = parseSnowflakeId(params.targetId);
    if (hasPermissionChanges && !targetId) {
      return this.buildErrorResponse('targetId 未提供');
    }

    const resolvedTargetType = params.targetType ?? 'role';
    if (hasPermissionChanges && resolvedTargetType !== 'member' && resolvedTargetType !== 'role') {
      return this.buildErrorResponse("targetType 必須是 'member' 或 'role'");
    }

    if (hasPermissionChanges) {
      if (resolvedTargetType === 'member') {
        const member = await guild.members.fetch(targetId!).catch(() => null);
        if (!member) {
          return this.buildErrorResponse('找不到指定的用戶');
        }
      } else if (!guild.roles.cache.get(targetId!)) {
        return this.buildErrorResponse('找不到指定的角色');
      }
    }

    try {
      let beforeAllowed: string[] = [];
      let beforeDenied: string[] = [];
      let afterAllowed: string[] = [];
      let afterDenied: string[] = [];

      if (hasPermissionChanges) {
        const permCategory = category as typeof category & {
          permissionOverwrites: {
            cache: {
              get(
                id: string,
              ): { allow: { bitfield: bigint }; deny: { bitfield: bigint } } | undefined;
            };
            edit(
              id: string,
              options: { allow?: bigint; deny?: bigint; type?: OverwriteType },
            ): Promise<unknown>;
            create(
              id: string,
              options: { allow?: bigint; deny?: bigint; type?: OverwriteType },
              reason?: string,
            ): Promise<unknown>;
          };
        };

        const existing = permCategory.permissionOverwrites.cache.get(targetId!);
        const currentAllow = existing?.allow.bitfield ?? BigInt(0);
        const currentDeny = existing?.deny.bitfield ?? BigInt(0);
        beforeAllowed = permissionNamesFromBits(currentAllow);
        beforeDenied = permissionNamesFromBits(currentDeny);

        const next = applyAllowDenyChanges(
          currentAllow,
          currentDeny,
          params.allowToAdd,
          params.allowToRemove,
          params.denyToAdd,
          params.denyToRemove,
        );
        afterAllowed = permissionNamesFromBits(next.allow);
        afterDenied = permissionNamesFromBits(next.deny);

        const overwriteType =
          resolvedTargetType === 'member' ? OverwriteType.Member : OverwriteType.Role;

        if (existing) {
          await permCategory.permissionOverwrites.edit(targetId!, {
            allow: next.allow,
            deny: next.deny,
            type: overwriteType,
          });
        } else {
          await permCategory.permissionOverwrites.create(
            targetId!,
            { allow: next.allow, deny: next.deny, type: overwriteType },
            '透過 AI Agent 修改分類權限',
          );
        }
      }

      if (hasRename) {
        await category.setName(normalizedName!, '透過 AI Agent 修改分類名稱');
      }

      return this.buildSuccessResponse(
        category.id,
        hasRename ? normalizedName! : category.name,
        hasRename,
        hasPermissionChanges,
        targetId,
        resolvedTargetType,
        beforeAllowed,
        beforeDenied,
        afterAllowed,
        afterDenied,
      );
    } catch (error) {
      return this.buildErrorResponse(
        `修改失敗: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }

  private buildSuccessResponse(
    categoryId: string,
    categoryName: string,
    renamed: boolean,
    permissionsUpdated: boolean,
    targetId: string | null,
    targetType: string,
    beforeAllowed: string[],
    beforeDenied: string[],
    afterAllowed: string[],
    afterDenied: string[],
  ): string {
    const message =
      renamed && permissionsUpdated
        ? '類別名稱與權限修改成功'
        : renamed
          ? '類別名稱修改成功'
          : '類別權限修改成功';

    let json = `{\n  "success": true,\n  "message": "${message}",\n  "categoryId": "${categoryId}",\n  "categoryName": "${escapeJson(categoryName)}",\n  "renamed": ${renamed},\n  "permissionsUpdated": ${permissionsUpdated}`;

    if (permissionsUpdated && targetId) {
      json += `,\n  "targetId": "${targetId}",\n  "targetType": "${targetType}",\n  "before": {\n    "allowed": ${permissionListToJson(beforeAllowed)},\n    "denied": ${permissionListToJson(beforeDenied)}\n  },\n  "after": {\n    "allowed": ${permissionListToJson(afterAllowed)},\n    "denied": ${permissionListToJson(afterDenied)}\n  }`;
    }

    json += '\n}';
    return json;
  }

  private buildErrorResponse(error: string): string {
    return `{\n  "success": false,\n  "error": "${escapeJson(error)}"\n}`;
  }
}
