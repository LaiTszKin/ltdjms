import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
/**
 * Routes admin panel button/select/modal interactions to the appropriate sub-handler
 * based on the customId prefix.
 * Matches Java AdminPanelRouter.
 */
export declare class AdminPanelRouter implements InteractionHandler {
    private readonly sessionManager;
    readonly customIdPrefix = "admin_";
    constructor(sessionManager: AdminPanelSessionManager);
    execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void>;
}
