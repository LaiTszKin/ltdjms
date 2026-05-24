import { PermissionFlagsBits } from 'discord.js';

const PERMISSION_KEYS = Object.keys(PermissionFlagsBits) as Array<keyof typeof PermissionFlagsBits>;

/** Parses Discord snowflake IDs, stripping mention wrappers. */
export function parseSnowflakeId(id: string | undefined | null): string | null {
  if (!id?.trim()) {
    return null;
  }

  let trimmed = id.trim();
  if (trimmed.startsWith('<@&') && trimmed.endsWith('>')) {
    trimmed = trimmed.slice(3, -1);
  } else if (trimmed.startsWith('<@') && trimmed.endsWith('>')) {
    trimmed = trimmed.slice(2, -1);
  } else if (trimmed.startsWith('<#') && trimmed.endsWith('>')) {
    trimmed = trimmed.slice(2, -1);
  } else if (trimmed.startsWith('<') && trimmed.endsWith('>')) {
    trimmed = trimmed.slice(1, -1);
  }

  return /^\d+$/.test(trimmed) ? trimmed : null;
}

/** Resolves permission name strings to a combined PermissionFlagsBits bigint. */
export function parsePermissionNames(names: string[] | undefined | null): bigint {
  if (!names?.length) {
    return BigInt(0);
  }

  let bits = BigInt(0);
  for (const name of names) {
    const key = name.toUpperCase().trim() as keyof typeof PermissionFlagsBits;
    const bit = PermissionFlagsBits[key];
    if (bit !== undefined) {
      bits |= bit;
    }
  }
  return bits;
}

/** Converts a PermissionFlagsBits bigint to sorted permission name strings. */
export function permissionNamesFromBits(bits: bigint): string[] {
  const names: string[] = [];
  for (const key of PERMISSION_KEYS) {
    const bit = PermissionFlagsBits[key];
    if ((bits & bit) === bit) {
      names.push(String(key));
    }
  }
  return names.sort();
}

/** Applies incremental allow/deny changes matching Java modify*Settings semantics. */
export function applyAllowDenyChanges(
  currentAllow: bigint,
  currentDeny: bigint,
  allowToAdd?: string[],
  allowToRemove?: string[],
  denyToAdd?: string[],
  denyToRemove?: string[],
): { allow: bigint; deny: bigint } {
  let allow = currentAllow;
  let deny = currentDeny;

  const allowAddBits = parsePermissionNames(allowToAdd);
  if (allowAddBits > BigInt(0)) {
    for (const key of PERMISSION_KEYS) {
      const bit = PermissionFlagsBits[key];
      if ((allowAddBits & bit) === bit) {
        deny &= ~bit;
        allow |= bit;
      }
    }
  }

  if (allowToRemove?.length) {
    allow &= ~parsePermissionNames(allowToRemove);
  }

  const denyAddBits = parsePermissionNames(denyToAdd);
  if (denyAddBits > BigInt(0)) {
    for (const key of PERMISSION_KEYS) {
      const bit = PermissionFlagsBits[key];
      if ((denyAddBits & bit) === bit) {
        allow &= ~bit;
        deny |= bit;
      }
    }
  }

  if (denyToRemove?.length) {
    deny &= ~parsePermissionNames(denyToRemove);
  }

  return { allow, deny };
}

export function normalizeOptionalName(value: string | undefined): string | null {
  if (value === undefined) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export function escapeJson(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t');
}

export function permissionListToJson(permissions: string[]): string {
  if (permissions.length === 0) {
    return '[]';
  }
  return `[${permissions.map((p) => `"${p}"`).join(', ')}]`;
}
