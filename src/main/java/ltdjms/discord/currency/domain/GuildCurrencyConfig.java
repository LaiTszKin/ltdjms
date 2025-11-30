package ltdjms.discord.currency.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the currency configuration for a specific Discord guild.
 * Each guild can have exactly one currency configuration with a custom name and icon.
 */
public record GuildCurrencyConfig(
        long guildId,
        String currencyName,
        String currencyIcon,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Default currency name used when no custom configuration exists.
     */
    public static final String DEFAULT_NAME = "Coins";

    /**
     * Default currency icon used when no custom configuration exists.
     */
    public static final String DEFAULT_ICON = "🪙";

    /**
     * Maximum length for the currency name.
     */
    public static final int MAX_NAME_LENGTH = 50;

    /**
     * Maximum length for the currency icon/label (supports multi-codepoint emoji,
     * Discord custom emoji markup, and short text).
     */
    public static final int MAX_ICON_LENGTH = 64;

    public GuildCurrencyConfig {
        Objects.requireNonNull(currencyName, "currencyName must not be null");
        Objects.requireNonNull(currencyIcon, "currencyIcon must not be null");

        if (currencyName.isBlank()) {
            throw new IllegalArgumentException("currencyName must not be blank");
        }
        if (currencyName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("currencyName must not exceed " + MAX_NAME_LENGTH + " characters");
        }
        if (currencyIcon.isBlank()) {
            throw new IllegalArgumentException("currencyIcon must not be blank");
        }
        if (currencyIcon.length() > MAX_ICON_LENGTH) {
            throw new IllegalArgumentException("currencyIcon must not exceed " + MAX_ICON_LENGTH + " characters");
        }
    }

    /**
     * Creates a default configuration for a guild with default name and icon.
     *
     * @param guildId the Discord guild ID
     * @return a new configuration with default values
     */
    public static GuildCurrencyConfig createDefault(long guildId) {
        Instant now = Instant.now();
        return new GuildCurrencyConfig(guildId, DEFAULT_NAME, DEFAULT_ICON, now, now);
    }

    /**
     * Creates a new configuration with updated values, preserving the original creation timestamp.
     *
     * @param newName the new currency name (null to keep current)
     * @param newIcon the new currency icon (null to keep current)
     * @return a new configuration with updated values
     */
    public GuildCurrencyConfig withUpdates(String newName, String newIcon) {
        return new GuildCurrencyConfig(
                this.guildId,
                newName != null ? newName : this.currencyName,
                newIcon != null ? newIcon : this.currencyIcon,
                this.createdAt,
                Instant.now()
        );
    }
}
