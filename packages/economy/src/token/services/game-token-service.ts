import {
  type Result,
  Ok,
  Err,
  DomainError,
  type CacheService,
  type CacheKeyGenerator,
  type DomainEventPublisher,
  type GameTokenChangedEvent,
} from '@ltdjms/shared';
import { TokenAccountRepository } from '../repositories/token-account-repo.js';
import type { GameTokenAccount, TokenAdjustmentResult } from '../../domain/types.js';
import { TOKEN_CACHE_TTL } from '../../domain/types.js';

/**
 * Service for managing game token accounts with caching.
 * Matches Java GameTokenService behavior.
 */
export class GameTokenService {
  private static readonly TOKEN_TTL_SECONDS = TOKEN_CACHE_TTL;

  constructor(
    private readonly accountRepository: TokenAccountRepository,
    private readonly eventPublisher: DomainEventPublisher,
    private readonly cacheService: CacheService,
    private readonly cacheKeyGenerator: CacheKeyGenerator,
  ) {}

  /**
   * Gets the current token balance for a member.
   * Uses cache (TTL 300s) - cache miss falls through to DB (auto-create via findOrCreate).
   */
  async getBalance(guildId: number, userId: number): Promise<number> {
    const cacheKey = this.cacheKeyGenerator.gameTokenKey(String(guildId), String(userId));
    const cachedBalance = await this.cacheService.get<number>(cacheKey);

    if (cachedBalance !== null) {
      return cachedBalance;
    }

    // Cache miss or no cache - query DB with auto-create
    const account = await this.accountRepository.findOrCreate(guildId, userId);
    const balance = account.tokens;
    await this.cacheService.put(cacheKey, balance, GameTokenService.TOKEN_TTL_SECONDS);
    return balance;
  }

  /**
   * Adjusts tokens with Result-based error handling.
   */
  async tryAdjustTokens(
    guildId: number,
    userId: number,
    amount: number,
  ): Promise<Result<TokenAdjustmentResult, DomainError>> {
    if (!Number.isFinite(amount)) {
      return new Err(
        DomainError.invalidInput(`Invalid adjustment amount: ${amount}`),
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

      // Update cache
      const cacheKey = this.cacheKeyGenerator.gameTokenKey(String(guildId), String(userId));
      await this.cacheService.put(
        cacheKey,
        updated.tokens,
        GameTokenService.TOKEN_TTL_SECONDS,
      );

      // Publish event
      this.eventPublisher.publish({
        guildId: String(guildId),
        userId,
        eventType: 'game_token_changed',
        newTokens: updated.tokens,
      } as GameTokenChangedEvent);

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
   * Deducts tokens using Result-based error handling.
   * @param tokens - positive number of tokens to deduct
   */
  async tryDeductTokens(
    guildId: number,
    userId: number,
    tokens: number,
  ): Promise<Result<GameTokenAccount, DomainError>> {
    if (tokens <= 0) {
      return new Err(
        DomainError.invalidInput(`Tokens to deduct must be positive: ${tokens}`),
      );
    }

    const result = await this.accountRepository.tryAdjustTokens(
      guildId,
      userId,
      -tokens,
    );

    if (result.isOk()) {
      const account = result.getValue();

      // Update cache
      const cacheKey = this.cacheKeyGenerator.gameTokenKey(String(guildId), String(userId));
      await this.cacheService.put(
        cacheKey,
        account.tokens,
        GameTokenService.TOKEN_TTL_SECONDS,
      );

      // Publish event
      this.eventPublisher.publish({
        guildId: String(guildId),
        userId,
        eventType: 'game_token_changed',
        newTokens: account.tokens,
      } as GameTokenChangedEvent);
    }

    return result;
  }

  /**
   * Adjusts tokens (deducts if negative, adds if positive).
   * Deduct path that throws on insufficient - use tryAdjustTokens for Result-based.
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

    const cacheKey = this.cacheKeyGenerator.gameTokenKey(String(guildId), String(userId));
    await this.cacheService.put(
      cacheKey,
      updated.tokens,
      GameTokenService.TOKEN_TTL_SECONDS,
    );

    this.eventPublisher.publish({
      guildId: String(guildId),
      userId,
      eventType: 'game_token_changed',
      newTokens: updated.tokens,
    } as GameTokenChangedEvent);

    return updated;
  }

  /**
   * Adjusts tokens (throws on error).
   */
  async adjustTokens(
    guildId: number,
    userId: number,
    amount: number,
  ): Promise<TokenAdjustmentResult> {
    const current = await this.accountRepository.findOrCreate(guildId, userId);
    const previousTokens = current.tokens;

    const updated = await this.accountRepository.adjustTokens(
      guildId,
      userId,
      amount,
    );

    const cacheKey = this.cacheKeyGenerator.gameTokenKey(String(guildId), String(userId));
    await this.cacheService.put(
      cacheKey,
      updated.tokens,
      GameTokenService.TOKEN_TTL_SECONDS,
    );

    this.eventPublisher.publish({
      guildId: String(guildId),
      userId,
      eventType: 'game_token_changed',
      newTokens: updated.tokens,
    } as GameTokenChangedEvent);

    return {
      guildId,
      userId,
      previousTokens,
      newTokens: updated.tokens,
      adjustment: amount,
    };
  }
}
