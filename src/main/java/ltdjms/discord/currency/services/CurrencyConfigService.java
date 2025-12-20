package ltdjms.discord.currency.services;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import ltdjms.discord.shared.events.CurrencyConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Service for managing guild currency configuration.
 * Handles creating and updating currency name and icon per guild.
 */
public class CurrencyConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(CurrencyConfigService.class);

    /**
     * Pattern to detect Discord custom emoji format.
     * Matches: {@code <:name:id>} or {@code <a:name:id>}
     * This is a loose pattern to catch anything that looks like a custom emoji,
     * the actual validation is done by EmojiValidator.
     */
    private static final Pattern CUSTOM_EMOJI_PATTERN =
            Pattern.compile("^<a?:[^:]+:[^>]+>$");

    private final GuildCurrencyConfigRepository configRepository;
    private final EmojiValidator emojiValidator;
    private final DomainEventPublisher eventPublisher;

    public CurrencyConfigService(
            GuildCurrencyConfigRepository configRepository,
            EmojiValidator emojiValidator,
            DomainEventPublisher eventPublisher) {
        this.configRepository = configRepository;
        this.emojiValidator = emojiValidator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Gets the currency configuration for a guild.
     * Returns defaults if no custom configuration exists.
     *
     * @param guildId the Discord guild ID
     * @return the currency configuration
     * @deprecated Use {@link #tryGetConfig(long)} for Result-based error handling
     */
    @Deprecated
    public GuildCurrencyConfig getConfig(long guildId) {
        return configRepository.findByGuildId(guildId)
                .orElse(GuildCurrencyConfig.createDefault(guildId));
    }

    /**
     * Gets the currency configuration for a guild using Result-based error handling.
     * Returns defaults if no custom configuration exists.
     *
     * @param guildId the Discord guild ID
     * @return Result containing the currency configuration, or DomainError on failure
     */
    public Result<GuildCurrencyConfig, DomainError> tryGetConfig(long guildId) {
        try {
            GuildCurrencyConfig config = configRepository.findByGuildId(guildId)
                    .orElse(GuildCurrencyConfig.createDefault(guildId));
            return Result.ok(config);
        } catch (RepositoryException e) {
            LOG.error("Failed to get currency config for guildId={}", guildId, e);
            return Result.err(DomainError.persistenceFailure("Failed to retrieve currency configuration", e));
        }
    }

    /**
     * Updates the currency configuration for a guild.
     * Creates a new configuration if one doesn't exist.
     *
     * @param guildId the Discord guild ID
     * @param name    the new currency name (null to keep current)
     * @param icon    the new currency icon (null to keep current)
     * @return the updated configuration
     * @throws IllegalArgumentException if the name or icon is invalid
     * @deprecated Use {@link #tryUpdateConfig(long, String, String)} for Result-based error handling
     */
    @Deprecated
    public GuildCurrencyConfig updateConfig(long guildId, String name, String icon) {
        LOG.debug("Updating currency config for guildId={}, name={}, icon={}", guildId, name, icon);

        // Validate inputs
        if (name != null) {
            validateName(name);
        }
        if (icon != null) {
            validateIcon(icon);
        }

        // Get existing config or create default
        GuildCurrencyConfig current = configRepository.findByGuildId(guildId)
                .orElse(GuildCurrencyConfig.createDefault(guildId));

        // Apply updates
        GuildCurrencyConfig updated = current.withUpdates(name, icon);

        // Save (upsert)
        GuildCurrencyConfig saved = configRepository.saveOrUpdate(updated);

        LOG.info("Updated currency config: guildId={}, name={}, icon={}",
                guildId, saved.currencyName(), saved.currencyIcon());

        return saved;
    }

    /**
     * Updates the currency configuration for a guild using Result-based error handling.
     * Creates a new configuration if one doesn't exist.
     *
     * @param guildId the Discord guild ID
     * @param name    the new currency name (null to keep current)
     * @param icon    the new currency icon (null to keep current)
     * @return Result containing the updated configuration, or DomainError on failure
     */
    public Result<GuildCurrencyConfig, DomainError> tryUpdateConfig(long guildId, String name, String icon) {
        LOG.debug("Updating currency config for guildId={}, name={}, icon={}", guildId, name, icon);

        // Validate inputs
        if (name != null) {
            Result<Unit, DomainError> nameValidation = tryValidateName(name);
            if (nameValidation.isErr()) {
                return Result.err(nameValidation.getError());
            }
        }
        if (icon != null) {
            Result<Unit, DomainError> iconValidation = tryValidateIcon(icon);
            if (iconValidation.isErr()) {
                return Result.err(iconValidation.getError());
            }
        }

        try {
            // Get existing config or create default
            GuildCurrencyConfig current = configRepository.findByGuildId(guildId)
                    .orElse(GuildCurrencyConfig.createDefault(guildId));

            // Apply updates
            GuildCurrencyConfig updated = current.withUpdates(name, icon);

            // Save (upsert)
            GuildCurrencyConfig saved = configRepository.saveOrUpdate(updated);

            LOG.info("Updated currency config: guildId={}, name={}, icon={}",
                    guildId, saved.currencyName(), saved.currencyIcon());

            // Publish event after successful save
            eventPublisher.publish(new CurrencyConfigChangedEvent(
                    guildId, saved.currencyName(), saved.currencyIcon()));

            return Result.ok(saved);
        } catch (RepositoryException e) {
            LOG.error("Failed to update currency config for guildId={}", guildId, e);
            return Result.err(DomainError.persistenceFailure("Failed to update currency configuration", e));
        }
    }

    private void validateName(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Currency name cannot be blank");
        }
        if (name.length() > GuildCurrencyConfig.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Currency name cannot exceed " + GuildCurrencyConfig.MAX_NAME_LENGTH + " characters");
        }
    }

    private Result<Unit, DomainError> tryValidateName(String name) {
        if (name.isBlank()) {
            return Result.err(DomainError.invalidInput("Currency name cannot be blank"));
        }
        if (name.length() > GuildCurrencyConfig.MAX_NAME_LENGTH) {
            return Result.err(DomainError.invalidInput(
                    "Currency name cannot exceed " + GuildCurrencyConfig.MAX_NAME_LENGTH + " characters"));
        }
        return Result.okVoid();
    }

    private void validateIcon(String icon) {
        if (icon.isBlank()) {
            throw new IllegalArgumentException("Currency icon cannot be blank");
        }
        if (icon.length() > GuildCurrencyConfig.MAX_ICON_LENGTH) {
            throw new IllegalArgumentException(
                    "Currency icon cannot exceed " + GuildCurrencyConfig.MAX_ICON_LENGTH + " characters");
        }

        // Check if it looks like a Discord custom emoji format
        if (looksLikeCustomEmoji(icon)) {
            // Validate using the emoji validator
            if (!emojiValidator.isValidCustomEmoji(icon)) {
                throw new IllegalArgumentException(
                        "Invalid Discord custom emoji: '" + icon + "'. " +
                        "Please ensure the emoji exists and is accessible.");
            }
        }
    }

    private Result<Unit, DomainError> tryValidateIcon(String icon) {
        if (icon.isBlank()) {
            return Result.err(DomainError.invalidInput("Currency icon cannot be blank"));
        }
        if (icon.length() > GuildCurrencyConfig.MAX_ICON_LENGTH) {
            return Result.err(DomainError.invalidInput(
                    "Currency icon cannot exceed " + GuildCurrencyConfig.MAX_ICON_LENGTH + " characters"));
        }

        // Check if it looks like a Discord custom emoji format
        if (looksLikeCustomEmoji(icon)) {
            // Validate using the emoji validator
            if (!emojiValidator.isValidCustomEmoji(icon)) {
                return Result.err(DomainError.invalidInput(
                        "Invalid Discord custom emoji: '" + icon + "'. " +
                        "Please ensure the emoji exists and is accessible."));
            }
        }
        return Result.okVoid();
    }

    /**
     * Checks if the icon string looks like a Discord custom emoji format.
     * This is a quick check to determine if we need to validate with JDA.
     *
     * @param icon the icon string to check
     * @return true if it looks like a custom emoji format
     */
    private boolean looksLikeCustomEmoji(String icon) {
        return CUSTOM_EMOJI_PATTERN.matcher(icon).matches();
    }
}
