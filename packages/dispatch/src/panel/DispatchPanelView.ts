import { type EmbedView, type ButtonView, ButtonStyle } from '@ltdjms/shared';

// ============================================================
// Custom ID Constants
// ============================================================

export const MODE_SELECT = 'dispatch_mode_select';
export const BUTTON_CREATE_MODE = 'dispatch_create_mode';
export const BUTTON_ASSIGN_MODE = 'dispatch_assign_mode';
export const BUTTON_VIEW_ORDERS = 'dispatch_view_orders';
export const BUTTON_VIEW_HISTORY = 'dispatch_view_history';
export const BUTTON_BACK_TO_MODE = 'dispatch_back_to_mode';
export const BUTTON_CONFIRM_ORDER = 'dispatch_confirm_order';
export const BUTTON_REQUEST_COMPLETION = 'dispatch_request_completion';
export const BUTTON_CONFIRM_COMPLETION = 'dispatch_confirm_completion';
export const BUTTON_REQUEST_AFTER_SALES = 'dispatch_request_after_sales';
export const BUTTON_CLAIM_AFTER_SALES = 'dispatch_claim_after_sales';
export const BUTTON_CLOSE_AFTER_SALES = 'dispatch_close_after_sales';
export const BUTTON_CREATE_CONFIRM = 'dispatch_create_confirm';
export const BUTTON_ASSIGN_CONFIRM = 'dispatch_assign_confirm';
export const SELECT_ESCORT_OPTION = 'dispatch_select_escort_option';
export const SELECT_ESCORT_OPTION_EXTRA = 'dispatch_select_escort_option_extra';
export const SELECT_PENDING_ORDER = 'dispatch_select_pending_order';

// ============================================================
// Embed Colors
// ============================================================

export const COLOR_INFO = 0x57f287;
export const COLOR_WARNING = 0xfee75c;
export const COLOR_ERROR = 0xed4245;

// ============================================================
// Embed Builders
// ============================================================

/** 主模式選擇面板。 */
export function buildModeSelectEmbed(): EmbedView {
  return {
    title: '護航派單系統',
    description: '請選擇您要進行的操作：',
    color: COLOR_INFO,
    fields: [
      { name: '📋 建立派單', value: '手動建立新的護航派單訂單', inline: false },
      { name: '📌 指派訂單', value: '將待派發的訂單指派給護航者', inline: false },
      { name: '📊 檢視訂單', value: '檢視近期訂單與待派發清單', inline: false },
    ],
    footer: '護航派單系統 v1.0',
  };
}

/** 建立模式面板。 */
export function buildCreateModeEmbed(): EmbedView {
  return {
    title: '建立派單',
    description: '請填寫以下資訊來建立新的護航派單：\n1. 選擇護航品類\n2. 輸入客戶 ID\n3. 確認送出',
    color: COLOR_INFO,
    footer: '選擇下方選項開始建立派單',
  };
}

/** 指派模式面板。 */
export function buildAssignModeEmbed(): EmbedView {
  return {
    title: '指派訂單',
    description: '正在查詢待派發的訂單，請稍後...',
    color: COLOR_WARNING,
    footer: '僅顯示尚未指定護航者的訂單',
  };
}

/** 無待派發訂單的提示。 */
export function buildNoPendingOrdersEmbed(): EmbedView {
  return {
    title: '無待派發訂單',
    description: '目前沒有任何待派發的護航訂單。',
    color: COLOR_INFO,
    footer: '護航派單系統',
  };
}

/** 查詢訂單失敗的錯誤訊息。 */
export function buildQueryFailedEmbed(): EmbedView {
  return {
    title: '查詢失敗',
    description: '查詢訂單時發生錯誤，請稍後再試。',
    color: COLOR_ERROR,
    footer: '護航派單系統',
  };
}

// ============================================================
// Button Builders
// ============================================================

export function buildCreateModeButton(disabled = false): ButtonView {
  return { id: BUTTON_CREATE_MODE, label: '建立派單', style: ButtonStyle.PRIMARY, disabled };
}

export function buildAssignModeButton(disabled = false): ButtonView {
  return { id: BUTTON_ASSIGN_MODE, label: '指派訂單', style: ButtonStyle.PRIMARY, disabled };
}

export function buildViewOrdersButton(disabled = false): ButtonView {
  return { id: BUTTON_VIEW_ORDERS, label: '檢視訂單', style: ButtonStyle.SECONDARY, disabled };
}

export function buildViewHistoryButton(disabled = false): ButtonView {
  return { id: BUTTON_VIEW_HISTORY, label: '歷史記錄', style: ButtonStyle.SECONDARY, disabled };
}

export function buildBackToModeButton(): ButtonView {
  return { id: BUTTON_BACK_TO_MODE, label: '返回', style: ButtonStyle.SECONDARY, disabled: false };
}

export function buildConfirmOrderButton(disabled = false): ButtonView {
  return { id: BUTTON_CONFIRM_ORDER, label: '確認接單', style: ButtonStyle.SUCCESS, disabled };
}

export function buildRequestCompletionButton(disabled = false): ButtonView {
  return { id: BUTTON_REQUEST_COMPLETION, label: '送出完成', style: ButtonStyle.PRIMARY, disabled };
}

