import { PermissionFlagsBits } from 'discord.js';
/**
 * Parses PermissionSetting[] into discord.js OverwriteResolvable[].
 * Matches Java PermissionParser.
 */
export class PermissionParser {
    /**
     * Converts PermissionSetting[] to OverwriteResolvable[].
     */
    parse(permissions) {
        return permissions.map((perm) => {
            const overwrite = {
                id: perm.id,
                type: perm.type === 'member' ? 1 : 0,
            };
            // Support both old format (allow/deny as BitField) and new format (allowSet/denySet as string[])
            const ow = overwrite;
            if (perm.allow !== undefined || perm.deny !== undefined) {
                ow.allow = perm.allow ?? BigInt(0);
                ow.deny = perm.deny ?? BigInt(0);
            }
            if (perm.allowSet && perm.allowSet.length > 0) {
                let allowBits = BigInt(0);
                for (const permName of perm.allowSet) {
                    const bit = this.resolvePermission(permName);
                    if (bit !== null)
                        allowBits |= bit;
                }
                ow.allow = allowBits;
            }
            if (perm.denySet && perm.denySet.length > 0) {
                let denyBits = BigInt(0);
                for (const permName of perm.denySet) {
                    const bit = this.resolvePermission(permName);
                    if (bit !== null)
                        denyBits |= bit;
                }
                ow.deny = denyBits;
            }
            return overwrite;
        });
    }
    /**
     * Resolves a permission name string to a PermissionFlagsBits BigInt.
     */
    resolvePermission(name) {
        const key = name.toUpperCase();
        return PermissionFlagsBits[key] ?? null;
    }
}
//# sourceMappingURL=PermissionParser.js.map