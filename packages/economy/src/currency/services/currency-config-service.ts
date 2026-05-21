import {
  type Result,
  Ok,
  Err,
  okVoid,
  DomainError,
  type DomainEventPublisher,
} from '@ltdjms/shared';
import type { CurrencyConfigChangedEvent } from '../../events/index.js';
import { EmojiValidator } from './emoji-validator.js';
import { CurrencyConfigRepository } from '../repositories/currency-config-repo.js';
import type { GuildCurrencyConfig } from '../../domain/types.js';
import {
  DEFAULT_CURRENCY_NAME,
  DEFAULT_CURRENCY_ICON,
  MAX_CURRENCY_NAME_LENGTH,
  MAX_CURRENCY_ICON_LENGTH,
} from '../../domain/types.js';

/**
 * Service for managing guild currency configuration.
 * Matches Java CurrencyConfigService behavior.
 */
export class CurrencyConfigService {
  constructor(
    private readonly configRepository: CurrencyConfigRepository,
    private readonly eventPublisher: DomainEventPublisher,
    private readonly emojiValidator: EmojiValidator,
  ) {}

  /**
   * Gets the currency configuration for a guild.
   * Returns defaults if no custom configuration exists.
   */
  async getConfig(guildId: number): Promise<GuildCurrencyConfig> {
    const config = await this.configRepository.findByGuildId(guildId);
    return config ?? createDefaultConfig(guildId);
  }

  /**
   * Gets the currency configuration with Result-based error handling.
   */
  async tryGetConfig(
    guildId: number,
  ): Promise<Result<GuildCurrencyConfig, DomainError>> {
    try {
      const config = await this.getConfig(guildId);
      return new Ok(config);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to retrieve currency config for guildId=${guildId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Updates the currency configuration for a guild.
   * Creates a new configuration if one doesn't exist.
   * Validates name length ≤ 50 and icon length ≤ 64.
   * For custom Discord emoji, validates the format matches `<:name:id>` or `<a:name:id>`.
   * Publishes CurrencyConfigChangedEvent on success.
   */
  async updateConfig(
    guildId: number,
    name: string,
    icon: string,
  ): Promise<GuildCurrencyConfig> {
    // Validate inputs
    validateName(name);
    const iconValidation = this.emojiValidator.validate(icon);
    if (iconValidation.isErr()) {
      throw iconValidation.getError();
    }

    // Get existing config or create default
    const current = await this.configRepository.findByGuildId(guildId);
    const base = current ?? createDefaultConfig(guildId);

    const updated: GuildCurrencyConfig = {
      ...base,
      currencyName: name,
      currencyIcon: icon,
    };

    const saved = await this.configRepository.saveOrUpdate(updated);

    // Publish event
    const event: CurrencyConfigChangedEvent = {
      guildId: String(guildId),
      eventType: 'currency_config_changed',
      currencyName: saved.currencyName,
      currencyIcon: saved.currencyIcon,
    };
    this.eventPublisher.publish(event);

    return saved;
  }

  /**
   * Updates the currency configuration with Result-based error handling.
   */
  async tryUpdateConfig(
    guildId: number,
    name: string,
    icon: string,
  ): Promise<Result<GuildCurrencyConfig, DomainError>> {
    const nameValidation = tryValidateName(name);
    if (nameValidation.isErr()) {
      return new Err(nameValidation.getError());
    }

    const iconValidation = this.emojiValidator.validate(icon);
    if (iconValidation.isErr()) {
      return new Err(iconValidation.getError());
    }

    try {
      const saved = await this.updateConfig(guildId, name, icon);
      return new Ok(saved);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to update currency config for guildId=${guildId}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }
}

function createDefaultConfig(guildId: number): GuildCurrencyConfig {
  return {
    guildId,
    currencyName: DEFAULT_CURRENCY_NAME,
    currencyIcon: DEFAULT_CURRENCY_ICON,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

function validateName(name: string): void {
  const result = tryValidateName(name);
  if (result.isErr()) {
    throw result.getError();
  }
}

function tryValidateName(name: string): Result<import('@ltdjms/shared').Unit, DomainError> {
  if (!name || name.trim().length === 0) {
    return new Err(DomainError.invalidInput('Currency name cannot be blank'));
  }
  if (name.length > MAX_CURRENCY_NAME_LENGTH) {
    return new Err(
      DomainError.invalidInput(
        `Currency name cannot exceed ${MAX_CURRENCY_NAME_LENGTH} characters`,
      ),
    );
  }
  return okVoid();
}

