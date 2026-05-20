import { type Result, Ok, Err, DomainError, type DomainEventPublisher, type GameTokenChangedEvent } from '@ltdjms/shared';
import {
  GameTokenService,
  GameTokenTransactionService,
  GameTokenTransactionSource,
  type TokenAdjustmentResult,
  type GameTokenTransaction,
  type TransactionPage,
} from '@ltdjms/economy';

/**
 * Facade for game token management operations.
 * Wraps GameTokenService and GameTokenTransactionService.
 * Publishes GameTokenChangedEvent on successful adjustments.
 * Matches Java GameTokenManagementFacade.
 */
export class GameTokenManagementFacade {
  constructor(
    private readonly tokenService: GameTokenService,
    private readonly tokenTransactionService: GameTokenTransactionService,
    private readonly eventPublisher: DomainEventPublisher,
  ) {}

  /**
   * Gets the current token balance for a member.
   */
  async getTokens(guildId: string, userId: string): Promise<Result<number, DomainError>> {
    try {
      const balance = await this.tokenService.getBalance(Number(guildId), Number(userId));
      return new Ok(balance);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to get token balance for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Adjusts tokens by the specified amount (positive = add, negative = deduct).
   *
   * NOTE: `reason` and `actorId` are received but currently discarded because
   * GameTokenService.tryAdjustTokens(guildId, userId, amount) does not yet accept
   * audit metadata. Once the service layer adds audit trail support, pass these
   * through to the service call.
   *
   * TODO(P1-34): Pass reason and actorId to service layer when tryAdjustTokens
   * signature accepts audit metadata (e.g., reason, actorId).
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

    const result = await this.tokenService.tryAdjustTokens(Number(guildId), Number(userId), amount);
    if (result.isOk()) {
      this.publishTokenChangedEvent(guildId, userId, result.getValue().newTokens);
    }
    return result;
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
      return new Err(DomainError.invalidInput('設定代幣數量必須為非負整數'));
    }

    try {
      const currentBalance = await this.tokenService.getBalance(Number(guildId), Number(userId));
      const delta = amount - currentBalance;

      if (delta === 0) {
        // No change needed
        return new Ok({
          guildId: Number(guildId),
          userId: Number(userId),
          previousTokens: currentBalance,
          newTokens: currentBalance,
          adjustment: 0,
        });
      }

      const result = await this.tokenService.tryAdjustTokens(Number(guildId), Number(userId), delta);
      if (result.isOk()) {
        this.publishTokenChangedEvent(guildId, userId, result.getValue().newTokens);
      }
      return result;
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to set tokens for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
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
        Number(guildId),
        Number(userId),
        page,
        pageSize,
      );
      return new Ok(txPage);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to get token transactions for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  private publishTokenChangedEvent(guildId: string, userId: string, newTokens: number): void {
    const event: GameTokenChangedEvent = {
      guildId: String(Number(guildId)),
      eventType: 'game_token_changed',
      userId: Number(userId),
      newTokens,
    };
    this.eventPublisher.publish(event);
  }

  private validateTokenAmount(
    amount: number,
    _allowZero: boolean,
  ): Result<never, DomainError> | null {
    if (!Number.isFinite(amount)) {
      return new Err(DomainError.invalidInput('代幣數量無效'));
    }
    if (Math.abs(amount) > Number.MAX_SAFE_INTEGER) {
      return new Err(DomainError.invalidInput('代幣數量超出允許範圍'));
    }
    return null;
  }
}
