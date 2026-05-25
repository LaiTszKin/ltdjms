package ltdjms.discord.membership.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipPeriodBounds;
import ltdjms.discord.membership.domain.MembershipPeriodSpendBounds;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierEvaluator;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.MembershipPeriodSpendChangedEvent;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;

/** Admin write use cases for membership tier and period spend adjustments. */
public class MembershipAdminService {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipAdminService.class);

  private final MembershipRepository membershipRepository;
  private final MembershipSpendRepository spendRepository;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  public MembershipAdminService(
      MembershipRepository membershipRepository,
      MembershipSpendRepository spendRepository,
      DomainEventPublisher eventPublisher,
      Clock clock) {
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.spendRepository = Objects.requireNonNull(spendRepository);
    this.eventPublisher = Objects.requireNonNull(eventPublisher);
    this.clock = Objects.requireNonNull(clock);
  }

  public Result<Unit, DomainError> adjustPeriodSpend(
      long userId, long guildId, long adminUserId, SpendAdjustMode mode, long amountM) {
    if (userId <= 0) {
      return Result.err(DomainError.invalidInput("userId must be positive"));
    }
    if (guildId <= 0) {
      return Result.err(DomainError.invalidInput("guildId must be positive"));
    }
    if (adminUserId <= 0) {
      return Result.err(DomainError.invalidInput("adminUserId must be positive"));
    }
    if (amountM < 0) {
      return Result.err(DomainError.invalidInput("amountM must be non-negative"));
    }

    Instant now = clock.instant();
    GlobalMemberMembership membership = membershipRepository.findOrCreate(userId);
    long currentSum = resolveCurrentPeriodSum(membership, now);
    long delta =
        switch (mode) {
          case ADD -> amountM;
          case DEDUCT -> -amountM;
          case SET -> amountM - currentSum;
        };

    if (delta == 0) {
      return Result.okVoid();
    }

    String sourceReference = "admin:" + adminUserId + ":" + UUID.randomUUID();
    try {
      boolean inserted =
          spendRepository.insertAdminAdjust(userId, guildId, delta, sourceReference, now);
      if (!inserted) {
        return Result.err(DomainError.unexpectedFailure("ADMIN_ADJUST insert conflict", null));
      }
      LOG.info(
          "Admin adjusted membership spend: userId={}, adminUserId={}, mode={}, amountM={}, delta={}",
          userId,
          adminUserId,
          mode,
          amountM,
          delta);
      eventPublisher.publish(new MembershipPeriodSpendChangedEvent(userId, now));
      return Result.okVoid();
    } catch (Exception e) {
      LOG.error(
          "Failed admin membership spend adjust: userId={}, adminUserId={}, mode={}",
          userId,
          adminUserId,
          mode,
          e);
      return Result.err(DomainError.persistenceFailure("調整本週期消費 M 失敗", e));
    }
  }

  public Result<MembershipTier, DomainError> setTier(
      long userId, long adminUserId, MembershipTier newTier) {
    if (userId <= 0) {
      return Result.err(DomainError.invalidInput("userId must be positive"));
    }
    if (adminUserId <= 0) {
      return Result.err(DomainError.invalidInput("adminUserId must be positive"));
    }
    if (newTier == null) {
      return Result.err(DomainError.invalidInput("newTier must not be null"));
    }

    try {
      GlobalMemberMembership membership = membershipRepository.findOrCreate(userId);
      MembershipTier previousEffective =
          MembershipTierEvaluator.effectiveTier(
              membership.currentTier(), membership.hasQualifyingBronzeOrder());

      boolean newBronzeFlag = resolveBronzeFlag(newTier);
      GlobalMemberMembership updated =
          new GlobalMemberMembership(
              membership.discordUserId(),
              newTier,
              membership.earliestGuildJoinAt(),
              membership.settlementDayOfMonth(),
              membership.lastSettlementAt(),
              membership.nextSettlementAt(),
              newBronzeFlag,
              membership.createdAt(),
              membership.updatedAt());

      membershipRepository.save(updated);

      MembershipTier newEffective =
          MembershipTierEvaluator.effectiveTier(newTier, newBronzeFlag);
      if (previousEffective != newEffective) {
        Instant now = clock.instant();
        eventPublisher.publish(
            new MembershipTierChangedEvent(
                userId, previousEffective.name(), newEffective.name(), 0L, now));
        LOG.info(
            "Admin set membership tier: userId={}, adminUserId={}, tier {} -> {}",
            userId,
            adminUserId,
            previousEffective,
            newEffective);
      }

      return Result.ok(newEffective);
    } catch (Exception e) {
      LOG.error("Failed admin membership tier set: userId={}, adminUserId={}", userId, adminUserId, e);
      return Result.err(DomainError.persistenceFailure("設定會員等級失敗", e));
    }
  }

  private long resolveCurrentPeriodSum(GlobalMemberMembership membership, Instant now) {
    MembershipPeriodBounds.Period period =
        MembershipPeriodBounds.currentPeriod(membership, now);
    Instant periodStart =
        MembershipPeriodSpendBounds.effectivePeriodStart(
            membership, period, spendRepository);
    return spendRepository.sumListPriceInPeriod(
        membership.discordUserId(), periodStart, period.endExclusive());
  }

  private static boolean resolveBronzeFlag(MembershipTier newTier) {
    if (newTier == MembershipTier.NONE) {
      return false;
    }
    return newTier.ordinal() >= MembershipTier.BRONZE.ordinal();
  }
}
