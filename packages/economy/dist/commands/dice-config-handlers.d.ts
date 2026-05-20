import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type DiceConfigRepository } from '../dice/repositories/dice-config-repo.js';
/**
 * /dice-game-1-config slash command handler (admin only).
 * Updates the dice game 1 configuration (min/max tokens, reward per dice value).
 */
export declare class DiceGame1ConfigHandler {
    private readonly diceConfigRepository;
    readonly commandName = "dice-game-1-config";
    constructor(diceConfigRepository: DiceConfigRepository);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
/**
 * /dice-game-2-config slash command handler (admin only).
 * Updates the dice game 2 configuration (min/max tokens, multipliers, bonuses).
 */
export declare class DiceGame2ConfigHandler {
    private readonly diceConfigRepository;
    readonly commandName = "dice-game-2-config";
    constructor(diceConfigRepository: DiceConfigRepository);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
