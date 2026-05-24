package ltdjms.discord.membership.persistence;

import java.time.Instant;
import java.util.Optional;

/** Persistence port for membership spend ledger entries. */
public interface MembershipSpendRepository {

  /**
   * Inserts a spend entry if no row exists for the same source identity.
   *
   * @return {@code true} when a new row was inserted, {@code false} when skipped as duplicate
   */
  boolean insertIfAbsent(
      long discordUserId,
      long guildId,
      long listPriceTwd,
      String escortOptionCode,
      String sourceType,
      String sourceReference,
      Instant paidAt);

  /** Returns the sum of catalog list prices M for a user within {@code [from, to)}. */
  long sumListPriceInPeriod(long discordUserId, Instant from, Instant to);

  /**
   * Inserts a spend entry and marks bronze qualification in a single transaction when the list
   * price meets the bronze threshold.
   *
   * @return {@code true} when a new spend row was inserted
   */
  boolean insertSpendAndQualifyBronzeIfThreshold(
      long discordUserId,
      long guildId,
      long listPriceTwd,
      String escortOptionCode,
      String sourceType,
      String sourceReference,
      Instant paidAt,
      long bronzeThresholdListPriceTwd);

  /** Returns the guild ID from the user's most recent spend entry, if any. */
  Optional<Long> findMostRecentGuildId(long discordUserId);
}
