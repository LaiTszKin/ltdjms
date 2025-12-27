package ltdjms.discord.currency.persistence;

import java.util.Optional;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;

/** Repository interface for guild currency configuration. */
public interface GuildCurrencyConfigRepository {

  Optional<GuildCurrencyConfig> findByGuildId(long guildId);

  GuildCurrencyConfig save(GuildCurrencyConfig config);

  GuildCurrencyConfig update(GuildCurrencyConfig config);

  GuildCurrencyConfig saveOrUpdate(GuildCurrencyConfig config);

  boolean deleteByGuildId(long guildId);
}
