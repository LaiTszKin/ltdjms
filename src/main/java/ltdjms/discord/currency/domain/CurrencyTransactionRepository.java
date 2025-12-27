package ltdjms.discord.currency.domain;

import java.util.List;

/**
 * Repository interface for currency transactions. Provides methods to save and query transaction
 * history.
 */
public interface CurrencyTransactionRepository {

  /**
   * Saves a new transaction to the database.
   *
   * @param transaction the transaction to save
   * @return the saved transaction with its generated ID
   */
  CurrencyTransaction save(CurrencyTransaction transaction);

  /**
   * Finds transactions for a specific user in a guild, ordered by most recent first.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param limit the maximum number of transactions to return
   * @param offset the number of transactions to skip (for pagination)
   * @return a list of transactions, ordered by created_at DESC
   */
  List<CurrencyTransaction> findByGuildIdAndUserId(
      long guildId, long userId, int limit, int offset);

  /**
   * Counts the total number of transactions for a user in a guild.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return the total count of transactions
   */
  long countByGuildIdAndUserId(long guildId, long userId);

  /**
   * Deletes all transactions for a user in a guild. Used primarily for testing or account cleanup.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return the number of deleted transactions
   */
  int deleteByGuildIdAndUserId(long guildId, long userId);
}
