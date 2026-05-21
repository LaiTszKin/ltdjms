import {
  type Result,
  ok,
  err,
  DomainError,
} from '@ltdjms/shared';
import {
  type DiceConfigService,
  type DiceGame1Config,
  type DiceGame2Config,
} from '@ltdjms/economy';

/**
 * Configuration update parameters for Dice Game 1.
 */
export interface DiceGame1ConfigUpdate {
  minTokensPerPlay: number;
  maxTokensPerPlay: number;
  rewardPerDiceValue: number;
}

/**
 * Configuration update parameters for Dice Game 2.
 */
export interface DiceGame2ConfigUpdate {
  minTokensPerPlay: number;
  maxTokensPerPlay: number;
  straightMultiplier: number;
  baseMultiplier: number;
  tripleLowBonus: number;
  tripleHighBonus: number;
}

/**
 * Facade for dice game configuration management.
 * Wraps DiceConfigRepository and handles event publishing.
 * Matches Java GameConfigManagementFacade.
 */
export class GameConfigManagementFacade {
  constructor(
    private readonly diceConfigService: DiceConfigService,
  ) {}

  /**
   * Gets the Dice Game 1 configuration for a guild.
   */
  async getDiceGame1Config(
    guildId: string,
  ): Promise<Result<DiceGame1Config, DomainError>> {
    try {
      const config = await this.diceConfigService.findDice1Config(Number(guildId));
      if (!config) {
        return err(DomainError.invalidInput('尚未設定骰子遊戲 1 的設定'));
      }
      return ok(config);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to get dice game 1 config for guildId=${guildId}`,
          e instanceof Error ? e : undefined,
        ),
      );
    }
  }

  /**
   * Updates the Dice Game 1 configuration for a guild.
   * Publishes DiceGameConfigChangedEvent on success.
   */
  async updateDiceGame1Config(
    guildId: string,
    config: DiceGame1ConfigUpdate,
  ): Promise<Result<DiceGame1Config, DomainError>> {
    // Validate
    if (
      config.minTokensPerPlay <= 0 ||
      config.maxTokensPerPlay <= 0 ||
      config.rewardPerDiceValue <= 0
    ) {
      return err(DomainError.invalidInput('所有數值必須為正數'));
    }
    if (config.minTokensPerPlay >= config.maxTokensPerPlay) {
      return err(DomainError.invalidInput('最高代幣必須大於最低代幣'));
    }

    try {
      const now = new Date();
      const numericGuildId = Number(guildId);
      const currentConfig = await this.diceConfigService.findDice1Config(numericGuildId);

      const updated: DiceGame1Config = {
        guildId: numericGuildId,
        minTokensPerPlay: config.minTokensPerPlay,
        maxTokensPerPlay: config.maxTokensPerPlay,
        rewardPerDiceValue: config.rewardPerDiceValue,
        createdAt: currentConfig?.createdAt ?? now,
        updatedAt: now,
      };

      const saved = await this.diceConfigService.upsertDice1Config(updated);

      return ok(saved);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to update dice game 1 config for guildId=${guildId}`,
          e instanceof Error ? e : undefined,
        ),
      );
    }
  }

  /**
   * Gets the Dice Game 2 configuration for a guild.
   */
  async getDiceGame2Config(
    guildId: string,
  ): Promise<Result<DiceGame2Config, DomainError>> {
    try {
      const config = await this.diceConfigService.findDice2Config(Number(guildId));
      if (!config) {
        return err(DomainError.invalidInput('尚未設定骰子遊戲 2 的設定'));
      }
      return ok(config);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to get dice game 2 config for guildId=${guildId}`,
          e instanceof Error ? e : undefined,
        ),
      );
    }
  }

  /**
   * Updates the Dice Game 2 configuration for a guild.
   * Publishes DiceGameConfigChangedEvent on success.
   */
  async updateDiceGame2Config(
    guildId: string,
    config: DiceGame2ConfigUpdate,
  ): Promise<Result<DiceGame2Config, DomainError>> {
    // Validate
    if (
      config.minTokensPerPlay <= 0 ||
      config.maxTokensPerPlay <= 0 ||
      config.straightMultiplier < 1.0 ||
      config.baseMultiplier < 1.0 ||
      config.tripleLowBonus < 1.0 ||
      config.tripleHighBonus < 1.0
    ) {
      return err(DomainError.invalidInput('所有倍率必須大於或等於 1.0，代幣數量必須為正數'));
    }
    if (config.minTokensPerPlay >= config.maxTokensPerPlay) {
      return err(DomainError.invalidInput('最高代幣必須大於最低代幣'));
    }

    try {
      const now = new Date();
      const numericGuildId = Number(guildId);
      const currentConfig = await this.diceConfigService.findDice2Config(numericGuildId);

      const updated: DiceGame2Config = {
        guildId: numericGuildId,
        minTokensPerPlay: config.minTokensPerPlay,
        maxTokensPerPlay: config.maxTokensPerPlay,
        straightMultiplier: config.straightMultiplier,
        baseMultiplier: config.baseMultiplier,
        tripleLowBonus: config.tripleLowBonus,
        tripleHighBonus: config.tripleHighBonus,
        createdAt: currentConfig?.createdAt ?? now,
        updatedAt: now,
      };

      const saved = await this.diceConfigService.upsertDice2Config(updated);

      return ok(saved);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to update dice game 2 config for guildId=${guildId}`,
          e instanceof Error ? e : undefined,
        ),
      );
    }
  }
}
