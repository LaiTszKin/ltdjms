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
import type { BalanceAdjustmentResult, MemberCurrencyAccount } from '../../domain/types.js';
import { CurrencyTransactionSource, DEFAULT_CURRENCY_NAME, DEFAULT_CURRENCY_ICON, BALANCE_CACHE_TTL } from '../../domain/types.js';

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
    if (amount === 0 || !Number.isFinite(amount)) {
      return new Err(
        DomainError.invalidInput(`Invalid adjustment amount: ${amount}`),
      );
    }

    // Overflow check using safe integer boundaries
    if (amount > Number.MAX_SAFE_INTEGER || amount < -Number.MAX_SAFE_INTEGER) {
      return new Err(
        DomainError.invalidInput(`Amount exceeds maximum: |${amount}| > ${Number.MAX_SAFE_INTEGER}`),
      );
    }

    try {
      const current = await this.accountRepository.findOrCreate(guildId, userId);
      const previousBalance = current.balance;

      // Check for overflow: previousBalance + amount must not exceed MAX_SAFE_INTEGER
      if (amount > 0 && previousBalance > Number.MAX_SAFE_INTEGER - amount) {
        return new Err(
          DomainError.invalidInput(
            `Balance overflow: ${previousBalance} + ${amount} exceeds maximum safe integer`,
          ),
        );
      }
      // Check for underflow: previousBalance + amount must not go below 0
      if (amount < 0 && previousBalance < -amount) {
        return new Err(
          DomainError.invalidInput(
            `Insufficient balance: ${previousBalance} cannot be reduced by ${Math.abs(amount)}`,
          ),
        );
      }

      const adjustResult = await this.accountRepository.tryAdjustBalance(
        guildId,
        userId,
        amount,
      );

      if (adjustResult.isErr()) {
        return new Err(adjustResult.getError());
      }

      const updated = adjustResult.getValue();

      // Update cache
      const cacheKey = this.cacheKeyGenerator.balanceKey(guildId, userId);
      await this.cacheService.put(cacheKey, updated.balance, BalanceAdjustmentService.BALANCE_TTL_SECONDS);

      // Publish event
      this.eventPublisher.publish({
        guildId,
        userId,
        newBalance: updated.balance,
      } as BalanceChangedEvent);

      // Record transaction
      await this.transactionService.recordTransaction(
        guildId,
        userId,
        amount,
        updated.balance,
        source,
        description,
      );

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
   * Adjusts a member's balance to a specific target value.
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

    try {
      const current = await this.accountRepository.findOrCreate(guildId, userId);
      const previousBalance = current.balance;

      const delta = targetBalance - previousBalance;

      if (!Number.isFinite(delta)) {
        return new Err(
          DomainError.invalidInput(
            `Adjustment would cause overflow: target=${targetBalance}, current=${previousBalance}`,
          ),
        );
      }

      const adjustResult = await this.accountRepository.tryAdjustBalance(
        guildId,
        userId,
        delta,
      );

      if (adjustResult.isErr()) {
        return new Err(adjustResult.getError());
      }

      const updated = adjustResult.getValue();

      // Update cache
      const cacheKey = this.cacheKeyGenerator.balanceKey(guildId, userId);
      await this.cacheService.put(cacheKey, updated.balance, BalanceAdjustmentService.BALANCE_TTL_SECONDS);

      // Publish event
      this.eventPublisher.publish({
        guildId,
        userId,
        newBalance: updated.balance,
      } as BalanceChangedEvent);

      // Record transaction
      await this.transactionService.recordTransaction(
        guildId,
        userId,
        delta,
        updated.balance,
        source,
        description,
      );

      const config = await this.configRepository.findByGuildId(guildId);

      return new Ok({
        guildId,
        userId,
        previousBalance,
        newBalance: updated.balance,
        adjustment: delta,
        currencyName: config?.currencyName ?? DEFAULT_CURRENCY_NAME,
        currencyIcon: config?.currencyIcon ?? DEFAULT_CURRENCY_ICON,
      });
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to adjust balance to target for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }
}
