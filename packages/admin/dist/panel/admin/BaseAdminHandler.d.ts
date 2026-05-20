import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { type AdminPanelSessionData } from '../../session/types.js';
import { BotErrorHandler } from '../../commands/infra/BotErrorHandler.js';
/**
 * Abstract base class for admin panel interaction handlers.
 * Provides common admin permission checking, session access, and deferred reply.
 *
 * @abstract
 * @implements InteractionHandler
 */
export declare abstract class BaseAdminHandler implements InteractionHandler {
    protected readonly sessionManager: AdminPanelSessionManager;
    protected readonly errorHandler: BotErrorHandler;
    abstract readonly customIdPrefix: string;
    constructor(sessionManager: AdminPanelSessionManager, errorHandler: BotErrorHandler);
    abstract execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
    /**
     * Checks whether the user has ADMINISTRATOR permission or is the guild owner.
     * Uses the raw Discord interaction from getHook().
     */
    protected checkAdminPermission(interaction: DiscordInteraction): boolean;
    /**
     * Retrieves the current admin panel session for the interaction user.
     * Returns null if no active session exists.
     */
    protected getSession(interaction: DiscordInteraction): AdminPanelSessionData | null;
    /**
     * Ensures the interaction has been deferred.
     * Safe to call multiple times — the DiscordInteraction abstraction
     * checks isAcknowledged() before deferring.
     */
    protected ensureDeferred(interaction: DiscordInteraction): Promise<void>;
}
