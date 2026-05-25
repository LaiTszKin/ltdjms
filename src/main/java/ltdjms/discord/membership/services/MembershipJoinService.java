package ltdjms.discord.membership.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipSettlementCalendar;
import ltdjms.discord.membership.persistence.MembershipRepository;

/** Records earliest guild join and initializes per-member settlement anchors. */
public class MembershipJoinService {

  /** Settlement anchor dates are computed at midnight in Asia/Taipei. */
  public static final java.time.ZoneId SETTLEMENT_ZONE =
      MembershipSettlementCalendar.SETTLEMENT_ZONE;

  private static final Logger LOG = LoggerFactory.getLogger(MembershipJoinService.class);

  private final MembershipRepository membershipRepository;
  private final Clock clock;

  public MembershipJoinService(MembershipRepository membershipRepository, Clock clock) {
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Records the earliest guild join for a member and initializes settlement anchors when needed.
   *
   * @param discordUserId Discord user snowflake
   * @param joinedAt time the member joined the guild
   */
  public void onMemberJoin(long discordUserId, Instant joinedAt) {
    membershipRepository.findOrCreate(discordUserId);

    int settlementDay = clampDayOfMonth(joinedAt);
    Instant firstSettlement = computeNextSettlement(settlementDay, joinedAt);
    Instant nextSettlement =
        MembershipSettlementCalendar.resolveUpcomingSettlementAt(
            settlementDay, firstSettlement, clock.instant(), SETTLEMENT_ZONE);

    boolean updated =
        membershipRepository.mergeEarliestGuildJoin(
            discordUserId, joinedAt, settlementDay, nextSettlement);
    if (updated) {
      LOG.info(
          "Recorded earliest guild join for userId={}, earliest={}, settlementDay={},"
              + " nextSettlement={}",
          discordUserId,
          joinedAt,
          settlementDay,
          nextSettlement);
    } else {
      LOG.debug(
          "Skipping join update for userId={}: joinedAt={} is not before stored earliest",
          discordUserId,
          joinedAt);
    }
  }

  public static int clampDayOfMonth(Instant joinedAt, java.time.ZoneId zone) {
    return MembershipSettlementCalendar.clampDayOfMonth(joinedAt, zone);
  }

  int clampDayOfMonth(Instant joinedAt) {
    return clampDayOfMonth(joinedAt, clock.getZone());
  }

  static int clampDayOfMonth(int dayOfMonth) {
    return MembershipSettlementCalendar.clampDayOfMonth(dayOfMonth);
  }

  Instant computeNextSettlement(int settlementDay, Instant joinedAt) {
    return computeNextSettlementAt(settlementDay, joinedAt, clock.getZone());
  }

  public static Instant computeNextSettlementAt(
      int settlementDay, Instant joinedAt, java.time.ZoneId zone) {
    return MembershipSettlementCalendar.computeNextSettlementAt(settlementDay, joinedAt, zone);
  }

  /**
   * Advances a never-settled member's stale {@code next_settlement_at} to the upcoming anchor when
   * historical join backfill left the first settlement month in the past.
   */
  public void repairStaleNextSettlement(long discordUserId) {
    membershipRepository
        .findByUserId(discordUserId)
        .ifPresent(
            membership -> {
              if (membership.lastSettlementAt() != null || membership.nextSettlementAt() == null) {
                return;
              }
              Instant now = clock.instant();
              if (membership.nextSettlementAt().isAfter(now)) {
                return;
              }
              Integer settlementDay = membership.settlementDayOfMonth();
              if (settlementDay == null && membership.earliestGuildJoinAt() != null) {
                settlementDay = clampDayOfMonth(membership.earliestGuildJoinAt());
              }
              if (settlementDay == null) {
                return;
              }
              Instant upcoming =
                  MembershipSettlementCalendar.resolveUpcomingSettlementAt(
                      settlementDay, membership.nextSettlementAt(), now, SETTLEMENT_ZONE);
              if (upcoming.equals(membership.nextSettlementAt())) {
                return;
              }
              membershipRepository.save(
                  new GlobalMemberMembership(
                      membership.discordUserId(),
                      membership.currentTier(),
                      membership.earliestGuildJoinAt(),
                      settlementDay,
                      membership.lastSettlementAt(),
                      upcoming,
                      membership.hasQualifyingBronzeOrder(),
                      membership.createdAt(),
                      membership.updatedAt()));
              LOG.info(
                  "Repaired stale next settlement for userId={}: {} -> {}",
                  discordUserId,
                  membership.nextSettlementAt(),
                  upcoming);
            });
  }
}
