import { EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle } from 'discord.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';
/**
 * /user-panel slash command handler.
 * Opens the user panel showing balance, tokens, and action buttons.
 */
export class UserPanelCommand {
    memberInfoFacade;
    sessionManager;
    embedBuilder;
    commandName = 'user-panel';
    constructor(memberInfoFacade, sessionManager, embedBuilder) {
        this.memberInfoFacade = memberInfoFacade;
        this.sessionManager = sessionManager;
        this.embedBuilder = embedBuilder;
    }
    async execute(interaction, context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        // Create session
        this.sessionManager.createSession(guildId, userId);
        // Query member info
        const result = await this.memberInfoFacade.getUserPanelView(guildId, userId);
        if (result.isErr()) {
            await interaction.reply(ZhTwStrings.unexpectedError);
            return;
        }
        const view = result.getValue();
        // Build embed from structured data
        const embedData = this.embedBuilder.buildUserPanelEmbed(view);
        const embed = new EmbedBuilder()
            .setTitle(embedData.title)
            .setDescription(embedData.description)
            .setColor(embedData.color);
        // Build action buttons
        const buttons = [
            new ButtonBuilder()
                .setCustomId('user_currency_history')
                .setLabel(ZhTwStrings.userPanelBtnCurrencyHistory)
                .setStyle(ButtonStyle.Secondary),
            new ButtonBuilder()
                .setCustomId('user_token_history')
                .setLabel(ZhTwStrings.userPanelBtnTokenHistory)
                .setStyle(ButtonStyle.Secondary),
            new ButtonBuilder()
                .setCustomId('user_redemption_history')
                .setLabel(ZhTwStrings.userPanelBtnRedemptionHistory)
                .setStyle(ButtonStyle.Secondary),
            new ButtonBuilder()
                .setCustomId('user_redeem_code')
                .setLabel(ZhTwStrings.userPanelBtnRedeemCode)
                .setStyle(ButtonStyle.Primary),
        ];
        const row = new ActionRowBuilder().addComponents(buttons);
        // Use the raw discord.js interaction to send embed with components
        const raw = interaction.getHook();
        await raw.reply({ embeds: [embed], components: [row] });
    }
}
//# sourceMappingURL=UserPanelCommand.js.map