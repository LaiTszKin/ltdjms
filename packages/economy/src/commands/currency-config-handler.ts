import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type CurrencyConfigService } from '../currency/services/currency-config-service.js';
import { DiceGameMessages } from '../localization/dice-game-messages.js';

/**
 * /currency-config slash command handler (admin only).
 * Updates the guild's currency name and icon.
 */
export class CurrencyConfigHandler {
  readonly commandName = 'currency-config';

  constructor(
    private readonly currencyConfigService: CurrencyConfigService,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = Number(interaction.getGuildId());

    const name = context.getOptionAsString('name');
    const icon = context.getOptionAsString('icon');

    if (!name || !icon) {
      await interaction.reply(DiceGameMessages.INVALID_OPTION);
      return;
    }

    const result = await this.currencyConfigService.tryUpdateConfig(
      guildId,
      name,
      icon,
    );

    if (result.isErr()) {
      const error = result.getError();
      await interaction.reply(
        DiceGameMessages.CURRENCY_CONFIG_FAILED
          .replace('{reason}', error.message),
      );
      return;
    }

    const config = result.getValue();

    await interaction.reply(
      DiceGameMessages.CURRENCY_CONFIG_SUCCESS
        .replace('{name}', config.currencyName)
        .replace('{icon}', config.currencyIcon),
    );
  }
}
