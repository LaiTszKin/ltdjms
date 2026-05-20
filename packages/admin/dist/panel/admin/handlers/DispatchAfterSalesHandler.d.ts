import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
/**
 * Handler for dispatch after-sales config interactions (admin_dispatch_*).
 * Supports add/remove after-sales staff members.
 */
export declare class DispatchAfterSalesHandler implements InteractionHandler {
    private readonly sessionManager;
    readonly customIdPrefix = "admin_dispatch";
    constructor(sessionManager: AdminPanelSessionManager);
    execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void>;
}
