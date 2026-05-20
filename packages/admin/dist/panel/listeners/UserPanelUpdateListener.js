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
export class UserPanelUpdateListener {
    sessionManager;
    memberInfoFacade;
    constructor(sessionManager, memberInfoFacade) {
        this.sessionManager = sessionManager;
        this.memberInfoFacade = memberInfoFacade;
    }
    /**
     * Handles a domain event and updates relevant user panels.
     * This is called by DomainEventPublisher for each registered listener.
     */
    async onEvent(event) {
        // Only handle events relevant to user panels
        if (!this.isRelevantEvent(event))
            return;
        const guildId = String(event.guildId);
        // Get all active sessions for this guild
        const sessions = this.sessionManager.getAllForGuild(guildId);
        if (sessions.length === 0) {
            const eventType = this.getEventTypeName(event);
            console.log(`[UserPanelUpdateListener] Event ${eventType} for guildId=${guildId}: no active sessions to update`);
            return;
        }
        // Determine affected user IDs based on event type
        let affectedUserIds = [];
        if (this.isBalanceChanged(event)) {
            affectedUserIds = [String(event.userId)];
        }
        else if (this.isTokenChanged(event)) {
            affectedUserIds = [String(event.userId)];
        }
        else if (this.isConfigChanged(event)) {
            // Currency config change affects all users in guild
            affectedUserIds = sessions.map((s) => s.userId);
        }
        // Update each affected session
        let updatedCount = 0;
        for (const session of sessions) {
            if (!affectedUserIds.includes(session.userId))
                continue;
            try {
                // Refresh data
                const result = await this.memberInfoFacade.getUserPanelView(guildId, session.userId);
                if (result.isErr()) {
                    console.error(`[UserPanelUpdateListener] Failed to refresh panel for guildId=${guildId}, userId=${session.userId}:`, result.getError());
                    continue;
                }
                const view = result.getValue();
                updatedCount++;
                // In a full implementation, this would update the user's panel embed.
                // The session data needs to store a channel ID or interaction hook
                // so the listener can call editReply() on the original panel message.
                //
                // For now, log what would be sent so the wiring is visible in logs:
                console.log(`[UserPanelUpdateListener] Would update panel for ` +
                    `guildId=${guildId}, userId=${session.userId}: ` +
                    `balance=${view.balance}${view.currencyIcon}, tokens=${view.tokens}`);
            }
            catch (err) {
                console.error(`[UserPanelUpdateListener] Error updating panel for guildId=${guildId}, userId=${session.userId}:`, err);
            }
        }
        const eventType = this.getEventTypeName(event);
        console.log(`[UserPanelUpdateListener] Event ${eventType}: updated ${updatedCount}/${sessions.length} active sessions in guildId=${guildId}`);
    }
    getEventTypeName(event) {
        if (this.isBalanceChanged(event))
            return 'BalanceChanged';
        if (this.isTokenChanged(event))
            return 'GameTokenChanged';
        if (this.isConfigChanged(event))
            return 'CurrencyConfigChanged';
        return 'Unknown';
    }
    isRelevantEvent(event) {
        return (this.isBalanceChanged(event) ||
            this.isTokenChanged(event) ||
            this.isConfigChanged(event));
    }
    isBalanceChanged(event) {
        return 'newBalance' in event && 'userId' in event && !('currencyName' in event);
    }
    isTokenChanged(event) {
        return 'newTokens' in event;
    }
    isConfigChanged(event) {
        return 'currencyName' in event && 'currencyIcon' in event;
    }
}
//# sourceMappingURL=UserPanelUpdateListener.js.map