import { type Result, Ok, Err, DomainError, type DomainEventPublisher, type BalanceChangedEvent } from '@ltdjms/shared';
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
 *
 * @deprecated Not used internally. The balance adjustment methods (adjustBalance,
 * deductBalance, setBalance) each have a dedicated API surface and do not use
 * this enum. Retained only for external consumers that may reference it.
 * Will be removed in a future version.
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
    private readonly eventPublisher?: DomainEventPublisher,
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

    const result = await this.balanceAdjustmentService.tryAdjustBalance(
      Number(guildId),
      Number(userId),
      amount,
      CurrencyTransactionSource.ADMIN_ADJUSTMENT,
      `管理員 ${actorId}：${reason}`,
    );

    if (result.isOk()) {
      this.publishBalanceChangedEvent(guildId, userId, result.getValue().newBalance);
    }

    return result;
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

    const result = await this.balanceAdjustmentService.tryAdjustBalance(
      Number(guildId),
      Number(userId),
      -amount,
      CurrencyTransactionSource.ADMIN_ADJUSTMENT,
      `管理員 ${actorId}：${reason}`,
    );

    if (result.isOk()) {
      this.publishBalanceChangedEvent(guildId, userId, result.getValue().newBalance);
    }

    return result;
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
      return new Err(DomainError.invalidInput('設定金額必須為非負整數'));
    }

    const result = await this.balanceAdjustmentService.tryAdjustBalanceTo(
      Number(guildId),
      Number(userId),
      amount,
      CurrencyTransactionSource.ADMIN_ADJUSTMENT,
      `管理員 ${actorId}：${reason}`,
    );

    if (result.isOk()) {
      this.publishBalanceChangedEvent(guildId, userId, result.getValue().newBalance);
    }

    return result;
  }

  private publishBalanceChangedEvent(
    guildId: string,
    userId: string,
    newBalance: number,
  ): void {
    if (!this.eventPublisher) return;
    const event: BalanceChangedEvent = {
      guildId,
      eventType: 'balance_changed',
      userId: Number(userId),
      newBalance,
    };
    this.eventPublisher.publish(event);
  }

  private validateAdjustmentAmount(
    amount: number,
    _operationName: string,
  ): Result<never, DomainError> | null {
    if (!Number.isFinite(amount) || !Number.isInteger(amount) || amount <= 0) {
      return new Err(DomainError.invalidInput('調整金額必須為正整數'));
    }
    if (amount > Number.MAX_SAFE_INTEGER) {
      return new Err(DomainError.invalidInput('金額超出允許範圍'));
    }
    return null;
  }
}
