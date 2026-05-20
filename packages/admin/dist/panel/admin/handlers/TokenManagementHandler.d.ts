import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { GameTokenManagementFacade } from '../../../facades/GameTokenManagementFacade.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
/**
 * Handler for token management interactions (admin_token_*).
 * Supports select member, view tokens, add/deduct/set via modal.
 */
export declare class TokenManagementHandler implements InteractionHandler {
    private readonly facade;
    private readonly sessionManager;
    readonly customIdPrefix = "admin_token";
    constructor(facade: GameTokenManagementFacade, sessionManager: AdminPanelSessionManager);
    execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void>;
}
