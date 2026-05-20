import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { MemberInfoFacade } from '../../../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../../../session/PanelSessionManager.js';
/**
 * Handler for transaction history interactions (user_history_*).
 * Supports paginated view of currency, token, and redemption transactions.
 */
export declare class TransactionHistoryHandler implements InteractionHandler {
    private readonly memberInfoFacade;
    private readonly sessionManager;
    readonly customIdPrefix = "user_history";
    constructor(memberInfoFacade: MemberInfoFacade, sessionManager: PanelSessionManager);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
