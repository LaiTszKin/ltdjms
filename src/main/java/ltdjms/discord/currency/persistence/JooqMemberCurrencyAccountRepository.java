package ltdjms.discord.currency.persistence;

import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
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
 * JOOQ-based implementation of MemberCurrencyAccountRepository.
 * Provides methods to load, create, and update balances atomically while preventing negative values.
 */
public class JooqMemberCurrencyAccountRepository implements MemberCurrencyAccountRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JooqMemberCurrencyAccountRepository.class);

    // Table and column references
    private static final org.jooq.Table<?> MEMBER_CURRENCY_ACCOUNT = table("member_currency_account");
    private static final org.jooq.Field<Long> GUILD_ID = field("guild_id", Long.class);
    private static final org.jooq.Field<Long> USER_ID = field("user_id", Long.class);
    private static final org.jooq.Field<Long> BALANCE = field("balance", Long.class);
    private static final org.jooq.Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final org.jooq.Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqMemberCurrencyAccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<MemberCurrencyAccount> findByGuildIdAndUserId(long guildId, long userId) {
        try {
            Record record = dsl.select(GUILD_ID, USER_ID, BALANCE, CREATED_AT, UPDATED_AT)
                    .from(MEMBER_CURRENCY_ACCOUNT)
                    .where(GUILD_ID.eq(guildId).and(USER_ID.eq(userId)))
                    .fetchOne();

            if (record != null) {
                return Optional.of(mapRecord(record));
            }
            return Optional.empty();
        } catch (Exception e) {
            LOG.error("Failed to find account for guildId={}, userId={}", guildId, userId, e);
            throw new RepositoryException("Failed to find member account", e);
        }
    }

    @Override
    public MemberCurrencyAccount save(MemberCurrencyAccount account) {
        try {
            int affected = dsl.insertInto(MEMBER_CURRENCY_ACCOUNT)
                    .set(GUILD_ID, account.guildId())
                    .set(USER_ID, account.userId())
                    .set(BALANCE, account.balance())
                    .set(CREATED_AT, toOffsetDateTime(account.createdAt()))
                    .set(UPDATED_AT, toOffsetDateTime(account.updatedAt()))
                    .execute();

            if (affected != 1) {
                throw new RepositoryException("Expected 1 row affected, got " + affected);
            }

            LOG.info("Saved member account: guildId={}, userId={}, balance={}",
                    account.guildId(), account.userId(), account.balance());
            return account;
        } catch (RepositoryException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to save account for guildId={}, userId={}", account.guildId(), account.userId(), e);
            throw new RepositoryException("Failed to save member account", e);
        }
    }

    @Override
    public MemberCurrencyAccount findOrCreate(long guildId, long userId) {
        return findByGuildIdAndUserId(guildId, userId)
                .orElseGet(() -> {
                    MemberCurrencyAccount newAccount = MemberCurrencyAccount.createNew(guildId, userId);
                    return save(newAccount);
                });
    }

    @Override
    public MemberCurrencyAccount adjustBalance(long guildId, long userId, long amount) {
        // First, ensure the account exists
        findOrCreate(guildId, userId);

        try {
            // Atomic update with non-negative check using JOOQ DSL with RETURNING
            OffsetDateTime now = toOffsetDateTime(Instant.now());
            Record result = dsl.update(MEMBER_CURRENCY_ACCOUNT)
                    .set(BALANCE, BALANCE.plus(amount))
                    .set(UPDATED_AT, now)
                    .where(GUILD_ID.eq(guildId)
                            .and(USER_ID.eq(userId))
                            .and(BALANCE.plus(amount).ge(0L)))
                    .returning(GUILD_ID, USER_ID, BALANCE, CREATED_AT, UPDATED_AT)
                    .fetchOne();

            if (result != null) {
                MemberCurrencyAccount updated = mapRecord(result);
                LOG.info("Adjusted balance: guildId={}, userId={}, amount={}, newBalance={}",
                        guildId, userId, amount, updated.balance());
                return updated;
            } else {
                // The update didn't match, meaning the balance check failed
                MemberCurrencyAccount current = findByGuildIdAndUserId(guildId, userId)
                        .orElseThrow(() -> new RepositoryException("Account not found after creation"));
                throw new NegativeBalanceException(
                        "Insufficient balance: current=" + current.balance() + ", adjustment=" + amount);
            }
        } catch (RepositoryException | NegativeBalanceException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to adjust balance for guildId={}, userId={}", guildId, userId, e);
            throw new RepositoryException("Failed to adjust balance", e);
        }
    }

    @Override
    public Result<MemberCurrencyAccount, DomainError> tryAdjustBalance(long guildId, long userId, long amount) {
        // First, ensure the account exists
        try {
            findOrCreate(guildId, userId);
        } catch (RepositoryException e) {
            return Result.err(DomainError.persistenceFailure("Failed to find or create account", e));
        }

        try {
            // Atomic update with non-negative check using JOOQ DSL with RETURNING
            OffsetDateTime now = toOffsetDateTime(Instant.now());
            Record result = dsl.update(MEMBER_CURRENCY_ACCOUNT)
                    .set(BALANCE, BALANCE.plus(amount))
                    .set(UPDATED_AT, now)
                    .where(GUILD_ID.eq(guildId)
                            .and(USER_ID.eq(userId))
                            .and(BALANCE.plus(amount).ge(0L)))
                    .returning(GUILD_ID, USER_ID, BALANCE, CREATED_AT, UPDATED_AT)
                    .fetchOne();

            if (result != null) {
                MemberCurrencyAccount updated = mapRecord(result);
                LOG.info("Adjusted balance: guildId={}, userId={}, amount={}, newBalance={}",
                        guildId, userId, amount, updated.balance());
                return Result.ok(updated);
            } else {
                // The update didn't match, meaning the balance check failed
                MemberCurrencyAccount current = findByGuildIdAndUserId(guildId, userId)
                        .orElse(null);
                long currentBalance = current != null ? current.balance() : 0;
                return Result.err(DomainError.insufficientBalance(
                        "Insufficient balance: current=" + currentBalance + ", adjustment=" + amount));
            }
        } catch (Exception e) {
            LOG.error("Failed to adjust balance for guildId={}, userId={}", guildId, userId, e);
            return Result.err(DomainError.persistenceFailure("Failed to adjust balance", e));
        }
    }

    @Override
    public MemberCurrencyAccount setBalance(long guildId, long userId, long newBalance) {
        if (newBalance < 0) {
            throw new IllegalArgumentException("Balance cannot be negative: " + newBalance);
        }

        // First, ensure the account exists
        findOrCreate(guildId, userId);

        try {
            // Use JOOQ DSL with RETURNING
            OffsetDateTime now = toOffsetDateTime(Instant.now());
            Record result = dsl.update(MEMBER_CURRENCY_ACCOUNT)
                    .set(BALANCE, newBalance)
                    .set(UPDATED_AT, now)
                    .where(GUILD_ID.eq(guildId).and(USER_ID.eq(userId)))
                    .returning(GUILD_ID, USER_ID, BALANCE, CREATED_AT, UPDATED_AT)
                    .fetchOne();

            if (result != null) {
                MemberCurrencyAccount updated = mapRecord(result);
                LOG.info("Set balance: guildId={}, userId={}, newBalance={}",
                        guildId, userId, updated.balance());
                return updated;
            } else {
                throw new RepositoryException("Account not found after creation");
            }
        } catch (RepositoryException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to set balance for guildId={}, userId={}", guildId, userId, e);
            throw new RepositoryException("Failed to set balance", e);
        }
    }

    @Override
    public boolean deleteByGuildIdAndUserId(long guildId, long userId) {
        try {
            int affected = dsl.deleteFrom(MEMBER_CURRENCY_ACCOUNT)
                    .where(GUILD_ID.eq(guildId).and(USER_ID.eq(userId)))
                    .execute();

            if (affected > 0) {
                LOG.info("Deleted member account: guildId={}, userId={}", guildId, userId);
            }
            return affected > 0;
        } catch (Exception e) {
            LOG.error("Failed to delete account for guildId={}, userId={}", guildId, userId, e);
            throw new RepositoryException("Failed to delete member account", e);
        }
    }

    private MemberCurrencyAccount mapRecord(Record record) {
        return new MemberCurrencyAccount(
                record.get(GUILD_ID),
                record.get(USER_ID),
                record.get(BALANCE),
                record.get(CREATED_AT).toInstant(),
                record.get(UPDATED_AT).toInstant()
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
