import { embedViewToApiEmbed, buildModeSelectEmbed, buildModeSelectActionRow, buildCreateModeEmbed, buildAssignModeEmbed, buildNoPendingOrdersEmbed, buildQueryFailedEmbed, MODE_SELECT, BUTTON_CREATE_MODE, BUTTON_ASSIGN_MODE, BUTTON_VIEW_ORDERS, BUTTON_VIEW_HISTORY, BUTTON_BACK_TO_MODE, BUTTON_CONFIRM_ORDER, BUTTON_REQUEST_COMPLETION, BUTTON_CONFIRM_COMPLETION, BUTTON_REQUEST_AFTER_SALES, } from './DispatchPanelView.js';
import { buildOrderCreatedEmbed, buildOrderListEmbed, buildErrorEmbed, buildPendingCustomerConfirmationEmbed, buildOrderCompletedEmbed, buildAfterSalesRequestedEmbed, } from './DispatchPanelMessageFactory.js';
const sessions = new Map();
function getSessionKey(guildId, userId) {
    return `${guildId}:${userId}`;
}
function getOrCreateSession(guildId, userId) {
    const key = getSessionKey(guildId, userId);
    let session = sessions.get(key);
    if (session == null) {
        session = { mode: null };
        sessions.set(key, session);
    }
    return session;
}
function clearSession(guildId, userId) {
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
    dispatchOrderService;
    pricingService;
    afterSalesStaffService;
    customIdPrefix = 'dispatch_';
    constructor(dispatchOrderService, pricingService, afterSalesStaffService) {
        this.dispatchOrderService = dispatchOrderService;
        this.pricingService = pricingService;
        this.afterSalesStaffService = afterSalesStaffService;
    }
    async execute(interaction, _context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        const customId = this.extractCustomId(interaction);
        // All panel interactions require an active session
        const session = getOrCreateSession(guildId, userId);
        try {
            await this.routeInteraction(customId, interaction, guildId, userId, session);
        }
        catch (e) {
            const message = e instanceof Error ? e.message : String(e);
            const errorView = buildErrorEmbed(`操作失敗：${message}`);
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
        }
    }
    async routeInteraction(customId, interaction, guildId, userId, session) {
        switch (customId) {
            // ---- Mode Selection ----
            case MODE_SELECT:
                await this.showMainPanel(interaction);
                break;
            case BUTTON_CREATE_MODE:
                session.mode = 'create';
                await this.showCreateMode(interaction);
                break;
            case BUTTON_ASSIGN_MODE:
                session.mode = 'assign';
                await this.showAssignMode(interaction, guildId);
                break;
            case BUTTON_VIEW_ORDERS:
                session.mode = 'view';
                await this.showRecentOrders(interaction, guildId);
                break;
            case BUTTON_VIEW_HISTORY:
                await this.showHistory(interaction, guildId);
                break;
            case BUTTON_BACK_TO_MODE:
                session.mode = null;
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
            default:
                await interaction.reply('未知的操作，請重新開啟面板。');
        }
    }
    // ============================================================
    // Mode Display Methods
    // ============================================================
    async showMainPanel(interaction) {
        const view = buildModeSelectEmbed();
        const buttons = buildModeSelectActionRow();
        const text = this.formatPanelText(view, buttons);
        if (interaction.isAcknowledged()) {
            await interaction.editEmbed({ description: text });
        }
        else {
            await interaction.reply(text);
        }
    }
    async showCreateMode(interaction) {
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
            await interaction.editEmbed({ description: response });
        }
        else {
            await interaction.reply(response);
        }
    }
    async showAssignMode(interaction, guildId) {
        const view = buildAssignModeEmbed();
        const guildIdNum = Number(guildId);
        if (Number.isNaN(guildIdNum)) {
            const errorView = buildQueryFailedEmbed();
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
            return;
        }
        const result = await this.dispatchOrderService.findPendingAssignmentOrders(guildIdNum);
        if (result.isErr()) {
            const errorView = buildQueryFailedEmbed();
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
            return;
        }
        const orders = result.getValue();
        if (orders.length === 0) {
            const noPendingView = buildNoPendingOrdersEmbed();
            await interaction.replyEmbed(embedViewToApiEmbed(noPendingView));
            return;
        }
        const orderListView = buildOrderListEmbed('待派發訂單', orders, '無待派發訂單');
        await interaction.replyEmbed(embedViewToApiEmbed(orderListView));
    }
    async showRecentOrders(interaction, guildId) {
        const guildIdNum = Number(guildId);
        if (Number.isNaN(guildIdNum)) {
            const errorView = buildQueryFailedEmbed();
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
            return;
        }
        const result = await this.dispatchOrderService.findRecentOrders(guildIdNum);
        if (result.isErr()) {
            const errorView = buildQueryFailedEmbed();
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
            return;
        }
        const orders = result.getValue();
        const orderListView = buildOrderListEmbed('📊 近期訂單', orders, '目前無歷史訂單記錄。');
        await interaction.replyEmbed(embedViewToApiEmbed(orderListView));
    }
    async showHistory(interaction, guildId) {
        // History is same as recent orders for now
        const guildIdNum = Number(guildId);
        if (Number.isNaN(guildIdNum)) {
            const errorView = buildQueryFailedEmbed();
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
            return;
        }
        const result = await this.dispatchOrderService.findRecentOrders(guildIdNum, 20);
        if (result.isErr()) {
            const errorView = buildQueryFailedEmbed();
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
            return;
        }
        const orders = result.getValue();
        const orderListView = buildOrderListEmbed('📊 歷史記錄', orders, '目前無歷史訂單記錄。');
        await interaction.replyEmbed(embedViewToApiEmbed(orderListView));
    }
    // ============================================================
    // Order Action Handlers (DM Flow)
    // ============================================================
    async handleConfirmOrder(interaction, guildId, userId, session) {
        if (!session.selectedOrderNumber) {
            await interaction.reply('請先選擇要確認的訂單。');
            return;
        }
        const result = await this.dispatchOrderService.confirmOrder(session.selectedOrderNumber, Number(userId));
        if (result.isErr()) {
            const errorView = buildErrorEmbed(result.getError().message);
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
            return;
        }
        const order = result.getValue();
        const confirmedView = buildOrderCreatedEmbed(order);
        await interaction.replyEmbed(embedViewToApiEmbed(confirmedView));
        session.mode = null;
    }
    async handleRequestCompletion(interaction, userId, session) {
        if (!session.selectedOrderNumber) {
            await interaction.reply('請先選擇要送出完成的訂單。');
            return;
        }
        const result = await this.dispatchOrderService.requestCompletion(session.selectedOrderNumber, Number(userId));
        if (result.isErr()) {
            const errorView = buildErrorEmbed(result.getError().message);
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
            return;
        }
        const order = result.getValue();
        const pendingView = buildPendingCustomerConfirmationEmbed(order);
        await interaction.replyEmbed(embedViewToApiEmbed(pendingView));
        session.mode = null;
    }
    async handleConfirmCompletion(interaction, userId, session) {
        if (!session.selectedOrderNumber) {
            await interaction.reply('請先選擇要確認完成的訂單。');
            return;
        }
        const result = await this.dispatchOrderService.customerConfirmCompletion(session.selectedOrderNumber, Number(userId));
        if (result.isErr()) {
            const errorView = buildErrorEmbed(result.getError().message);
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
            return;
        }
        const order = result.getValue();
        const completedView = buildOrderCompletedEmbed(order);
        await interaction.replyEmbed(embedViewToApiEmbed(completedView));
        session.mode = null;
    }
    async handleRequestAfterSales(interaction, userId, session) {
        if (!session.selectedOrderNumber) {
            await interaction.reply('請先選擇要申請售後的訂單。');
            return;
        }
        const result = await this.dispatchOrderService.requestAfterSales(session.selectedOrderNumber, Number(userId));
        if (result.isErr()) {
            const errorView = buildErrorEmbed(result.getError().message);
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
            return;
        }
        const order = result.getValue();
        const afterSalesView = buildAfterSalesRequestedEmbed(order);
        await interaction.replyEmbed(embedViewToApiEmbed(afterSalesView));
        session.mode = null;
    }
    // ============================================================
    // Helpers
    // ============================================================
    extractCustomId(interaction) {
        try {
            const hook = interaction.getHook();
            return hook?.customId ?? '';
        }
        catch {
            return '';
        }
    }
    formatPanelText(view, buttons) {
        const lines = [];
        if (view.title)
            lines.push(`**${view.title}**`);
        if (view.description)
            lines.push(view.description);
        lines.push('');
        for (const field of view.fields ?? []) {
            lines.push(`**${field.name}：** ${field.value}`);
        }
        lines.push('');
        lines.push('---');
        lines.push(buttons.map((b) => `\`/${b.label}\``).join(' | '));
        if (view.footer)
            lines.push(`_${view.footer}_`);
        return lines.join('\n');
    }
}
//# sourceMappingURL=DispatchPanelInteractionHandler.js.map