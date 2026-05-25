package ltdjms.discord.membership.services;

import java.time.Instant;
import java.util.Objects;

/** Membership-owned view of a paid escort fiat order for spend ledger recording. */
public record PaidEscortOrderSnapshot(
    String orderNumber,
    long buyerUserId,
    long guildId,
    Instant paidAt,
    Long orderListPriceTwd,
    String escortOptionCode,
    Long productFiatPriceTwd,
    boolean escortLinked) {

  public PaidEscortOrderSnapshot {
    Objects.requireNonNull(orderNumber, "orderNumber must not be null");
  }
}
