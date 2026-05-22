import { type Result, ok, err, DomainError, safeSnowflakeToNumber } from '@ltdjms/shared';
import { type GameTokenService } from '../token/services/game-token-service.js';
import { type GameTokenTransactionService } from '../token/services/game-token-tx-service.js';
import {
  GameTokenTransactionSource,
  type TokenAdjustmentResult,
  type GameTokenTransaction,
  type TransactionPage,
} from '../domain/types.js';

/**
 * Facade for game token management operations.
 * Wraps GameTokenService and GameTokenTransactionService.
 * Matches Java GameTokenManagementFacade.
 *
 * NOTE: Event publishing is handled by GameTokenService internally.
 * This facade does NOT publish GameTokenChangedEvent — doing so would
 * duplicate events (see P1-2 of QA-REPORT).
 */
export class GameTokenManagementFacade {
  constructor(
    private readonly tokenService: GameTokenService,
    private readonly tokenTransactionService: GameTokenTransactionService,
  ) {}

  /**
   * Gets the current token balance for a member.
   */
  async getTokens(guildId: string, userId: string): Promise<Result<number, DomainError>> {
    try {
      const balance = await this.tokenService.getBalance(safeSnowflakeToNumber(guildId), userId);
      return ok(balance);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to get token balance for guildId=${guildId}, userId=${userId}`,
          e instanceof Error ? e : undefined,
        ),
      );
    }
  }

  /**
   * Adjusts tokens by the specified amount (positive = add, negative = deduct).
   * Passes `reason` as the transaction description for audit trail purposes.
   *
   * NOTE: `actorId` is received but currently not persisted — the service layer
   * does not yet accept an actor identifier. Once the service adds actor audit
   * support, pass `actorId` through as well.
   */
  async adjustTokens(
    guildId: string,
    userId: string,
    amount: number,
    reason: string,
    actorId: string,
  ): Promise<Result<TokenAdjustmentResult, DomainError>> {
    const validation = this.validateTokenAmount(amount, false);
    if (validation) return validation;

    return this.tokenService.tryAdjustTokens(
      safeSnowflakeToNumber(guildId),
      userId,
      amount,
      GameTokenTransactionSource.ADMIN_ADJUSTMENT,
      reason,
    );
  }

  /**
   * Sets tokens to a specific value by adjusting the delta.
   */
  async setTokens(
    guildId: string,
    userId: string,
    amount: number,
    reason: string,
    actorId: string,
  ): Promise<Result<TokenAdjustmentResult, DomainError>> {
    if (!Number.isFinite(amount) || amount < 0) {
      return err(DomainError.invalidInput('設定代幣數量必須為非負整數'));
    }

    try {
      const currentBalance = await this.tokenService.getBalance(
        safeSnowflakeToNumber(guildId),
        userId,
      );
      const delta = amount - currentBalance;

      if (delta === 0) {
        // No change needed
        return ok({
          guildId: safeSnowflakeToNumber(guildId),
          userId: String(userId),
          previousTokens: currentBalance,
          newTokens: currentBalance,
          adjustment: 0,
        });
      }

      return this.tokenService.tryAdjustTokens(safeSnowflakeToNumber(guildId), userId, delta);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to set tokens for guildId=${guildId}, userId=${userId}`,
          e instanceof Error ? e : undefined,
        ),
      );
    }
  }

  /**
   * Gets a paginated list of token transactions for a member.
   */
  async getTokenTransactionPage(
    guildId: string,
    userId: string,
    page: number = 1,
    pageSize: number = 10,
  ): Promise<Result<TransactionPage<GameTokenTransaction>, DomainError>> {
    try {
      const txPage = await this.tokenTransactionService.getTransactionPage(
        safeSnowflakeToNumber(guildId),
        userId,
        page,
        pageSize,
      );
      return ok(txPage);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to get token transactions for guildId=${guildId}, userId=${userId}`,
          e instanceof Error ? e : undefined,
        ),
      );
    }
  }

  private validateTokenAmount(
    amount: number,
    _allowZero: boolean,
  ): Result<never, DomainError> | null {
    if (!Number.isFinite(amount)) {
      return err(DomainError.invalidInput('代幣數量無效'));
    }
    if (Math.abs(amount) > Number.MAX_SAFE_INTEGER) {
      return err(DomainError.invalidInput('代幣數量超出允許範圍'));
    }
    return null;
  }
}
