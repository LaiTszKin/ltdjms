/**
 * Admin panel view state.
 * Tracks which screen the admin is currently viewing.
 */
export declare enum AdminPanelViewState {
    MAIN = "MAIN",
    PRODUCT_LIST = "PRODUCT_LIST",
    PRODUCT_DETAIL = "PRODUCT_DETAIL",
    PRODUCT_CODE_LIST = "PRODUCT_CODE_LIST"
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
}
/**
 * User panel session data stored in the session.
 */
export interface PanelSessionData {
    guildId: string;
    userId: string;
    createdAt: number;
    lastAccessedAt: number;
}
