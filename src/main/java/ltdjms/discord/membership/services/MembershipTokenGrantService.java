package ltdjms.discord.membership.services;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.membership.domain.MembershipPeriodBounds;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierConfig;
import ltdjms.discord.membership.persistence.GrantClaimState;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.persistence.MembershipTokenGrantRepository;
import ltdjms.discord.membership.persistence.PendingMembershipGrant;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Grants monthly game tokens after successful membership settlement. */
public class MembershipTokenGrantService {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipTokenGrantService.class);
  static final int PENDING_GRANT_BATCH_LIMIT = 100;
  static final int MAX_GRANT_BATCHES_PER_TICK = 20;

  private final MembershipTokenGrantRepository grantRepository;
  private final MembershipSpendRepository spendRepository;
  private final MembershipRepository membershipRepository;
  private final GameTokenService gameTokenService;
  private final GameTokenTransactionService gameTokenTransactionService;

  public MembershipTokenGrantService(
      MembershipTokenGrantRepository grantRepository,
      MembershipSpendRepository spendRepository,
      MembershipRepository membershipRepository,
      GameTokenService gameTokenService,
      GameTokenTransactionService gameTokenTransactionService) {
    this.grantRepository = Objects.requireNonNull(grantRepository);
    this.spendRepository = Objects.requireNonNull(spendRepository);
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.gameTokenService = Objects.requireNonNull(gameTokenService);
    this.gameTokenTransactionService = Objects.requireNonNull(gameTokenTransactionService);
  }

  /**
   * Retries token grants for settled periods that were not recorded due to transient failures.
   *
   * @return number of grants successfully completed
   */
  public int retryPendingGrants() {
    int completed = 0;
    List<PendingMembershipGrant> pending;
    int batches = 0;
    do {
      pending = grantRepository.findPendingGrants(PENDING_GRANT_BATCH_LIMIT);
      for (PendingMembershipGrant grant : pending) {
        Instant periodStart =
            resolvePeriodStart(grant.discordUserId(), grant.settlementPeriodEnd());
        if (grantForSettlement(
            grant.discordUserId(), periodStart, grant.settlementPeriodEnd(), grant.tier())) {
          completed++;
        }
      }
      batches++;
    } while (pending.size() == PENDING_GRANT_BATCH_LIMIT && batches < MAX_GRANT_BATCHES_PER_TICK);

    if (pending.size() == PENDING_GRANT_BATCH_LIMIT) {
      LOG.warn(
          "Membership token grant backlog remains after {} batches ({} per batch)",
          MAX_GRANT_BATCHES_PER_TICK,
          PENDING_GRANT_BATCH_LIMIT);
    }
    return completed;
  }

  /**
   * Idempotently grants tokens for a completed settlement period.
   *
   * @param discordUserId Discord user snowflake
   * @param settlementPeriodStart inclusive start of the settled period
   * @param settlementPeriodEnd exclusive end of the settled period
   * @param tier tier after settlement
   * @return {@code true} when grant completed or already recorded, {@code false} on retryable
   *     failure
   */
  public boolean grantForSettlement(
      long discordUserId,
      Instant settlementPeriodStart,
      Instant settlementPeriodEnd,
      MembershipTier tier) {
    Objects.requireNonNull(settlementPeriodStart, "settlementPeriodStart must not be null");
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

    Optional<GrantClaimState> claimStateOpt =
        grantRepository.findClaimState(discordUserId, settlementPeriodEnd);
    if (claimStateOpt.isPresent() && "COMPLETED".equals(claimStateOpt.get().status())) {
      return true;
    }
    if (claimStateOpt.isPresent() && "SKIPPED_NO_GUILD".equals(claimStateOpt.get().status())) {
      return true;
    }
    if (claimStateOpt.isPresent()
        && "FAILED".equals(claimStateOpt.get().status())
        && claimStateOpt.get().tokensAdjusted()
        && claimStateOpt.get().auditRecorded()) {
      grantRepository.completeGrantClaim(discordUserId, settlementPeriodEnd);
      return true;
    }

    if (claimStateOpt.isEmpty() || "FAILED".equals(claimStateOpt.get().status())) {
      if (!grantRepository.tryClaimGrantLog(discordUserId, settlementPeriodEnd, tier, tokens)) {
        return grantRepository
            .findClaimState(discordUserId, settlementPeriodEnd)
            .map(
                state ->
                    "COMPLETED".equals(state.status()) || "SKIPPED_NO_GUILD".equals(state.status()))
            .orElse(false);
      }
    }

    GrantClaimState claimState =
        grantRepository
            .findClaimState(discordUserId, settlementPeriodEnd)
            .orElse(new GrantClaimState("CLAIMED", false, false));
    boolean tokensAlreadyAdjusted = claimState.tokensAdjusted();
    boolean auditAlreadyRecorded = claimState.auditRecorded();

    var guildIdOpt =
        spendRepository.findPrimaryGuildInPeriod(
            discordUserId, settlementPeriodStart, settlementPeriodEnd);
    if (guildIdOpt.isEmpty()) {
      LOG.warn(
          "Skipping membership token grant: no spend guild for userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd);
      grantRepository.markSkippedNoGuild(discordUserId, settlementPeriodEnd);
      return true;
    }

    long guildId = guildIdOpt.get();
    long newBalance;

    if (!tokensAlreadyAdjusted) {
      Result<GameTokenService.TokenAdjustmentResult, DomainError> adjustResult =
          gameTokenService.tryAdjustTokens(guildId, discordUserId, tokens);
      if (adjustResult.isErr()) {
        LOG.error(
            "Failed to grant membership tokens for userId={}, periodEnd={}, tier={}, tokens={}:"
                + " {}",
            discordUserId,
            settlementPeriodEnd,
            tier,
            tokens,
            adjustResult.getError().message());
        grantRepository.releaseGrantClaim(discordUserId, settlementPeriodEnd);
        return false;
      }
      newBalance = adjustResult.getValue().newTokens();
      grantRepository.markTokensAdjusted(discordUserId, settlementPeriodEnd);
    } else {
      newBalance = gameTokenService.getBalance(guildId, discordUserId);
    }

    if (auditAlreadyRecorded) {
      grantRepository.completeGrantClaim(discordUserId, settlementPeriodEnd);
      return true;
    }

    try {
      gameTokenTransactionService.recordTransaction(
          guildId,
          discordUserId,
          tokens,
          newBalance,
          GameTokenTransaction.Source.MEMBERSHIP_GRANT,
          String.format("會員結算贈幣 (%s)", tier.name()));
      grantRepository.markAuditRecorded(discordUserId, settlementPeriodEnd);
      grantRepository.completeGrantClaim(discordUserId, settlementPeriodEnd);
    } catch (RuntimeException e) {
      LOG.error(
          "Failed to record membership token grant audit for userId={}, periodEnd={}",
          discordUserId,
          settlementPeriodEnd,
          e);
      grantRepository.markGrantFailed(discordUserId, settlementPeriodEnd);
      return false;
    }

    LOG.info(
        "Granted membership tokens: userId={}, guildId={}, periodEnd={}, tier={}, tokens={}",
        discordUserId,
        guildId,
        settlementPeriodEnd,
        tier,
        tokens);
    return true;
  }

  private Instant resolvePeriodStart(long discordUserId, Instant settlementPeriodEnd) {
    return membershipRepository
        .findByUserId(discordUserId)
        .map(
            membership ->
                MembershipPeriodBounds.resolvePeriodStartForEndedPeriod(
                    membership, settlementPeriodEnd))
        .orElse(Instant.EPOCH);
  }
}
