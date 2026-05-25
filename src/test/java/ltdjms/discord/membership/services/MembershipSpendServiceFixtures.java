package ltdjms.discord.membership.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test fixtures for membership spend recording. */
public final class MembershipSpendServiceFixtures {

  private MembershipSpendServiceFixtures() {}

  /** Returns a no-op mock for tests that do not exercise membership spend recording. */
  public static MembershipSpendRecorder noop() {
    MembershipSpendRecorder recorder = mock(MembershipSpendRecorder.class);
    when(recorder.recordPaidEscortOrder(any(PaidEscortOrderSnapshot.class))).thenReturn(true);
    return recorder;
  }

  /** Returns a no-op mock for tests that do not exercise membership spend retry. */
  public static MembershipSpendRetryService noopRetry() {
    return mock(MembershipSpendRetryService.class);
  }
}
