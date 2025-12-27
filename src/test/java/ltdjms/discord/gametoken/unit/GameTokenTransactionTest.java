package ltdjms.discord.gametoken.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.gametoken.domain.GameTokenTransaction;

/** Unit tests for GameTokenTransaction domain object. */
class GameTokenTransactionTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Nested
  @DisplayName("constructor validation")
  class ConstructorValidation {

    @Test
    @DisplayName("should reject null source")
    void shouldRejectNullSource() {
      assertThatThrownBy(
              () ->
                  new GameTokenTransaction(
                      1L, TEST_GUILD_ID, TEST_USER_ID, 100, 100, null, null, Instant.now()))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("source must not be null");
    }

    @Test
    @DisplayName("should reject negative balanceAfter")
    void shouldRejectNegativeBalanceAfter() {
      assertThatThrownBy(
              () ->
                  new GameTokenTransaction(
                      1L,
                      TEST_GUILD_ID,
                      TEST_USER_ID,
                      -100,
                      -50,
                      GameTokenTransaction.Source.ADMIN_ADJUSTMENT,
                      null,
                      Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("balanceAfter cannot be negative");
    }

    @Test
    @DisplayName("should allow zero balanceAfter")
    void shouldAllowZeroBalanceAfter() {
      GameTokenTransaction tx =
          new GameTokenTransaction(
              1L,
              TEST_GUILD_ID,
              TEST_USER_ID,
              -100,
              0,
              GameTokenTransaction.Source.DICE_GAME_1_PLAY,
              null,
              Instant.now());
      assertThat(tx.balanceAfter()).isZero();
    }
  }

  @Nested
  @DisplayName("create factory method")
  class CreateMethod {

    @Test
    @DisplayName("should create transaction with null id")
    void shouldCreateTransactionWithNullId() {
      GameTokenTransaction tx =
          GameTokenTransaction.create(
              TEST_GUILD_ID,
              TEST_USER_ID,
              50,
              150,
              GameTokenTransaction.Source.ADMIN_ADJUSTMENT,
              "Test adjustment");

      assertThat(tx.id()).isNull();
      assertThat(tx.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(tx.userId()).isEqualTo(TEST_USER_ID);
      assertThat(tx.amount()).isEqualTo(50);
      assertThat(tx.balanceAfter()).isEqualTo(150);
      assertThat(tx.source()).isEqualTo(GameTokenTransaction.Source.ADMIN_ADJUSTMENT);
      assertThat(tx.description()).isEqualTo("Test adjustment");
      assertThat(tx.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("should create transaction with null description")
    void shouldCreateTransactionWithNullDescription() {
      GameTokenTransaction tx =
          GameTokenTransaction.create(
              TEST_GUILD_ID,
              TEST_USER_ID,
              -5,
              95,
              GameTokenTransaction.Source.DICE_GAME_1_PLAY,
              null);

      assertThat(tx.description()).isNull();
    }
  }

  @Nested
  @DisplayName("formatForDisplay")
  class FormatForDisplay {

    @Test
    @DisplayName("should format positive amount correctly")
    void shouldFormatPositiveAmountCorrectly() {
      GameTokenTransaction tx =
          new GameTokenTransaction(
              1L,
              TEST_GUILD_ID,
              TEST_USER_ID,
              100,
              200,
              GameTokenTransaction.Source.ADMIN_ADJUSTMENT,
              null,
              Instant.now());

      String display = tx.formatForDisplay();

      assertThat(display).contains("+100");
      assertThat(display).contains("餘額: 200");
      assertThat(display).contains("管理員調整");
    }

    @Test
    @DisplayName("should format negative amount correctly")
    void shouldFormatNegativeAmountCorrectly() {
      GameTokenTransaction tx =
          new GameTokenTransaction(
              1L,
              TEST_GUILD_ID,
              TEST_USER_ID,
              -5,
              95,
              GameTokenTransaction.Source.DICE_GAME_1_PLAY,
              null,
              Instant.now());

      String display = tx.formatForDisplay();

      assertThat(display).contains("-5");
      assertThat(display).contains("餘額: 95");
      assertThat(display).contains("骰子遊戲 1 消耗");
    }

    @Test
    @DisplayName("should include description when present")
    void shouldIncludeDescriptionWhenPresent() {
      GameTokenTransaction tx =
          new GameTokenTransaction(
              1L,
              TEST_GUILD_ID,
              TEST_USER_ID,
              50,
              150,
              GameTokenTransaction.Source.REWARD,
              "Birthday gift",
              Instant.now());

      String display = tx.formatForDisplay();

      assertThat(display).contains("Birthday gift");
      assertThat(display).contains("獎勵");
    }

    @Test
    @DisplayName("should not include separator when description is null")
    void shouldNotIncludeSeparatorWhenDescriptionIsNull() {
      GameTokenTransaction tx =
          new GameTokenTransaction(
              1L,
              TEST_GUILD_ID,
              TEST_USER_ID,
              10,
              110,
              GameTokenTransaction.Source.INITIAL,
              null,
              Instant.now());

      String display = tx.formatForDisplay();

      assertThat(display).doesNotContain(" - ");
    }

    @Test
    @DisplayName("should not include separator when description is blank")
    void shouldNotIncludeSeparatorWhenDescriptionIsBlank() {
      GameTokenTransaction tx =
          new GameTokenTransaction(
              1L,
              TEST_GUILD_ID,
              TEST_USER_ID,
              10,
              110,
              GameTokenTransaction.Source.INITIAL,
              "   ",
              Instant.now());

      String display = tx.formatForDisplay();

      assertThat(display).doesNotContain(" - ");
    }
  }

  @Nested
  @DisplayName("Source enum")
  class SourceEnum {

    @Test
    @DisplayName("should return correct display names")
    void shouldReturnCorrectDisplayNames() {
      assertThat(GameTokenTransaction.Source.ADMIN_ADJUSTMENT.getDisplayName()).isEqualTo("管理員調整");
      assertThat(GameTokenTransaction.Source.DICE_GAME_1_PLAY.getDisplayName())
          .isEqualTo("骰子遊戲 1 消耗");
      assertThat(GameTokenTransaction.Source.DICE_GAME_2_PLAY.getDisplayName())
          .isEqualTo("骰子遊戲 2 消耗");
      assertThat(GameTokenTransaction.Source.GAME_PLAY.getDisplayName()).isEqualTo("遊戲消耗");
      assertThat(GameTokenTransaction.Source.REWARD.getDisplayName()).isEqualTo("獎勵");
      assertThat(GameTokenTransaction.Source.INITIAL.getDisplayName()).isEqualTo("初始化");
    }
  }

  @Nested
  @DisplayName("getShortTimestamp")
  class GetShortTimestamp {

    @Test
    @DisplayName("should return Discord relative timestamp format")
    void shouldReturnDiscordTimestampFormat() {
      Instant testTime = Instant.ofEpochSecond(1700000000L);
      GameTokenTransaction tx =
          new GameTokenTransaction(
              1L,
              TEST_GUILD_ID,
              TEST_USER_ID,
              10,
              110,
              GameTokenTransaction.Source.INITIAL,
              null,
              testTime);

      String timestamp = tx.getShortTimestamp();

      assertThat(timestamp).isEqualTo("<t:1700000000:R>");
    }
  }
}
