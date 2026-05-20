import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type BalanceService } from '../currency/services/balance-service.js';
/**
 * /balance slash command handler.
 * Displays the caller's current currency balance with currency name and icon.
 */
export declare class BalanceHandler {
    private readonly balanceService;
    readonly commandName = "balance";
    constructor(balanceService: BalanceService);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
