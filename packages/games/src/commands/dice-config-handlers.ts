import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type DiceConfigService } from '../dice/services/dice-config-service.js';
import { DiceGameMessages } from '@ltdjms/shared';

/**
 * /dice-game-1-config slash command handler (admin only).
 * Updates the dice game 1 configuration (min/max tokens, reward per dice value).
 */
export class DiceGame1ConfigHandler {
  readonly commandName = 'dice-game-1-config';

  constructor(private readonly diceConfigService: DiceConfigService) {}

  async execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void> {
    interaction.makeEphemeral();
    if (!interaction.isAdministrator()) {
      await interaction.reply('此操作需要管理員權限');
      return;
    }

    const guildId = Number(interaction.getGuildId());

    const minTokensStr = context.getOptionAsString('min-tokens');
    const maxTokensStr = context.getOptionAsString('max-tokens');
    const rewardStr = context.getOptionAsString('reward');

    if (!minTokensStr || !maxTokensStr || !rewardStr) {
      await interaction.reply(DiceGameMessages.INVALID_OPTION);
      return;
    }

    const minTokens = parseInt(minTokensStr, 10);
    const maxTokens = parseInt(maxTokensStr, 10);
    const rewardPerDiceValue = parseInt(rewardStr, 10);

    if (
      !Number.isFinite(minTokens) ||
      !Number.isFinite(maxTokens) ||
      !Number.isFinite(rewardPerDiceValue) ||
      minTokens <= 0 ||
      maxTokens <= 0 ||
      rewardPerDiceValue <= 0 ||
      maxTokens < minTokens
    ) {
      await interaction.reply(DiceGameMessages.INVALID_OPTION);
      return;
    }

    try {
      const saved = await this.diceConfigService.upsertDice1Config({
        guildId,
        minTokensPerPlay: minTokens,
        maxTokensPerPlay: maxTokens,
        rewardPerDiceValue,
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      await interaction.reply(
        `${DiceGameMessages.DICE_CONFIG_SUCCESS}\n${DiceGameMessages.DICE_CONFIG_1_DISPLAY.replace(
          '{min}',
          String(saved.minTokensPerPlay),
        )
          .replace('{max}', String(saved.maxTokensPerPlay))
          .replace('{reward}', String(saved.rewardPerDiceValue))}`,
      );
    } catch (err) {
      await interaction.reply(
        DiceGameMessages.DICE_CONFIG_FAILED.replace(
          '{reason}',
          err instanceof Error ? err.message : String(err),
        ),
      );
    }
  }
}

/**
 * /dice-game-2-config slash command handler (admin only).
 * Updates the dice game 2 configuration (min/max tokens, multipliers, bonuses).
 */
export class DiceGame2ConfigHandler {
  readonly commandName = 'dice-game-2-config';

  constructor(private readonly diceConfigService: DiceConfigService) {}

  async execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void> {
    interaction.makeEphemeral();
    if (!interaction.isAdministrator()) {
      await interaction.reply('此操作需要管理員權限');
      return;
    }

    const guildId = Number(interaction.getGuildId());

    const minTokensStr = context.getOptionAsString('min-tokens');
    const maxTokensStr = context.getOptionAsString('max-tokens');
    const straightMulStr = context.getOptionAsString('straight-multiplier');
    const baseMulStr = context.getOptionAsString('base-multiplier');
    const tripleLowStr = context.getOptionAsString('triple-low-bonus');
    const tripleHighStr = context.getOptionAsString('triple-high-bonus');

    if (
      !minTokensStr ||
      !maxTokensStr ||
      !straightMulStr ||
      !baseMulStr ||
      !tripleLowStr ||
      !tripleHighStr
    ) {
      await interaction.reply(DiceGameMessages.INVALID_OPTION);
      return;
    }

    const minTokens = parseInt(minTokensStr, 10);
    const maxTokens = parseInt(maxTokensStr, 10);
    const straightMultiplier = parseInt(straightMulStr, 10);
    const baseMultiplier = parseInt(baseMulStr, 10);
    const tripleLowBonus = parseInt(tripleLowStr, 10);
    const tripleHighBonus = parseInt(tripleHighStr, 10);

    if (
      !Number.isFinite(minTokens) ||
      !Number.isFinite(maxTokens) ||
      !Number.isFinite(straightMultiplier) ||
      !Number.isFinite(baseMultiplier) ||
      !Number.isFinite(tripleLowBonus) ||
      !Number.isFinite(tripleHighBonus) ||
      minTokens <= 0 ||
      maxTokens <= 0 ||
      straightMultiplier <= 0 ||
      baseMultiplier <= 0 ||
      tripleLowBonus <= 0 ||
      tripleHighBonus <= 0 ||
      maxTokens < minTokens
    ) {
      await interaction.reply(DiceGameMessages.INVALID_OPTION);
      return;
    }

    try {
      const saved = await this.diceConfigService.upsertDice2Config({
        guildId,
        minTokensPerPlay: minTokens,
        maxTokensPerPlay: maxTokens,
        straightMultiplier,
        baseMultiplier,
        tripleLowBonus,
        tripleHighBonus,
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      await interaction.reply(
        `${DiceGameMessages.DICE_CONFIG_SUCCESS}\n${DiceGameMessages.DICE_CONFIG_2_DISPLAY.replace(
          '{min}',
          String(saved.minTokensPerPlay),
        )
          .replace('{max}', String(saved.maxTokensPerPlay))
          .replace('{straightMul}', String(saved.straightMultiplier))
          .replace('{baseMul}', String(saved.baseMultiplier))
          .replace('{tripleLow}', String(saved.tripleLowBonus))
          .replace('{tripleHigh}', String(saved.tripleHighBonus))}`,
      );
    } catch (err) {
      await interaction.reply(
        DiceGameMessages.DICE_CONFIG_FAILED.replace(
          '{reason}',
          err instanceof Error ? err.message : String(err),
        ),
      );
    }
  }
}
