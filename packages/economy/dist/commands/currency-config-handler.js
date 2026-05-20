import { DiceGameMessages } from '../localization/dice-game-messages.js';
/**
 * /currency-config slash command handler (admin only).
 * Updates the guild's currency name and icon.
 */
export class CurrencyConfigHandler {
    currencyConfigService;
    commandName = 'currency-config';
    constructor(currencyConfigService) {
        this.currencyConfigService = currencyConfigService;
    }
    async execute(interaction, context) {
        const guildId = Number(interaction.getGuildId());
        const name = context.getOptionAsString('name');
        const icon = context.getOptionAsString('icon');
        if (!name || !icon) {
            await interaction.reply(DiceGameMessages.INVALID_OPTION);
            return;
        }
        const result = await this.currencyConfigService.tryUpdateConfig(guildId, name, icon);
        if (result.isErr()) {
            const error = result.getError();
            await interaction.reply(DiceGameMessages.CURRENCY_CONFIG_FAILED
                .replace('{reason}', error.message));
            return;
        }
        const config = result.getValue();
        await interaction.reply(DiceGameMessages.CURRENCY_CONFIG_SUCCESS
            .replace('{name}', config.currencyName)
            .replace('{icon}', config.currencyIcon));
    }
}
//# sourceMappingURL=currency-config-handler.js.map