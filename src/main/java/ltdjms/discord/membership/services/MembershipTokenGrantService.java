package ltdjms.discord.membership.services;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierConfig;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.persistence.MembershipTokenGrantRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Grants monthly game tokens after successful membership settlement. */
public class MembershipTokenGrantService {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipTokenGrantService.class);

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
   * Idempotently grants tokens for a completed settlement period.
   *
   * @param discordUserId Discord user snowflake
   * @param settlementPeriodEnd exclusive end of the settled period
   * @param tier tier after settlement
   */
  public void grantForSettlement(
      long discordUserId, Instant settlementPeriodEnd, MembershipTier tier) {
    Objects.requireNonNull(settlementPeriodEnd, "settlementPeriodEnd must not be null");
    Objects.requireNonNull(tier, "tier must not be null");

    int tokens = MembershipTierConfig.monthlyTokenGrant(tier);
    if (tokens <= 0) {
      LOG.debug(
          "Skipping membership token grant for userId={}, tier={}: zero grant amount",
          discordUserId,
          tier);
      return;
    }

    if (grantRepository.hasGrantForPeriod(discordUserId, settlementPeriodEnd)) {
      LOG.debug(
          "Skipping duplicate membership token grant: userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd);
      return;
    }

    Optional<Long> guildIdOpt = spendRepository.findMostRecentGuildId(discordUserId);
    if (guildIdOpt.isEmpty()) {
      LOG.warn(
          "Cannot grant membership tokens: no spend guild for userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd);
      return;
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
      return;
    }

    GameTokenService.TokenAdjustmentResult adjustment = adjustResult.getValue();
    gameTokenTransactionService.recordTransaction(
        guildId,
        discordUserId,
        tokens,
        adjustment.newTokens(),
        GameTokenTransaction.Source.MEMBERSHIP_GRANT,
        String.format("會員結算贈幣 (%s)", tier.name()));

    grantRepository.insertGrantLog(discordUserId, settlementPeriodEnd, tier, tokens);

    LOG.info(
        "Granted membership tokens: userId={}, guildId={}, periodEnd={}, tier={}, tokens={}",
        discordUserId,
        guildId,
        settlementPeriodEnd,
        tier,
        tokens);
  }
}
