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
    getTokens(guildId: number, userId: number): Promise<Result<number, DomainError>>;
    /**
     * Adjusts tokens by the specified amount (positive = add, negative = deduct).
     */
    adjustTokens(guildId: number, userId: number, amount: number, reason: string, actorId: number): Promise<Result<TokenAdjustmentResult, DomainError>>;
    /**
     * Sets tokens to a specific value by adjusting the delta.
     */
    setTokens(guildId: number, userId: number, amount: number, reason: string, actorId: number): Promise<Result<TokenAdjustmentResult, DomainError>>;
    /**
     * Gets a paginated list of token transactions for a member.
     */
    getTokenTransactionPage(guildId: number, userId: number, page?: number, pageSize?: number): Promise<Result<TransactionPage<GameTokenTransaction>, DomainError>>;
    private validateTokenAmount;
}
