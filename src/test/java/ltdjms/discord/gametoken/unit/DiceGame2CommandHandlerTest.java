package ltdjms.discord.gametoken.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.gametoken.services.DiceGame2Service.DiceGame2Result;

/**
 * Unit tests for DiceGame2CommandHandler response formatting. Tests the formatting logic without
 * mocking JDA events (due to Java 25 restrictions). The actual command handler integration is
 * tested via integration tests.
 */
class DiceGame2CommandHandlerTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Nested
  @DisplayName("DiceGame2Result formatting")
  class DiceGame2ResultFormatting {

    @Test
    @DisplayName("should format basic result message with all sections")
    void shouldFormatBasicResultMessage() {
      // Given
      DiceGame2Result result =
          new DiceGame2Result(
              TEST_GUILD_ID,
              TEST_USER_ID,
              List.of(1, 2, 3, 4, 5, 6, 1, 1, 1, 2, 2, 2, 1, 3, 6),
              5_300_000L,
              1_000_000L,
              6_300_000L,
              List.of(List.of(1, 2, 3, 4, 5, 6)),
              List.of(List.of(1, 1, 1), List.of(2, 2, 2)),
              2_100_000L, // straight reward
              200_000L, // non-straight reward
              3_000_000L // triple reward
              );

      // When
      String message = result.formatMessage("💰", "Gold");

      // Then
      assertThat(message).contains("Dice Game 2 Results");
      assertThat(message).contains(":one:");
      assertThat(message).contains(":six:");
      assertThat(message).contains("5,300,000");
      assertThat(message).contains("6,300,000");
      assertThat(message).contains("💰");
      assertThat(message).contains("Gold");
    }

    @Test
    @DisplayName("should show straight reward when straights exist")
    void shouldShowStraightRewardWhenExists() {
      // Given
      DiceGame2Result result =
          new DiceGame2Result(
              TEST_GUILD_ID,
              TEST_USER_ID,
              List.of(1, 2, 3, 6, 5, 4, 3, 6, 5, 4, 3, 6, 5, 4, 3),
              1_660_000L,
              0L,
              1_660_000L,
              List.of(List.of(1, 2, 3)),
              List.of(),
              600_000L,
              1_060_000L,
              0L);

      // When
      String message = result.formatMessage("🪙", "Coins");

      // Then
      assertThat(message).contains("Straights:");
      assertThat(message).contains("600,000");
    }

    @Test
    @DisplayName("should show triple reward when triples exist")
    void shouldShowTripleRewardWhenExists() {
      // Given
      DiceGame2Result result =
          new DiceGame2Result(
              TEST_GUILD_ID,
              TEST_USER_ID,
              List.of(1, 1, 1, 6, 5, 4, 3, 6, 5, 4, 3, 6, 5, 4, 3),
              2_580_000L,
              0L,
              2_580_000L,
              List.of(),
              List.of(List.of(1, 1, 1)),
              0L,
              1_080_000L,
              1_500_000L);

      // When
      String message = result.formatMessage("🪙", "Coins");

      // Then
      assertThat(message).contains("Triples:");
      assertThat(message).contains("1,500,000");
      assertThat(message).contains("1 group");
    }

    @Test
    @DisplayName("should show multiple triple groups correctly")
    void shouldShowMultipleTripleGroups() {
      // Given
      DiceGame2Result result =
          new DiceGame2Result(
              TEST_GUILD_ID,
              TEST_USER_ID,
              List.of(1, 1, 1, 6, 2, 2, 2, 6, 3, 6, 4, 6, 5, 6, 6),
              3_960_000L,
              0L,
              3_960_000L,
              List.of(),
              List.of(List.of(1, 1, 1), List.of(2, 2, 2)),
              0L,
              960_000L,
              3_000_000L);

      // When
      String message = result.formatMessage("💎", "Gems");

      // Then
      assertThat(message).contains("Triples:");
      assertThat(message).contains("2 groups");
    }

    @Test
    @DisplayName("should show base reward when non-straight values exist")
    void shouldShowBaseRewardWhenNonStraightExists() {
      // Given
      DiceGame2Result result =
          new DiceGame2Result(
              TEST_GUILD_ID,
              TEST_USER_ID,
              List.of(1, 3, 5, 1, 3, 5, 1, 3, 5, 1, 3, 5, 1, 3, 6),
              920_000L,
              0L,
              920_000L,
              List.of(),
              List.of(),
              0L,
              920_000L,
              0L);

      // When
      String message = result.formatMessage("🪙", "Coins");

      // Then
      assertThat(message).contains("Base:");
      assertThat(message).contains("920,000");
    }

    @Test
    @DisplayName("should format all 15 dice rolls")
    void shouldFormatAll15DiceRolls() {
      // Given
      DiceGame2Result result =
          new DiceGame2Result(
              TEST_GUILD_ID,
              TEST_USER_ID,
              List.of(1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6, 1, 2, 3),
              5_000_000L,
              0L,
              5_000_000L,
              List.of(List.of(1, 2, 3, 4, 5, 6), List.of(1, 2, 3, 4, 5, 6), List.of(1, 2, 3)),
              List.of(),
              4_800_000L,
              200_000L,
              0L);

      // When
      String message = result.formatMessage("🪙", "Coins");

      // Then - verify all 15 dice are present
      // Count the number of dice emojis
      int diceCount = 0;
      for (String emoji : List.of(":one:", ":two:", ":three:", ":four:", ":five:", ":six:")) {
        int idx = 0;
        while ((idx = message.indexOf(emoji, idx)) != -1) {
          diceCount++;
          idx += emoji.length();
        }
      }
      assertThat(diceCount).isEqualTo(15);
    }

    @Test
    @DisplayName("should include total reward and new balance")
    void shouldIncludeTotalRewardAndNewBalance() {
      // Given
      DiceGame2Result result =
          new DiceGame2Result(
              TEST_GUILD_ID,
              TEST_USER_ID,
              List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
              300_000L,
              10_000_000L,
              10_300_000L,
              List.of(),
              List.of(),
              0L,
              300_000L,
              0L);

      // When
      String message = result.formatMessage("💰", "Gold");

      // Then
      assertThat(message).contains("**Total Reward:**");
      assertThat(message).contains("300,000");
      assertThat(message).contains("**New Balance:**");
      assertThat(message).contains("10,300,000");
    }
  }

  @Nested
  @DisplayName("DiceGame2Result record accessors")
  class DiceGame2ResultAccessors {

    @Test
    @DisplayName("should include all required fields")
    void shouldIncludeAllRequiredFields() {
      // Given
      List<Integer> rolls = List.of(1, 2, 3, 4, 5, 6, 1, 1, 1, 2, 2, 2, 1, 3, 6);
      List<List<Integer>> straights = List.of(List.of(1, 2, 3, 4, 5, 6));
      List<List<Integer>> triples = List.of(List.of(1, 1, 1), List.of(2, 2, 2));

      DiceGame2Result result =
          new DiceGame2Result(
              TEST_GUILD_ID,
              TEST_USER_ID,
              rolls,
              5_300_000L,
              1_000_000L,
              6_300_000L,
              straights,
              triples,
              2_100_000L,
              200_000L,
              3_000_000L);

      // Then
      assertThat(result.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(result.userId()).isEqualTo(TEST_USER_ID);
      assertThat(result.diceRolls()).isEqualTo(rolls);
      assertThat(result.totalReward()).isEqualTo(5_300_000L);
      assertThat(result.previousBalance()).isEqualTo(1_000_000L);
      assertThat(result.newBalance()).isEqualTo(6_300_000L);
      assertThat(result.straightSegments()).isEqualTo(straights);
      assertThat(result.tripleSegments()).isEqualTo(triples);
      assertThat(result.straightReward()).isEqualTo(2_100_000L);
      assertThat(result.nonStraightReward()).isEqualTo(200_000L);
      assertThat(result.tripleReward()).isEqualTo(3_000_000L);
    }
  }
}
