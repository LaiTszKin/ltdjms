package ltdjms.discord.membership.persistence;

/** Guards membership settlement scheduler ticks for single-instance execution. */
public interface MembershipSettlementTickGuard {

  /** Runs {@code tick} when this instance holds the scheduler lock. */
  void runGuarded(Runnable tick);
}
