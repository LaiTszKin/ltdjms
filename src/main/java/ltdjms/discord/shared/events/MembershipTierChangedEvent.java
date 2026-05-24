package ltdjms.discord.shared.events;

import java.time.Instant;

import ltdjms.discord.membership.domain.MembershipTier;

/** Event fired when a member's global membership tier changes after settlement. */
public record MembershipTierChangedEvent(
    long userId,
    MembershipTier previous,
    MembershipTier current,
    long periodAvgListPriceM,
    Instant settledAt)
    implements DomainEvent {

  /** Global membership is not guild-scoped; returns {@code 0}. */
  @Override
  public long guildId() {
    return 0L;
  }
}
