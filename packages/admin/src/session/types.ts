/**
 * Admin panel view state.
 * Tracks which screen the admin is currently viewing.
 */
export enum AdminPanelViewState {
  MAIN = 'MAIN',
  PRODUCT_LIST = 'PRODUCT_LIST',
  PRODUCT_DETAIL = 'PRODUCT_DETAIL',
  PRODUCT_CODE_LIST = 'PRODUCT_CODE_LIST',
  BALANCE = 'BALANCE',
  TOKEN = 'TOKEN',
  GAME_CONFIG = 'GAME_CONFIG',
  AI_CHANNEL = 'AI_CHANNEL',
  AI_AGENT = 'AI_AGENT',
  DISPATCH_STAFF = 'DISPATCH_STAFF',
  ESCORT_PRICING = 'ESCORT_PRICING',
  ESCORT_CATALOG = 'ESCORT_CATALOG',
}

/**
 * Admin panel session data stored in the session.
 */
export interface AdminPanelSessionData {
  guildId: string;
  userId: string;
  viewState: AdminPanelViewState;
  context: Record<string, string>;
  createdAt: number;
  lastAccessedAt: number;
  /** The channel ID where the admin panel was last rendered. Used by listeners for push updates. */
  channelId?: string;
  /** The message ID of the last panel embed. Used by listeners for push updates. */
  messageId?: string;
}

/**
 * User panel session data stored in the session.
 */
export interface PanelSessionData {
  guildId: string;
  userId: string;
  createdAt: number;
  lastAccessedAt: number;
  /** The channel ID where the user panel was last rendered. Used by listeners for push updates. */
  channelId?: string;
  /** The message ID of the last panel embed. Used by listeners for push updates. */
  messageId?: string;
}
