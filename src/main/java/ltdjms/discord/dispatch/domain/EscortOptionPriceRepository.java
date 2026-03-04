package ltdjms.discord.dispatch.domain;

import java.util.Map;
import java.util.Optional;

/** Repository for guild-specific escort option pricing overrides. */
public interface EscortOptionPriceRepository {

  /**
   * Finds all configured price overrides in a guild.
   *
   * @return map of optionCode -> priceTwd
   */
  Map<String, Long> findAllByGuildId(long guildId);

  /** Finds a guild-level price override for one option code. */
  Optional<Long> findByGuildIdAndOptionCode(long guildId, String optionCode);

  /** Upserts a guild-level price override. */
  void upsert(long guildId, String optionCode, long priceTwd, Long updatedByUserId);

  /** Deletes a guild-level price override. */
  boolean delete(long guildId, String optionCode);
}
