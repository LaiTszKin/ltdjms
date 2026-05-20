import { DiceGameMessages } from '../localization/dice-game-messages.js';
/**
 * /dice-game-2 slash command handler.
 * Plays dice game 2 with the specified number of tokens (each token = 3 dice).
 * Shows detailed analysis of straights, triples, and remaining dice.
 */
export class DiceGame2Handler {
    diceGame2Service;
    commandName = 'dice-game-2';
    constructor(diceGame2Service) {
        this.diceGame2Service = diceGame2Service;
    }
    async execute(interaction, context) {
        const guildId = Number(interaction.getGuildId());
        const userId = Number(interaction.getUserId());
        const tokenCountStr = context.getOptionAsString('tokens');
        if (!tokenCountStr) {
            await interaction.reply(DiceGameMessages.INVALID_TOKEN_COUNT);
            return;
        }
        const tokenCount = parseInt(tokenCountStr, 10);
        if (!Number.isFinite(tokenCount) || tokenCount <= 0) {
            await interaction.reply(DiceGameMessages.INVALID_TOKEN_COUNT);
            return;
        }
        const result = await this.diceGame2Service.play(guildId, userId, tokenCount);
        if (result.isErr()) {
            const error = result.getError();
            await interaction.reply(error.message);
            return;
        }
        const gameResult = result.getValue();
        const diceDisplay = [...gameResult.diceRolls].join('、');
        const straightDisplay = gameResult.straightSegments.length > 0
            ? gameResult.straightSegments
                .map((seg) => `[${[...seg].join('、')}]`)
                .join(' ')
            : '無';
        const tripleDisplay = gameResult.tripleSegments.length > 0
            ? gameResult.tripleSegments
                .map((seg) => `[${[...seg].join('、')}]`)
                .join(' ')
            : '無';
        const message = [
            `**${DiceGameMessages.GAME_2_TITLE}**`,
            '',
            DiceGameMessages.GAME_2_RESULT
                .replace('{dice}', diceDisplay)
                .replace('{straightSegments}', straightDisplay)
                .replace('{tripleSegments}', tripleDisplay)
                .replace('{straightReward}', String(gameResult.straightReward))
                .replace('{tripleReward}', String(gameResult.tripleReward))
                .replace('{baseReward}', String(gameResult.nonStraightReward))
                .replace('{totalReward}', String(gameResult.totalReward))
                .replace('{previousBalance}', String(gameResult.previousBalance))
                .replace('{newBalance}', String(gameResult.newBalance)),
        ].join('\n');
        await interaction.reply(message);
    }
}
//# sourceMappingURL=dice-game-2-handler.js.map