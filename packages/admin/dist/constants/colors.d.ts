/**
 * Shared color constants for admin panel embeds.
 *
 * All admin panel view factories should use these constants
 * instead of hardcoded hex values to ensure visual consistency.
 *
 * Standardized to uppercase hex (P3-29).
 */
export declare const Colors: {
    /** Discord blurple — used for primary/admin panels. */
    readonly PRIMARY: 5793266;
    /** Discord green — used for success states and balance displays. */
    readonly SUCCESS: 5763719;
    /** Discord red — used for danger/error states. */
    readonly DANGER: 15548997;
    /** Discord yellow — used for warning states and game settings. */
    readonly WARNING: 16705372;
    /** Dark blue-gray for product list embeds. */
    readonly PRODUCT_DEFAULT: 2899536;
    /** Purple for product code list embeds. */
    readonly PRODUCT_CODES: 9323693;
    /** Blurple for user panel. */
    readonly USER_PANEL: 5793266;
    /** Green for currency transaction history. */
    readonly HISTORY_CURRENCY: 3066993;
    /** Purple for token transaction history. */
    readonly HISTORY_TOKEN: 10181046;
    /** Orange for redemption history. */
    readonly HISTORY_REDEMPTION: 15105570;
};
