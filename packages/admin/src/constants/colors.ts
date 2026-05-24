/**
 * Shared color constants for admin panel embeds.
 *
 * All admin panel view factories should use these constants
 * instead of hardcoded hex values to ensure visual consistency.
 *
 * Standardized to uppercase hex (P3-29).
 */
export const Colors = {
  /** Discord blurple — used for primary/admin panels. */
  PRIMARY: 0x5865f2,
  /** Discord green — used for success states and balance displays. */
  SUCCESS: 0x57f287,
  /** Discord red — used for danger/error states. */
  DANGER: 0xed4245,
  /** Discord yellow — used for warning states and game settings. */
  WARNING: 0xfee75c,

  // ============================================================
  // Product panel colors
  // ============================================================
  /** Dark blue-gray for product list embeds. */
  PRODUCT_DEFAULT: 0x2c3e50,
  /** Purple for product code list embeds. */
  PRODUCT_CODES: 0x8e44ad,
} as const;
