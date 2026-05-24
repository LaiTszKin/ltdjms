import { PermissionFlagsBits, type OverwriteResolvable } from 'discord.js';
import type { PermissionSetting } from '../services/ai-chat-service.js';
import { parsePermissionNames } from './permission-modify-helper.js';

export interface ChannelPermissionSettingInput {
  roleId: string;
  allowSet?: string[];
  denySet?: string[];
  permissionSet?: string;
}

/**
 * Parses PermissionSetting[] into discord.js OverwriteResolvable[].
 * Matches Java PermissionParser.
 */
export class PermissionParser {
  /**
   * Converts PermissionSetting[] to OverwriteResolvable[].
   */
  parse(permissions: PermissionSetting[]): OverwriteResolvable[] {
    return permissions.map((perm) => {
      const overwrite: OverwriteResolvable = {
        id: perm.id,
        type: perm.type === 'member' ? 1 : 0,
      };

      // Support both old format (allow/deny as BitField) and new format (allowSet/denySet as string[])
      const ow = overwrite as { id: string; type: number; allow?: bigint; deny?: bigint };
      if (perm.allow !== undefined || perm.deny !== undefined) {
        ow.allow = perm.allow ?? BigInt(0);
        ow.deny = perm.deny ?? BigInt(0);
      }

      if (perm.allowSet && perm.allowSet.length > 0) {
        let allowBits = BigInt(0);
        for (const permName of perm.allowSet) {
          const bit = this.resolvePermission(permName);
          if (bit !== null) allowBits |= bit;
        }
        ow.allow = allowBits;
      }

      if (perm.denySet && perm.denySet.length > 0) {
        let denyBits = BigInt(0);
        for (const permName of perm.denySet) {
          const bit = this.resolvePermission(permName);
          if (bit !== null) denyBits |= bit;
        }
        ow.deny = denyBits;
      }

      return overwrite;
    });
  }

  /** Parses Java-aligned channel permission settings for create channel/category tools. */
  parseChannelPermissionSettings(settings: ChannelPermissionSettingInput[]): OverwriteResolvable[] {
    return settings.map((setting) => {
      const allowSet = this.resolveAllowSet(setting.allowSet, setting.permissionSet);
      const denySet = setting.denySet ?? [];
      return {
        id: setting.roleId,
        type: 0,
        allow: parsePermissionNames(allowSet),
        deny: parsePermissionNames(denySet),
      };
    });
  }

  private resolveAllowSet(allowSet?: string[], permissionSet?: string): string[] {
    if (allowSet?.length) {
      return allowSet;
    }
    if (!permissionSet?.trim()) {
      return [];
    }

    switch (permissionSet.trim().toLowerCase()) {
      case 'admin_only':
      case 'admin-only':
      case 'admins_only':
        return ['ADMINISTRATOR', 'VIEW_CHANNEL', 'MESSAGE_SEND'];
      case 'private':
      case 'private_only':
        return ['VIEW_CHANNEL', 'MESSAGE_SEND'];
      case 'read_only':
      case 'readonly':
        return ['VIEW_CHANNEL'];
      case 'full':
      case 'all':
        return [
          'ADMINISTRATOR',
          'MANAGE_CHANNELS',
          'MANAGE_ROLES',
          'MANAGE_GUILD',
          'VIEW_CHANNEL',
          'SEND_MESSAGES',
          'READ_MESSAGE_HISTORY',
          'CONNECT',
          'SPEAK',
          'PRIORITY_SPEAKER',
        ];
      default:
        return [];
    }
  }

  /**
   * Resolves a permission name string to a PermissionFlagsBits BigInt.
   */
  private resolvePermission(name: string): bigint | null {
    const key = name.toUpperCase() as keyof typeof PermissionFlagsBits;
    return PermissionFlagsBits[key] ?? null;
  }
}
