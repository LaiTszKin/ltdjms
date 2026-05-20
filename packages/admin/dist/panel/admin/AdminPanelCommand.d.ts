import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type CommandHandler } from '../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { AdminPanelViewFactory } from './views/AdminPanelViewFactory.js';
/**
 * /admin-panel slash command handler.
 * Opens the admin panel main menu with 9 feature buttons.
 * Requires ADMINISTRATOR permission (enforced by Discord and handler).
 */
export declare class AdminPanelCommand implements CommandHandler {
    private readonly sessionManager;
    private readonly viewFactory;
    readonly commandName = "admin-panel";
    constructor(sessionManager: AdminPanelSessionManager, viewFactory: AdminPanelViewFactory);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
    private hasAdminPermission;
}
