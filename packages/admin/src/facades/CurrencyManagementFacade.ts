import { type Result, Ok, Err, DomainError } from '@ltdjms/shared';
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
 * Adjustment mode for balance operations.
 */
export enum BalanceAdjustMode {
  ADD = 'ADD',
  DEDUCT = 'DEDUCT',
  SET = 'SET',
}

/**
 * Facade that aggregates currency management operations.
 * Wraps BalanceService, BalanceAdjustmentService, and CurrencyConfigService.
 * Matches Java CurrencyManagementFacade.
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
  async getConfig(guildId: number): Promise<Result<GuildCurrencyConfig, DomainError>> {
    return this.currencyConfigService.tryGetConfig(guildId);
  }

  /**
   * Gets the balance view for a member in a guild.
   */
  async getBalance(guildId: number, userId: number): Promise<Result<BalanceView, DomainError>> {
    return this.balanceService.tryGetBalance(guildId, userId);
  }

  /**
   * Adjusts a member's balance by adding the specified positive amount.
   */
  async adjustBalance(
    guildId: number,
    userId: number,
    amount: number,
    reason: string,
    actorId: number,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    const validation = this.validateAdjustmentAmount(amount, '增加');
    if (validation) return validation;

    return this.balanceAdjustmentService.tryAdjustBalance(
      guildId,
      userId,
      amount,
      CurrencyTransactionSource.ADMIN_ADJUSTMENT,
      `管理員 ${actorId}：${reason}`,
    );
  }

  /**
   * Deducts from a member's balance.
   */
  async deductBalance(
    guildId: number,
    userId: number,
    amount: number,
    reason: string,
    actorId: number,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    const validation = this.validateAdjustmentAmount(amount, '扣除');
    if (validation) return validation;

    return this.balanceAdjustmentService.tryAdjustBalance(
      guildId,
      userId,
      -amount,
      CurrencyTransactionSource.ADMIN_ADJUSTMENT,
      `管理員 ${actorId}：${reason}`,
    );
  }

  /**
   * Sets a member's balance to a specific target value.
   */
  async setBalance(
    guildId: number,
    userId: number,
    amount: number,
    reason: string,
    actorId: number,
  ): Promise<Result<BalanceAdjustmentResult, DomainError>> {
    if (!Number.isFinite(amount) || amount < 0) {
      return new Err(DomainError.invalidInput('設定金額必須為非負整數'));
    }

    return this.balanceAdjustmentService.tryAdjustBalanceTo(
      guildId,
      userId,
      amount,
      CurrencyTransactionSource.ADMIN_ADJUSTMENT,
      `管理員 ${actorId}：${reason}`,
    );
  }

  private validateAdjustmentAmount(
    amount: number,
    _operationName: string,
  ): Result<never, DomainError> | null {
    if (!Number.isFinite(amount) || amount <= 0) {
      return new Err(DomainError.invalidInput('調整金額必須為正整數'));
    }
    if (amount > Number.MAX_SAFE_INTEGER) {
      return new Err(DomainError.invalidInput('金額超出允許範圍'));
    }
    return null;
  }
}
