import { type Result, DomainError, type DomainEventPublisher } from '@ltdjms/shared';
import { CurrencyConfigRepository } from '../repositories/currency-config-repo.js';
import type { GuildCurrencyConfig } from '../../domain/types.js';
/**
 * Service for managing guild currency configuration.
 * Matches Java CurrencyConfigService behavior.
 */
export declare class CurrencyConfigService {
    private readonly configRepository;
    private readonly eventPublisher;
    constructor(configRepository: CurrencyConfigRepository, eventPublisher: DomainEventPublisher);
    /**
     * Gets the currency configuration for a guild.
     * Returns defaults if no custom configuration exists.
     */
    getConfig(guildId: number): Promise<GuildCurrencyConfig>;
    /**
     * Gets the currency configuration with Result-based error handling.
     */
    tryGetConfig(guildId: number): Promise<Result<GuildCurrencyConfig, DomainError>>;
    /**
     * Updates the currency configuration for a guild.
     * Creates a new configuration if one doesn't exist.
     * Validates name length ≤ 50 and icon length ≤ 64.
     * For custom Discord emoji, validates the format matches `<:name:id>` or `<a:name:id>`.
     * Publishes CurrencyConfigChangedEvent on success.
     */
    updateConfig(guildId: number, name: string, icon: string): Promise<GuildCurrencyConfig>;
    /**
     * Updates the currency configuration with Result-based error handling.
     */
    tryUpdateConfig(guildId: number, name: string, icon: string): Promise<Result<GuildCurrencyConfig, DomainError>>;
}
