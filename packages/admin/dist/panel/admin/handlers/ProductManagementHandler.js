import { AdminPanelViewState } from '../../../session/types.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for product management interactions (admin_product_*).
 * Supports product list, detail, create, edit, delete, and code generation.
 * Manages session state transitions: MAIN → PRODUCT_LIST → PRODUCT_DETAIL → PRODUCT_CODE_LIST.
 */
export class ProductManagementHandler {
    sessionManager;
    customIdPrefix = 'admin_product';
    constructor(sessionManager) {
        this.sessionManager = sessionManager;
    }
    async execute(interaction, _context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        const guildIdNum = interaction.getGuildId();
        const session = this.sessionManager.getSession(guildId, userId);
        if (!session) {
            await interaction.reply(ZhTwStrings.sessionExpired);
            return;
        }
        // Update session state
        this.sessionManager.setViewState(guildIdNum, userId, AdminPanelViewState.PRODUCT_LIST);
        await interaction.reply('產品管理功能');
    }
}
//# sourceMappingURL=ProductManagementHandler.js.map