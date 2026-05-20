import { Ok, Err, DomainError, GameType, } from '@ltdjms/shared';
/**
 * Facade for dice game configuration management.
 * Wraps DiceConfigRepository and handles event publishing.
 * Matches Java GameConfigManagementFacade.
 */
export class GameConfigManagementFacade {
    diceConfigRepo;
    eventPublisher;
    constructor(diceConfigRepo, eventPublisher) {
        this.diceConfigRepo = diceConfigRepo;
        this.eventPublisher = eventPublisher;
    }
    /**
     * Gets the Dice Game 1 configuration for a guild.
     */
    async getDiceGame1Config(guildId) {
        try {
            const config = await this.diceConfigRepo.findDice1Config(guildId);
            if (!config) {
                return new Err(DomainError.invalidInput('尚未設定骰子遊戲 1 的設定'));
            }
            return new Ok(config);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to get dice game 1 config for guildId=${guildId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Updates the Dice Game 1 configuration for a guild.
     * Publishes DiceGameConfigChangedEvent on success.
     */
    async updateDiceGame1Config(guildId, config) {
        // Validate
        if (config.minTokensPerPlay <= 0 ||
            config.maxTokensPerPlay <= 0 ||
            config.rewardPerDiceValue <= 0) {
            return new Err(DomainError.invalidInput('所有數值必須為正數'));
        }
        if (config.minTokensPerPlay >= config.maxTokensPerPlay) {
            return new Err(DomainError.invalidInput('最高代幣必須大於最低代幣'));
        }
        try {
            const now = new Date();
            const currentConfig = await this.diceConfigRepo.findDice1Config(guildId);
            const updated = {
                guildId,
                minTokensPerPlay: config.minTokensPerPlay,
                maxTokensPerPlay: config.maxTokensPerPlay,
                rewardPerDiceValue: config.rewardPerDiceValue,
                createdAt: currentConfig?.createdAt ?? now,
                updatedAt: now,
            };
            const saved = await this.diceConfigRepo.upsertDice1Config(updated);
            // Publish event
            const event = {
                guildId,
                gameType: GameType.DICE_GAME_1,
            };
            this.eventPublisher.publish(event);
            return new Ok(saved);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to update dice game 1 config for guildId=${guildId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Gets the Dice Game 2 configuration for a guild.
     */
    async getDiceGame2Config(guildId) {
        try {
            const config = await this.diceConfigRepo.findDice2Config(guildId);
            if (!config) {
                return new Err(DomainError.invalidInput('尚未設定骰子遊戲 2 的設定'));
            }
            return new Ok(config);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to get dice game 2 config for guildId=${guildId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Updates the Dice Game 2 configuration for a guild.
     * Publishes DiceGameConfigChangedEvent on success.
     */
    async updateDiceGame2Config(guildId, config) {
        // Validate
        if (config.minTokensPerPlay <= 0 ||
            config.maxTokensPerPlay <= 0 ||
            config.straightMultiplier < 1.0 ||
            config.baseMultiplier < 1.0 ||
            config.tripleLowBonus < 1.0 ||
            config.tripleHighBonus < 1.0) {
            return new Err(DomainError.invalidInput('所有倍率必須大於或等於 1.0，代幣數量必須為正數'));
        }
        if (config.minTokensPerPlay >= config.maxTokensPerPlay) {
            return new Err(DomainError.invalidInput('最高代幣必須大於最低代幣'));
        }
        try {
            const now = new Date();
            const currentConfig = await this.diceConfigRepo.findDice2Config(guildId);
            const updated = {
                guildId,
                minTokensPerPlay: config.minTokensPerPlay,
                maxTokensPerPlay: config.maxTokensPerPlay,
                straightMultiplier: config.straightMultiplier,
                baseMultiplier: config.baseMultiplier,
                tripleLowBonus: config.tripleLowBonus,
                tripleHighBonus: config.tripleHighBonus,
                createdAt: currentConfig?.createdAt ?? now,
                updatedAt: now,
            };
            const saved = await this.diceConfigRepo.upsertDice2Config(updated);
            // Publish event
            const event = {
                guildId,
                gameType: GameType.DICE_GAME_2,
            };
            this.eventPublisher.publish(event);
            return new Ok(saved);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to update dice game 2 config for guildId=${guildId}`, err instanceof Error ? err : undefined));
        }
    }
}
//# sourceMappingURL=GameConfigManagementFacade.js.map