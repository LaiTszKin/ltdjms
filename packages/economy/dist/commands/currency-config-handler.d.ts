import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type CurrencyConfigService } from '../currency/services/currency-config-service.js';
/**
 * /currency-config slash command handler (admin only).
 * Updates the guild's currency name and icon.
 */
export declare class CurrencyConfigHandler {
    private readonly currencyConfigService;
    readonly commandName = "currency-config";
    constructor(currencyConfigService: CurrencyConfigService);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
