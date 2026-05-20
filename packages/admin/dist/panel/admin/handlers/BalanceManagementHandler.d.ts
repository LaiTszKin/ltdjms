import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { CurrencyManagementFacade } from '../../../facades/CurrencyManagementFacade.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
/**
 * Handler for balance management interactions (admin_balance_*).
 * Supports select member, view balance, add/deduct/set via modal.
 *
 * NOTE: This is the first handler to extend BaseAdminHandler (P2-42).
 * The remaining admin handlers (TokenManagementHandler, GameSettingsHandler,
 * AIChannelConfigHandler, etc.) should also be migrated to extend
 * BaseAdminHandler for shared session/permission/defer infrastructure.
 */
export declare class BalanceManagementHandler extends BaseAdminHandler {
    private readonly facade;
    readonly customIdPrefix = "admin_balance";
    constructor(facade: CurrencyManagementFacade, sessionManager: AdminPanelSessionManager, errorHandler: BotErrorHandler);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
