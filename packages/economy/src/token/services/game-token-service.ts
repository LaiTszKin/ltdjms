import {
  type Result,
  Ok,
  Err,
  DomainError,
  type CacheService,
  type CacheKeyGenerator,
  type DomainEventPublisher,
} from '@ltdjms/shared';
import type { GameTokenChangedEvent } from '@ltdjms/economy';
import { TokenAccountRepository } from '../repositories/token-account-repo.js';
import { GameTokenTransactionService } from './game-token-tx-service.js';
import type { GameTokenAccount, TokenAdjustmentResult } from '../../domain/types.js';
import { TOKEN_CACHE_TTL, GameTokenTransactionSource } from '../../domain/types.js';

/**
 * Service for managing game token accounts with caching.
 * Matches Java GameTokenService behavior.
 */
export class GameTokenService {
  private static readonly TOKEN_TTL_SECONDS = TOKEN_CACHE_TTL;

  /** Per-key in-flight promises to prevent cache stampede on token reads. */
  private readonly pendingFetches = new Map<string, Promise<number>>();

  /**
   * Updates the cache and publishes a GameTokenChangedEvent after a token adjustment.
   * Extracted to eliminate duplicate cache/event logic across four methods (P1-12).
   */
  private async updateCacheAndPublishEvent(
    guildId: number,
    userId: number,
    newTokens: number,
  ): Promise<void> {
    const cacheKey = this.cacheKeyGenerator.gameTokenKey(String(guildId), String(userId));
    await this.cacheService.put(
      cacheKey,
      newTokens,
      GameTokenService.TOKEN_TTL_SECONDS,
    );

    const event: GameTokenChangedEvent = {
      guildId: String(guildId),
      userId,
      eventType: 'game_token_changed',
      newTokens,
    };
    this.eventPublisher.publish(event);
  }

  constructor(
    private readonly accountRepository: TokenAccountRepository,
    private readonly eventPublisher: DomainEventPublisher,
    private readonly cacheService: CacheService,
    private readonly cacheKeyGenerator: CacheKeyGenerator,
    private readonly transactionService: GameTokenTransactionService,
  ) {}

  /**
   * Gets the current token balance for a member.
   * Uses cache (TTL 300s) - cache miss falls through to DB query.
   * Auto-creates the token account if it does not exist (P3-16).
   */
  async getBalance(guildId: number, userId: number): Promise<number> {
    const cacheKey = this.cacheKeyGenerator.gameTokenKey(String(guildId), String(userId));
    const cachedBalance = await this.cacheService.get<number>(cacheKey);

    if (cachedBalance !== null) {
      return cachedBalance;
    }

    // Prevent cache stampede: coalesce concurrent requests for the same key
    const pending = this.pendingFetches.get(cacheKey);
    if (pending) {
      return await pending;
    }

    const fetchPromise = this.accountRepository.findOrCreate(guildId, userId)
      .then(account => account.tokens);
    this.pendingFetches.set(cacheKey, fetchPromise);
    try {
      const balance = await fetchPromise;
      await this.cacheService.put(cacheKey, balance, GameTokenService.TOKEN_TTL_SECONDS);
      return balance;
    } finally {
      this.pendingFetches.delete(cacheKey);
    }
  }

  /**
   * Adjusts tokens with Result-based error handling.
   * Records a transaction after the adjustment, cache update, and event publishing (P1-5).
   *
   * @param source - transaction source for recording (default ADMIN_ADJUSTMENT)
   * @param description - optional description for the transaction record
   */
  async tryAdjustTokens(
    guildId: number,
    userId: number,
    amount: number,
    source: GameTokenTransactionSource = GameTokenTransactionSource.ADMIN_ADJUSTMENT,
    description: string | null = null,
  ): Promise<Result<TokenAdjustmentResult, DomainError>> {
    if (!Number.isFinite(amount)) {
      return new Err(
        DomainError.invalidInput(`Invalid adjustment amount: ${amount}`),
      );
    }

    if (amount === 0) {
      return new Err(
        DomainError.invalidInput('調整金額不可為零'),
      );
    }

    try {
      const current = await this.accountRepository.findOrCreate(guildId, userId);
      const previousTokens = current.tokens;

      const adjustResult = await this.accountRepository.tryAdjustTokens(
        guildId,
        userId,
        amount,
      );

      if (adjustResult.isErr()) {
        return new Err(adjustResult.getError());
      }

      const updated = adjustResult.getValue();

      // Update cache and publish event
      await this.updateCacheAndPublishEvent(guildId, userId, updated.tokens);

      // Record transaction atomically with the token adjustment (P1-5)
      await this.transactionService.recordTransaction(
        guildId,
        userId,
        amount,
        updated.tokens,
        source,
        description,
      );

      return new Ok({
        guildId,
        userId,
        previousTokens,
        newTokens: updated.tokens,
        adjustment: amount,
      });
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to adjust tokens for guildId=${guildId}, userId=${userId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Checks if a member has enough tokens.
   */
  async hasEnoughTokens(
    guildId: number,
    userId: number,
    requiredTokens: number,
  ): Promise<boolean> {
    const balance = await this.getBalance(guildId, userId);
    return balance >= requiredTokens;
  }

  /**
   * Deducts tokens using Result-based error handling, then immediately records
   * the transaction so that deduction and recording are within the same logical
   * scope (P1-10). This prevents the process from crashing between the two
   * operations.
   *
   * Delegates to {@link tryAdjustTokens} for the actual adjustment, cache
   * update, and event publishing to avoid duplicating that logic (P2-16).
   *
   * @param tokens - positive number of tokens to deduct
   * @param source - transaction source for recording
   */
  async tryDeductTokens(
    guildId: number,
    userId: number,
    tokens: number,
    source: GameTokenTransactionSource = GameTokenTransactionSource.GAME_PLAY,
  ): Promise<Result<TokenAdjustmentResult, DomainError>> {
    if (tokens <= 0) {
      return new Err(
        DomainError.invalidInput(`Tokens to deduct must be positive: ${tokens}`),
      );
    }

    // Delegate to tryAdjustTokens which handles findOrCreate, adjustment,
    // cache update, event publishing, and transaction recording (P1-5, P2-16)
    const result = await this.tryAdjustTokens(guildId, userId, -tokens, source, null);

    return result;
  }

  /**
   * Adjusts tokens (deducts if negative, adds if positive).
   * Deduct path that throws on insufficient - use tryAdjustTokens for Result-based.
   * Records a transaction after successful deduction (P1-9).
   */
  async deductTokens(
    guildId: number,
    userId: number,
    tokens: number,
  ): Promise<GameTokenAccount> {
    if (tokens <= 0) {
      throw new Error(`Tokens to deduct must be positive: ${tokens}`);
    }

    const updated = await this.accountRepository.adjustTokens(
      guildId,
      userId,
      -tokens,
    );

    // Update cache and publish event
    await this.updateCacheAndPublishEvent(guildId, userId, updated.tokens);

    // Record transaction after successful deduction (P1-9), matching
    // the pattern used by tryDeductTokens (P1-10).
    await this.transactionService.recordTransaction(
      guildId,
      userId,
      -tokens,
      updated.tokens,
      GameTokenTransactionSource.GAME_PLAY,
      null,
    );

    return updated;
  }
}
