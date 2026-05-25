package ltdjms.discord.shared.events;

import java.time.Instant;

/** Event fired when a member's global membership tier changes after settlement. */
public record MembershipTierChangedEvent(
    long userId,
    String previousTierCode,
    String currentTierCode,
    /**
     * Period average list price M for settlement-driven changes. For immediate bronze promotion on
     * a single qualifying order, this is the qualifying order's catalog list price M, not a period
     * average.
     */
    long periodAvgListPriceM,
    Instant settledAt)
    implements DomainEvent {

  /** Global membership is not guild-scoped; returns {@code 0}. */
  @Override
  public long guildId() {
    return 0L;
  }
}
