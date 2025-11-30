package ltdjms.discord.currency.services;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
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

    public CurrencyConfigService(GuildCurrencyConfigRepository configRepository, EmojiValidator emojiValidator) {
        this.configRepository = configRepository;
        this.emojiValidator = emojiValidator;
    }

    /**
     * Gets the currency configuration for a guild.
     * Returns defaults if no custom configuration exists.
     *
     * @param guildId the Discord guild ID
     * @return the currency configuration
     */
    public GuildCurrencyConfig getConfig(long guildId) {
        return configRepository.findByGuildId(guildId)
                .orElse(GuildCurrencyConfig.createDefault(guildId));
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
     */
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

    private void validateName(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Currency name cannot be blank");
        }
        if (name.length() > GuildCurrencyConfig.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Currency name cannot exceed " + GuildCurrencyConfig.MAX_NAME_LENGTH + " characters");
        }
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
