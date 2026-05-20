import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
/**
 * Handler for product management interactions (admin_product_*).
 * Supports product list, detail, create, edit, delete, and code generation.
 * Manages session state transitions: MAIN → PRODUCT_LIST → PRODUCT_DETAIL → PRODUCT_CODE_LIST.
 */
export declare class ProductManagementHandler implements InteractionHandler {
    private readonly sessionManager;
    readonly customIdPrefix = "admin_product";
    constructor(sessionManager: AdminPanelSessionManager);
    execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void>;
}
