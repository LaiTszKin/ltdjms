import { type Result, DomainError, type CacheService, type CacheKeyGenerator, type DomainEventPublisher } from '@ltdjms/shared';
import { CurrencyAccountRepository } from '../repositories/currency-account-repo.js';
import { CurrencyConfigRepository } from '../repositories/currency-config-repo.js';
import { CurrencyTransactionService } from './currency-tx-service.js';
import type { BalanceAdjustmentResult } from '../../domain/types.js';
import { CurrencyTransactionSource } from '../../domain/types.js';
/**
 * Service for adjusting member currency balances with validation.
 * Matches Java BalanceAdjustmentService behavior.
 */
export declare class BalanceAdjustmentService {
    private readonly accountRepository;
    private readonly configRepository;
    private readonly transactionService;
    private readonly eventPublisher;
    private readonly cacheService;
    private readonly cacheKeyGenerator;
    private static readonly BALANCE_TTL_SECONDS;
    constructor(accountRepository: CurrencyAccountRepository, configRepository: CurrencyConfigRepository, transactionService: CurrencyTransactionService, eventPublisher: DomainEventPublisher, cacheService: CacheService, cacheKeyGenerator: CacheKeyGenerator);
    /**
     * Adjusts a member's balance by the specified amount with Result-based error handling.
     * Validates overflow (via safe integer check), applies adjustment, records transaction,
     * publishes event, and updates cache.
     */
    tryAdjustBalance(guildId: number, userId: number, amount: number, source?: CurrencyTransactionSource, description?: string | null): Promise<Result<BalanceAdjustmentResult, DomainError>>;
    /**
     * Adjusts a member's balance to a specific target value.
     */
    tryAdjustBalanceTo(guildId: number, userId: number, targetBalance: number, source?: CurrencyTransactionSource, description?: string | null): Promise<Result<BalanceAdjustmentResult, DomainError>>;
}
