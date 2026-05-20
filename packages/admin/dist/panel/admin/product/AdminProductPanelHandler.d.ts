import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { type ShopService } from '@ltdjms/shop';
/**
 * Product-specific handler for the admin panel.
 * Manages the full product CRUD lifecycle with session state tracking.
 * Matches Java AdminProductPanelHandler.
 */
export declare class AdminProductPanelHandler implements InteractionHandler {
    private readonly sessionManager;
    private readonly shopService;
    readonly customIdPrefix = "admin_product";
    constructor(sessionManager: AdminPanelSessionManager, shopService: ShopService);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
