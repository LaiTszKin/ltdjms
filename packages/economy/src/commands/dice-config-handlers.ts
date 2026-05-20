import {
  type DiscordInteraction,
  type DiscordContext,
  type DomainEventPublisher,
  type DiceGameConfigChangedEvent,
  GameType,
} from '@ltdjms/shared';
import { type DiceConfigRepository } from '../dice/repositories/dice-config-repo.js';
import { DiceGameMessages } from '../localization/dice-game-messages.js';

/**
 * /dice-game-1-config slash command handler (admin only).
 * Updates the dice game 1 configuration (min/max tokens, reward per dice value).
 */
export class DiceGame1ConfigHandler {
  readonly commandName = 'dice-game-1-config';

  constructor(
    private readonly diceConfigRepository: DiceConfigRepository,
    private readonly eventPublisher: DomainEventPublisher,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
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
      // Fetch old config (if any) for the change event
      const oldConfig = await this.diceConfigRepository.findDice1Config(guildId);

      const saved = await this.diceConfigRepository.upsertDice1Config({
        guildId,
        minTokensPerPlay: minTokens,
        maxTokensPerPlay: maxTokens,
        rewardPerDiceValue,
        createdAt: new Date(),
        updatedAt: new Date(),
      });

      // Publish config changed event (P2-15)
      const event: DiceGameConfigChangedEvent = {
        guildId: String(guildId),
        eventType: 'dice_game_config_changed',
        gameType: GameType.DICE_GAME_1,
        oldConfig: oldConfig ? { ...oldConfig } : undefined,
        newConfig: { ...saved },
      };
      this.eventPublisher.publish(event);

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
 * Updates the dice game 2 configuration (min/max tokens, multipliers, bonuses).
 */
export class DiceGame2ConfigHandler {
  readonly commandName = 'dice-game-2-config';

  constructor(
    private readonly diceConfigRepository: DiceConfigRepository,
    private readonly eventPublisher: DomainEventPublisher,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
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

    try {
      // Fetch old config (if any) for the change event
      const oldConfig = await this.diceConfigRepository.findDice2Config(guildId);

      const saved = await this.diceConfigRepository.upsertDice2Config({
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

      // Publish config changed event (P2-15)
      const event: DiceGameConfigChangedEvent = {
        guildId: String(guildId),
        eventType: 'dice_game_config_changed',
        gameType: GameType.DICE_GAME_2,
        oldConfig: oldConfig ? { ...oldConfig } : undefined,
        newConfig: { ...saved },
      };
      this.eventPublisher.publish(event);

      await interaction.reply(
        `${DiceGameMessages.DICE_CONFIG_SUCCESS}\n${DiceGameMessages.DICE_CONFIG_2_DISPLAY
          .replace('{min}', String(saved.minTokensPerPlay))
          .replace('{max}', String(saved.maxTokensPerPlay))
          .replace('{straightMul}', String(saved.straightMultiplier))
          .replace('{baseMul}', String(saved.baseMultiplier))
          .replace('{tripleLow}', String(saved.tripleLowBonus))
          .replace('{tripleHigh}', String(saved.tripleHighBonus))}`,
      );
    } catch (err) {
      await interaction.reply(
        DiceGameMessages.DICE_CONFIG_FAILED
          .replace('{reason}', err instanceof Error ? err.message : String(err)),
      );
    }
  }
}
