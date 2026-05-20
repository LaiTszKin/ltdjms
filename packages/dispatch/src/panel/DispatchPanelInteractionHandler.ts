import {
  type DiscordInteraction, type DiscordContext,
  type EmbedView, type ButtonView,
} from '@ltdjms/shared';
import {
  type EscortDispatchOrderService,
  type EscortOptionPricingService,
  type DispatchAfterSalesStaffService,
} from '../service/index.js';
import { type DispatchNotificationService } from '../notification/index.js';
import {
  isPendingEscortConfirmation,
  isConfirmed,
  isCompleted,
  isAfterSalesRequested,
  isAfterSalesInProgress,
} from '../domain/index.js';
import {
  embedViewToApiEmbed,
  buttonsToComponents,
  buildPanelReplyPayload,
  buildModeSelectEmbed,
  buildModeSelectActionRow,
  buildOrderDetailActionRow,
  buildCreateModeEmbed,
  buildAssignModeEmbed,
  buildNoPendingOrdersEmbed,
  buildQueryFailedEmbed,
  formatPanelText,
  COLOR_INFO,
  COLOR_WARNING,
  COLOR_ERROR,
  MODE_SELECT,
  BUTTON_CREATE_MODE,
  BUTTON_ASSIGN_MODE,
  BUTTON_VIEW_ORDERS,
  BUTTON_VIEW_HISTORY,
  BUTTON_BACK_TO_MODE,
  BUTTON_CONFIRM_ORDER,
  BUTTON_REQUEST_COMPLETION,
  BUTTON_CONFIRM_COMPLETION,
  BUTTON_REQUEST_AFTER_SALES,
  BUTTON_CLAIM_AFTER_SALES,
  BUTTON_CLOSE_AFTER_SALES,
  SELECT_ESCORT_OPTION,
  SELECT_ESCORT_OPTION_EXTRA,
  SELECT_PENDING_ORDER,
} from './DispatchPanelView.js';
import {
  buildOrderCreatedEmbed,
  buildOrderConfirmedEmbed,
  buildOrderAssignedEmbed,
  buildOrderDetailEmbed,
  buildOrderListEmbed,
  buildErrorEmbed,
  buildPendingCustomerConfirmationEmbed,
  buildOrderCompletedEmbed,
  buildAfterSalesRequestedEmbed,
  buildAfterSalesClaimedEmbed,
  buildAfterSalesClosedEmbed,
} from './DispatchPanelMessageFactory.js';

// ============================================================
// Session State
// ============================================================

export interface DispatchSessionState {
  mode: 'create' | 'assign' | 'view' | null;
  selectedCustomerId?: number;
  selectedEscortUserId?: number;
  selectedOptionCode?: string;
  selectedOrderNumber?: string;
  statusMessage?: string;
}

const sessions = new Map<string, DispatchSessionState>();

function getSessionKey(guildId: string, userId: string): string {
  return `${guildId}:${userId}`;
}

function getOrCreateSession(guildId: string, userId: string): DispatchSessionState {
  const key = getSessionKey(guildId, userId);
  let session = sessions.get(key);
  if (session == null) {
    session = { mode: null };
    sessions.set(key, session);
  }
  return session;
}

function clearSession(guildId: string, userId: string): void {
  const key = getSessionKey(guildId, userId);
  sessions.delete(key);
}

// ============================================================
// Interaction Handler
// ============================================================

/**
 * Handles all `dispatch_*` button and select menu interactions
 * for the escort dispatch panel. DM-only checks are enforced per action.
 */
export class DispatchPanelInteractionHandler {
  readonly customIdPrefix = 'dispatch_';

  constructor(
    private readonly dispatchOrderService: EscortDispatchOrderService,
    private readonly pricingService: EscortOptionPricingService,
    private readonly afterSalesStaffService: DispatchAfterSalesStaffService,
    private readonly notificationService: DispatchNotificationService,
  ) {}

  async execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();
    const customId = this.extractCustomId(interaction);

    // Admin permission check (spec R14.1)
    if (!(await this.checkAdminPermission(interaction, context, guildId, userId))) {
      await interaction.reply('你沒有權限使用派單面板。');
      return;
    }

    // All panel interactions require an active session
    const session = getOrCreateSession(guildId, userId);

