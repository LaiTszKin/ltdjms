import { type DomainEvent } from '@ltdjms/shared';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { MemberInfoFacade } from '../../facades/MemberInfoFacade.js';
/**
 * Listens to domain events and updates active user panel sessions.
 * Events: BalanceChangedEvent, GameTokenChangedEvent, CurrencyConfigChangedEvent.
 * Matches Java UserPanelUpdateListener.
 *
 * NOTE: The current implementation fetches fresh data but does not push
 * updates to the user's panel message. To send actual embed updates,
 * the session data should be extended with a channel ID or interaction
 * token. This requires the session infrastructure to support storing
 * Discord interaction hooks, which will be addressed when migrating
 * to Redis-based session storage.
 */
export declare class UserPanelUpdateListener {
    private readonly sessionManager;
    private readonly memberInfoFacade;
    constructor(sessionManager: PanelSessionManager, memberInfoFacade: MemberInfoFacade);
    /**
     * Handles a domain event and updates relevant user panels.
     * This is called by DomainEventPublisher for each registered listener.
     */
    onEvent(event: DomainEvent): Promise<void>;
    private getEventTypeName;
    private isRelevantEvent;
    private isBalanceChanged;
    private isTokenChanged;
    private isConfigChanged;
}
