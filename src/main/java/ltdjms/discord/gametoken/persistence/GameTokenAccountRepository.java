package ltdjms.discord.gametoken.persistence;

import java.util.Optional;

import ltdjms.discord.gametoken.domain.GameTokenAccount;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Repository interface for game token accounts. */
public interface GameTokenAccountRepository {

  Optional<GameTokenAccount> findByGuildIdAndUserId(long guildId, long userId);

  GameTokenAccount save(GameTokenAccount account);

  GameTokenAccount findOrCreate(long guildId, long userId);

  GameTokenAccount adjustTokens(long guildId, long userId, long amount);

  /**
   * Adjusts the token balance atomically with explicit error handling via Result. Returns Ok with
   * the updated account, or Err with a DomainError if: - The operation would result in a negative
   * token count (INSUFFICIENT_TOKENS) - A database error occurred (PERSISTENCE_FAILURE)
   */
  Result<GameTokenAccount, DomainError> tryAdjustTokens(long guildId, long userId, long amount);

  GameTokenAccount setTokens(long guildId, long userId, long newTokens);

  boolean deleteByGuildIdAndUserId(long guildId, long userId);
}
