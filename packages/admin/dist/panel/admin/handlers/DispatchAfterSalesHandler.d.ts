import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { type DispatchAfterSalesStaffService } from '@ltdjms/dispatch';
/**
 * Handler for dispatch after-sales config interactions (admin_dispatch_*).
 * Supports add/remove after-sales staff members.
 */
export declare class DispatchAfterSalesHandler implements InteractionHandler {
    private readonly sessionManager;
    private readonly afterSalesStaffService;
    readonly customIdPrefix = "admin_dispatch";
    constructor(sessionManager: AdminPanelSessionManager, afterSalesStaffService: DispatchAfterSalesStaffService);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
