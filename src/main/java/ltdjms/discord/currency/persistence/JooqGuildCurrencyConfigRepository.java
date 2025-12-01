package ltdjms.discord.currency.persistence;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * JOOQ-based implementation of GuildCurrencyConfigRepository.
 * Provides CRUD operations scoped by guild ID.
 */
public class JooqGuildCurrencyConfigRepository implements GuildCurrencyConfigRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JooqGuildCurrencyConfigRepository.class);

    // Table and column references
    private static final org.jooq.Table<?> GUILD_CURRENCY_CONFIG = table("guild_currency_config");
    private static final org.jooq.Field<Long> GUILD_ID = field("guild_id", Long.class);
    private static final org.jooq.Field<String> CURRENCY_NAME = field("currency_name", String.class);
    private static final org.jooq.Field<String> CURRENCY_ICON = field("currency_icon", String.class);
    private static final org.jooq.Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final org.jooq.Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqGuildCurrencyConfigRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<GuildCurrencyConfig> findByGuildId(long guildId) {
        try {
            Record record = dsl.select(GUILD_ID, CURRENCY_NAME, CURRENCY_ICON, CREATED_AT, UPDATED_AT)
                    .from(GUILD_CURRENCY_CONFIG)
                    .where(GUILD_ID.eq(guildId))
                    .fetchOne();

            if (record != null) {
                return Optional.of(mapRecord(record));
            }
            return Optional.empty();
        } catch (Exception e) {
            LOG.error("Failed to find guild currency config for guildId={}", guildId, e);
            throw new RepositoryException("Failed to find guild currency config", e);
        }
    }

    @Override
    public GuildCurrencyConfig save(GuildCurrencyConfig config) {
        try {
            int affected = dsl.insertInto(GUILD_CURRENCY_CONFIG)
                    .set(GUILD_ID, config.guildId())
                    .set(CURRENCY_NAME, config.currencyName())
                    .set(CURRENCY_ICON, config.currencyIcon())
                    .set(CREATED_AT, toOffsetDateTime(config.createdAt()))
                    .set(UPDATED_AT, toOffsetDateTime(config.updatedAt()))
                    .execute();

            if (affected != 1) {
                throw new RepositoryException("Expected 1 row affected, got " + affected);
            }

            LOG.info("Saved guild currency config: guildId={}, name={}", config.guildId(), config.currencyName());
            return config;
        } catch (RepositoryException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to save guild currency config for guildId={}", config.guildId(), e);
            throw new RepositoryException("Failed to save guild currency config", e);
        }
    }

    @Override
    public GuildCurrencyConfig update(GuildCurrencyConfig config) {
        try {
            int affected = dsl.update(GUILD_CURRENCY_CONFIG)
                    .set(CURRENCY_NAME, config.currencyName())
                    .set(CURRENCY_ICON, config.currencyIcon())
                    .set(UPDATED_AT, toOffsetDateTime(config.updatedAt()))
                    .where(GUILD_ID.eq(config.guildId()))
                    .execute();

            if (affected != 1) {
                throw new RepositoryException("Expected 1 row affected, got " + affected);
            }

            LOG.info("Updated guild currency config: guildId={}, name={}", config.guildId(), config.currencyName());
            return config;
        } catch (RepositoryException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to update guild currency config for guildId={}", config.guildId(), e);
            throw new RepositoryException("Failed to update guild currency config", e);
        }
    }

    @Override
    public GuildCurrencyConfig saveOrUpdate(GuildCurrencyConfig config) {
        try {
            dsl.insertInto(GUILD_CURRENCY_CONFIG)
                    .set(GUILD_ID, config.guildId())
                    .set(CURRENCY_NAME, config.currencyName())
                    .set(CURRENCY_ICON, config.currencyIcon())
                    .set(CREATED_AT, toOffsetDateTime(config.createdAt()))
                    .set(UPDATED_AT, toOffsetDateTime(config.updatedAt()))
                    .onConflict(GUILD_ID)
                    .doUpdate()
                    .set(CURRENCY_NAME, config.currencyName())
                    .set(CURRENCY_ICON, config.currencyIcon())
                    .set(UPDATED_AT, toOffsetDateTime(config.updatedAt()))
                    .execute();

            LOG.info("Saved/updated guild currency config: guildId={}, name={}", config.guildId(), config.currencyName());
            return config;
        } catch (Exception e) {
            LOG.error("Failed to save/update guild currency config for guildId={}", config.guildId(), e);
            throw new RepositoryException("Failed to save/update guild currency config", e);
        }
    }

    @Override
    public boolean deleteByGuildId(long guildId) {
        try {
            int affected = dsl.deleteFrom(GUILD_CURRENCY_CONFIG)
                    .where(GUILD_ID.eq(guildId))
                    .execute();

            if (affected > 0) {
                LOG.info("Deleted guild currency config: guildId={}", guildId);
            }
            return affected > 0;
        } catch (Exception e) {
            LOG.error("Failed to delete guild currency config for guildId={}", guildId, e);
            throw new RepositoryException("Failed to delete guild currency config", e);
        }
    }

    private GuildCurrencyConfig mapRecord(Record record) {
        return new GuildCurrencyConfig(
                record.get(GUILD_ID),
                record.get(CURRENCY_NAME),
                record.get(CURRENCY_ICON),
                record.get(CREATED_AT).toInstant(),
                record.get(UPDATED_AT).toInstant()
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
