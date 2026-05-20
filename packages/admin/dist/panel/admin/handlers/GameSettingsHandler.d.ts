import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { GameConfigManagementFacade } from '../../../facades/GameConfigManagementFacade.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
/**
 * Handler for game settings interactions (admin_game_*).
 * Supports game selection, view current config, edit via modal.
 */
export declare class GameSettingsHandler implements InteractionHandler {
    private readonly facade;
    private readonly sessionManager;
    readonly customIdPrefix = "admin_game";
    constructor(facade: GameConfigManagementFacade, sessionManager: AdminPanelSessionManager);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
