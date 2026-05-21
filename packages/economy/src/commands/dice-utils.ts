import { type CurrencyConfigService } from '../currency/services/currency-config-service.js';

/**
 * Resolves the currency display name and icon for a guild.
 * Falls back to defaults if no custom currency config exists.
 *
 * Extracted as a shared utility to eliminate duplicate lookups
 * across dice game handlers (P2-14).
 */
export async function resolveCurrencyDisplay(
  guildId: number,
  currencyConfigService: CurrencyConfigService,
): Promise<{ currencyName: string; currencyIcon: string }> {
  const config = await currencyConfigService.getConfig(guildId);
  return {
    currencyName: config.currencyName,
    currencyIcon: config.currencyIcon,
  };
}
