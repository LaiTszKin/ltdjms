package ltdjms.discord.membership.services;

/** Port for recording escort fiat payments into the membership spend ledger. */
public interface MembershipSpendRecorder {

  /**
   * Records catalog list price M for a paid escort-linked fiat order.
   *
   * @return {@code true} when spend was recorded, intentionally skipped, or already present; {@code
   *     false} on persistence failure
   */
  boolean recordPaidEscortOrder(PaidEscortOrderSnapshot snapshot);
}
