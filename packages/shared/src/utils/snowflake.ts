/**
 * Converts a Discord snowflake string to a JavaScript number.
 *
 * Discord snowflakes (64-bit) can exceed Number.MAX_SAFE_INTEGER (9e15),
 * causing silent precision loss when parsed via Number().
 * This function detects such loss and logs a warning instead of throwing,
 * allowing the application to function with imprecise IDs for testing.
 *
 * A full BigInt migration is needed for production 64-bit support.
 */
export function safeSnowflakeToNumber(id: string): number {
  const num = Number(id);
  if (!Number.isSafeInteger(num) || String(num) !== id) {
    console.warn(
      `[snowflake] ID ${id} loses precision as number ${num}. ` +
        `Consider BigInt migration for full 64-bit Discord snowflake support.`,
    );
  }
  return num;
}
