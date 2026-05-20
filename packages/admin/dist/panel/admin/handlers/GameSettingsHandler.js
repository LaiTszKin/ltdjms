import { EmbedBuilder } from 'discord.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Handler for game settings interactions (admin_game_*).
 * Supports game selection, view current config, edit via modal.
 */
export class GameSettingsHandler {
    facade;
    sessionManager;
    customIdPrefix = 'admin_game';
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
        // Try to get Dice Game 1 config
        const dice1Result = await this.facade.getDiceGame1Config(guildId);
        const dice2Result = await this.facade.getDiceGame2Config(guildId);
        const descriptionLines = [];
        descriptionLines.push(`**${ZhTwStrings.gameDiceGame1}**`);
        if (dice1Result.isOk()) {
            const cfg = dice1Result.getValue();
            descriptionLines.push(ZhTwStrings.gameDice1Fields
                .replace('{min}', String(cfg.minTokensPerPlay))
                .replace('{max}', String(cfg.maxTokensPerPlay))
                .replace('{reward}', String(cfg.rewardPerDiceValue)));
        }
        else {
            descriptionLines.push('尚未設定');
        }
        descriptionLines.push('');
        descriptionLines.push(`**${ZhTwStrings.gameDiceGame2}**`);
        if (dice2Result.isOk()) {
            const cfg = dice2Result.getValue();
            descriptionLines.push(ZhTwStrings.gameDice2Fields
                .replace('{min}', String(cfg.minTokensPerPlay))
                .replace('{max}', String(cfg.maxTokensPerPlay))
                .replace('{straight}', String(cfg.straightMultiplier))
                .replace('{base}', String(cfg.baseMultiplier))
                .replace('{lowTriple}', String(cfg.tripleLowBonus))
                .replace('{highTriple}', String(cfg.tripleHighBonus)));
        }
        else {
            descriptionLines.push('尚未設定');
        }
        const embed = new EmbedBuilder()
            .setTitle(ZhTwStrings.gameSelectTitle)
            .setDescription(descriptionLines.join('\n'))
            .setColor(0xFEE75C);
        await interaction.editEmbed(embed);
    }
}
//# sourceMappingURL=GameSettingsHandler.js.map