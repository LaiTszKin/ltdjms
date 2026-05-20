import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { type EscortOptionCatalogRepository } from '@ltdjms/dispatch';
/**
 * Handler for escort catalog interactions (admin_escortcatalog_*).
 * Supports CRUD operations on the global escort option catalog.
 */
export declare class EscortCatalogHandler implements InteractionHandler {
    private readonly sessionManager;
    private readonly catalogRepository;
    readonly customIdPrefix = "admin_escortcatalog";
    constructor(sessionManager: AdminPanelSessionManager, catalogRepository: EscortOptionCatalogRepository);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
