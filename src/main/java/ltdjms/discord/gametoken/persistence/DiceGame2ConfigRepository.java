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

    DiceGame2Config updateTokensPerPlay(long guildId, long tokensPerPlay);

    boolean deleteByGuildId(long guildId);
}
