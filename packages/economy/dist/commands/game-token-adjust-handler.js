import { DiceGameMessages } from '../localization/dice-game-messages.js';
/**
 * /game-token-adjust slash command handler (admin only).
 * Adjusts a member's game token balance by the specified amount.
 */
export class GameTokenAdjustHandler {
    gameTokenService;
    commandName = 'game-token-adjust';
    constructor(gameTokenService) {
        this.gameTokenService = gameTokenService;
    }
    async execute(interaction, context) {
        const guildId = Number(interaction.getGuildId());
        const actorId = Number(interaction.getUserId());
        const targetUserIdStr = context.getOptionAsString('user');
        const amountStr = context.getOptionAsString('amount');
        if (!targetUserIdStr || !amountStr) {
            await interaction.reply(DiceGameMessages.INVALID_OPTION);
            return;
        }
        const targetUserId = parseInt(targetUserIdStr, 10);
        const amount = parseInt(amountStr, 10);
        if (!Number.isFinite(targetUserId) || !Number.isFinite(amount) || amount === 0) {
            await interaction.reply(DiceGameMessages.INVALID_OPTION);
            return;
        }
        const result = await this.gameTokenService.tryAdjustTokens(guildId, targetUserId, amount);
        if (result.isErr()) {
            const error = result.getError();
            await interaction.reply(DiceGameMessages.TOKEN_ADJUST_FAILED
                .replace('{reason}', error.message));
            return;
        }
        const adjustment = result.getValue();
        const message = [
            `**${DiceGameMessages.TOKEN_ADJUST_TITLE}**`,
            '',
            DiceGameMessages.TOKEN_ADJUST_SUCCESS
                .replace('{before}', String(adjustment.previousTokens))
                .replace('{after}', String(adjustment.newTokens))
                .replace('{amount}', String(adjustment.adjustment)),
        ].join('\n');
        await interaction.reply(message);
    }
}
//# sourceMappingURL=game-token-adjust-handler.js.map