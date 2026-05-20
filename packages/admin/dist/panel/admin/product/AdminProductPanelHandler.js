import { AdminPanelViewState } from '../../../session/types.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Product-specific handler for the admin panel.
 * Manages the full product CRUD lifecycle with session state tracking.
 * Matches Java AdminProductPanelHandler.
 */
export class AdminProductPanelHandler {
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
        this.sessionManager.setViewState(guildIdNum, userId, AdminPanelViewState.PRODUCT_LIST);
        await interaction.reply('產品管理');
    }
}
//# sourceMappingURL=AdminProductPanelHandler.js.map