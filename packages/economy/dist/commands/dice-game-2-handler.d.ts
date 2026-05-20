import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type DiceGame2Service } from '../dice/services/dice-game-2-service.js';
/**
 * /dice-game-2 slash command handler.
 * Plays dice game 2 with the specified number of tokens (each token = 3 dice).
 * Shows detailed analysis of straights, triples, and remaining dice.
 */
export declare class DiceGame2Handler {
    private readonly diceGame2Service;
    readonly commandName = "dice-game-2";
    constructor(diceGame2Service: DiceGame2Service);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
