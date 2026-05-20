import { type DomainEvent } from '@ltdjms/shared';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
/**
 * Listens to domain events and updates active admin panel sessions.
 * Handles 13+ event types across different admin panel view states.
 * Matches Java AdminPanelUpdateListener.
 *
 * NOTE: The current implementation logs events and identifies which sessions
 * would be updated. To send actual updates to users, the session data should
 * be extended with an interaction hook or channel ID. This requires changes
 * to the session model and access to the Discord client, which will be
 * implemented when the session infrastructure is upgraded to Redis.
 */
export declare class AdminPanelUpdateListener {
    private readonly sessionManager;
    constructor(sessionManager: AdminPanelSessionManager);
    /**
     * Handles a domain event and updates relevant admin panels.
     * Only updates panels that are in the relevant view state.
     */
    onEvent(event: DomainEvent): Promise<void>;
    /**
     * Returns a human-readable name for the event type for logging.
     */
    private getEventTypeName;
    private isAdminRelevantEvent;
    private shouldUpdateForViewState;
    private isCurrencyConfigChanged;
    private isDiceConfigChanged;
    private isProductChanged;
    private isCodesGenerated;
    private isAIAgentChannelConfigChanged;
    private isBalanceChanged;
    private isGameTokenChanged;
    private isProductRedemptionCompleted;
    private isAgentFailed;
}
