import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type GameTokenService } from '../token/services/game-token-service.js';
/**
 * /game-token-adjust slash command handler (admin only).
 * Adjusts a member's game token balance by the specified amount.
 */
export declare class GameTokenAdjustHandler {
    private readonly gameTokenService;
    readonly commandName = "game-token-adjust";
    constructor(gameTokenService: GameTokenService);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
