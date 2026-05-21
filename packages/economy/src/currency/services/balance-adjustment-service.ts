import {
  type Result,
  Ok,
  Err,
  DomainError,
  type CacheService,
  type CacheKeyGenerator,
  type DomainEventPublisher,
} from '@ltdjms/shared';
import type { BalanceChangedEvent } from '../../events/index.js';
import { CurrencyAccountRepository } from '../repositories/currency-account-repo.js';
import { CurrencyTransactionService } from './currency-tx-service.js';
import { BalanceService } from './balance-service.js';
import type { BalanceAdjustmentResult } from '../../domain/types.js';
import { CurrencyTransactionSource, BALANCE_CACHE_TTL, isValidAdjustmentAmount, MAX_ADJUSTMENT_AMOUNT } from '../../domain/types.js';

/**
 * Service for adjusting member currency balances with validation.
 * Matches Java BalanceAdjustmentService behavior.
 */
export class BalanceAdjustmentService {
  private static readonly BALANCE_TTL_SECONDS = BALANCE_CACHE_TTL;

  constructor(
    private readonly accountRepository: CurrencyAccountRepository,
    private readonly balanceService: BalanceService,
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
    userId: string,
    amount: number,
    previousBalance: number,
    source: CurrencyTransactionSource,
    description: string | null,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    // Overflow check: JavaScript Number.MAX_SAFE_INTEGER (2^53-1) is used instead of
    // Java Long.MAX_VALUE (2^63-1) because JS lacks 64-bit integer overflow semantics.
    // This is a platform adaptation — the effective range is ~9e15 vs ~9e18 in Java.
    // If >MAX_SAFE_INTEGER precision is needed, migrate to BigInt. (P1-3)
    if (amount > 0 && previousBalance > Number.MAX_SAFE_INTEGER - amount) {
      return new Err(
        DomainError.invalidInput(
          `Balance overflow: ${previousBalance} + ${amount} exceeds maximum safe integer`,
        ),
      );
    }

    // Check for underflow: previousBalance + amount must not go below MIN_SAFE_INTEGER (P1-1)
    if (amount < 0 && previousBalance < Number.MIN_SAFE_INTEGER - amount) {
      return new Err(
        DomainError.invalidInput(
          `Balance underflow: ${previousBalance} + ${amount} below minimum safe integer`,
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

      // Update cache and fetch config in parallel (P3-5)
      const cacheKey = this.cacheKeyGenerator.balanceKey(String(guildId), String(userId));
      const [cachedConfig] = await Promise.all([
        this.balanceService.getCachedConfig(guildId),
        this.cacheService.put(cacheKey, updated.balance, BalanceAdjustmentService.BALANCE_TTL_SECONDS),
      ]);

      return new Ok({
        guildId,
        userId,
        previousBalance,
        newBalance: updated.balance,
        adjustment: amount,
        currencyName: cachedConfig.currencyName,
        currencyIcon: cachedConfig.currencyIcon,
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
   * Rolls back all applied chunks with a single compensating adjustment.
   * Used by tryBatchAdjust when a chunk fails mid-batch.
   */
  private async rollbackAppliedChunks(
    guildId: number,
    userId: string,
    appliedChunks: number[],
  ): Promise<void> {
    const totalApplied = appliedChunks.reduce((sum, c) => sum + c, 0);
    if (totalApplied !== 0) {
      const rollbackResult = await this.accountRepository.tryAdjustBalance(guildId, userId, -totalApplied);
      if (rollbackResult.isErr()) {
        console.error('[BalanceAdjustmentService] Rollback failed after batch error:', rollbackResult.getError());
      }
    }
  }

  /**
   * Adjusts a member's balance by the specified amount with Result-based error handling.
   * Validates overflow (via safe integer check), applies adjustment, records transaction,
   * publishes event, and updates cache.
   */
  async tryAdjustBalance(
    guildId: number,
    userId: string,
    amount: number,
    source: CurrencyTransactionSource = CurrencyTransactionSource.ADMIN_ADJUSTMENT,
    description: string | null = null,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    if (!Number.isFinite(amount)) {
      return new Err(
        DomainError.invalidInput(`Invalid adjustment amount: ${amount}`),
      );
    }

    if (amount === 0) {
      return new Err(
        DomainError.invalidInput('調整金額不可為零'),
      );
    }

    // Overflow check using safe integer boundaries (spec R1.4)
    if (!isValidAdjustmentAmount(amount)) {
      return new Err(
        DomainError.invalidInput(`Amount exceeds maximum: |${amount}| > ${MAX_ADJUSTMENT_AMOUNT}`),
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
   * Adjusts a member's balance — public alias for tryAdjustBalance.
   * Exists to align with spec naming conventions while keeping tryAdjustBalance
   * as the canonical implementation. (P1-24)
   */
  async adjustBalance(
    guildId: number,
    userId: string,
    amount: number,
    source: CurrencyTransactionSource = CurrencyTransactionSource.ADMIN_ADJUSTMENT,
    description: string | null = null,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    return this.tryAdjustBalance(guildId, userId, amount, source, description);
  }

  /**
   * Batch-adjusts a member's balance by the total amount, splitting into chunks
   * if maxChunkSize is specified (e.g. for reward amounts exceeding a per-operation limit).
   * Records a single transaction, publishes one event, and updates cache once.
   * Returns the aggregated result.
   */
  async tryBatchAdjust(
    guildId: number,
    userId: string,
    totalAmount: number,
    source: CurrencyTransactionSource = CurrencyTransactionSource.ADMIN_ADJUSTMENT,
    description: string | null = null,
    maxChunkSize?: number,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    if (!Number.isFinite(totalAmount)) {
      return new Err(
        DomainError.invalidInput(`Invalid total adjustment amount: ${totalAmount}`),
      );
    }

    if (totalAmount === 0) {
      return new Err(
        DomainError.invalidInput('調整金額不可為零'),
      );
    }

    try {
      const current = await this.accountRepository.findOrCreate(guildId, userId);
      const previousBalance = current.balance;
      let newBalance = previousBalance;

      // Write a preliminary transaction record for audit trail.
      // If a crash occurs mid-batch, this record documents the in-progress
      // adjustment and the balance at which processing started. (P1-6)
      await this.transactionService.recordTransaction(
        guildId,
        userId,
        0,
        previousBalance,
        source,
        `BATCH_PROCESSING:${totalAmount} from ${source}${description ? ` — ${description}` : ''}`,
      );

      // Apply in chunks with compensating rollback on failure (P2-1)
      const appliedChunks: number[] = [];
      let remaining = totalAmount;
      try {
        while (remaining !== 0) {
          const chunk = maxChunkSize !== undefined
            ? Math.min(Math.abs(remaining), maxChunkSize) * Math.sign(remaining)
            : remaining;

          const result = await this.accountRepository.tryAdjustBalance(guildId, userId, chunk);
          if (result.isErr()) {
            await this.rollbackAppliedChunks(guildId, userId, appliedChunks);
            return new Err(result.getError());
          }
          appliedChunks.push(chunk);
          newBalance = result.getValue().balance;
          remaining -= chunk;
        }
      } catch (txErr) {
        await this.rollbackAppliedChunks(guildId, userId, appliedChunks);
        throw txErr;
      }

      const actualAdjustment = newBalance - previousBalance;

      // Record transaction (single record for the total adjustment)
      await this.transactionService.recordTransaction(
        guildId,
        userId,
        actualAdjustment,
        newBalance,
        source,
        description,
      );

      // Publish event
      const event: BalanceChangedEvent = {
        guildId: String(guildId),
        userId,
        eventType: 'balance_changed',
        newBalance,
      };
      this.eventPublisher.publish(event);

      // Update cache and fetch config in parallel (P3-5)
      const cacheKey = this.cacheKeyGenerator.balanceKey(String(guildId), String(userId));
      const [cachedConfig] = await Promise.all([
        this.balanceService.getCachedConfig(guildId),
        this.cacheService.put(cacheKey, newBalance, BalanceAdjustmentService.BALANCE_TTL_SECONDS),
      ]);

      return new Ok({
        guildId,
        userId,
        previousBalance,
        newBalance,
        adjustment: actualAdjustment,
        currencyName: cachedConfig.currencyName,
        currencyIcon: cachedConfig.currencyIcon,
      });
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to batch adjust balance for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }
}
