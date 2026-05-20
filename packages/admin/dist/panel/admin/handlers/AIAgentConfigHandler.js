import { EmbedBuilder } from 'discord.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for AI agent config interactions (admin_aiagent_*).
 * Supports enable/disable/remove agent mode on channels.
 */
export class AIAgentConfigHandler {
    facade;
    sessionManager;
    customIdPrefix = 'admin_aiagent';
    constructor(facade, sessionManager) {
        this.facade = facade;
        this.sessionManager = sessionManager;
    }
    async execute(interaction, context) {
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        const session = this.sessionManager.getSession(guildId, userId);
        if (!session) {
            await interaction.reply(ZhTwStrings.sessionExpired);
            return;
        }
        await interaction.deferReply();
        // Get current agent config
        const result = await this.facade.getAgentConfigs(guildId);
        let description;
        if (result.isOk() && result.getValue().length > 0) {
            const channelList = result.getValue().map((ch) => `<#${ch}>`).join('\n');
            description = ZhTwStrings.aiAgentList.replace('{channels}', channelList);
        }
        else {
            description = ZhTwStrings.aiAgentEmpty;
        }
        const embed = new EmbedBuilder()
            .setTitle(ZhTwStrings.aiAgentTitle)
            .setDescription(description)
            .setColor(0x5865F2);
        await interaction.editEmbed(embed);
    }
}
//# sourceMappingURL=AIAgentConfigHandler.js.map