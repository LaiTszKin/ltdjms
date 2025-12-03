package ltdjms.discord.currency.services;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.NegativeBalanceException;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.BalanceChangedEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for adjusting member currency balances.
 * Handles atomic balance changes with validation for non-negative balances.
 */
public class BalanceAdjustmentService {

    private static final Logger LOG = LoggerFactory.getLogger(BalanceAdjustmentService.class);

    private final MemberCurrencyAccountRepository accountRepository;
    private final GuildCurrencyConfigRepository configRepository;
    private final CurrencyTransactionService transactionService;
    private final DomainEventPublisher eventPublisher;

    public BalanceAdjustmentService(
            MemberCurrencyAccountRepository accountRepository,
            GuildCurrencyConfigRepository configRepository,
            CurrencyTransactionService transactionService,
            DomainEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.configRepository = configRepository;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Adjusts a member's balance by the specified amount.
     *
     * @param guildId the Discord guild ID
     * @param userId  the Discord user ID
     * @param amount  the amount to adjust (positive for credit, negative for debit)
     * @return the adjustment result
     * @throws IllegalArgumentException   if the amount exceeds the per-command maximum
     * @throws NegativeBalanceException   if the adjustment would result in a negative balance
     * @deprecated Use {@link #tryAdjustBalance(long, long, long)} for Result-based error handling
     */
    @Deprecated
    public BalanceAdjustmentResult adjustBalance(long guildId, long userId, long amount) {
        LOG.debug("Adjusting balance for guildId={}, userId={}, amount={}", guildId, userId, amount);

        // Validate amount
        if (!MemberCurrencyAccount.isValidAdjustmentAmount(amount)) {
            throw new IllegalArgumentException(
                    "Amount exceeds maximum: |" + amount + "| > " + MemberCurrencyAccount.MAX_ADJUSTMENT_AMOUNT);
        }

        // Get current balance
        MemberCurrencyAccount current = accountRepository.findOrCreate(guildId, userId);
        long previousBalance = current.balance();

        // Apply adjustment
        MemberCurrencyAccount updated = accountRepository.adjustBalance(guildId, userId, amount);

        // Publish event
        eventPublisher.publish(new BalanceChangedEvent(guildId, userId, updated.balance()));

        // Get currency config for display
        GuildCurrencyConfig config = configRepository.findByGuildId(guildId)
                .orElse(GuildCurrencyConfig.createDefault(guildId));

        BalanceAdjustmentResult result = new BalanceAdjustmentResult(
                guildId,
                userId,
                previousBalance,
                updated.balance(),
                amount,
                config.currencyName(),
                config.currencyIcon()
        );

        LOG.info("Balance adjusted: guildId={}, userId={}, previous={}, new={}, adjustment={}",
                guildId, userId, previousBalance, updated.balance(), amount);

        return result;
    }

    /**
     * Adjusts a member's balance by the specified amount using Result-based error handling.
     *
     * @param guildId the Discord guild ID
     * @param userId  the Discord user ID
     * @param amount  the amount to adjust (positive for credit, negative for debit)
     * @return Result containing BalanceAdjustmentResult on success, or DomainError on failure
     */
    public Result<BalanceAdjustmentResult, DomainError> tryAdjustBalance(long guildId, long userId, long amount) {
        LOG.debug("Adjusting balance for guildId={}, userId={}, amount={}", guildId, userId, amount);

        // Validate amount
        if (!MemberCurrencyAccount.isValidAdjustmentAmount(amount)) {
            return Result.err(DomainError.invalidInput(
                    "Amount exceeds maximum: |" + amount + "| > " + MemberCurrencyAccount.MAX_ADJUSTMENT_AMOUNT));
        }

        // Get current balance
        MemberCurrencyAccount current = accountRepository.findOrCreate(guildId, userId);
        long previousBalance = current.balance();

        // Apply adjustment using Result-based API
        Result<MemberCurrencyAccount, DomainError> adjustResult =
                accountRepository.tryAdjustBalance(guildId, userId, amount);

        if (adjustResult.isErr()) {
            return Result.err(adjustResult.getError());
        }

        MemberCurrencyAccount updated = adjustResult.getValue();
        
        // Publish event
        eventPublisher.publish(new BalanceChangedEvent(guildId, userId, updated.balance()));

        // Get currency config for display
        GuildCurrencyConfig config = configRepository.findByGuildId(guildId)
                .orElse(GuildCurrencyConfig.createDefault(guildId));

        BalanceAdjustmentResult result = new BalanceAdjustmentResult(
                guildId,
                userId,
                previousBalance,
                updated.balance(),
                amount,
                config.currencyName(),
                config.currencyIcon()
        );

        // Record transaction after successful adjustment
        transactionService.recordTransaction(
                guildId,
                userId,
                amount,
                updated.balance(),
                CurrencyTransaction.Source.ADMIN_ADJUSTMENT,
                null
        );

        LOG.info("Balance adjusted: guildId={}, userId={}, previous={}, new={}, adjustment={}",
                guildId, userId, previousBalance, updated.balance(), amount);

        return Result.ok(result);
    }

    /**
     * Adjusts a member's balance to a specific target value using Result-based error handling.
     *
     * @param guildId       the Discord guild ID
     * @param userId        the Discord user ID
     * @param targetBalance the target balance to set (must be non-negative)
     * @return Result containing BalanceAdjustmentResult on success, or DomainError on failure
     */
    public Result<BalanceAdjustmentResult, DomainError> tryAdjustBalanceTo(long guildId, long userId, long targetBalance) {
        LOG.debug("Adjusting balance to target for guildId={}, userId={}, targetBalance={}",
                guildId, userId, targetBalance);

        // Validate target balance
        if (targetBalance < 0) {
            return Result.err(DomainError.invalidInput("Target balance cannot be negative: " + targetBalance));
        }

        // Get current balance
        MemberCurrencyAccount current = accountRepository.findOrCreate(guildId, userId);
        long previousBalance = current.balance();

        // Calculate delta with overflow protection
        long delta;
        try {
            delta = Math.subtractExact(targetBalance, previousBalance);
        } catch (ArithmeticException e) {
            return Result.err(DomainError.invalidInput(
                    "Adjustment would cause overflow: target=" + targetBalance + ", current=" + previousBalance));
        }

        // Validate delta
        if (!MemberCurrencyAccount.isValidAdjustmentAmount(delta)) {
            return Result.err(DomainError.invalidInput(
                    "Amount exceeds maximum: |" + delta + "| > " + MemberCurrencyAccount.MAX_ADJUSTMENT_AMOUNT));
        }

        // Apply adjustment using Result-based API
        Result<MemberCurrencyAccount, DomainError> adjustResult =
                accountRepository.tryAdjustBalance(guildId, userId, delta);

        if (adjustResult.isErr()) {
            return Result.err(adjustResult.getError());
        }

        MemberCurrencyAccount updated = adjustResult.getValue();

        // Publish event
        eventPublisher.publish(new BalanceChangedEvent(guildId, userId, updated.balance()));

        // Get currency config for display
        GuildCurrencyConfig config = configRepository.findByGuildId(guildId)
                .orElse(GuildCurrencyConfig.createDefault(guildId));

        BalanceAdjustmentResult result = new BalanceAdjustmentResult(
                guildId,
                userId,
                previousBalance,
                updated.balance(),
                delta,
                config.currencyName(),
                config.currencyIcon()
        );

        // Record transaction after successful adjustment
        transactionService.recordTransaction(
                guildId,
                userId,
                delta,
                updated.balance(),
                CurrencyTransaction.Source.ADMIN_ADJUSTMENT,
                null
        );

        LOG.info("Balance adjusted to target: guildId={}, userId={}, previous={}, new={}, adjustment={}",
                guildId, userId, previousBalance, updated.balance(), delta);

        return Result.ok(result);
    }

    /**
     * Adjusts a member's balance to a specific target value.
     *
     * @param guildId       the Discord guild ID
     * @param userId        the Discord user ID
     * @param targetBalance the target balance to set (must be non-negative)
     * @return the adjustment result
     * @throws IllegalArgumentException if the target balance is negative or the delta exceeds maximum
     * @deprecated Use {@link #tryAdjustBalanceTo(long, long, long)} for Result-based error handling
     */
    @Deprecated
    public BalanceAdjustmentResult adjustBalanceTo(long guildId, long userId, long targetBalance) {
        LOG.debug("Adjusting balance to target for guildId={}, userId={}, targetBalance={}",
                guildId, userId, targetBalance);

        // Validate target balance
        if (targetBalance < 0) {
            throw new IllegalArgumentException("Target balance cannot be negative: " + targetBalance);
        }

        // Get current balance
        MemberCurrencyAccount current = accountRepository.findOrCreate(guildId, userId);
        long previousBalance = current.balance();

        // Calculate delta with overflow protection
        long delta;
        try {
            delta = Math.subtractExact(targetBalance, previousBalance);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Adjustment would cause overflow: target=" + targetBalance + ", current=" + previousBalance);
        }

        // Validate delta
        if (!MemberCurrencyAccount.isValidAdjustmentAmount(delta)) {
            throw new IllegalArgumentException(
                    "Amount exceeds maximum: |" + delta + "| > " + MemberCurrencyAccount.MAX_ADJUSTMENT_AMOUNT);
        }

        // Apply adjustment
        MemberCurrencyAccount updated = accountRepository.adjustBalance(guildId, userId, delta);

        // Publish event
        eventPublisher.publish(new BalanceChangedEvent(guildId, userId, updated.balance()));

        // Get currency config for display
        GuildCurrencyConfig config = configRepository.findByGuildId(guildId)
                .orElse(GuildCurrencyConfig.createDefault(guildId));

        BalanceAdjustmentResult result = new BalanceAdjustmentResult(
                guildId,
                userId,
                previousBalance,
                updated.balance(),
                delta,
                config.currencyName(),
                config.currencyIcon()
        );

        LOG.info("Balance adjusted to target: guildId={}, userId={}, previous={}, new={}, adjustment={}",
                guildId, userId, previousBalance, updated.balance(), delta);

        return result;
    }

    /**
     * Result of a balance adjustment operation.
     */
    public record BalanceAdjustmentResult(
            long guildId,
            long userId,
            long previousBalance,
            long newBalance,
            long adjustment,
            String currencyName,
            String currencyIcon
    ) {
        /**
         * Formats the result as a Discord message.
         */
        public String formatMessage(String targetUserMention) {
            String action = adjustment >= 0 ? "Added" : "Removed";
            long displayAmount = Math.abs(adjustment);
            return String.format("%s %s %,d %s to/from %s\nNew balance: %s %,d %s",
                    action, currencyIcon, displayAmount, currencyName, targetUserMention,
                    currencyIcon, newBalance, currencyName);
        }
    }
}
