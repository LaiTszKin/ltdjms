package ltdjms.discord.shared.events;

import java.time.Instant;

/** Event fired when a member's period spend ledger changes (e.g. admin adjustment). */
public record MembershipPeriodSpendChangedEvent(long userId, Instant changedAt)
    implements DomainEvent {

  /** Global membership is not guild-scoped; returns {@code 0}. */
  @Override
  public long guildId() {
    return 0L;
  }
}
