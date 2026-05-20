import { Ok, Err, okVoid, DomainError, } from '@ltdjms/shared';
import { DEFAULT_CURRENCY_NAME, DEFAULT_CURRENCY_ICON, MAX_CURRENCY_NAME_LENGTH, MAX_CURRENCY_ICON_LENGTH, } from '../../domain/types.js';
/**
 * Pattern to detect Discord custom emoji format.
 * Matches `<:name:id>` or `<a:name:id>`.
 */
const CUSTOM_EMOJI_PATTERN = /^<a?:[^:]+:[^>]+>$/;
/**
 * Service for managing guild currency configuration.
 * Matches Java CurrencyConfigService behavior.
 */
export class CurrencyConfigService {
    configRepository;
    eventPublisher;
    constructor(configRepository, eventPublisher) {
        this.configRepository = configRepository;
        this.eventPublisher = eventPublisher;
    }
    /**
     * Gets the currency configuration for a guild.
     * Returns defaults if no custom configuration exists.
     */
    async getConfig(guildId) {
        const config = await this.configRepository.findByGuildId(guildId);
        return config ?? createDefaultConfig(guildId);
    }
    /**
     * Gets the currency configuration with Result-based error handling.
     */
    async tryGetConfig(guildId) {
        try {
            const config = await this.getConfig(guildId);
            return new Ok(config);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to retrieve currency config for guildId=${guildId}`, err instanceof Error ? err : undefined));
        }
    }
    /**
     * Updates the currency configuration for a guild.
     * Creates a new configuration if one doesn't exist.
     * Validates name length ≤ 50 and icon length ≤ 64.
     * For custom Discord emoji, validates the format matches `<:name:id>` or `<a:name:id>`.
     * Publishes CurrencyConfigChangedEvent on success.
     */
    async updateConfig(guildId, name, icon) {
        // Validate inputs
        validateName(name);
        validateIcon(icon);
        // Get existing config or create default
        const current = await this.configRepository.findByGuildId(guildId);
        const base = current ?? createDefaultConfig(guildId);
        const updated = {
            ...base,
            currencyName: name,
            currencyIcon: icon,
        };
        const saved = await this.configRepository.saveOrUpdate(updated);
        // Publish event
        const event = {
            guildId,
            currencyName: saved.currencyName,
            currencyIcon: saved.currencyIcon,
        };
        this.eventPublisher.publish(event);
        return saved;
    }
    /**
     * Updates the currency configuration with Result-based error handling.
     */
    async tryUpdateConfig(guildId, name, icon) {
        const nameValidation = tryValidateName(name);
        if (nameValidation.isErr()) {
            return new Err(nameValidation.getError());
        }
        const iconValidation = tryValidateIcon(icon);
        if (iconValidation.isErr()) {
            return new Err(iconValidation.getError());
        }
        try {
            const saved = await this.updateConfig(guildId, name, icon);
            return new Ok(saved);
        }
        catch (err) {
            return new Err(DomainError.persistenceFailure(`Failed to update currency config for guildId=${guildId}`, err instanceof Error ? err : undefined));
        }
    }
}
function createDefaultConfig(guildId) {
    return {
        guildId,
        currencyName: DEFAULT_CURRENCY_NAME,
        currencyIcon: DEFAULT_CURRENCY_ICON,
        createdAt: new Date(),
        updatedAt: new Date(),
    };
}
function validateName(name) {
    const result = tryValidateName(name);
    if (result.isErr()) {
        throw new Error(result.getError().message);
    }
}
function tryValidateName(name) {
    if (!name || name.trim().length === 0) {
        return new Err(DomainError.invalidInput('Currency name cannot be blank'));
    }
    if (name.length > MAX_CURRENCY_NAME_LENGTH) {
        return new Err(DomainError.invalidInput(`Currency name cannot exceed ${MAX_CURRENCY_NAME_LENGTH} characters`));
    }
    return okVoid();
}
function validateIcon(icon) {
    const result = tryValidateIcon(icon);
    if (result.isErr()) {
        throw new Error(result.getError().message);
    }
}
function tryValidateIcon(icon) {
    if (!icon || icon.trim().length === 0) {
        return new Err(DomainError.invalidInput('Currency icon cannot be blank'));
    }
    if (icon.length > MAX_CURRENCY_ICON_LENGTH) {
        return new Err(DomainError.invalidInput(`Currency icon cannot exceed ${MAX_CURRENCY_ICON_LENGTH} characters`));
    }
    if (looksLikeCustomEmoji(icon)) {
        if (!CUSTOM_EMOJI_PATTERN.test(icon)) {
            return new Err(DomainError.invalidInput(`Invalid Discord custom emoji: '${icon}'. Please ensure the emoji exists and is accessible.`));
        }
    }
    return okVoid();
}
function looksLikeCustomEmoji(icon) {
    return /^<a?:.+:.+>$/.test(icon);
}
//# sourceMappingURL=currency-config-service.js.map