export function buildConfirmCompletionButton(disabled = false): ButtonView {
  return { id: BUTTON_CONFIRM_COMPLETION, label: '確認完成', style: ButtonStyle.SUCCESS, disabled };
}

export function buildRequestAfterSalesButton(disabled = false): ButtonView {
  return { id: BUTTON_REQUEST_AFTER_SALES, label: '申請售後', style: ButtonStyle.DANGER, disabled };
}

export function buildClaimAfterSalesButton(disabled = false): ButtonView {
  return { id: BUTTON_CLAIM_AFTER_SALES, label: '承接售後', style: ButtonStyle.SUCCESS, disabled };
}

export function buildCloseAfterSalesButton(disabled = false): ButtonView {
  return { id: BUTTON_CLOSE_AFTER_SALES, label: '結案', style: ButtonStyle.DANGER, disabled };
}

// ============================================================
// Row Builders
// ============================================================

/** 模式選擇的操作列。 */
export function buildModeSelectActionRow(): ButtonView[] {
  return [buildCreateModeButton(), buildAssignModeButton(), buildViewOrdersButton()];
}

/** 訂單詳情頁的操作列。 */
export function buildOrderDetailActionRow(
  canConfirm: boolean,
  canComplete: boolean,
  canRequestAfterSales: boolean,
  canClaimAfterSales: boolean,
  canCloseAfterSales: boolean,
): ButtonView[] {
  const buttons: ButtonView[] = [];
  if (canConfirm) {
    buttons.push(buildConfirmOrderButton());
  }
  if (canComplete) {
    buttons.push(buildRequestCompletionButton());
  }
  if (canRequestAfterSales) {
    buttons.push(buildRequestAfterSalesButton());
  }
  if (canClaimAfterSales) {
    buttons.push(buildClaimAfterSalesButton());
  }
  if (canCloseAfterSales) {
    buttons.push(buildCloseAfterSalesButton());
  }
  buttons.push(buildBackToModeButton());
  return buttons;
}

// ============================================================
// Select Menu Builders
// ============================================================

/** Builds a select menu for choosing an escort option. */
export interface SelectOptionView {
  value: string;
  label: string;
  description?: string;
}

export function buildEscortOptionSelectMenu(
  options: SelectOptionView[],
  customId: string,
  placeholder = '請選擇護航品類',
): { type: 'stringSelect'; custom_id: string; placeholder: string; options: SelectOptionView[] } {
  return {
    type: 'stringSelect',
    custom_id: customId,
    placeholder,
    options,
  };
}

/**
 * Splits a select menu into two when options exceed the Discord limit (25).
 * Returns [primaryMenu, extraMenu] where extraMenu is null if splitting isn't needed.
 */
export function splitSelectMenuOptions(
  options: SelectOptionView[],
  baseId: string,
  extraId: string,
  placeholder = '請選擇',
): { primary: ReturnType<typeof buildEscortOptionSelectMenu>; extra: ReturnType<typeof buildEscortOptionSelectMenu> | null } {
  if (options.length <= 25) {
    return {
      primary: buildEscortOptionSelectMenu(options, baseId, placeholder),
      extra: null,
    };
  }
  return {
    primary: buildEscortOptionSelectMenu(options.slice(0, 25), baseId, placeholder),
    extra: buildEscortOptionSelectMenu(options.slice(25), extraId, '更多選項…'),
  };
}

// ============================================================
// Embed Conversion Utility
// ============================================================

/**
 * Converts an EmbedView to a raw embed data object compatible with
 * DiscordInteraction.replyEmbed() / editEmbed().
 */
export function embedViewToApiEmbed(view: EmbedView): Record<string, unknown> {
  const embed: Record<string, unknown> = {};
  if (view.title) embed.title = view.title;
  if (view.description) embed.description = view.description;
  if (view.color) embed.color = view.color;
  if (view.fields && view.fields.length > 0) {
    embed.fields = view.fields.map((f) => ({
      name: f.name,
      value: f.value,
      inline: f.inline,
    }));
  }
  if (view.footer) embed.footer = { text: view.footer };
  return embed;
}

/**
 * Converts a ButtonView array to a component payload array
 * for Discord interaction reply/edit methods.
 */
export function buttonsToComponents(buttons: ButtonView[]): unknown[] {
  return [
    {
      type: 1, // ActionRow
      components: buttons.map((b) => ({
        type: 2, // Button
        custom_id: b.id,
        label: b.label,
        style: b.style,
        disabled: b.disabled,
      })),
    },
  ];
}

/**
 * Builds a full interaction reply payload with embed + action components.
 */
// ============================================================
// Format Utilities
// ============================================================

export function buildPanelReplyPayload(
  embedView: EmbedView,
  buttons?: ButtonView[],
): { embed: Record<string, unknown>; components: unknown[]; ephemeral: boolean } {
  const payload: { embed: Record<string, unknown>; components: unknown[]; ephemeral: boolean } = {
    embed: embedViewToApiEmbed(embedView),
    components: [],
    ephemeral: true,
  };
  if (buttons && buttons.length > 0) {
    payload.components = buttonsToComponents(buttons);
  }
  return payload;
}
