import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
/**
 * Handler for escort catalog interactions (admin_escortcatalog_*).
 * Supports CRUD operations on the global escort option catalog.
 */
export declare class EscortCatalogHandler implements InteractionHandler {
    private readonly sessionManager;
    readonly customIdPrefix = "admin_escortcatalog";
    constructor(sessionManager: AdminPanelSessionManager);
    execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void>;
}
