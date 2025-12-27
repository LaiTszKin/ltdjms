package ltdjms.discord.gametoken.persistence;

import java.util.Optional;

import ltdjms.discord.gametoken.domain.DiceGame1Config;

/** Repository interface for dice-game-1 configuration. */
public interface DiceGame1ConfigRepository {

  Optional<DiceGame1Config> findByGuildId(long guildId);

  DiceGame1Config save(DiceGame1Config config);

  DiceGame1Config findOrCreateDefault(long guildId);

  /**
   * Updates the token range for a guild's dice-game-1 configuration.
   *
   * @param guildId the guild ID
   * @param minTokens the minimum tokens per play
   * @param maxTokens the maximum tokens per play
   * @return the updated configuration
   */
  DiceGame1Config updateTokensPerPlayRange(long guildId, long minTokens, long maxTokens);

  /**
   * Updates the reward per dice value for a guild's dice-game-1 configuration.
   *
   * @param guildId the guild ID
   * @param rewardPerDiceValue the new reward per dice value
   * @return the updated configuration
   */
  DiceGame1Config updateRewardPerDiceValue(long guildId, long rewardPerDiceValue);

  boolean deleteByGuildId(long guildId);
}
