package ltdjms.discord.gametoken.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 單元測試：GameTokenAccount */
class GameTokenAccountTest {

  private static final long GUILD_ID = 123456789012345678L;
  private static final long USER_ID = 987654321098765432L;

  @Test
  @DisplayName("createNew 應建立 0 代幣的新帳戶")
  void createNewShouldCreateZeroTokenAccount() {
    GameTokenAccount account = GameTokenAccount.createNew(GUILD_ID, USER_ID);

    assertThat(account.guildId()).isEqualTo(GUILD_ID);
    assertThat(account.userId()).isEqualTo(USER_ID);
    assertThat(account.tokens()).isZero();
    assertThat(account.createdAt()).isNotNull();
    assertThat(account.updatedAt()).isNotNull();
  }

  @Test
  @DisplayName("建構子應拒絕負數代幣餘額")
  void constructorShouldRejectNegativeTokens() {
    Instant now = Instant.now();

    assertThatThrownBy(() -> new GameTokenAccount(GUILD_ID, USER_ID, -1L, now, now))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tokens cannot be negative");
  }

  @Test
  @DisplayName("withAdjustedTokens 應正確增加代幣")
  void withAdjustedTokensShouldIncreaseTokens() {
    Instant now = Instant.now();
    GameTokenAccount account = new GameTokenAccount(GUILD_ID, USER_ID, 10L, now, now);

    GameTokenAccount updated = account.withAdjustedTokens(5L);

    assertThat(updated.tokens()).isEqualTo(15L);
    assertThat(updated.guildId()).isEqualTo(GUILD_ID);
    assertThat(updated.userId()).isEqualTo(USER_ID);
    assertThat(updated.updatedAt()).isAfterOrEqualTo(account.updatedAt());
  }

  @Test
  @DisplayName("withAdjustedTokens 應拒絕導致負餘額的調整")
  void withAdjustedTokensShouldRejectNegativeResult() {
    GameTokenAccount account = GameTokenAccount.createNew(GUILD_ID, USER_ID);

    assertThatThrownBy(() -> account.withAdjustedTokens(-1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative balance");
  }
}
