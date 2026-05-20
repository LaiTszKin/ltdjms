import { type DomainEvent } from '@ltdjms/shared';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
/**
 * Listens to domain events and updates active admin panel sessions.
 * Handles 9+ event types across different admin panel view states.
 * Matches Java AdminPanelUpdateListener.
 */
export declare class AdminPanelUpdateListener {
    private readonly sessionManager;
    constructor(sessionManager: AdminPanelSessionManager);
    /**
     * Handles a domain event and updates relevant admin panels.
     * Only updates panels that are in the relevant view state.
     */
    onEvent(event: DomainEvent): Promise<void>;
    private isAdminRelevantEvent;
    private shouldUpdateForViewState;
    private isCurrencyConfigChanged;
    private isDiceConfigChanged;
    private isProductChanged;
    private isCodesGenerated;
}
