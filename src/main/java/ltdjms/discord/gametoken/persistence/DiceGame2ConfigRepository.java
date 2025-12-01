package ltdjms.discord.gametoken.persistence;

import ltdjms.discord.gametoken.domain.DiceGame2Config;

import java.util.Optional;

/**
 * Repository interface for dice-game-2 configuration.
 */
public interface DiceGame2ConfigRepository {

    Optional<DiceGame2Config> findByGuildId(long guildId);

    DiceGame2Config save(DiceGame2Config config);

    DiceGame2Config findOrCreateDefault(long guildId);

    /**
     * Updates the token range for a guild's dice-game-2 configuration.
     *
     * @param guildId   the guild ID
     * @param minTokens the minimum tokens per play
     * @param maxTokens the maximum tokens per play
     * @return the updated configuration
     */
    DiceGame2Config updateTokensPerPlayRange(long guildId, long minTokens, long maxTokens);

    /**
     * Updates the multipliers for a guild's dice-game-2 configuration.
     *
     * @param guildId            the guild ID
     * @param straightMultiplier the new straight multiplier
     * @param baseMultiplier     the new base multiplier
     * @return the updated configuration
     */
    DiceGame2Config updateMultipliers(long guildId, long straightMultiplier, long baseMultiplier);

    /**
     * Updates the triple bonuses for a guild's dice-game-2 configuration.
     *
     * @param guildId        the guild ID
     * @param tripleLowBonus the new low triple bonus
     * @param tripleHighBonus the new high triple bonus
     * @return the updated configuration
     */
    DiceGame2Config updateTripleBonuses(long guildId, long tripleLowBonus, long tripleHighBonus);

    boolean deleteByGuildId(long guildId);
}
