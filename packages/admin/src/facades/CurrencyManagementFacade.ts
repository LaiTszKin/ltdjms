import { type Result, ok, err, DomainError } from '@ltdjms/shared';
import { CurrencyTransactionSource } from '@ltdjms/economy';
import type {
  BalanceService,
  BalanceAdjustmentService,
  CurrencyConfigService,
  GuildCurrencyConfig,
  BalanceView,
  BalanceAdjustmentResult,
} from '@ltdjms/economy';

/**
 * Facade that aggregates currency management operations.
 * Wraps BalanceService, BalanceAdjustmentService, and CurrencyConfigService.
 * Matches Java CurrencyManagementFacade.
 *
 * NOTE: Event publishing is handled by BalanceAdjustmentService internally.
 * This facade does NOT publish BalanceChangedEvent — doing so would
 * duplicate events (see P1-2 of QA-REPORT).
 */
export class CurrencyManagementFacade {
  constructor(
    private readonly balanceService: BalanceService,
    private readonly balanceAdjustmentService: BalanceAdjustmentService,
    private readonly currencyConfigService: CurrencyConfigService,
  ) {}

  /**
   * Gets the currency configuration for a guild.
   */
  async getConfig(guildId: string): Promise<Result<GuildCurrencyConfig, DomainError>> {
    return this.currencyConfigService.tryGetConfig(Number(guildId));
  }

  /**
   * Gets the balance view for a member in a guild.
   */
  async getBalance(guildId: string, userId: string): Promise<Result<BalanceView, DomainError>> {
    return this.balanceService.getBalance(Number(guildId), userId);
  }

  /**
   * Adjusts a member's balance by adding the specified positive amount.
   */
  async adjustBalance(
    guildId: string,
    userId: string,
    amount: number,
    reason: string,
    actorId: string,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    const validation = this.validateAdjustmentAmount(amount, '增加');
    if (validation) return validation;

    return this.balanceAdjustmentService.tryAdjustBalance(
      Number(guildId),
      userId,
      amount,
      CurrencyTransactionSource.ADMIN_ADJUSTMENT,
      `管理員 ${actorId}：${reason}`,
    );
  }

  /**
   * Sets a member's balance to a specific target value.
   * Computes the delta from current balance and applies it via tryAdjustBalance.
   */
  async setBalance(
    guildId: string,
    userId: string,
    amount: number,
    reason: string,
    actorId: string,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    if (!Number.isFinite(amount) || amount < 0) {
      return err(DomainError.invalidInput('設定金額必須為非負整數'));
    }

    const currentResult = await this.balanceService.getBalance(Number(guildId), userId);
    if (currentResult.isErr()) {
      return err(currentResult.getError());
    }

    const currentBalance = currentResult.getValue().balance;
    const delta = amount - currentBalance;

    return this.balanceAdjustmentService.tryAdjustBalance(
      Number(guildId),
      userId,
      delta,
      CurrencyTransactionSource.ADMIN_ADJUSTMENT,
      `管理員 ${actorId}：${reason}`,
    );
  }

  private validateAdjustmentAmount(
    amount: number,
    _operationName: string,
  ): Result<never, DomainError> | null {
    if (!Number.isFinite(amount) || !Number.isInteger(amount) || amount <= 0) {
      return err(DomainError.invalidInput('調整金額必須為正整數'));
    }
    if (amount > Number.MAX_SAFE_INTEGER) {
      return err(DomainError.invalidInput('金額超出允許範圍'));
    }
    return null;
  }
}
