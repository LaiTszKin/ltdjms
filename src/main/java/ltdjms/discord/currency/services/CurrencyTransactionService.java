package ltdjms.discord.currency.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.domain.CurrencyTransactionRepository;

/**
 * Service for querying currency transaction history. Provides methods to retrieve paginated
 * transaction records for users.
 */
public class CurrencyTransactionService {

  private static final Logger LOG = LoggerFactory.getLogger(CurrencyTransactionService.class);

  /** Default number of transactions per page. */
  public static final int DEFAULT_PAGE_SIZE = 10;

  private final CurrencyTransactionRepository transactionRepository;

  public CurrencyTransactionService(CurrencyTransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  /**
   * Gets a page of transactions for a user.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param page the page number (1-based)
   * @param pageSize the number of items per page
   * @return a page of transactions
   */
  public TransactionPage getTransactionPage(long guildId, long userId, int page, int pageSize) {
    if (page < 1) {
      page = 1;
    }
    if (pageSize < 1) {
      pageSize = DEFAULT_PAGE_SIZE;
    }

    long totalCount = transactionRepository.countByGuildIdAndUserId(guildId, userId);

    int totalPages = (int) Math.ceil((double) totalCount / pageSize);
    if (totalPages < 1) {
      totalPages = 1;
    }
    if (page > totalPages) {
      page = totalPages;
    }

    int offset = (page - 1) * pageSize;
    List<CurrencyTransaction> transactions =
        transactionRepository.findByGuildIdAndUserId(guildId, userId, pageSize, offset);

    LOG.debug(
        "Retrieved currency transaction page: guildId={}, userId={}, page={}/{}, count={}",
        guildId,
        userId,
        page,
        totalPages,
        transactions.size());

    return new TransactionPage(transactions, page, totalPages, totalCount, pageSize);
  }

  /**
   * Gets the first page of transactions with default page size.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return the first page of transactions
   */
  public TransactionPage getTransactionPage(long guildId, long userId) {
    return getTransactionPage(guildId, userId, 1, DEFAULT_PAGE_SIZE);
  }

  /**
   * Records a new transaction.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param amount the amount changed
   * @param balanceAfter the balance after this transaction
   * @param source the source of the transaction
   * @param description optional description
   * @return the saved transaction
   */
  public CurrencyTransaction recordTransaction(
      long guildId,
      long userId,
      long amount,
      long balanceAfter,
      CurrencyTransaction.Source source,
      String description) {
    CurrencyTransaction transaction =
        CurrencyTransaction.create(guildId, userId, amount, balanceAfter, source, description);
    CurrencyTransaction saved = transactionRepository.save(transaction);
    LOG.info(
        "Recorded currency transaction: guildId={}, userId={}, amount={}, source={}",
        guildId,
        userId,
        amount,
        source);
    return saved;
  }

  /** Represents a page of transactions. */
  public record TransactionPage(
      List<CurrencyTransaction> transactions,
      int currentPage,
      int totalPages,
      long totalCount,
      int pageSize) {
    /**
     * Checks if there is a next page.
     *
     * @return true if there is a next page
     */
    public boolean hasNextPage() {
      return currentPage < totalPages;
    }

    /**
     * Checks if there is a previous page.
     *
     * @return true if there is a previous page
     */
    public boolean hasPreviousPage() {
      return currentPage > 1;
    }

    /**
     * Checks if this page is empty.
     *
     * @return true if there are no transactions
     */
    public boolean isEmpty() {
      return transactions.isEmpty();
    }

    /**
     * Gets the formatted page indicator text.
     *
     * @return a string like "第 1/5 頁"
     */
    public String formatPageIndicator() {
      return String.format("第 %d/%d 頁（共 %d 筆）", currentPage, totalPages, totalCount);
    }
  }
}
