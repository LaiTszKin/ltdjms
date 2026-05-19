import { Ok, Err, DomainError, } from '@ltdjms/shared';
import { CurrencyTransactionSource, DEFAULT_CURRENCY_NAME, DEFAULT_CURRENCY_ICON, BALANCE_CACHE_TTL } from '../../domain/types.js';
/**
 * Service for adjusting member currency balances with validation.
 * Matches Java BalanceAdjustmentService behavior.
 */
export class BalanceAdjustmentService {
    accountRepository;
    configRepository;
    transactionService;
    eventPublisher;
    cacheService;
    cacheKeyGenerator;
    static BALANCE_TTL_SECONDS = BALANCE_CACHE_TTL;
    constructor(accountRepository, configRepository, transactionService, eventPublisher, cacheService, cacheKeyGenerator) {
        this.accountRepository = accountRepository;
        this.configRepository = configRepository;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
        this.cacheKeyGenerator = cacheKeyGenerator;
    }
    /**
     * Adjusts a member's balance by the specified amount with Result-based error handling.
     * Validates overflow (via safe integer check), applies adjustment, records transaction,
     * publishes event, and updates cache.
     */
    async tryAdjustBalance(guildId, userId, amount, source = CurrencyTransactionSource.ADMIN_ADJUSTMENT, description = null) {
        if (amount === 0 || !Number.isFinite(amount)) {
            return new Err(DomainError.invalidInput(`Invalid adjustment amount: ${amount}`));
        }
        // Overflow check using safe integer boundaries
        if (amount > Number.MAX_SAFE_INTEGER || amount < -Number.MAX_SAFE_INTEGER) {
            return new Err(DomainError.invalidInput(`Amount exceeds maximum: |${amount}| > ${Number.MAX_SAFE_INTEGER}`));
        }
        try {
            const current = await this.accountRepository.findOrCreate(guildId, userId);
            const previousBalance = current.balance;
            const adjustResult = await this.accountRepository.tryAdjustBalance(guildId, userId, amount);
            if (adjustResult.isErr()) {
                return new Err(adjustResult.getError());
            }
            const updated = adjustResult.getValue();
            // Update cache
            const cacheKey = this.cacheKeyGenerator.balanceKey(guildId, userId);
            await this.cacheService.put(cacheKey, updated.balance, BalanceAdjustmentService.BALANCE_TTL_SECONDS);
            // Publish event
            this.eventPublisher.publish({
                guildId,
                userId,
                newBalance: updated.balance,
            });
            // Record transaction
            await this.transactionService.recordTransaction(guildId, userId, amount, updated.balance, source, description);
            const config = await this.configRepository.findByGuildId(guildId);
            return new Ok({
                guildId,
                userId,
                previousBalance,
                newBalance: updated.balance,
                adjustment: amount,
                currencyName: config?.currencyName ?? DEFAULT_CURRENCY_NAME,
                currencyIcon: config?.currencyIcon ?? DEFAULT_CURRENCY_ICON,
            });
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to adjust balance for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Adjusts a member's balance to a specific target value.
     */
    async tryAdjustBalanceTo(guildId, userId, targetBalance, source = CurrencyTransactionSource.ADMIN_ADJUSTMENT, description = null) {
        if (targetBalance < 0) {
            return new Err(DomainError.invalidInput(`Target balance cannot be negative: ${targetBalance}`));
        }
        try {
            const current = await this.accountRepository.findOrCreate(guildId, userId);
            const previousBalance = current.balance;
            const delta = targetBalance - previousBalance;
            if (!Number.isFinite(delta)) {
                return new Err(DomainError.invalidInput(`Adjustment would cause overflow: target=${targetBalance}, current=${previousBalance}`));
            }
            const adjustResult = await this.accountRepository.tryAdjustBalance(guildId, userId, delta);
            if (adjustResult.isErr()) {
                return new Err(adjustResult.getError());
            }
            const updated = adjustResult.getValue();
            // Update cache
            const cacheKey = this.cacheKeyGenerator.balanceKey(guildId, userId);
            await this.cacheService.put(cacheKey, updated.balance, BalanceAdjustmentService.BALANCE_TTL_SECONDS);
            // Publish event
            this.eventPublisher.publish({
                guildId,
                userId,
                newBalance: updated.balance,
            });
            // Record transaction
            await this.transactionService.recordTransaction(guildId, userId, delta, updated.balance, source, description);
            const config = await this.configRepository.findByGuildId(guildId);
            return new Ok({
                guildId,
                userId,
                previousBalance,
                newBalance: updated.balance,
                adjustment: delta,
                currencyName: config?.currencyName ?? DEFAULT_CURRENCY_NAME,
                currencyIcon: config?.currencyIcon ?? DEFAULT_CURRENCY_ICON,
            });
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to adjust balance to target for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
}
//# sourceMappingURL=balance-adjustment-service.js.map