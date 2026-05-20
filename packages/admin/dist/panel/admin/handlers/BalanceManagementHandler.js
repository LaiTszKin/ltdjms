import { EmbedBuilder } from 'discord.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
/**
 * Handler for balance management interactions (admin_balance_*).
 * Supports select member, view balance, add/deduct/set via modal.
 *
 * NOTE: This is the first handler to extend BaseAdminHandler (P2-42).
 * The remaining admin handlers (TokenManagementHandler, GameSettingsHandler,
 * AIChannelConfigHandler, etc.) should also be migrated to extend
 * BaseAdminHandler for shared session/permission/defer infrastructure.
 */
export class BalanceManagementHandler extends BaseAdminHandler {
    facade;
    customIdPrefix = 'admin_balance';
    constructor(facade, sessionManager, errorHandler) {
        super(sessionManager, errorHandler);
        this.facade = facade;
    }
    async execute(interaction, context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        const session = this.getSession(interaction);
        if (!session) {
            await interaction.reply(ZhTwStrings.sessionExpired);
            return;
        }
        await this.ensureDeferred(interaction);
        // Query the admin's own balance as a preview
        const result = await this.facade.getBalance(guildId, userId);
        if (result.isOk()) {
            const balanceView = result.getValue();
            const embed = new EmbedBuilder()
                .setTitle(ZhTwStrings.balanceTitle)
                .setDescription(ZhTwStrings.balanceDisplay
                .replace('{balance}', String(balanceView.balance))
                .replace('{currencyIcon}', balanceView.currencyIcon))
                .setColor(0x57F287);
            await interaction.editEmbed(embed);
        }
        else {
            const embed = new EmbedBuilder()
                .setTitle(ZhTwStrings.balanceTitle)
                .setDescription('請選擇成員進行貨幣管理')
                .setColor(0x57F287);
            await interaction.editEmbed(embed);
        }
    }
}
//# sourceMappingURL=BalanceManagementHandler.js.map