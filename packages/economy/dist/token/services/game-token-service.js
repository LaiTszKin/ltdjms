import { Ok, Err, DomainError, } from '@ltdjms/shared';
import { TOKEN_CACHE_TTL } from '../../domain/types.js';
/**
 * Service for managing game token accounts with caching.
 * Matches Java GameTokenService behavior.
 */
export class GameTokenService {
    accountRepository;
    eventPublisher;
    cacheService;
    cacheKeyGenerator;
    static TOKEN_TTL_SECONDS = TOKEN_CACHE_TTL;
    constructor(accountRepository, eventPublisher, cacheService, cacheKeyGenerator) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
        this.cacheKeyGenerator = cacheKeyGenerator;
    }
    /**
     * Gets the current token balance for a member.
     * Uses cache (TTL 300s) - cache miss falls through to DB (no auto-create).
     */
    async getBalance(guildId, userId) {
        const cacheKey = this.cacheKeyGenerator.gameTokenKey(guildId, userId);
        const cachedBalance = await this.cacheService.get(cacheKey);
        if (cachedBalance !== null) {
            return cachedBalance;
        }
        const account = await this.accountRepository.findByGuildIdAndUserId(guildId, userId);
        const balance = account?.tokens ?? 0;
        await this.cacheService.put(cacheKey, balance, GameTokenService.TOKEN_TTL_SECONDS);
        return balance;
    }
    /**
     * Adjusts tokens with Result-based error handling.
     */
    async tryAdjustTokens(guildId, userId, amount) {
        if (!Number.isFinite(amount) || amount === 0) {
            return new Err(DomainError.invalidInput(`Invalid adjustment amount: ${amount}`));
        }
        try {
            const current = await this.accountRepository.findOrCreate(guildId, userId);
            const previousTokens = current.tokens;
            const adjustResult = await this.accountRepository.tryAdjustTokens(guildId, userId, amount);
            if (adjustResult.isErr()) {
                return new Err(adjustResult.getError());
            }
            const updated = adjustResult.getValue();
            // Update cache
            const cacheKey = this.cacheKeyGenerator.gameTokenKey(guildId, userId);
            await this.cacheService.put(cacheKey, updated.tokens, GameTokenService.TOKEN_TTL_SECONDS);
            // Publish event
            this.eventPublisher.publish({
                guildId,
                userId,
                newTokens: updated.tokens,
            });
            return new Ok({
                guildId,
                userId,
                previousTokens,
                newTokens: updated.tokens,
                adjustment: amount,
            });
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to adjust tokens for guildId=${guildId}, userId=${userId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Checks if a member has enough tokens.
     */
    async hasEnoughTokens(guildId, userId, requiredTokens) {
        const balance = await this.getBalance(guildId, userId);
        return balance >= requiredTokens;
    }
    /**
     * Deducts tokens using Result-based error handling.
     * @param tokens - positive number of tokens to deduct
     */
    async tryDeductTokens(guildId, userId, tokens) {
        if (tokens <= 0) {
            return new Err(DomainError.invalidInput(`Tokens to deduct must be positive: ${tokens}`));
        }
        const result = await this.accountRepository.tryAdjustTokens(guildId, userId, -tokens);
        if (result.isOk()) {
            const account = result.getValue();
            // Update cache
            const cacheKey = this.cacheKeyGenerator.gameTokenKey(guildId, userId);
            await this.cacheService.put(cacheKey, account.tokens, GameTokenService.TOKEN_TTL_SECONDS);
            // Publish event
            this.eventPublisher.publish({
                guildId,
                userId,
                newTokens: account.tokens,
            });
        }
        return result;
    }
    /**
     * Adjusts tokens (deducts if negative, adds if positive).
     * Deduct path that throws on insufficient - use tryAdjustTokens for Result-based.
     */
    async deductTokens(guildId, userId, tokens) {
        if (tokens <= 0) {
            throw new Error(`Tokens to deduct must be positive: ${tokens}`);
        }
        const updated = await this.accountRepository.adjustTokens(guildId, userId, -tokens);
        const cacheKey = this.cacheKeyGenerator.gameTokenKey(guildId, userId);
        await this.cacheService.put(cacheKey, updated.tokens, GameTokenService.TOKEN_TTL_SECONDS);
        this.eventPublisher.publish({
            guildId,
            userId,
            newTokens: updated.tokens,
        });
        return updated;
    }
    /**
     * Adjusts tokens (throws on error).
     */
    async adjustTokens(guildId, userId, amount) {
        const current = await this.accountRepository.findOrCreate(guildId, userId);
        const previousTokens = current.tokens;
        const updated = await this.accountRepository.adjustTokens(guildId, userId, amount);
        const cacheKey = this.cacheKeyGenerator.gameTokenKey(guildId, userId);
        await this.cacheService.put(cacheKey, updated.tokens, GameTokenService.TOKEN_TTL_SECONDS);
        this.eventPublisher.publish({
            guildId,
            userId,
            newTokens: updated.tokens,
        });
        return {
            guildId,
            userId,
            previousTokens,
            newTokens: updated.tokens,
            adjustment: amount,
        };
    }
}
//# sourceMappingURL=game-token-service.js.map