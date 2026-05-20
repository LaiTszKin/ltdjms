import { type EmbedView, type ButtonView } from '@ltdjms/shared';
export declare const MODE_SELECT = "dispatch_mode_select";
export declare const BUTTON_CREATE_MODE = "dispatch_create_mode";
export declare const BUTTON_ASSIGN_MODE = "dispatch_assign_mode";
export declare const BUTTON_VIEW_ORDERS = "dispatch_view_orders";
export declare const BUTTON_VIEW_HISTORY = "dispatch_view_history";
export declare const BUTTON_BACK_TO_MODE = "dispatch_back_to_mode";
export declare const BUTTON_CONFIRM_ORDER = "dispatch_confirm_order";
export declare const BUTTON_REQUEST_COMPLETION = "dispatch_request_completion";
export declare const BUTTON_CONFIRM_COMPLETION = "dispatch_confirm_completion";
export declare const BUTTON_REQUEST_AFTER_SALES = "dispatch_request_after_sales";
export declare const SELECT_ESCORT_OPTION = "dispatch_select_escort_option";
export declare const COLOR_INFO = 5763719;
export declare const COLOR_WARNING = 16705372;
export declare const COLOR_ERROR = 15548997;
/** 主模式選擇面板。 */
export declare function buildModeSelectEmbed(): EmbedView;
/** 建立模式面板。 */
export declare function buildCreateModeEmbed(): EmbedView;
/** 指派模式面板。 */
export declare function buildAssignModeEmbed(): EmbedView;
/** 無待派發訂單的提示。 */
export declare function buildNoPendingOrdersEmbed(): EmbedView;
/** 查詢訂單失敗的錯誤訊息。 */
export declare function buildQueryFailedEmbed(): EmbedView;
export declare function buildCreateModeButton(disabled?: boolean): ButtonView;
export declare function buildAssignModeButton(disabled?: boolean): ButtonView;
export declare function buildViewOrdersButton(disabled?: boolean): ButtonView;
export declare function buildViewHistoryButton(disabled?: boolean): ButtonView;
export declare function buildBackToModeButton(): ButtonView;
export declare function buildConfirmOrderButton(disabled?: boolean): ButtonView;
export declare function buildRequestCompletionButton(disabled?: boolean): ButtonView;
export declare function buildConfirmCompletionButton(disabled?: boolean): ButtonView;
export declare function buildRequestAfterSalesButton(disabled?: boolean): ButtonView;
/** 模式選擇的操作列。 */
export declare function buildModeSelectActionRow(): ButtonView[];
/** 訂單詳情頁的操作列。 */
export declare function buildOrderDetailActionRow(canConfirm: boolean, canComplete: boolean, canRequestAfterSales: boolean): ButtonView[];
/**
 * Converts an EmbedView to a raw embed data object compatible with
 * DiscordInteraction.replyEmbed() / editEmbed().
 */
export declare function embedViewToApiEmbed(view: EmbedView): Record<string, unknown>;
/**
 * Converts a ButtonView array to a component payload array
 * for Discord interaction reply/edit methods.
 */
export declare function buttonsToComponents(buttons: ButtonView[]): unknown[];
/**
 * Builds a full interaction reply payload with embed + action components.
 */
export declare function buildPanelReplyPayload(embedView: EmbedView, buttons?: ButtonView[]): {
    embed: Record<string, unknown>;
    components: unknown[];
    ephemeral: boolean;
};
