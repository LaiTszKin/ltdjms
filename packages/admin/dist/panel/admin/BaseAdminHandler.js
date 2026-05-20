/**
 * Abstract base class for admin panel interaction handlers.
 * Provides common admin permission checking, session access, and deferred reply.
 *
 * @abstract
 * @implements InteractionHandler
 */
export class BaseAdminHandler {
    sessionManager;
    errorHandler;
    constructor(sessionManager, errorHandler) {
        this.sessionManager = sessionManager;
        this.errorHandler = errorHandler;
    }
    /**
     * Checks whether the user has ADMINISTRATOR permission or is the guild owner.
     * Uses the raw Discord interaction from getHook().
     */
    checkAdminPermission(interaction) {
        try {
            const raw = interaction.getHook();
            const userId = String(interaction.getUserId());
            // Discord PermissionFlagsBits.Administrator is 8n
            if (raw.memberPermissions?.has(8n)) {
                return true;
            }
            if (raw.guild?.ownerId === userId) {
                return true;
            }
        }
        catch {
            // Fall through to denial
        }
        return false;
    }
    /**
     * Retrieves the current admin panel session for the interaction user.
     * Returns null if no active session exists.
     */
    getSession(interaction) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        return this.sessionManager.getSession(guildId, userId);
    }
    /**
     * Ensures the interaction has been deferred.
     * Safe to call multiple times — the DiscordInteraction abstraction
     * checks isAcknowledged() before deferring.
     */
    async ensureDeferred(interaction) {
        if (!interaction.isAcknowledged()) {
            await interaction.deferReply();
        }
    }
}
//# sourceMappingURL=BaseAdminHandler.js.map