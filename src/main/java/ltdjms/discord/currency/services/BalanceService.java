package ltdjms.discord.currency.services;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Service interface for retrieving member balances. */
public interface BalanceService {

  /**
   * Gets the balance view for a member in a guild. If the member has no account, one is created
   * with zero balance. If the guild has no currency configuration, defaults are used.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return the balance view with amount, currency name, and icon
   * @deprecated Use {@link #tryGetBalance(long, long)} for Result-based error handling
   */
  @Deprecated
  BalanceView getBalance(long guildId, long userId);

  /**
   * Gets the balance view for a member in a guild using Result-based error handling. If the member
   * has no account, one is created with zero balance. If the guild has no currency configuration,
   * defaults are used.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return Result containing BalanceView on success, or DomainError on failure
   */
  Result<BalanceView, DomainError> tryGetBalance(long guildId, long userId);
}
