import { type CurrencyConfigRepository } from '../currency/repositories/currency-config-repo.js';
import {
  DEFAULT_CURRENCY_NAME,
  DEFAULT_CURRENCY_ICON,
} from '../domain/types.js';

/**
 * Resolves the currency display name and icon for a guild.
 * Falls back to defaults if no custom currency config exists.
 *
 * Extracted as a shared utility to eliminate duplicate lookups
 * across dice game handlers (P2-14).
 */
export async function resolveCurrencyDisplay(
  guildId: number,
  currencyConfigRepository: CurrencyConfigRepository,
): Promise<{ currencyName: string; currencyIcon: string }> {
  const currencyConfig = await currencyConfigRepository.findByGuildId(guildId);
  return {
    currencyName: currencyConfig?.currencyName ?? DEFAULT_CURRENCY_NAME,
    currencyIcon: currencyConfig?.currencyIcon ?? DEFAULT_CURRENCY_ICON,
  };
}
