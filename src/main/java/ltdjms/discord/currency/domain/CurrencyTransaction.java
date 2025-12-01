package ltdjms.discord.currency.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single currency transaction in a user's account history.
 * Each transaction records the amount changed, the resulting balance,
 * and the source of the transaction.
 *
 * @param id           the unique transaction ID (null for new unsaved transactions)
 * @param guildId      the Discord guild ID
 * @param userId       the Discord user ID
 * @param amount       the amount changed (positive for credit, negative for debit)
 * @param balanceAfter the balance after this transaction
 * @param source       the source of the transaction
 * @param description  optional description for the transaction
 * @param createdAt    the timestamp when the transaction was created
 */
public record CurrencyTransaction(
        Long id,
        long guildId,
        long userId,
        long amount,
        long balanceAfter,
        Source source,
        String description,
        Instant createdAt
) {
    /**
     * Sources of currency transactions.
     */
    public enum Source {
        /** Currency added/adjusted by an administrator via /adjust-balance or admin panel */
        ADMIN_ADJUSTMENT("管理員調整");

        private final String displayName;

        Source(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Gets the zh-TW display name for this source.
         *
         * @return the localized display name
         */
        public String getDisplayName() {
            return displayName;
        }
    }

    public CurrencyTransaction {
        Objects.requireNonNull(source, "source must not be null");
        if (balanceAfter < 0) {
            throw new IllegalArgumentException("balanceAfter cannot be negative: " + balanceAfter);
        }
    }

    /**
     * Creates a new transaction record (without an ID, to be assigned by the database).
     *
     * @param guildId      the Discord guild ID
     * @param userId       the Discord user ID
     * @param amount       the amount changed
     * @param balanceAfter the balance after this transaction
     * @param source       the source of the transaction
     * @param description  optional description
     * @return a new transaction record
     */
    public static CurrencyTransaction create(
            long guildId,
            long userId,
            long amount,
            long balanceAfter,
            Source source,
            String description
    ) {
        return new CurrencyTransaction(
                null,
                guildId,
                userId,
                amount,
                balanceAfter,
                source,
                description,
                Instant.now()
        );
    }

    /**
     * Formats this transaction for display in Discord.
     *
     * @return a formatted string showing the transaction details
     */
    public String formatForDisplay() {
        String amountStr = amount >= 0
                ? String.format("+%,d", amount)
                : String.format("%,d", amount);
        String descStr = description != null && !description.isBlank()
                ? " - " + description
                : "";
        return String.format(
                "%s | %s | 餘額: %,d%s",
                source.getDisplayName(),
                amountStr,
                balanceAfter,
                descStr
        );
    }

    /**
     * Gets a shortened timestamp string for display.
     *
     * @return the timestamp in a short format
     */
    public String getShortTimestamp() {
        return String.format("<t:%d:R>", createdAt.getEpochSecond());
    }
}
