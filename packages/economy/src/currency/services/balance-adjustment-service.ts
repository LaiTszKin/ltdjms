import {
  type Result,
  Ok,
  Err,
  DomainError,
  type CacheService,
  type CacheKeyGenerator,
  type DomainEventPublisher,
  type BalanceChangedEvent,
} from '@ltdjms/shared';
import { CurrencyAccountRepository } from '../repositories/currency-account-repo.js';
import { CurrencyConfigRepository } from '../repositories/currency-config-repo.js';
import { CurrencyTransactionService } from './currency-tx-service.js';
import type { BalanceAdjustmentResult } from '../../domain/types.js';
import { CurrencyTransactionSource, DEFAULT_CURRENCY_NAME, DEFAULT_CURRENCY_ICON, BALANCE_CACHE_TTL, isValidAdjustmentAmount } from '../../domain/types.js';

/**
 * Service for adjusting member currency balances with validation.
 * Matches Java BalanceAdjustmentService behavior.
 */
export class BalanceAdjustmentService {
  private static readonly BALANCE_TTL_SECONDS = BALANCE_CACHE_TTL;

  constructor(
    private readonly accountRepository: CurrencyAccountRepository,
    private readonly configRepository: CurrencyConfigRepository,
    private readonly transactionService: CurrencyTransactionService,
    private readonly eventPublisher: DomainEventPublisher,
    private readonly cacheService: CacheService,
    private readonly cacheKeyGenerator: CacheKeyGenerator,
  ) {}

  /**
   * Processes a balance adjustment using a pre-fetched current balance.
   * Handles overflow check, repository adjustment, transaction recording,
   * event publishing, and cache update.
   *
   * This is extracted to avoid redundant findOrCreate queries when the caller
   * already has the current balance (P2-5).
   */
  private async processAdjustment(
    guildId: number,
    userId: number,
    amount: number,
    previousBalance: number,
    source: CurrencyTransactionSource,
    description: string | null,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    // Check for overflow: previousBalance + amount must not exceed MAX_SAFE_INTEGER
    if (amount > 0 && previousBalance > Number.MAX_SAFE_INTEGER - amount) {
      return new Err(
        DomainError.invalidInput(
          `Balance overflow: ${previousBalance} + ${amount} exceeds maximum safe integer`,
        ),
      );
    }

    try {
      const adjustResult = await this.accountRepository.tryAdjustBalance(
        guildId,
        userId,
        amount,
      );

      if (adjustResult.isErr()) {
        return new Err(adjustResult.getError());
      }

      const updated = adjustResult.getValue();

      // Record transaction first (P1-11) — ensures transaction is persisted
      // before the event is published or the cache is updated, preventing
      // a scenario where a crash after the event/cache update loses the record.
      await this.transactionService.recordTransaction(
        guildId,
        userId,
        amount,
        updated.balance,
        source,
        description,
      );

      // Publish event
      const event: BalanceChangedEvent = {
        guildId: String(guildId),
        userId,
        eventType: 'balance_changed',
        newBalance: updated.balance,
      };
      this.eventPublisher.publish(event);

      // Update cache
      const cacheKey = this.cacheKeyGenerator.balanceKey(String(guildId), String(userId));
      await this.cacheService.put(cacheKey, updated.balance, BalanceAdjustmentService.BALANCE_TTL_SECONDS);

      const config = await this.configRepository.findByGuildId(guildId);

      return new Ok({
        guildId,
        userId,
        previousBalance,
        newBalance: updated.balance,
        adjustment: amount,
        currencyName: config?.currencyName ?? DEFAULT_CURRENCY_NAME,
        currencyIcon: config?.currencyIcon ?? DEFAULT_CURRENCY_ICON,
      });
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to adjust balance for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Adjusts a member's balance by the specified amount with Result-based error handling.
   * Validates overflow (via safe integer check), applies adjustment, records transaction,
   * publishes event, and updates cache.
   */
  async tryAdjustBalance(
    guildId: number,
    userId: number,
    amount: number,
    source: CurrencyTransactionSource = CurrencyTransactionSource.ADMIN_ADJUSTMENT,
    description: string | null = null,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    if (!Number.isFinite(amount)) {
      return new Err(
        DomainError.invalidInput(`Invalid adjustment amount: ${amount}`),
      );
    }

    // Overflow check using safe integer boundaries (spec R1.4)
    if (!isValidAdjustmentAmount(amount)) {
      return new Err(
        DomainError.invalidInput(`Amount exceeds maximum: |${amount}| > ${Number.MAX_SAFE_INTEGER}`),
      );
    }

    try {
      const current = await this.accountRepository.findOrCreate(guildId, userId);
      return await this.processAdjustment(guildId, userId, amount, current.balance, source, description);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to adjust balance for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Adjusts a member's balance to a specific target value.
   * Computes the delta from the current balance and delegates to processAdjustment,
   * avoiding a redundant findOrCreate query (P2-5).
   */
  async tryAdjustBalanceTo(
    guildId: number,
    userId: number,
    targetBalance: number,
    source: CurrencyTransactionSource = CurrencyTransactionSource.ADMIN_ADJUSTMENT,
    description: string | null = null,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    if (targetBalance < 0) {
      return new Err(
        DomainError.invalidInput(`Target balance cannot be negative: ${targetBalance}`),
      );
    }

    const current = await this.accountRepository.findOrCreate(guildId, userId);
    const delta = targetBalance - current.balance;

    // Validate delta the same way tryAdjustBalance does (P1-8)
    if (!Number.isFinite(delta)) {
      return new Err(
        DomainError.invalidInput(
          `Adjustment would cause overflow: target=${targetBalance}, current=${current.balance}`,
        ),
      );
    }
    if (!isValidAdjustmentAmount(delta)) {
      return new Err(
        DomainError.invalidInput(`Delta exceeds maximum: |${delta}| > ${Number.MAX_SAFE_INTEGER}`),
      );
    }

    // Delegate to processAdjustment which handles adjustment, transaction recording,
    // event publishing, and cache update — without a redundant findOrCreate query (P2-5).
    return this.processAdjustment(guildId, userId, delta, current.balance, source, description);
  }
}
