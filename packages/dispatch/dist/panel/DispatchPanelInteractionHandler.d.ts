import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type EscortDispatchOrderService, type EscortOptionPricingService, type DispatchAfterSalesStaffService } from '../service/index.js';
import { type DispatchNotificationService } from '../notification/index.js';
export interface DispatchSessionState {
    mode: 'create' | 'assign' | 'view' | null;
    selectedCustomerId?: number;
    selectedEscortUserId?: number;
    selectedOptionCode?: string;
    selectedOrderNumber?: string;
    statusMessage?: string;
}
/**
 * Handles all `dispatch_*` button and select menu interactions
 * for the escort dispatch panel. DM-only checks are enforced per action.
 */
export declare class DispatchPanelInteractionHandler {
    private readonly dispatchOrderService;
    private readonly pricingService;
    private readonly afterSalesStaffService;
    private readonly notificationService;
    readonly customIdPrefix = "dispatch_";
    constructor(dispatchOrderService: EscortDispatchOrderService, pricingService: EscortOptionPricingService, afterSalesStaffService: DispatchAfterSalesStaffService, notificationService: DispatchNotificationService);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
    private routeInteraction;
    private showMainPanel;
    private showCreateMode;
    private showAssignMode;
    private showRecentOrders;
    private showHistory;
    private handleConfirmOrder;
    private handleRequestCompletion;
    private handleConfirmCompletion;
    private handleRequestAfterSales;
    private handleClaimAfterSales;
    private handleCloseAfterSales;
    private handleSelectMenuChoice;
    private handleOrderSelected;
    private checkAdminPermission;
    private extractCustomId;
    /**
     * Sends a reply with an embed and optional action buttons via
     * the underlying discord.js interaction hook.
     */
    private replyWithPayload;
    private formatPanelText;
}
