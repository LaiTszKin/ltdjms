import { type Result, ok, err, DomainError } from '@ltdjms/shared';
import {
  BalanceService,
  BalanceAdjustmentService,
  CurrencyConfigService,
  CurrencyTransactionSource,
  type GuildCurrencyConfig,
  type BalanceView,
  type BalanceAdjustmentResult,
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
    return this.balanceService.tryGetBalance(Number(guildId), Number(userId));
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
      Number(userId),
      amount,
      CurrencyTransactionSource.ADMIN_ADJUSTMENT,
      `管理員 ${actorId}：${reason}`,
    );
  }

  /**
   * Deducts from a member's balance.
   *
   * NOTE: This is a convenience wrapper around adjustBalance that negates the
   * amount. Not explicitly listed in the original spec (R13.1), but provided
   * as a symmetric counterpart to adjustBalance for clarity in the admin panel.
   */
  async deductBalance(
    guildId: string,
    userId: string,
    amount: number,
    reason: string,
    actorId: string,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    const validation = this.validateAdjustmentAmount(amount, '扣除');
    if (validation) return validation;

    return this.balanceAdjustmentService.tryAdjustBalance(
      Number(guildId),
      Number(userId),
      -amount,
      CurrencyTransactionSource.ADMIN_ADJUSTMENT,
      `管理員 ${actorId}：${reason}`,
    );
  }

  /**
   * Sets a member's balance to a specific target value.
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

    return this.balanceAdjustmentService.tryAdjustBalanceTo(
      Number(guildId),
      Number(userId),
      amount,
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
