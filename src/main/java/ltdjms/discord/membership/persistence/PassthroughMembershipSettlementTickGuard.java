package ltdjms.discord.membership.persistence;

/** Test/no-op guard that always runs settlement ticks. */
public final class PassthroughMembershipSettlementTickGuard
    implements MembershipSettlementTickGuard {

  public static final PassthroughMembershipSettlementTickGuard INSTANCE =
      new PassthroughMembershipSettlementTickGuard();

  private PassthroughMembershipSettlementTickGuard() {}

  @Override
  public void runGuarded(Runnable tick) {
    tick.run();
  }
}
