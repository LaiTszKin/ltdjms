package ltdjms.discord.gametoken.services;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.gametoken.domain.GameTokenAccount;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.persistence.InsufficientTokensException;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.GameTokenChangedEvent;

/**
 * Service for managing game token accounts. Handles token adjustments with validation for
 * non-negative balances.
 */
public class GameTokenService {

  private static final Logger LOG = LoggerFactory.getLogger(GameTokenService.class);
  private static final int TOKEN_TTL_SECONDS = 300;

  private final GameTokenAccountRepository accountRepository;
  private final DomainEventPublisher eventPublisher;
  private final CacheService cacheService;
  private final CacheKeyGenerator cacheKeyGenerator;

  public GameTokenService(
      GameTokenAccountRepository accountRepository,
      DomainEventPublisher eventPublisher,
      CacheService cacheService,
      CacheKeyGenerator cacheKeyGenerator) {
    this.accountRepository = accountRepository;
    this.eventPublisher = eventPublisher;
    this.cacheService = cacheService;
    this.cacheKeyGenerator = cacheKeyGenerator;
  }

  /**
   * Gets the current token balance for a member.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return the token balance (0 if account doesn't exist)
   */
  public long getBalance(long guildId, long userId) {
    Long cachedBalance = getCachedBalance(guildId, userId);
    if (cachedBalance != null) {
      return cachedBalance;
    }

    // Cache miss - get from database
    long balance =
        accountRepository
            .findByGuildIdAndUserId(guildId, userId)
            .map(GameTokenAccount::tokens)
            .orElse(0L);
    putCachedBalance(guildId, userId, balance);
    return balance;
  }

  /**
   * Adjusts a member's token balance by the specified amount.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param amount the amount to adjust (positive for credit, negative for debit)
   * @return the adjustment result
   * @throws InsufficientTokensException if the adjustment would result in a negative balance
   */
  public TokenAdjustmentResult adjustTokens(long guildId, long userId, long amount) {
    LOG.debug("Adjusting tokens for guildId={}, userId={}, amount={}", guildId, userId, amount);

    // Get current balance
    GameTokenAccount current = accountRepository.findOrCreate(guildId, userId);
    long previousTokens = current.tokens();

    // Apply adjustment
    GameTokenAccount updated = accountRepository.adjustTokens(guildId, userId, amount);

    // Update cache
    updateCachedBalance(guildId, userId, updated.tokens());

    // Publish event
    eventPublisher.publish(new GameTokenChangedEvent(guildId, userId, updated.tokens()));

    TokenAdjustmentResult result =
        new TokenAdjustmentResult(guildId, userId, previousTokens, updated.tokens(), amount);

    LOG.info(
        "Tokens adjusted: guildId={}, userId={}, previous={}, new={}, adjustment={}",
        guildId,
        userId,
        previousTokens,
        updated.tokens(),
        amount);

    return result;
  }

  /**
   * Checks if a member has enough tokens.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param requiredTokens the number of tokens required
   * @return true if the member has enough tokens
   */
  public boolean hasEnoughTokens(long guildId, long userId, long requiredTokens) {
    return getBalance(guildId, userId) >= requiredTokens;
  }

  /**
   * Deducts tokens from a member's account.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param tokens the number of tokens to deduct (must be positive)
   * @return the updated account
   * @throws IllegalArgumentException if tokens is not positive
   * @throws InsufficientTokensException if the member doesn't have enough tokens
   */
  public GameTokenAccount deductTokens(long guildId, long userId, long tokens) {
    if (tokens <= 0) {
      throw new IllegalArgumentException("Tokens to deduct must be positive: " + tokens);
    }
    GameTokenAccount updated = accountRepository.adjustTokens(guildId, userId, -tokens);

    // Update cache
    updateCachedBalance(guildId, userId, updated.tokens());

    // Publish event
    eventPublisher.publish(new GameTokenChangedEvent(guildId, userId, updated.tokens()));

    return updated;
  }

