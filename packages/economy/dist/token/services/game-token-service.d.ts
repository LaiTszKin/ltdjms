import { type Result, DomainError, type CacheService, type CacheKeyGenerator, type DomainEventPublisher } from '@ltdjms/shared';
import { TokenAccountRepository } from '../repositories/token-account-repo.js';
import type { GameTokenAccount, TokenAdjustmentResult } from '../../domain/types.js';
/**
 * Service for managing game token accounts with caching.
 * Matches Java GameTokenService behavior.
 */
export declare class GameTokenService {
    private readonly accountRepository;
    private readonly eventPublisher;
    private readonly cacheService;
    private readonly cacheKeyGenerator;
    private static readonly TOKEN_TTL_SECONDS;
    constructor(accountRepository: TokenAccountRepository, eventPublisher: DomainEventPublisher, cacheService: CacheService, cacheKeyGenerator: CacheKeyGenerator);
    /**
     * Gets the current token balance for a member.
     * Uses cache (TTL 300s) - cache miss falls through to DB (no auto-create).
     */
    getBalance(guildId: number, userId: number): Promise<number>;
    /**
     * Adjusts tokens with Result-based error handling.
     */
    tryAdjustTokens(guildId: number, userId: number, amount: number): Promise<Result<TokenAdjustmentResult, DomainError>>;
    /**
     * Checks if a member has enough tokens.
     */
    hasEnoughTokens(guildId: number, userId: number, requiredTokens: number): Promise<boolean>;
    /**
     * Deducts tokens using Result-based error handling.
     * @param tokens - positive number of tokens to deduct
     */
    tryDeductTokens(guildId: number, userId: number, tokens: number): Promise<Result<GameTokenAccount, DomainError>>;
    /**
     * Adjusts tokens (deducts if negative, adds if positive).
     * Deduct path that throws on insufficient - use tryAdjustTokens for Result-based.
     */
    deductTokens(guildId: number, userId: number, tokens: number): Promise<GameTokenAccount>;
    /**
     * Adjusts tokens (throws on error).
     */
    adjustTokens(guildId: number, userId: number, amount: number): Promise<TokenAdjustmentResult>;
}
