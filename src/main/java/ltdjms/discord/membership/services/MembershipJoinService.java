package ltdjms.discord.membership.services;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.persistence.MembershipRepository;

/** Records earliest guild join and initializes per-member settlement anchors. */
public class MembershipJoinService {

  /** Settlement anchor dates are computed at midnight in Asia/Taipei. */
  public static final ZoneId SETTLEMENT_ZONE = ZoneId.of("Asia/Taipei");

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
    Instant nextSettlement = computeNextSettlement(settlementDay, joinedAt);

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

  public static int clampDayOfMonth(Instant joinedAt, ZoneId zone) {
    int day = joinedAt.atZone(zone).getDayOfMonth();
    return clampDayOfMonth(day);
  }

  int clampDayOfMonth(Instant joinedAt) {
    return clampDayOfMonth(joinedAt, clock.getZone());
  }

  static int clampDayOfMonth(int dayOfMonth) {
    if (dayOfMonth >= 29) {
      return 28;
    }
    return dayOfMonth;
  }

  Instant computeNextSettlement(int settlementDay, Instant joinedAt) {
    return computeNextSettlementAt(settlementDay, joinedAt, clock.getZone());
  }

  public static Instant computeNextSettlementAt(int settlementDay, Instant joinedAt, ZoneId zone) {
    ZonedDateTime joinZoned = joinedAt.atZone(zone);
    java.time.LocalDate joinDate = joinZoned.toLocalDate();

    ZonedDateTime candidate =
        resolveAnchorDate(joinDate.getYear(), joinDate.getMonthValue(), settlementDay, zone);

    if (!joinZoned.isBefore(candidate)) {
      java.time.YearMonth nextMonth = java.time.YearMonth.from(joinDate).plusMonths(1);
      candidate =
          resolveAnchorDate(nextMonth.getYear(), nextMonth.getMonthValue(), settlementDay, zone);
    }

    return candidate.toInstant();
  }

  /** Advances the settlement anchor by one calendar month. */
  public static Instant advanceNextSettlementAt(int settlementDay, Instant currentNext, ZoneId zone) {
    ZonedDateTime anchor = currentNext.atZone(zone);
    java.time.YearMonth nextMonth = java.time.YearMonth.from(anchor.toLocalDate()).plusMonths(1);
    return resolveAnchorDate(nextMonth.getYear(), nextMonth.getMonthValue(), settlementDay, zone)
        .toInstant();
  }

  private static ZonedDateTime resolveAnchorDate(
      int year, int month, int settlementDay, ZoneId zone) {
    java.time.LocalDate anchor = java.time.LocalDate.of(year, month, settlementDay);
    return anchor.atStartOfDay(zone);
  }
}
