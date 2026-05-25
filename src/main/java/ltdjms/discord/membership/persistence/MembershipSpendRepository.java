package ltdjms.discord.membership.persistence;

import java.time.Instant;
import java.util.Optional;

/** Persistence port for membership spend ledger entries. */
public interface MembershipSpendRepository {

  /** Returns the sum of catalog list prices M for a user within {@code [from, to)}. */
  long sumListPriceInPeriod(long discordUserId, Instant from, Instant to);

  /**
   * Inserts a spend entry and marks bronze qualification in a single transaction when the list
   * price meets the bronze threshold.
   */
  SpendRecordResult insertSpendAndQualifyBronzeIfThreshold(
      long discordUserId,
      long guildId,
      long listPriceTwd,
      String escortOptionCode,
      String sourceType,
      String sourceReference,
      Instant paidAt,
      long bronzeThresholdListPriceTwd);

  /**
   * Returns the guild with the highest spend total in {@code [from, to)} for token grant routing.
   */
  Optional<Long> findPrimaryGuildInPeriod(long discordUserId, Instant from, Instant to);
}