    try {
      await this.routeInteraction(customId, interaction, guildId, userId, session);
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      const errorView = buildErrorEmbed(`操作失敗：${message}`);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
    }
  }

  private async routeInteraction(
    customId: string,
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    switch (customId) {
      // ---- Mode Selection ----
      case MODE_SELECT:
        await this.showMainPanel(interaction);
        break;
      case BUTTON_CREATE_MODE:
        clearSession(guildId, userId);
        session.mode = 'create';
        await this.showCreateMode(interaction);
        break;
      case BUTTON_ASSIGN_MODE:
        clearSession(guildId, userId);
        session.mode = 'assign';
        await this.showAssignMode(interaction, guildId);
        break;
      case BUTTON_VIEW_ORDERS:
        clearSession(guildId, userId);
        session.mode = 'view';
        await this.showRecentOrders(interaction, guildId);
        break;
      case BUTTON_VIEW_HISTORY:
        await this.showHistory(interaction, guildId);
        break;
      case BUTTON_BACK_TO_MODE:
        clearSession(guildId, userId);
        await this.showMainPanel(interaction);
        break;

      // ---- Order Actions ----
      case BUTTON_CONFIRM_ORDER:
        await this.handleConfirmOrder(interaction, guildId, userId, session);
        break;
      case BUTTON_REQUEST_COMPLETION:
        await this.handleRequestCompletion(interaction, userId, session);
        break;
      case BUTTON_CONFIRM_COMPLETION:
        await this.handleConfirmCompletion(interaction, userId, session);
        break;
      case BUTTON_REQUEST_AFTER_SALES:
        await this.handleRequestAfterSales(interaction, userId, session);
        break;

      // ---- After-Sales Actions (R8/R9) ----
      case BUTTON_CLAIM_AFTER_SALES:
        await this.handleClaimAfterSales(interaction, userId, session);
        break;
      case BUTTON_CLOSE_AFTER_SALES:
        await this.handleCloseAfterSales(interaction, userId, session);
        break;

      // ---- Select Menu Interactions (R2.3, R3) ----
      case SELECT_ESCORT_OPTION:
      case SELECT_ESCORT_OPTION_EXTRA:
      case SELECT_PENDING_ORDER:
        await this.handleSelectMenuChoice(interaction, customId, userId, session);
        break;

      default:
        await interaction.reply('未知的操作，請重新開啟面板。');
    }
  }

  // ============================================================
  // Mode Display Methods
  // ============================================================

  private async showMainPanel(interaction: DiscordInteraction): Promise<void> {
    const view = buildModeSelectEmbed();
    const buttons = buildModeSelectActionRow();
    await this.replyWithPayload(interaction, view, buttons);
  }

  private async showCreateMode(interaction: DiscordInteraction): Promise<void> {
    const view = buildCreateModeEmbed();
    const suggestions = [
      '請使用以下格式輸入派單資訊：',
      '`護航者ID 客戶ID 選項代碼`',
      '',
      '範例：`123456 789012 BASIC`',
      '',
      '或輸入 `cancel` 取消操作。',
    ].join('\n');

    const response = `**${view.title}**\n${view.description}\n\n${suggestions}`;
    if (interaction.isAcknowledged()) {
      await interaction.editEmbed({ description: response } as never);
    } else {
      await interaction.reply(response);
    }
  }

  private async showAssignMode(interaction: DiscordInteraction, guildId: string): Promise<void> {
    const view = buildAssignModeEmbed();
    const guildIdNum = Number(guildId);

    if (Number.isNaN(guildIdNum)) {
      const errorView = buildQueryFailedEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const result = await this.dispatchOrderService.findPendingAssignmentOrders(guildIdNum);

    if (result.isErr()) {
      const errorView = buildQueryFailedEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const orders = result.getValue();
    if (orders.length === 0) {
      const noPendingView = buildNoPendingOrdersEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(noPendingView) as never);
      return;
    }

    const orderListView = buildOrderListEmbed('待派發訂單', orders, '無待派發訂單');
    await interaction.replyEmbed(embedViewToApiEmbed(orderListView) as never);
  }

  private async showRecentOrders(interaction: DiscordInteraction, guildId: string): Promise<void> {
    const guildIdNum = Number(guildId);
    if (Number.isNaN(guildIdNum)) {
      const errorView = buildQueryFailedEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const result = await this.dispatchOrderService.findRecentOrders(guildIdNum);

    if (result.isErr()) {
      const errorView = buildQueryFailedEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const orders = result.getValue();
    const orderListView = buildOrderListEmbed('📊 近期訂單', orders, '目前無歷史訂單記錄。');
    await interaction.replyEmbed(embedViewToApiEmbed(orderListView) as never);
  }

  private async showHistory(interaction: DiscordInteraction, guildId: string): Promise<void> {
    // History is same as recent orders for now
    const guildIdNum = Number(guildId);
    if (Number.isNaN(guildIdNum)) {
      const errorView = buildQueryFailedEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const result = await this.dispatchOrderService.findRecentOrders(guildIdNum, 20);

    if (result.isErr()) {
      const errorView = buildQueryFailedEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const orders = result.getValue();
    const orderListView = buildOrderListEmbed('📊 歷史記錄', orders, '目前無歷史訂單記錄。');
    await interaction.replyEmbed(embedViewToApiEmbed(orderListView) as never);
  }

  // ============================================================
  // Order Action Handlers (DM Flow)
  // ============================================================

  private async handleConfirmOrder(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    if ((interaction as unknown as { inGuild?: boolean }).inGuild) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要確認的訂單。');
      return;
    }

    const result = await this.dispatchOrderService.confirmOrder(
      session.selectedOrderNumber,
      Number(userId),
    );

    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const order = result.getValue();
    const confirmedView = buildOrderConfirmedEmbed(order);
    await interaction.replyEmbed(embedViewToApiEmbed(confirmedView) as never);
    await this.notificationService.notifyEscortConfirmed(order);
    session.mode = null;
  }

  private async handleRequestCompletion(
    interaction: DiscordInteraction,
    userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    if ((interaction as unknown as { inGuild?: boolean }).inGuild) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要送出完成的訂單。');
      return;
    }

    const result = await this.dispatchOrderService.requestCompletion(
      session.selectedOrderNumber,
      Number(userId),
    );

    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const order = result.getValue();
    const pendingView = buildPendingCustomerConfirmationEmbed(order);
    await interaction.replyEmbed(embedViewToApiEmbed(pendingView) as never);
    await this.notificationService.notifyCompletionRequested(order);
    session.mode = null;
  }

  private async handleConfirmCompletion(
    interaction: DiscordInteraction,
    userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    if ((interaction as unknown as { inGuild?: boolean }).inGuild) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要確認完成的訂單。');
      return;
    }

    const result = await this.dispatchOrderService.customerConfirmCompletion(
      session.selectedOrderNumber,
      Number(userId),
    );

    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const order = result.getValue();
    const completedView = buildOrderCompletedEmbed(order);
    await interaction.replyEmbed(embedViewToApiEmbed(completedView) as never);
    await this.notificationService.notifyCustomerConfirmed(order);
    session.mode = null;
  }

  private async handleRequestAfterSales(
    interaction: DiscordInteraction,
    userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    if ((interaction as unknown as { inGuild?: boolean }).inGuild) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要申請售後的訂單。');
      return;
    }

    const result = await this.dispatchOrderService.requestAfterSales(
      session.selectedOrderNumber,
      Number(userId),
    );

    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const order = result.getValue();
    const afterSalesView = buildAfterSalesRequestedEmbed(order);
    await interaction.replyEmbed(embedViewToApiEmbed(afterSalesView) as never);

    // R7.4: Notify after-sales staff; if none available, warn via edited embed
    const notified = await this.notificationService.notifyAfterSalesRequested(order);
    if (!notified) {
      const updatedView: EmbedView = {
        ...afterSalesView,
        description: `${afterSalesView.description ?? ''}\n\n⚠️ 目前尚未設定售後人員，請管理員盡快設定。`,
      };
      await interaction.editEmbed(embedViewToApiEmbed(updatedView) as never);
    }

    session.mode = null;
  }

  // ============================================================
  // After-Sales Action Handlers (R8/R9)
  // ============================================================

  private async handleClaimAfterSales(
    interaction: DiscordInteraction,
    userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    if ((interaction as unknown as { inGuild?: boolean }).inGuild) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要承接的售後訂單。');
      return;
    }

    const result = await this.dispatchOrderService.claimAfterSales(
      session.selectedOrderNumber,
      Number(userId),
    );

    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const order = result.getValue();
    const claimedView = buildAfterSalesClaimedEmbed(order);
    await interaction.replyEmbed(embedViewToApiEmbed(claimedView) as never);
    await this.notificationService.notifyAfterSalesClaimed(order);
    session.mode = null;
  }

  private async handleCloseAfterSales(
    interaction: DiscordInteraction,
    userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    if ((interaction as unknown as { inGuild?: boolean }).inGuild) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要結案的售後訂單。');
      return;
    }

    const result = await this.dispatchOrderService.closeAfterSales(
      session.selectedOrderNumber,
      Number(userId),
    );

    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const order = result.getValue();
    const closedView = buildAfterSalesClosedEmbed(order);
    await interaction.replyEmbed(embedViewToApiEmbed(closedView) as never);
    await this.notificationService.notifyAfterSalesClosed(order);
    session.mode = null;
  }

  // ============================================================
  // Select Menu Handler (R2.3, R3)
  // ============================================================

  private async handleSelectMenuChoice(
    interaction: DiscordInteraction,
    customId: string,
    userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    // Extract selected value from the interaction
    const selectedValue = (interaction as unknown as { values?: string[] }).values?.[0];
    if (!selectedValue) {
      await interaction.reply('請選擇一個選項。');
      return;
    }

    if (customId === SELECT_ESCORT_OPTION || customId === SELECT_ESCORT_OPTION_EXTRA) {
      session.selectedOptionCode = selectedValue;
      await interaction.reply(`已選擇護航品類：${selectedValue}。請輸入客戶 ID。`);
    } else if (customId === SELECT_PENDING_ORDER) {
      session.selectedOrderNumber = selectedValue;
      await this.handleOrderSelected(interaction, selectedValue, userId, session);
    }
  }

  private async handleOrderSelected(
    interaction: DiscordInteraction,
    orderNumber: string,
    _userId: string,
    _session: DispatchSessionState,
  ): Promise<void> {
    const result = await this.dispatchOrderService.findByOrderNumber(orderNumber);

    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const order = result.getValue();

    const detailView = buildOrderDetailEmbed(order);
    const canConfirm = isPendingEscortConfirmation(order);
    const canComplete = isConfirmed(order);
    const canRequestAfterSales = isCompleted(order) || isAfterSalesRequested(order) || isAfterSalesInProgress(order);
    const buttons = buildOrderDetailActionRow(canConfirm, canComplete, canRequestAfterSales);
    await this.replyWithPayload(interaction, detailView, buttons);
  }

  // ============================================================
  // Permission Check
  // ============================================================

  private async checkAdminPermission(
    interaction: DiscordInteraction,
    _context: DiscordContext,
    _guildId: string,
    _userId: string,
  ): Promise<boolean> {
    try {
      // Check if the member has ADMINISTRATOR permission or is the guild owner.
      const memberPermissions = (interaction as unknown as { memberPermissions?: string }).memberPermissions;
      if (memberPermissions && (BigInt(memberPermissions) & 0x8n) !== 0n) {
        return true;
      }
      // No ADMINISTRATOR bit — deny permission.
      return false;
    } catch {
      return false; // Default to deny on unexpected errors
    }
  }

  // ============================================================
  // Helpers
  // ============================================================

  private extractCustomId(interaction: DiscordInteraction): string {
    try {
      // Discord.js exposes customId directly on message component interactions.
      // getHook() is for reply hooks, not for reading the incoming custom ID.
      return (interaction as unknown as { customId?: string }).customId ?? '';
    } catch {
      return '';
    }
  }

  /**
   * Sends a reply with an embed and optional action buttons via
   * the underlying discord.js interaction hook.
   */
  private async replyWithPayload(
    interaction: DiscordInteraction,
    embedView: EmbedView,
    buttons?: ButtonView[],
  ): Promise<void> {
    const payload = buildPanelReplyPayload(embedView, buttons);
    const hook = interaction.getHook() as any;
    if (interaction.isAcknowledged()) {
      await hook.editReply({ embeds: [payload.embed], components: payload.components });
    } else {
      await hook.reply({ embeds: [payload.embed], components: payload.components, ephemeral: payload.ephemeral });
    }
  }

  private formatPanelText(
    view: ReturnType<typeof buildModeSelectEmbed>,
    buttons: ReturnType<typeof buildModeSelectActionRow>,
  ): string {
    return formatPanelText(view, buttons);
  }
}
