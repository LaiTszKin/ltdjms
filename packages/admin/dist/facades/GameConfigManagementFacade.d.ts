import { type Result, DomainError, type DomainEventPublisher } from '@ltdjms/shared';
import { DiceConfigRepository, type DiceGame1Config, type DiceGame2Config } from '@ltdjms/economy';
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
export declare class GameConfigManagementFacade {
    private readonly diceConfigRepo;
    private readonly eventPublisher;
    constructor(diceConfigRepo: DiceConfigRepository, eventPublisher: DomainEventPublisher);
    /**
     * Gets the Dice Game 1 configuration for a guild.
     */
    getDiceGame1Config(guildId: string): Promise<Result<DiceGame1Config, DomainError>>;
    /**
     * Updates the Dice Game 1 configuration for a guild.
     * Publishes DiceGameConfigChangedEvent on success.
     */
    updateDiceGame1Config(guildId: string, config: DiceGame1ConfigUpdate): Promise<Result<DiceGame1Config, DomainError>>;
    /**
     * Gets the Dice Game 2 configuration for a guild.
     */
    getDiceGame2Config(guildId: string): Promise<Result<DiceGame2Config, DomainError>>;
    /**
     * Updates the Dice Game 2 configuration for a guild.
     * Publishes DiceGameConfigChangedEvent on success.
     */
    updateDiceGame2Config(guildId: string, config: DiceGame2ConfigUpdate): Promise<Result<DiceGame2Config, DomainError>>;
}
