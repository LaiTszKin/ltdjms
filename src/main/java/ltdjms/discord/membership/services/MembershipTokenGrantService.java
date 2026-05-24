package ltdjms.discord.membership.services;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierConfig;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.persistence.MembershipTokenGrantRepository;
import ltdjms.discord.membership.persistence.PendingMembershipGrant;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Grants monthly game tokens after successful membership settlement. */
public class MembershipTokenGrantService {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipTokenGrantService.class);
  static final int PENDING_GRANT_BATCH_LIMIT = 100;

  private final MembershipTokenGrantRepository grantRepository;
  private final MembershipSpendRepository spendRepository;
  private final GameTokenService gameTokenService;
  private final GameTokenTransactionService gameTokenTransactionService;

  public MembershipTokenGrantService(
      MembershipTokenGrantRepository grantRepository,
      MembershipSpendRepository spendRepository,
      GameTokenService gameTokenService,
      GameTokenTransactionService gameTokenTransactionService) {
    this.grantRepository = Objects.requireNonNull(grantRepository);
    this.spendRepository = Objects.requireNonNull(spendRepository);
    this.gameTokenService = Objects.requireNonNull(gameTokenService);
    this.gameTokenTransactionService = Objects.requireNonNull(gameTokenTransactionService);
  }

  /**
   * Retries token grants for settled periods that were not recorded due to transient failures.
   *
   * @return number of grants successfully completed
   */
  public int retryPendingGrants() {
    List<PendingMembershipGrant> pending =
        grantRepository.findPendingGrants(PENDING_GRANT_BATCH_LIMIT);
    int completed = 0;
    for (PendingMembershipGrant grant : pending) {
      if (grantForSettlement(grant.discordUserId(), grant.settlementPeriodEnd(), grant.tier())) {
        completed++;
      }
    }
    return completed;
  }

  /**
   * Idempotently grants tokens for a completed settlement period.
   *
   * @param discordUserId Discord user snowflake
   * @param settlementPeriodEnd exclusive end of the settled period
   * @param tier tier after settlement
   * @return {@code true} when grant completed or already recorded, {@code false} on retryable
   *     failure
   */
  public boolean grantForSettlement(
      long discordUserId, Instant settlementPeriodEnd, MembershipTier tier) {
    Objects.requireNonNull(settlementPeriodEnd, "settlementPeriodEnd must not be null");
    Objects.requireNonNull(tier, "tier must not be null");

    int tokens = MembershipTierConfig.monthlyTokenGrant(tier);
    if (tokens <= 0) {
      LOG.debug(
          "Skipping membership token grant for userId={}, tier={}: zero grant amount",
          discordUserId,
          tier);
      return true;
    }

    if (!grantRepository.tryClaimGrantLog(discordUserId, settlementPeriodEnd, tier, tokens)) {
      LOG.debug(
          "Skipping duplicate membership token grant: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd);
      return true;
    }

    var guildIdOpt = spendRepository.findMostRecentGuildId(discordUserId);
    if (guildIdOpt.isEmpty()) {
      LOG.warn(
          "Cannot grant membership tokens: no spend guild for userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd);
      grantRepository.releaseGrantClaim(discordUserId, settlementPeriodEnd);
      return false;
    }

    long guildId = guildIdOpt.get();
    Result<GameTokenService.TokenAdjustmentResult, DomainError> adjustResult =
        gameTokenService.tryAdjustTokens(guildId, discordUserId, tokens);
    if (adjustResult.isErr()) {
      LOG.error(
          "Failed to grant membership tokens for userId={}, periodEnd={}, tier={}, tokens={}: {}",
          discordUserId,
          settlementPeriodEnd,
          tier,
          tokens,
          adjustResult.getError().message());
      grantRepository.releaseGrantClaim(discordUserId, settlementPeriodEnd);
      return false;
    }

    GameTokenService.TokenAdjustmentResult adjustment = adjustResult.getValue();
    gameTokenTransactionService.recordTransaction(
        guildId,
        discordUserId,
        tokens,
        adjustment.newTokens(),
        GameTokenTransaction.Source.MEMBERSHIP_GRANT,
        String.format("會員結算贈幣 (%s)", tier.name()));

    LOG.info(
        "Granted membership tokens: userId={}, guildId={}, periodEnd={}, tier={}, tokens={}",
        discordUserId,
        guildId,
        settlementPeriodEnd,
        tier,
        tokens);
    return true;
  }
}