  /**
   * Adjusts a member's token balance using Result-based error handling.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param amount the amount to adjust (positive for credit, negative for debit)
   * @return Result containing TokenAdjustmentResult on success, or DomainError on failure
   */
  public Result<TokenAdjustmentResult, DomainError> tryAdjustTokens(
      long guildId, long userId, long amount) {
    LOG.debug("Adjusting tokens for guildId={}, userId={}, amount={}", guildId, userId, amount);

    // Get current balance
    GameTokenAccount current = accountRepository.findOrCreate(guildId, userId);
    long previousTokens = current.tokens();

    // Apply adjustment using Result-based API
    Result<GameTokenAccount, DomainError> adjustResult =
        accountRepository.tryAdjustTokens(guildId, userId, amount);

    if (adjustResult.isErr()) {
      return Result.err(adjustResult.getError());
    }

    GameTokenAccount updated = adjustResult.getValue();

    // Update cache
    updateCachedBalance(guildId, userId, updated.tokens());

    // Publish event
    eventPublisher.publish(new GameTokenChangedEvent(guildId, userId, updated.tokens()));

    TokenAdjustmentResult result =
        new TokenAdjustmentResult(guildId, userId, previousTokens, updated.tokens(), amount);

    LOG.info(
        "Tokens adjusted: guildId={}, userId={}, previous={}, new={}, adjustment={}",
        guildId,
        userId,
        previousTokens,
        updated.tokens(),
        amount);

    return Result.ok(result);
  }

  /**
   * Deducts tokens using Result-based error handling.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param tokens the number of tokens to deduct (must be positive)
   * @return Result containing the updated account, or DomainError on failure
   */
  public Result<GameTokenAccount, DomainError> tryDeductTokens(
      long guildId, long userId, long tokens) {
    if (tokens <= 0) {
      return Result.err(DomainError.invalidInput("Tokens to deduct must be positive: " + tokens));
    }
    Result<GameTokenAccount, DomainError> result =
        accountRepository.tryAdjustTokens(guildId, userId, -tokens);

    if (result.isOk()) {
      // Update cache
      updateCachedBalance(guildId, userId, result.getValue().tokens());

      // Publish event
      eventPublisher.publish(
          new GameTokenChangedEvent(guildId, userId, result.getValue().tokens()));
    }

    return result;
  }

  /** Result of a token adjustment operation. */
  public record TokenAdjustmentResult(
      long guildId, long userId, long previousTokens, long newTokens, long adjustment) {
    /** Formats the result as a Discord message. */
    public String formatMessage(String targetUserMention) {
      String action = adjustment >= 0 ? "Added" : "Removed";
      long displayAmount = Math.abs(adjustment);
      return String.format(
          "%s %,d game tokens to/from %s\nNew balance: %,d game tokens",
          action, displayAmount, targetUserMention, newTokens);
    }
  }

  /**
   * Gets the token balance from cache.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return the cached balance, or null if not in cache
   */
  private Long getCachedBalance(long guildId, long userId) {
    String cacheKey = cacheKeyGenerator.gameTokenKey(guildId, userId);
    Optional<Long> cached = cacheService.get(cacheKey, Long.class);
    if (cached.isPresent()) {
      LOG.debug("Cache hit for game token: guildId={}, userId={}", guildId, userId);
      return cached.get();
    }
    LOG.debug("Cache miss for game token: guildId={}, userId={}", guildId, userId);
    return null;
  }

  /**
   * Puts the token balance into cache.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param balance the balance to cache
   */
  private void putCachedBalance(long guildId, long userId, long balance) {
    String cacheKey = cacheKeyGenerator.gameTokenKey(guildId, userId);
    cacheService.put(cacheKey, balance, TOKEN_TTL_SECONDS);
    LOG.debug("Cached game token: guildId={}, userId={}, balance={}", guildId, userId, balance);
  }

  /**
   * Updates the cached token balance.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param balance the new balance to cache
   */
  private void updateCachedBalance(long guildId, long userId, long balance) {
    String cacheKey = cacheKeyGenerator.gameTokenKey(guildId, userId);
    cacheService.put(cacheKey, balance, TOKEN_TTL_SECONDS);
    LOG.debug(
        "Updated cached game token: guildId={}, userId={}, balance={}", guildId, userId, balance);
  }
}
