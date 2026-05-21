import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type DiceConfigService } from '../dice/services/dice-config-service.js';
import { DiceGameMessages } from '../localization/dice-game-messages.js';

/**
 * /dice-game-1-config slash command handler (admin only).
 * Updates the dice game 1 configuration (min/max tokens, reward per dice value).
 */
export class DiceGame1ConfigHandler {
  readonly commandName = 'dice-game-1-config';

  constructor(
    private readonly diceConfigService: DiceConfigService,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
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
        `${DiceGameMessages.DICE_CONFIG_SUCCESS}\n${DiceGameMessages.DICE_CONFIG_1_DISPLAY
          .replace('{min}', String(saved.minTokensPerPlay))
          .replace('{max}', String(saved.maxTokensPerPlay))
          .replace('{reward}', String(saved.rewardPerDiceValue))}`,
      );
    } catch (err) {
      await interaction.reply(
        DiceGameMessages.DICE_CONFIG_FAILED
          .replace('{reason}', err instanceof Error ? err.message : String(err)),
      );
    }
  }
}

/**
 * /dice-game-2-config slash command handler (admin only).
 * Updates the dice game 2 configuration (min/max tokens, multipliers, bonuses,
 * and individual face multipliers).
 */
export class DiceGame2ConfigHandler {
  readonly commandName = 'dice-game-2-config';

  constructor(
    private readonly diceConfigService: DiceConfigService,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
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
      !minTokensStr || !maxTokensStr ||
      !straightMulStr || !baseMulStr ||
      !tripleLowStr || !tripleHighStr
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
      minTokens <= 0 || maxTokens <= 0 ||
      straightMultiplier <= 0 || baseMultiplier <= 0 ||
      tripleLowBonus <= 0 || tripleHighBonus <= 0 ||
      maxTokens < minTokens
    ) {
      await interaction.reply(DiceGameMessages.INVALID_OPTION);
      return;
    }

    // Parse optional face multipliers (P1-5)
    const faceMultipliers: [number, number, number, number, number, number] = [
      parseInt(context.getOptionAsString('face-1') ?? '1', 10),
      parseInt(context.getOptionAsString('face-2') ?? '1', 10),
      parseInt(context.getOptionAsString('face-3') ?? '1', 10),
      parseInt(context.getOptionAsString('face-4') ?? '1', 10),
      parseInt(context.getOptionAsString('face-5') ?? '1', 10),
      parseInt(context.getOptionAsString('face-6') ?? '1', 10),
    ];

    if (faceMultipliers.some(m => !Number.isFinite(m) || m < 0)) {
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
        faceMultipliers,
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      await interaction.reply(
        `${DiceGameMessages.DICE_CONFIG_SUCCESS}\n${DiceGameMessages.DICE_CONFIG_2_DISPLAY
          .replace('{min}', String(saved.minTokensPerPlay))
          .replace('{max}', String(saved.maxTokensPerPlay))
          .replace('{straightMul}', String(saved.straightMultiplier))
          .replace('{baseMul}', String(saved.baseMultiplier))
          .replace('{tripleLow}', String(saved.tripleLowBonus))
          .replace('{tripleHigh}', String(saved.tripleHighBonus))
          .replace('{face1}', String(saved.faceMultipliers[0]))
          .replace('{face2}', String(saved.faceMultipliers[1]))
          .replace('{face3}', String(saved.faceMultipliers[2]))
          .replace('{face4}', String(saved.faceMultipliers[3]))
          .replace('{face5}', String(saved.faceMultipliers[4]))
          .replace('{face6}', String(saved.faceMultipliers[5]))}`,
      );
    } catch (err) {
      await interaction.reply(
        DiceGameMessages.DICE_CONFIG_FAILED
          .replace('{reason}', err instanceof Error ? err.message : String(err)),
      );
    }
  }
}
