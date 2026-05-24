package ltdjms.discord.membership.services;

import static org.mockito.Mockito.mock;

/** Test fixtures for membership spend recording. */
public final class MembershipSpendServiceFixtures {

  private MembershipSpendServiceFixtures() {}

  /** Returns a no-op mock for tests that do not exercise membership spend recording. */
  public static MembershipSpendService noop() {
    return mock(MembershipSpendService.class);
  }
}
