import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
/**
 * Handler for escort pricing interactions (admin_escortprice_*).
 * Supports view pricing list, edit guild override, reset to default.
 */
export declare class EscortPricingHandler implements InteractionHandler {
    private readonly sessionManager;
    readonly customIdPrefix = "admin_escortprice";
    constructor(sessionManager: AdminPanelSessionManager);
    execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void>;
}
