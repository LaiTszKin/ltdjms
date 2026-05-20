import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type EscortDispatchOrderService, type EscortOptionPricingService, type DispatchAfterSalesStaffService } from '../service/index.js';
export interface DispatchSessionState {
    mode: 'create' | 'assign' | 'view' | null;
    selectedUserId?: number;
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
    readonly customIdPrefix = "dispatch_";
    constructor(dispatchOrderService: EscortDispatchOrderService, pricingService: EscortOptionPricingService, afterSalesStaffService: DispatchAfterSalesStaffService);
    execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void>;
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
    private extractCustomId;
    private formatPanelText;
}
