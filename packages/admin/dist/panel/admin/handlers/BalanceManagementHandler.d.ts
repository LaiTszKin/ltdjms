import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { CurrencyManagementFacade } from '../../../facades/CurrencyManagementFacade.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
/**
 * Handler for balance management interactions (admin_balance_*).
 * Supports select member, view balance, add/deduct/set via modal.
 */
export declare class BalanceManagementHandler implements InteractionHandler {
    private readonly facade;
    private readonly sessionManager;
    readonly customIdPrefix = "admin_balance";
    constructor(facade: CurrencyManagementFacade, sessionManager: AdminPanelSessionManager);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
