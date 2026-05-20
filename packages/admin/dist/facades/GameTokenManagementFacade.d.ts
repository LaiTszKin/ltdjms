import { type Result, DomainError } from '@ltdjms/shared';
import { GameTokenService, GameTokenTransactionService, type TokenAdjustmentResult, type GameTokenTransaction, type TransactionPage } from '@ltdjms/economy';
/**
 * Facade for game token management operations.
 * Wraps GameTokenService and GameTokenTransactionService.
 * Matches Java GameTokenManagementFacade.
 */
export declare class GameTokenManagementFacade {
    private readonly tokenService;
    private readonly tokenTransactionService;
    constructor(tokenService: GameTokenService, tokenTransactionService: GameTokenTransactionService);
    /**
     * Gets the current token balance for a member.
     */
    getTokens(guildId: string, userId: string): Promise<Result<number, DomainError>>;
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
    adjustTokens(guildId: string, userId: string, amount: number, reason: string, actorId: string): Promise<Result<TokenAdjustmentResult, DomainError>>;
    /**
     * Sets tokens to a specific value by adjusting the delta.
     */
    setTokens(guildId: string, userId: string, amount: number, reason: string, actorId: string): Promise<Result<TokenAdjustmentResult, DomainError>>;
    /**
     * Gets a paginated list of token transactions for a member.
     */
    getTokenTransactionPage(guildId: string, userId: string, page?: number, pageSize?: number): Promise<Result<TransactionPage<GameTokenTransaction>, DomainError>>;
    private validateTokenAmount;
}
