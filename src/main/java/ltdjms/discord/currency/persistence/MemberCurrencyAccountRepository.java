package ltdjms.discord.currency.persistence;

import java.util.Optional;

import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Repository interface for member currency accounts. */
public interface MemberCurrencyAccountRepository {

  Optional<MemberCurrencyAccount> findByGuildIdAndUserId(long guildId, long userId);

  MemberCurrencyAccount save(MemberCurrencyAccount account);

  MemberCurrencyAccount findOrCreate(long guildId, long userId);

  MemberCurrencyAccount adjustBalance(long guildId, long userId, long amount);

  /**
   * Adjusts the balance atomically with explicit error handling via Result. Returns Ok with the
   * updated account, or Err with a DomainError if: - The operation would result in a negative
   * balance (INSUFFICIENT_BALANCE) - A database error occurred (PERSISTENCE_FAILURE)
   */
  Result<MemberCurrencyAccount, DomainError> tryAdjustBalance(
      long guildId, long userId, long amount);

  MemberCurrencyAccount setBalance(long guildId, long userId, long newBalance);

  boolean deleteByGuildIdAndUserId(long guildId, long userId);
}
