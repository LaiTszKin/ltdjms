package ltdjms.discord.currency.services;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;

/**
 * Default implementation of BalanceService. Handles auto-creation of accounts and combines balance
 * with currency configuration.
 */
public class DefaultBalanceService implements BalanceService {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultBalanceService.class);
  private static final int BALANCE_TTL_SECONDS = 300;

  private final MemberCurrencyAccountRepository accountRepository;
  private final GuildCurrencyConfigRepository configRepository;
  private final CacheService cacheService;
  private final CacheKeyGenerator cacheKeyGenerator;

  public DefaultBalanceService(
      MemberCurrencyAccountRepository accountRepository,
      GuildCurrencyConfigRepository configRepository,
      CacheService cacheService,
      CacheKeyGenerator cacheKeyGenerator) {
    this.accountRepository = accountRepository;
    this.configRepository = configRepository;
    this.cacheService = cacheService;
    this.cacheKeyGenerator = cacheKeyGenerator;
  }

  @Override
  @Deprecated
  public BalanceView getBalance(long guildId, long userId) {
    LOG.debug("Getting balance for guildId={}, userId={}", guildId, userId);

    Long balance = getCachedBalance(guildId, userId);
    if (balance == null) {
      // Cache miss - get from database
      MemberCurrencyAccount account = accountRepository.findOrCreate(guildId, userId);
      balance = account.balance();
      putCachedBalance(guildId, userId, balance);
    }

    // Get guild currency configuration (or defaults)
    GuildCurrencyConfig config =
        configRepository.findByGuildId(guildId).orElse(GuildCurrencyConfig.createDefault(guildId));

    BalanceView view =
        new BalanceView(guildId, userId, balance, config.currencyName(), config.currencyIcon());

    LOG.info(
        "Retrieved balance: guildId={}, userId={}, balance={}, currency={}",
        guildId,
        userId,
        balance,
        config.currencyName());

    return view;
  }

  @Override
  public Result<BalanceView, DomainError> tryGetBalance(long guildId, long userId) {
    LOG.debug("Getting balance for guildId={}, userId={}", guildId, userId);

    try {
      Long balance = getCachedBalance(guildId, userId);
      if (balance == null) {
        // Cache miss - get from database
        MemberCurrencyAccount account = accountRepository.findOrCreate(guildId, userId);
        balance = account.balance();
        putCachedBalance(guildId, userId, balance);
      }

      // Get guild currency configuration (or defaults)
      GuildCurrencyConfig config =
          configRepository
              .findByGuildId(guildId)
              .orElse(GuildCurrencyConfig.createDefault(guildId));

      BalanceView view =
          new BalanceView(guildId, userId, balance, config.currencyName(), config.currencyIcon());

      LOG.info(
          "Retrieved balance: guildId={}, userId={}, balance={}, currency={}",
          guildId,
          userId,
          balance,
          config.currencyName());

      return Result.ok(view);
    } catch (RepositoryException e) {
      LOG.error("Failed to get balance for guildId={}, userId={}", guildId, userId, e);
      return Result.err(DomainError.persistenceFailure("Failed to retrieve balance", e));
    }
  }

  /**
   * Gets the balance from cache.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return the cached balance, or null if not in cache
   */
  private Long getCachedBalance(long guildId, long userId) {
    String cacheKey = cacheKeyGenerator.balanceKey(guildId, userId);
    Optional<Long> cached = cacheService.get(cacheKey, Long.class);
    if (cached.isPresent()) {
      LOG.debug("Cache hit for balance: guildId={}, userId={}", guildId, userId);
      return cached.get();
    }
    LOG.debug("Cache miss for balance: guildId={}, userId={}", guildId, userId);
    return null;
  }

  /**
   * Puts the balance into cache.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param balance the balance to cache
   */
  private void putCachedBalance(long guildId, long userId, long balance) {
    String cacheKey = cacheKeyGenerator.balanceKey(guildId, userId);
    cacheService.put(cacheKey, balance, BALANCE_TTL_SECONDS);
    LOG.debug("Cached balance: guildId={}, userId={}, balance={}", guildId, userId, balance);
  }
}
