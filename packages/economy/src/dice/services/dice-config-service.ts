import { type DomainEventPublisher } from '@ltdjms/shared';
import { type DiceGameConfigChangedEvent } from '../../events/index.js';
import { GameType } from '../../events/index.js';
import { DiceConfigRepository } from '../repositories/dice-config-repo.js';
import type { DiceGame1Config, DiceGame2Config } from '../../domain/types.js';

/**
 * Service for managing dice game configuration.
 * Wraps DiceConfigRepository with event publishing and serves as the
 * service-layer boundary for all dice config operations.
 */
export class DiceConfigService {
  constructor(
    private readonly diceConfigRepository: DiceConfigRepository,
    private readonly eventPublisher: DomainEventPublisher,
  ) {}

  /**
   * Finds dice game 1 configuration by guild ID.
   * Returns null if not found.
   */
  async findDice1Config(guildId: number): Promise<DiceGame1Config | null> {
    return this.diceConfigRepository.findDice1Config(guildId);
  }

  /**
   * Finds dice game 2 configuration by guild ID.
   * Returns null if not found.
   */
  async findDice2Config(guildId: number): Promise<DiceGame2Config | null> {
    return this.diceConfigRepository.findDice2Config(guildId);
  }

  /**
   * Finds dice game 1 configuration by guild ID.
   * Creates and persists a default config if none exists.
   */
  async findOrCreateDefaultDice1(guildId: number): Promise<DiceGame1Config> {
    return this.diceConfigRepository.findOrCreateDefaultDice1(guildId);
  }

  /**
   * Finds dice game 2 configuration by guild ID.
   * Creates and persists a default config if none exists.
   */
  async findOrCreateDefaultDice2(guildId: number): Promise<DiceGame2Config> {
    return this.diceConfigRepository.findOrCreateDefaultDice2(guildId);
  }

  /**
   * Upserts dice game 1 configuration and publishes a
   * DiceGameConfigChangedEvent on success.
   */
  async upsertDice1Config(config: DiceGame1Config): Promise<DiceGame1Config> {
    const oldConfig = await this.diceConfigRepository.findDice1Config(config.guildId);

    const saved = await this.diceConfigRepository.upsertDice1Config(config);

    const event: DiceGameConfigChangedEvent = {
      guildId: String(config.guildId),
      eventType: 'dice_game_config_changed',
      gameType: GameType.DICE_GAME_1,
      oldConfig: oldConfig ? { ...oldConfig } : undefined,
      newConfig: { ...saved },
    };
    this.eventPublisher.publish(event);

    return saved;
  }

  /**
   * Upserts dice game 2 configuration and publishes a
   * DiceGameConfigChangedEvent on success.
   */
  async upsertDice2Config(config: DiceGame2Config): Promise<DiceGame2Config> {
    const oldConfig = await this.diceConfigRepository.findDice2Config(config.guildId);

    const saved = await this.diceConfigRepository.upsertDice2Config(config);

    const event: DiceGameConfigChangedEvent = {
      guildId: String(config.guildId),
      eventType: 'dice_game_config_changed',
      gameType: GameType.DICE_GAME_2,
      oldConfig: oldConfig ? { ...oldConfig } : undefined,
      newConfig: { ...saved },
    };
    this.eventPublisher.publish(event);

    return saved;
  }
}
