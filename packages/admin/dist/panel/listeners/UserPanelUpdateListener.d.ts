import { type DomainEvent } from '@ltdjms/shared';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { MemberInfoFacade } from '../../facades/MemberInfoFacade.js';
/**
 * Listens to domain events and updates active user panel sessions.
 * Events: BalanceChangedEvent, GameTokenChangedEvent, CurrencyConfigChangedEvent.
 * Matches Java UserPanelUpdateListener.
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
    private isRelevantEvent;
    private isBalanceChanged;
    private isTokenChanged;
    private isConfigChanged;
}
