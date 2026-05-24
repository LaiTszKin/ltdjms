package ltdjms.discord.membership.domain;

import java.time.Instant;

/** Global membership aggregate keyed by Discord user ID. */
public record GlobalMemberMembership(
    long discordUserId,
    MembershipTier currentTier,
    Instant earliestGuildJoinAt,
    Integer settlementDayOfMonth,
    Instant lastSettlementAt,
    Instant nextSettlementAt,
    boolean hasQualifyingBronzeOrder,
    Instant createdAt,
    Instant updatedAt) {

  public GlobalMemberMembership {
    if (settlementDayOfMonth != null && (settlementDayOfMonth < 1 || settlementDayOfMonth > 28)) {
      throw new IllegalArgumentException(
          "settlementDayOfMonth must be between 1 and 28: " + settlementDayOfMonth);
    }
  }

  /** Creates a new membership row with default tier {@link MembershipTier#NONE}. */
  public static GlobalMemberMembership createNew(long discordUserId) {
    Instant now = Instant.now();
    return new GlobalMemberMembership(
        discordUserId, MembershipTier.NONE, null, null, null, null, false, now, now);
  }
}
