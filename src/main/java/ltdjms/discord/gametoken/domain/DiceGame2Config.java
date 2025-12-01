package ltdjms.discord.gametoken.domain;

import java.time.Instant;

/**
 * Represents the dice-game-2 configuration for a specific Discord guild.
 * Each guild can have exactly one configuration specifying the token cost per play.
 */
public record DiceGame2Config(
        long guildId,
        long tokensPerPlay,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Default tokens required per play when no custom configuration exists.
     */
    public static final long DEFAULT_TOKENS_PER_PLAY = 1L;

    public DiceGame2Config {
        if (tokensPerPlay < 0) {
            throw new IllegalArgumentException("tokensPerPlay cannot be negative: " + tokensPerPlay);
        }
    }

    /**
     * Creates a default configuration for a guild with default tokens per play.
     *
     * @param guildId the Discord guild ID
     * @return a new configuration with default values
     */
    public static DiceGame2Config createDefault(long guildId) {
        Instant now = Instant.now();
        return new DiceGame2Config(guildId, DEFAULT_TOKENS_PER_PLAY, now, now);
    }

    /**
     * Creates a new configuration with updated tokens per play.
     *
     * @param newTokensPerPlay the new tokens per play value
     * @return a new configuration with the updated value
     */
    public DiceGame2Config withTokensPerPlay(long newTokensPerPlay) {
        return new DiceGame2Config(
                this.guildId,
                newTokensPerPlay,
                this.createdAt,
                Instant.now()
        );
    }
}
