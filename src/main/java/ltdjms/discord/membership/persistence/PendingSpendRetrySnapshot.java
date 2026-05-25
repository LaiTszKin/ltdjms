package ltdjms.discord.membership.persistence;

import java.time.Instant;

/** Snapshot payload stored with a spend retry row. */
public record PendingSpendRetrySnapshot(
    String orderNumber,
    long buyerUserId,
    long guildId,
    Instant paidAt,
    Long orderListPriceTwd,
    String escortOptionCode,
    Long productFiatPriceTwd,
    boolean escortLinked,
    int attemptCount) {}
