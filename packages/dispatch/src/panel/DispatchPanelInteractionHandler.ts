import {
  type DiscordInteraction,
  type DiscordContext,
  type DiscordRuntimeGateway,
  type EmbedView,
  type ButtonView,
  ButtonStyle,
  safeSnowflakeToNumber,
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
  isPendingCustomerConfirmation,
  isCompleted,
  isAfterSalesRequested,
  isAfterSalesInProgress,
  isAfterSalesAssignee,
  EscortDispatchOrderStatus,
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
  splitSelectMenuOptions,
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
  BUTTON_CREATE_CONFIRM,
  BUTTON_ASSIGN_CONFIRM,
  SELECT_ESCORT_OPTION,
  SELECT_ESCORT_OPTION_EXTRA,
  SELECT_PENDING_ORDER,
} from './DispatchPanelView.js';
import { COLOR_INFO, COLOR_WARNING, COLOR_ERROR } from '../constants.js';
import type { SelectOptionView } from './DispatchPanelView.js';
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
import {
  DispatchPanelSessionManager,
  type DispatchSessionState,
} from './DispatchPanelSessionManager.js';

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
    private readonly sessionManager: DispatchPanelSessionManager,
    private readonly gateway?: DiscordRuntimeGateway,
  ) {}

  async execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();
    const customId = this.extractCustomId(interaction);

    try {
      // P0-3: Notification button interactions (DM-only) — no admin check or guild session needed
      if (customId.startsWith('dispatch_notify_')) {
        await this.handleNotifyInteraction(customId, interaction, userId);
        return;
      }

      // Admin permission check (spec R14.1) — only for guild panel interactions
      if (!(await this.checkAdminPermission(interaction))) {
        await interaction.reply('你沒有權限使用派單面板。');
        return;
      }

      // All panel interactions require an active session
      const session = this.sessionManager.getOrCreate(guildId, userId);
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
        this.sessionManager.clear(guildId, userId);
        session.mode = 'create';
        await this.showCreateMode(interaction, guildId);
        break;
      case BUTTON_ASSIGN_MODE:
        this.sessionManager.clear(guildId, userId);
        session.mode = 'assign';
        await this.showAssignMode(interaction, guildId);
        break;
      case BUTTON_VIEW_ORDERS:
        this.sessionManager.clear(guildId, userId);
        session.mode = 'view';
        await this.showRecentOrders(interaction, guildId);
        break;
      case BUTTON_VIEW_HISTORY:
        await this.showHistory(interaction, guildId);
        break;
      case BUTTON_BACK_TO_MODE:
        this.sessionManager.clear(guildId, userId);
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

      // ---- Create Mode Order Confirm ----
      case BUTTON_CREATE_CONFIRM:
        await this.handleCreateOrderConfirm(interaction, guildId, userId, session);
        break;

      // ---- Assign Mode Confirm ----
      case BUTTON_ASSIGN_CONFIRM:
        await this.handleAssignOrder(interaction, guildId, userId, session);
        break;

      // ---- Create Customer Modal Submit ----
      case 'dispatch_create_customer_modal':
        await this.handleCreateCustomerModal(customId, interaction, guildId, session);
        break;

      // ---- Escort Member Select Menu ----
      case 'dispatch_select_escort_member':
        await this.handleEscortMemberSelect(interaction, session);
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

  private async showCreateMode(interaction: DiscordInteraction, guildId: string): Promise<void> {
    const view = buildCreateModeEmbed();
    const guildIdNum = safeSnowflakeToNumber(guildId);

    if (Number.isNaN(guildIdNum)) {
      const errorView = buildQueryFailedEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const result = await this.pricingService.listOptionPrices(guildIdNum);

    if (result.isErr()) {
      const errorView = buildQueryFailedEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const prices = result.getValue();
    const selectOptions: SelectOptionView[] = prices.map((p) => ({
      value: p.optionCode,
      label: `${p.option.type} - ${p.option.level} - ${p.option.target}`,
      description: `NT$${p.effectivePriceTwd.toLocaleString()}`,
    }));

    const { primary, extra } = splitSelectMenuOptions(
      selectOptions,
      SELECT_ESCORT_OPTION,
      SELECT_ESCORT_OPTION_EXTRA,
      '請選擇護航品類',
    );

    const components: unknown[] = [{ type: 1, components: [{ ...primary, type: 3 }] }];
    if (extra != null) {
      components.push({ type: 1, components: [{ ...extra, type: 3 }] });
    }

    const embedPayload = embedViewToApiEmbed(view);
    const hook = interaction.getHook() as any;
    if (interaction.isAcknowledged()) {
      await hook.editReply({ embeds: [embedPayload], components });
    } else {
      await hook.reply({ embeds: [embedPayload], components, ephemeral: true });
    }
  }

  private async showAssignMode(interaction: DiscordInteraction, guildId: string): Promise<void> {
    const view = buildAssignModeEmbed();
    const guildIdNum = safeSnowflakeToNumber(guildId);

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

    // P0-4: Render a select menu with pending orders for the admin to choose
    const selectOptions: SelectOptionView[] = orders.map((o) => ({
      value: o.orderNumber,
      label: `#${o.orderNumber} - 客戶 ${o.customerUserId}`,
      description: o.sourceEscortOptionCode ? `品類: ${o.sourceEscortOptionCode}` : '一般',
    }));

    const { primary, extra } = splitSelectMenuOptions(
      selectOptions,
      SELECT_PENDING_ORDER,
      SELECT_PENDING_ORDER,
      '請選擇待派發訂單',
    );

    const components: unknown[] = [{ type: 1, components: [{ ...primary, type: 3 }] }];
    if (extra != null) {
      components.push({ type: 1, components: [{ ...extra, type: 3 }] });
    }

    const embedPayload = embedViewToApiEmbed(view);
    const hook = interaction.getHook() as any;
    if (interaction.isAcknowledged()) {
      await hook.editReply({ embeds: [embedPayload], components });
    } else {
      await hook.reply({ embeds: [embedPayload], components, ephemeral: true });
    }
  }

  private async showRecentOrders(interaction: DiscordInteraction, guildId: string): Promise<void> {
    const guildIdNum = safeSnowflakeToNumber(guildId);
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
    const guildIdNum = safeSnowflakeToNumber(guildId);
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
    if (this.getInGuild(interaction)) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要確認的訂單。');
      return;
    }

    const result = await this.dispatchOrderService.confirmOrder(
      session.selectedOrderNumber,
      safeSnowflakeToNumber(userId),
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
    if (this.getInGuild(interaction)) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要送出完成的訂單。');
      return;
    }

    const result = await this.dispatchOrderService.requestCompletion(
      session.selectedOrderNumber,
      safeSnowflakeToNumber(userId),
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
    if (this.getInGuild(interaction)) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要確認完成的訂單。');
      return;
    }

    const result = await this.dispatchOrderService.customerConfirmCompletion(
      session.selectedOrderNumber,
      safeSnowflakeToNumber(userId),
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
    if (this.getInGuild(interaction)) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要申請售後的訂單。');
      return;
    }

    const result = await this.dispatchOrderService.requestAfterSales(
      session.selectedOrderNumber,
      safeSnowflakeToNumber(userId),
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
    if (this.getInGuild(interaction)) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要承接的售後訂單。');
      return;
    }

    const result = await this.dispatchOrderService.claimAfterSales(
      session.selectedOrderNumber,
      safeSnowflakeToNumber(userId),
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
    if (this.getInGuild(interaction)) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }
    if (!session.selectedOrderNumber) {
      await interaction.reply('請先選擇要結案的售後訂單。');
      return;
    }

    const result = await this.dispatchOrderService.closeAfterSales(
      session.selectedOrderNumber,
      safeSnowflakeToNumber(userId),
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
    // Extract selected value from the interaction using the interface method
    const values = interaction.getSelectedValues();
    const selectedValue = values.length > 0 ? values[0] : undefined;
    if (!selectedValue) {
      await interaction.reply('請選擇一個選項。');
      return;
    }

    if (customId === SELECT_ESCORT_OPTION || customId === SELECT_ESCORT_OPTION_EXTRA) {
      session.selectedOptionCode = selectedValue;
      // P0-1: 顯示 Modal 讓管理員輸入客戶 Discord ID
      await interaction.showModal({
        title: '輸入客戶 ID',
        custom_id: 'dispatch_create_customer_modal',
        components: [
          {
            type: 1,
            components: [
              {
                type: 4,
                custom_id: 'customer_user_id',
                label: '客戶 Discord ID',
                style: 1,
                min_length: 1,
                max_length: 32,
                required: true,
                placeholder: '請輸入客戶的 Discord 使用者 ID',
              },
            ],
          },
        ],
      });
    } else if (customId === SELECT_PENDING_ORDER) {
      session.selectedOrderNumber = selectedValue;
      await this.handleOrderSelected(interaction, selectedValue, userId, session);
    }
  }

  private async handleOrderSelected(
    interaction: DiscordInteraction,
    orderNumber: string,
    userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    const result = await this.dispatchOrderService.findByOrderNumber(orderNumber);

    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const order = result.getValue();

    const detailView = buildOrderDetailEmbed(order);
    // P2-15: 僅在已指派護航者時顯示「確認接單」按鈕
    const canConfirm = isPendingEscortConfirmation(order) && order.escortUserId > 0;
    const canComplete = isConfirmed(order);
    const canRequestAfterSales =
      isPendingCustomerConfirmation(order) || order.status === EscortDispatchOrderStatus.COMPLETED;
    const canClaimAfterSales = isAfterSalesRequested(order);
    const canCloseAfterSales =
      isAfterSalesInProgress(order) && isAfterSalesAssignee(order, safeSnowflakeToNumber(userId));
    const buttons = buildOrderDetailActionRow(
      canConfirm,
      canComplete,
      canRequestAfterSales,
      canClaimAfterSales,
      canCloseAfterSales,
    );

    // P0-2: 在 Assign Mode 中顯示 member select menu + 派發訂單按鈕
    if (session.mode === 'assign' && isPendingEscortConfirmation(order)) {
      const memberSelectComponent = {
        type: 5 as const, // UserSelect
        custom_id: 'dispatch_select_escort_member',
        placeholder: '請選擇要指派的護航者',
      };
      const assignActionRow = {
        type: 1 as const,
        components: [
          {
            type: 2 as const,
            style: ButtonStyle.PRIMARY,
            custom_id: BUTTON_ASSIGN_CONFIRM,
            label: '派發訂單',
          },
        ],
      };
      const payload = buildPanelReplyPayload(detailView, buttons);
      const hook = interaction.getHook() as any;
      const components = [
        { type: 1 as const, components: [memberSelectComponent] },
        assignActionRow,
        ...payload.components,
      ];
      if (interaction.isAcknowledged()) {
        await hook.editReply({ embeds: [payload.embed], components });
      } else {
        await hook.reply({ embeds: [payload.embed], components, ephemeral: payload.ephemeral });
      }
      return;
    }

    await this.replyWithPayload(interaction, detailView, buttons);
  }

  // ============================================================
  // Create Mode Handlers (P0-1)
  // ============================================================

  /**
   * Handles the modal submit from the create customer modal.
   * Validates the customer ID and shows a confirmation embed.
   */
  private async handleCreateCustomerModal(
    _customId: string,
    interaction: DiscordInteraction,
    guildId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    const customerUserId: string = interaction.getTextInputValue('customer_user_id');

    if (!customerUserId || !/^\d{17,19}$/.test(customerUserId)) {
      const errorView = buildErrorEmbed('請輸入有效的 Discord 使用者 ID（必須為 17-19 位數字）。');
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    // P1-13: 成員驗證由 service 層處理，panel 層不再重複檢查
    session.selectedCustomerId = Number(customerUserId);

    const optionCode = session.selectedOptionCode ?? '未指定';
    const view: EmbedView = {
      title: '📋 確認建立派單',
      description: `請確認以下資訊：\n• 護航品類：${optionCode}\n• 客戶 ID：${customerUserId}`,
      color: COLOR_INFO,
      fields: [
        { name: '護航品類', value: optionCode, inline: true },
        { name: '客戶 ID', value: customerUserId, inline: true },
      ],
      footer: '確認後將建立新的護航派單',
    };

    const buttons: ButtonView[] = [
      { id: BUTTON_CREATE_CONFIRM, label: '確認建立', style: ButtonStyle.SUCCESS, disabled: false },
      { id: BUTTON_BACK_TO_MODE, label: '取消', style: ButtonStyle.SECONDARY, disabled: false },
    ];

    await this.replyWithPayload(interaction, view, buttons);
  }

  /**
   * Handles the "確認建立" button click.
   * Calls dispatchOrderService.createManualOpenOrder() to create the order.
   */
  private async handleCreateOrderConfirm(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    const guildIdNum = safeSnowflakeToNumber(guildId);
    if (Number.isNaN(guildIdNum)) {
      const errorView = buildQueryFailedEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    if (!session.selectedCustomerId || !session.selectedOptionCode) {
      const errorView = buildErrorEmbed('遺失客戶 ID 或護航品類資訊，請重新選擇。');
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const result = await this.dispatchOrderService.createManualOpenOrder(
      guildIdNum,
      safeSnowflakeToNumber(userId),
      session.selectedCustomerId,
      session.selectedOptionCode,
    );

    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const order = result.getValue();
    const successView = buildOrderCreatedEmbed(order);
    await this.replyWithPayload(interaction, successView);
    session.mode = null;
  }

  // ============================================================
  // Assign Mode Handlers (P0-2)
  // ============================================================

  /**
   * Handles the escort member select menu.
   * Saves the selected escort user ID to the session.
   */
  private async handleEscortMemberSelect(
    interaction: DiscordInteraction,
    session: DispatchSessionState,
  ): Promise<void> {
    const selectedEscortId = interaction.getSelectedValues()[0];
    if (!selectedEscortId || !/^\d{17,19}$/.test(selectedEscortId)) {
      await interaction.reply('請選擇有效的護航者。');
      return;
    }

    session.selectedEscortUserId = Number(selectedEscortId);

    // Acknowledge the select menu interaction with an ephemeral message
    const hook = interaction.getHook() as any;
    if (interaction.isAcknowledged()) {
      await hook.followUp({ content: `已選擇護航者：<@${selectedEscortId}>`, ephemeral: true });
    } else {
      await hook.reply({ content: `已選擇護航者：<@${selectedEscortId}>`, ephemeral: true });
    }
  }

  /**
   * Handles the "派發訂單" button click.
   * Calls dispatchOrderService.assignPendingOrder() and sends DM to escort.
   */
  private async handleAssignOrder(
    interaction: DiscordInteraction,
    guildId: string,
    _userId: string,
    session: DispatchSessionState,
  ): Promise<void> {
    const guildIdNum = safeSnowflakeToNumber(guildId);
    if (Number.isNaN(guildIdNum)) {
      const errorView = buildQueryFailedEmbed();
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    if (!session.selectedOrderNumber || !session.selectedEscortUserId) {
      const errorView = buildErrorEmbed('請選擇訂單與護航者後再派發。');
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    // 使用 raw hook 以支援 ephemeral defer（介面未暴露 ephemeral 參數）
    const hook = interaction.getHook() as any;
    await hook.deferReply({ ephemeral: true });

    const result = await this.dispatchOrderService.assignPendingOrder(
      session.selectedOrderNumber,
      safeSnowflakeToNumber(_userId),
      session.selectedEscortUserId,
    );

    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await hook.editReply({ embeds: [embedViewToApiEmbed(errorView)] });
      session.mode = null;
      return;
    }

    const order = result.getValue();

    // P1-14: 即使 DM 發送失敗，指派仍然視為成功
    let dmWarning = '';
    try {
      const dmSent = await this.notificationService.notifyEscortAssigned(order);
      if (!dmSent) {
        dmWarning = '\n\n⚠️ 護航者 DM 發送失敗，請手動通知。';
      }
    } catch {
      dmWarning = '\n\n⚠️ 護航者 DM 發送失敗，請手動通知。';
    }

    const assignedView = buildOrderAssignedEmbed(order);
    const updatedDescription = `${assignedView.description ?? ''}${dmWarning}`;
    const updatedEmbed = {
      ...embedViewToApiEmbed(assignedView),
      description: updatedDescription,
    };
    await hook.editReply({ embeds: [updatedEmbed] });
    session.mode = null;
  }

  // ============================================================
  // Notification Button Handlers (P0-3: DM Notification Routing)
  // ============================================================

  /**
   * Routes DM notification button clicks to the appropriate order action.
   * CustomId format: dispatch_notify_{action}:{orderNumber}
   */
  private async handleNotifyInteraction(
    customId: string,
    interaction: DiscordInteraction,
    userId: string,
  ): Promise<void> {
    // DM-only: reject if interaction originates from a guild channel
    if (this.getInGuild(interaction)) {
      await interaction.reply('請在機器人私訊中操作');
      return;
    }

    // Parse: "dispatch_notify_{action}:{orderNumber}"
    const rest = customId.substring('dispatch_notify_'.length);
    const colonIdx = rest.indexOf(':');
    if (colonIdx === -1) {
      await interaction.reply('通知格式無效');
      return;
    }

    const action = rest.substring(0, colonIdx);
    const orderNumber = rest.substring(colonIdx + 1);

    switch (action) {
      case 'confirm':
        await this.handleNotifyConfirm(interaction, orderNumber, userId);
        break;
      case 'complete':
        await this.handleNotifyComplete(interaction, orderNumber, userId);
        break;
      case 'confirm_completion':
        await this.handleNotifyConfirmCompletion(interaction, orderNumber, userId);
        break;
      case 'after_sales':
        await this.handleNotifyAfterSales(interaction, orderNumber, userId);
        break;
      case 'claim':
        await this.handleNotifyClaim(interaction, orderNumber, userId);
        break;
      case 'close':
        await this.handleNotifyClose(interaction, orderNumber, userId);
        break;
      default:
        await interaction.reply('未知的通知操作');
    }
  }

  /** Notification handler: escort confirms assignment. */
  private async handleNotifyConfirm(
    interaction: DiscordInteraction,
    orderNumber: string,
    userId: string,
  ): Promise<void> {
    const result = await this.dispatchOrderService.confirmOrder(
      orderNumber,
      safeSnowflakeToNumber(userId),
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
  }

  /** Notification handler: escort requests completion. */
  private async handleNotifyComplete(
    interaction: DiscordInteraction,
    orderNumber: string,
    userId: string,
  ): Promise<void> {
    const result = await this.dispatchOrderService.requestCompletion(
      orderNumber,
      safeSnowflakeToNumber(userId),
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
  }

  /** Notification handler: customer confirms completion. */
  private async handleNotifyConfirmCompletion(
    interaction: DiscordInteraction,
    orderNumber: string,
    userId: string,
  ): Promise<void> {
    const result = await this.dispatchOrderService.customerConfirmCompletion(
      orderNumber,
      safeSnowflakeToNumber(userId),
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
  }

  /** Notification handler: customer requests after-sales. */
  private async handleNotifyAfterSales(
    interaction: DiscordInteraction,
    orderNumber: string,
    userId: string,
  ): Promise<void> {
    const result = await this.dispatchOrderService.requestAfterSales(
      orderNumber,
      safeSnowflakeToNumber(userId),
    );
    if (result.isErr()) {
      const errorView = buildErrorEmbed(result.getError().message);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
      return;
    }

    const order = result.getValue();
    const afterSalesView = buildAfterSalesRequestedEmbed(order);
    await interaction.replyEmbed(embedViewToApiEmbed(afterSalesView) as never);
    await this.notificationService.notifyAfterSalesRequested(order);
  }

  /** Notification handler: after-sales staff claims case. */
  private async handleNotifyClaim(
    interaction: DiscordInteraction,
    orderNumber: string,
    userId: string,
  ): Promise<void> {
    const result = await this.dispatchOrderService.claimAfterSales(
      orderNumber,
      safeSnowflakeToNumber(userId),
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
  }

  /** Notification handler: after-sales staff closes case. */
  private async handleNotifyClose(
    interaction: DiscordInteraction,
    orderNumber: string,
    userId: string,
  ): Promise<void> {
    const result = await this.dispatchOrderService.closeAfterSales(
      orderNumber,
      safeSnowflakeToNumber(userId),
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
  }

  // ============================================================
  // Permission Check
  // ============================================================

  private async checkAdminPermission(interaction: DiscordInteraction): Promise<boolean> {
    // isAdministrator() 同時檢查 ADMINISTRATOR 權限與 guild owner（ownerId）
    return interaction.isAdministrator();
  }

  // ============================================================
  // Helpers
  // ============================================================

  private extractCustomId(interaction: DiscordInteraction): string {
    return interaction.getCustomId();
  }

  /** Checks if the interaction originates from a guild channel. */
  private getInGuild(interaction: DiscordInteraction): boolean {
    return interaction.getGuildId() != null && interaction.getGuildId() !== '0';
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
      await hook.reply({
        embeds: [payload.embed],
        components: payload.components,
        ephemeral: payload.ephemeral,
      });
    }
  }
}
