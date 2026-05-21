/**
 * Converts a Discord snowflake string to a JavaScript number with precision loss detection.
 *
 * Discord snowflakes (64-bit) can exceed Number.MAX_SAFE_INTEGER (9e15),
 * causing silent precision loss when parsed via Number().
 * This function detects such loss and throws a descriptive error.
 *
 * For production use, a full BigInt migration would be needed to truly
 * support the full snowflake range. This guard ensures corruption is
 * detected rather than silent.
 */
export function safeSnowflakeToNumber(id: string): number {
  const num = Number(id);
  if (!Number.isSafeInteger(num)) {
    throw new Error(
      `Snowflake ID ${id} cannot be safely stored as number (result: ${num}). ` +
      `The application must be migrated to BigInt-based ID handling for full 64-bit support.`,
    );
  }
  if (String(num) !== id) {
    throw new Error(
      `Snowflake ID ${id} loses precision when converted to number (result: ${num}). ` +
      `The application must be migrated to BigInt-based ID handling for full 64-bit support.`,
    );
  }
  return num;
}
