/**
 * User panel customId constants. Mirrors Java UserPanelButtonHandler public static finals.
 */
export const UserPanelConstants = {
  BUTTON_PREFIX_TOKEN_HISTORY: 'user_panel_token_history',
  BUTTON_PREFIX_TOKEN_PAGE: 'user_panel_token_page_',

  BUTTON_PREFIX_CURRENCY_HISTORY: 'user_panel_currency_history',
  BUTTON_PREFIX_CURRENCY_PAGE: 'user_panel_currency_page_',

  BUTTON_PREFIX_PRODUCT_REDEMPTION_HISTORY: 'user_panel_product_redemption_history',
  BUTTON_PREFIX_PRODUCT_REDEMPTION_PAGE: 'user_panel_product_redemption_page_',

  BUTTON_REDEEM: 'user_panel_redeem',
  MODAL_REDEEM: 'user_panel_modal_redeem',
  MODAL_REDEEM_CODE_FIELD: 'code',

  BUTTON_BACK_TO_PANEL: 'user_panel_back',

  /** Routing prefix for SlashCommandListener interaction dispatch. */
  ROUTING_PREFIX: 'user_panel_',
} as const;

export const USER_PANEL_PAGE_SIZE = 10;

export const USER_PANEL_EMBED_COLOR = 0x5865f2;

export const USER_PANEL_FOOTER_INITIAL = '點擊下方按鈕查看流水紀錄或兌換碼';

/** Shorter footer used on push updates (matches Java UserPanelUpdateListener). */
export const USER_PANEL_FOOTER_PUSH_UPDATE = '點擊下方按鈕查看流水紀錄';
