import { PermissionFlagsBits, type OverwriteResolvable } from 'discord.js';
import type { PermissionSetting } from '../services/ai-chat-service.js';

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

  /**
   * Resolves a permission name string to a PermissionFlagsBits BigInt.
   */
  private resolvePermission(name: string): bigint | null {
    const key = name.toUpperCase() as keyof typeof PermissionFlagsBits;
    return PermissionFlagsBits[key] ?? null;
  }
}
