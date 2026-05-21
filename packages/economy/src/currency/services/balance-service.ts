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

  private static readonly MAX_CACHE_SIZE = 1000;

  /** In-memory cache for currency config to avoid repeated DB queries. */
  private readonly configCache = new Map<string, { config: GuildCurrencyConfig | null; expiresAt: number }>();

  /** Per-key in-flight promises to prevent cache stampede on balance reads. */
  private readonly pendingFetches = new Map<string, Promise<number>>();

  /** Per-guild in-flight config fetches to coalesce concurrent cache misses (P1-11). */
  private readonly pendingConfigFetches = new Map<number, Promise<GuildCurrencyConfig | null>>();

  constructor(
    private readonly accountRepository: CurrencyAccountRepository,
    private readonly configRepository: CurrencyConfigRepository,
    private readonly cacheService: CacheService,
    private readonly cacheKeyGenerator: CacheKeyGenerator,
  ) {}

  /**
   * Gets the currency config from in-memory cache, falling through to DB on miss.
   * Coalesces concurrent cache misses for the same guildId to prevent
   * redundant DB queries (P1-11).
   */
  async getCachedConfig(guildId: number): Promise<{ currencyName: string; currencyIcon: string }> {
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

    // Coalesce concurrent requests for the same guildId
    const pending = this.pendingConfigFetches.get(guildId);
    if (pending) {
      const config = await pending;
      return {
        currencyName: config?.currencyName ?? DEFAULT_CURRENCY_NAME,
        currencyIcon: config?.currencyIcon ?? DEFAULT_CURRENCY_ICON,
      };
    }

    const fetch = this.configRepository.findByGuildId(guildId);
    this.pendingConfigFetches.set(guildId, fetch);
    try {
      const config = await fetch;
      this.configCache.set(cacheKey, {
        config: config ?? null,
        expiresAt: now + BalanceService.CONFIG_CACHE_TTL_SECONDS * 1000,
      });

      // Evict oldest entry when cache exceeds max capacity
      if (this.configCache.size > BalanceService.MAX_CACHE_SIZE) {
        const firstKey = this.configCache.keys().next();
        if (firstKey.value !== undefined) {
          this.configCache.delete(firstKey.value);
        }
      }

      return {
        currencyName: config?.currencyName ?? DEFAULT_CURRENCY_NAME,
        currencyIcon: config?.currencyIcon ?? DEFAULT_CURRENCY_ICON,
      };
    } finally {
      this.pendingConfigFetches.delete(guildId);
    }
  }

  /**
   * Gets the balance view for a member in a guild.
   * Uses cache (TTL 300s) - cache miss falls through to DB.
   * Auto-creates account if none exists.
   *
   * This is the raw-Promise variant (unchecked). For the Result-based variant
   * referenced in spec R1.1, see {@link getBalance}.
   */
  async getBalanceUnchecked(guildId: number, userId: string): Promise<BalanceView> {
    const cacheKey = this.cacheKeyGenerator.balanceKey(String(guildId), String(userId));
    const cachedBalance = await this.cacheService.get<number>(cacheKey);

    let balance: number;
    if (cachedBalance !== null) {
      balance = cachedBalance;
    } else {
      // Prevent cache stampede: coalesce concurrent requests for the same key
      const pending = this.pendingFetches.get(cacheKey);
      if (pending) {
        balance = await pending;
      } else {
        const fetchPromise = this.accountRepository.findOrCreate(guildId, userId)
          .then(account => account.balance);
        this.pendingFetches.set(cacheKey, fetchPromise);
        try {
          balance = await fetchPromise;
          await this.cacheService.put(cacheKey, balance, BalanceService.BALANCE_TTL_SECONDS);
        } finally {
          this.pendingFetches.delete(cacheKey);
        }
      }
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
   * This is the Result-based variant referenced in spec R1.1.
   */
  async getBalance(
    guildId: number,
    userId: string,
  ): Promise<Result<BalanceView, DomainError>> {
    try {
      const view = await this.getBalanceUnchecked(guildId, userId);
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
