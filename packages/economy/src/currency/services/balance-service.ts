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
import type { BalanceView } from '../../domain/types.js';
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

  constructor(
    private readonly accountRepository: CurrencyAccountRepository,
    private readonly configRepository: CurrencyConfigRepository,
    private readonly cacheService: CacheService,
    private readonly cacheKeyGenerator: CacheKeyGenerator,
  ) {}

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

    const config = await this.configRepository.findByGuildId(guildId);
    const currencyName = config?.currencyName ?? DEFAULT_CURRENCY_NAME;
    const currencyIcon = config?.currencyIcon ?? DEFAULT_CURRENCY_ICON;

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
