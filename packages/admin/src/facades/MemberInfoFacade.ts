import { type Result, Ok, Err, DomainError } from '@ltdjms/shared';
import {
  BalanceService,
  GameTokenService,
  CurrencyTransactionService,
  GameTokenTransactionService,
  type BalanceView,
  type CurrencyTransaction,
  type GameTokenTransaction,
  type TransactionPage,
} from '@ltdjms/economy';
import {
  RedemptionService,
  type RedemptionResult,
} from '@ltdjms/shop';

/**
 * Summary view combining balance and token info for the user panel.
 */
export interface MemberPanelView {
  readonly guildId: number;
  readonly userId: number;
  readonly balance: number;
  readonly currencyName: string;
  readonly currencyIcon: string;
  readonly tokens: number;
}

/**
 * Facade for member-facing queries.
 * Aggregates BalanceService, GameTokenService, transaction services, and redemption.
 * Matches Java MemberInfoFacade.
 */
export class MemberInfoFacade {
  constructor(
    private readonly balanceService: BalanceService,
    private readonly tokenService: GameTokenService,
    private readonly currencyTxService: CurrencyTransactionService,
    private readonly tokenTxService: GameTokenTransactionService,
    private readonly redemptionService: RedemptionService,
  ) {}

  /**
   * Gets a combined view of the member's balance and token info.
   */
  async getUserPanelView(
    guildId: number,
    userId: number,
  ): Promise<Result<MemberPanelView, DomainError>> {
    try {
      const balanceView = await this.balanceService.getBalance(guildId, userId);
      const tokenBalance = await this.tokenService.getBalance(guildId, userId);

      return new Ok({
        guildId,
        userId,
        balance: balanceView.balance,
        currencyName: balanceView.currencyName,
        currencyIcon: balanceView.currencyIcon,
        tokens: tokenBalance,
      });
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to get user panel view for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Gets a paginated list of currency transactions for a member.
   */
  async getCurrencyTransactionPage(
    guildId: number,
    userId: number,
    page: number = 1,
    pageSize: number = 10,
  ): Promise<Result<TransactionPage<CurrencyTransaction>, DomainError>> {
    try {
      const txPage = await this.currencyTxService.getTransactionPage(
        guildId,
        userId,
        page,
        pageSize,
      );
      return new Ok(txPage);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to get currency transactions for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Gets a paginated list of token transactions for a member.
   */
  async getTokenTransactionPage(
    guildId: number,
    userId: number,
    page: number = 1,
    pageSize: number = 10,
  ): Promise<Result<TransactionPage<GameTokenTransaction>, DomainError>> {
    try {
      const txPage = await this.tokenTxService.getTransactionPage(
        guildId,
        userId,
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

  /**
   * Redeems a redemption code for the user.
   */
  async redeemCode(
    guildId: number,
    userId: number,
    codeStr: string,
  ): Promise<Result<RedemptionResult, DomainError>> {
    return this.redemptionService.redeemCode(codeStr, guildId, userId);
  }
}
