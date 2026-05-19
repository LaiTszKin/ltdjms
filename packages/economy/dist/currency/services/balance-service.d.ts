import { type Result, DomainError, type CacheService, type CacheKeyGenerator } from '@ltdjms/shared';
import { CurrencyAccountRepository } from '../repositories/currency-account-repo.js';
import { CurrencyConfigRepository } from '../repositories/currency-config-repo.js';
import type { BalanceView } from '../../domain/types.js';
/**
 * Service for retrieving member balances with caching.
 * Matches Java DefaultBalanceService behavior.
 */
export declare class BalanceService {
    private readonly accountRepository;
    private readonly configRepository;
    private readonly cacheService;
    private readonly cacheKeyGenerator;
    private static readonly BALANCE_TTL_SECONDS;
    constructor(accountRepository: CurrencyAccountRepository, configRepository: CurrencyConfigRepository, cacheService: CacheService, cacheKeyGenerator: CacheKeyGenerator);
    /**
     * Gets the balance view for a member in a guild.
     * Uses cache (TTL 300s) - cache miss falls through to DB.
     * Auto-creates account if none exists.
     */
    getBalance(guildId: number, userId: number): Promise<BalanceView>;
    /**
     * Gets the balance view with Result-based error handling.
     */
    tryGetBalance(guildId: number, userId: number): Promise<Result<BalanceView, DomainError>>;
}
