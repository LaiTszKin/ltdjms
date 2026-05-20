import {
  type Result,
  Ok,
  Err,
  DomainError,
  type CacheService,
  type CacheKeyGenerator,
} from '@ltdjms/shared';
import { CurrencyAccountRepository } from '../repositories/currency-account-repo.js';
import { CurrencyConfigRepository } from '../repositories/currency-config-repo.js';
import type { BalanceView, GuildCurrencyConfig } from '../../domain/types.js';
import {
  DEFAULT_CURRENCY_NAME,
  DEFAULT_CURRENCY_ICON,
  BALANCE_CACHE_TTL,
} from '../../domain/types.js';

/**
 * Service for retrieving member balances with caching.
 * Matches Java DefaultBalanceService behavior.
 */
export class BalanceService {
  private static readonly BALANCE_TTL_SECONDS = BALANCE_CACHE_TTL;
  private static readonly CONFIG_CACHE_TTL_SECONDS = 600;

  /** In-memory cache for currency config to avoid repeated DB queries. */
  private readonly configCache = new Map<string, { config: GuildCurrencyConfig | null; expiresAt: number }>();

  constructor(
    private readonly accountRepository: CurrencyAccountRepository,
    private readonly configRepository: CurrencyConfigRepository,
    private readonly cacheService: CacheService,
    private readonly cacheKeyGenerator: CacheKeyGenerator,
  ) {}

  /**
   * Gets the currency config from in-memory cache, falling through to DB on miss.
   */
  private async getCachedConfig(guildId: number): Promise<{ currencyName: string; currencyIcon: string }> {
    const cacheKey = `currency_config:${guildId}`;
    const now = Date.now();

    const cached = this.configCache.get(cacheKey);
    if (cached && cached.expiresAt > now) {
      const config = cached.config;
      return {
        currencyName: config?.currencyName ?? DEFAULT_CURRENCY_NAME,
        currencyIcon: config?.currencyIcon ?? DEFAULT_CURRENCY_ICON,
      };
    }

    const config = await this.configRepository.findByGuildId(guildId);
    this.configCache.set(cacheKey, {
      config: config ?? null,
      expiresAt: now + BalanceService.CONFIG_CACHE_TTL_SECONDS * 1000,
    });

    return {
      currencyName: config?.currencyName ?? DEFAULT_CURRENCY_NAME,
      currencyIcon: config?.currencyIcon ?? DEFAULT_CURRENCY_ICON,
    };
  }

  /**
   * Gets the balance view for a member in a guild.
   * Uses cache (TTL 300s) - cache miss falls through to DB.
   * Auto-creates account if none exists.
   *
   * This is the non-Result variant, matching Java's getBalance().
   * For the Result-based variant referenced in spec R1.1, see {@link tryGetBalance}.
   */
  async getBalance(guildId: number, userId: number): Promise<BalanceView> {
    const cacheKey = this.cacheKeyGenerator.balanceKey(String(guildId), String(userId));
    const cachedBalance = await this.cacheService.get<number>(cacheKey);

    let balance: number;
    if (cachedBalance !== null) {
      balance = cachedBalance;
    } else {
      const account = await this.accountRepository.findOrCreate(guildId, userId);
      balance = account.balance;
      await this.cacheService.put(cacheKey, balance, BalanceService.BALANCE_TTL_SECONDS);
    }

    const { currencyName, currencyIcon } = await this.getCachedConfig(guildId);

    return {
      guildId,
      userId,
      balance,
      currencyName,
      currencyIcon,
    };
  }

  /**
   * Gets the balance view with Result-based error handling.
   * This is the Result-based variant referenced in spec R1.1,
   * matching Java's tryGetBalance().
   */
  async tryGetBalance(
    guildId: number,
    userId: number,
  ): Promise<Result<BalanceView, DomainError>> {
    try {
      const view = await this.getBalance(guildId, userId);
      return new Ok(view);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to retrieve balance for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }
}
