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
    PRIMARY: 0x5865F2,
    /** Discord green — used for success states and balance displays. */
    SUCCESS: 0x57F287,
    /** Discord red — used for danger/error states. */
    DANGER: 0xED4245,
    /** Discord yellow — used for warning states and game settings. */
    WARNING: 0xFEE75C,
    // ============================================================
    // Product panel colors
    // ============================================================
    /** Dark blue-gray for product list embeds. */
    PRODUCT_DEFAULT: 0x2C3E50,
    /** Purple for product code list embeds. */
    PRODUCT_CODES: 0x8E44AD,
    // ============================================================
    // User panel colors
    // ============================================================
    /** Blurple for user panel. */
    USER_PANEL: 0x5865F2,
    /** Green for currency transaction history. */
    HISTORY_CURRENCY: 0x2ECC71,
    /** Purple for token transaction history. */
    HISTORY_TOKEN: 0x9B59B6,
    /** Orange for redemption history. */
    HISTORY_REDEMPTION: 0xE67E22,
};
//# sourceMappingURL=colors.js.map