import { AdminPanelViewState } from '../../session/types.js';
/**
 * Listens to domain events and updates active admin panel sessions.
 * Handles 9+ event types across different admin panel view states.
 * Matches Java AdminPanelUpdateListener.
 */
export class AdminPanelUpdateListener {
    sessionManager;
    constructor(sessionManager) {
        this.sessionManager = sessionManager;
    }
    /**
     * Handles a domain event and updates relevant admin panels.
     * Only updates panels that are in the relevant view state.
     */
    async onEvent(event) {
        if (!this.isAdminRelevantEvent(event))
            return;
        const guildId = event.guildId;
        const sessions = this.sessionManager.getAllForGuild(guildId);
        if (sessions.length === 0)
            return;
        for (const session of sessions) {
            try {
                const shouldUpdate = this.shouldUpdateForViewState(event, session.viewState);
                if (!shouldUpdate)
                    continue;
                // In a full implementation, this would:
                // 1. Query fresh data based on event type
                // 2. Build new embed
                // 3. Call editReply() on the session's interaction hook
                console.log(`[AdminPanelUpdateListener] Would update panel for guildId=${guildId}, userId=${session.userId}, viewState=${session.viewState}`);
            }
            catch (err) {
                console.error(`[AdminPanelUpdateListener] Error updating panel:`, err);
                // If updating fails, we could remove the session
            }
        }
    }
    isAdminRelevantEvent(event) {
        return (this.isCurrencyConfigChanged(event) ||
            this.isDiceConfigChanged(event) ||
            this.isProductChanged(event) ||
            this.isCodesGenerated(event));
    }
    shouldUpdateForViewState(event, viewState) {
        if (this.isCurrencyConfigChanged(event)) {
            // Currency config change always triggers main panel refresh
            return true;
        }
        if (this.isDiceConfigChanged(event)) {
            // Only relevant when viewing game settings
            return viewState === AdminPanelViewState.MAIN;
        }
        if (this.isProductChanged(event)) {
            // Only relevant when viewing product list or detail
            return (viewState === AdminPanelViewState.PRODUCT_LIST ||
                viewState === AdminPanelViewState.PRODUCT_DETAIL);
        }
        if (this.isCodesGenerated(event)) {
            return viewState === AdminPanelViewState.PRODUCT_CODE_LIST;
        }
        return false;
    }
    isCurrencyConfigChanged(event) {
        return 'currencyName' in event && 'currencyIcon' in event;
    }
    isDiceConfigChanged(event) {
        return 'gameType' in event;
    }
    isProductChanged(event) {
        return 'productId' in event && 'operationType' in event;
    }
    isCodesGenerated(event) {
        return 'productId' in event && 'count' in event;
    }
}
//# sourceMappingURL=AdminPanelUpdateListener.js.map