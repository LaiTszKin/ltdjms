import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type DiceGame1Service } from '../dice/services/dice-game-1-service.js';
/**
 * /dice-game-1 slash command handler.
 * Plays dice game 1 with the specified number of tokens (each token = 1 die).
 */
export declare class DiceGame1Handler {
    private readonly diceGame1Service;
    readonly commandName = "dice-game-1";
    constructor(diceGame1Service: DiceGame1Service);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
