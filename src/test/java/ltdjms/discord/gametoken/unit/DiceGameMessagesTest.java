package ltdjms.discord.gametoken.unit;

import ltdjms.discord.gametoken.services.DiceGame1Service.DiceGameResult;
import ltdjms.discord.gametoken.services.DiceGame2Service.DiceGame2Result;
import ltdjms.discord.shared.localization.DiceGameMessages;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DiceGameMessages locale-aware message formatting.
 */
class DiceGameMessagesTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final long TEST_USER_ID = 987654321098765432L;

    @Nested
    @DisplayName("DiceGame1 messages with zh-TW locale")
    class DiceGame1ZhTwMessages {

        @Test
        @DisplayName("should format result message in zh-TW when locale is CHINESE_TAIWAN")
        void shouldFormatResultMessageInZhTw() {
            // Given
            DiceGameResult result = new DiceGameResult(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(3, 4, 5, 2, 6),
                    5_000_000L, 1_000_000L, 6_000_000L
            );

            // When
            String message = DiceGameMessages.formatDiceGame1Result(
                    result, "💰", "金幣", DiscordLocale.CHINESE_TAIWAN);

            // Then
            assertThat(message).contains("骰子遊戲結果");
            assertThat(message).contains("骰子結果");
            assertThat(message).contains("總獎勵");
            assertThat(message).contains("新餘額");
            assertThat(message).contains("💰");
            assertThat(message).contains("金幣");
            assertThat(message).contains("5,000,000");
        }

        @Test
        @DisplayName("should format insufficient tokens message in zh-TW")
        void shouldFormatInsufficientTokensMessageInZhTw() {
            // When
            String message = DiceGameMessages.formatInsufficientTokens(
                    10, 5, DiscordLocale.CHINESE_TAIWAN);

            // Then
            assertThat(message).contains("遊戲代幣不足");
            assertThat(message).contains("需要");
            assertThat(message).contains("10");
            assertThat(message).contains("目前餘額");
            assertThat(message).contains("5");
        }
    }

    @Nested
    @DisplayName("DiceGame1 messages with English locale")
    class DiceGame1EnglishMessages {

        @Test
        @DisplayName("should format result message in English when locale is not zh-TW")
        void shouldFormatResultMessageInEnglish() {
            // Given
            DiceGameResult result = new DiceGameResult(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(3, 4, 5, 2, 6),
                    5_000_000L, 1_000_000L, 6_000_000L
            );

            // When
            String message = DiceGameMessages.formatDiceGame1Result(
                    result, "💰", "Gold", DiscordLocale.ENGLISH_US);

            // Then
            assertThat(message).contains("Dice Game Results");
            assertThat(message).contains("Rolls:");
            assertThat(message).contains("Total Reward:");
            assertThat(message).contains("New Balance:");
            assertThat(message).contains("💰");
            assertThat(message).contains("Gold");
        }

        @Test
        @DisplayName("should format insufficient tokens message in English")
        void shouldFormatInsufficientTokensMessageInEnglish() {
            // When
            String message = DiceGameMessages.formatInsufficientTokens(
                    10, 5, DiscordLocale.ENGLISH_US);

            // Then
            assertThat(message).contains("don't have enough game tokens");
            assertThat(message).contains("Required:");
            assertThat(message).contains("10");
            assertThat(message).contains("Your balance:");
            assertThat(message).contains("5");
        }
    }

    @Nested
    @DisplayName("DiceGame2 messages with zh-TW locale")
    class DiceGame2ZhTwMessages {

        @Test
        @DisplayName("should format result message in zh-TW with straights and triples")
        void shouldFormatResultMessageInZhTwWithStraightsAndTriples() {
            // Given
            DiceGame2Result result = new DiceGame2Result(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(1, 2, 3, 4, 5, 6, 1, 1, 1, 2, 2, 2, 1, 3, 6),
                    5_300_000L, 1_000_000L, 6_300_000L,
                    List.of(List.of(1, 2, 3, 4, 5, 6)),
                    List.of(List.of(1, 1, 1), List.of(2, 2, 2)),
                    2_100_000L, 200_000L, 3_000_000L
            );

            // When
            String message = DiceGameMessages.formatDiceGame2Result(
                    result, "💰", "金幣", DiscordLocale.CHINESE_TAIWAN);

            // Then
            assertThat(message).contains("骰子遊戲2結果");
            assertThat(message).contains("骰子結果");
            assertThat(message).contains("順子");
            assertThat(message).contains("三條");
            assertThat(message).contains("總獎勵");
            assertThat(message).contains("新餘額");
        }

        @Test
        @DisplayName("should format triple groups in zh-TW correctly")
        void shouldFormatTripleGroupsInZhTw() {
            // Given
            DiceGame2Result result = new DiceGame2Result(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(1, 1, 1, 2, 2, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5),
                    4_800_000L, 0L, 4_800_000L,
                    List.of(),
                    List.of(List.of(1, 1, 1), List.of(2, 2, 2)),
                    0L, 1_800_000L, 3_000_000L
            );

            // When
            String message = DiceGameMessages.formatDiceGame2Result(
                    result, "💰", "金幣", DiscordLocale.CHINESE_TAIWAN);

            // Then
            assertThat(message).contains("2 組");
        }
    }

    @Nested
    @DisplayName("DiceGame2 messages with English locale")
    class DiceGame2EnglishMessages {

        @Test
        @DisplayName("should format result message in English when locale is not zh-TW")
        void shouldFormatResultMessageInEnglish() {
            // Given
            DiceGame2Result result = new DiceGame2Result(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(1, 2, 3, 4, 5, 6, 1, 1, 1, 2, 2, 2, 1, 3, 6),
                    5_300_000L, 1_000_000L, 6_300_000L,
                    List.of(List.of(1, 2, 3, 4, 5, 6)),
                    List.of(List.of(1, 1, 1), List.of(2, 2, 2)),
                    2_100_000L, 200_000L, 3_000_000L
            );

            // When
            String message = DiceGameMessages.formatDiceGame2Result(
                    result, "💰", "Gold", DiscordLocale.ENGLISH_US);

            // Then
            assertThat(message).contains("Dice Game 2 Results");
            assertThat(message).contains("Rolls:");
            assertThat(message).contains("Straights:");
            assertThat(message).contains("Triples:");
            assertThat(message).contains("Total Reward:");
            assertThat(message).contains("New Balance:");
        }
    }

    @Nested
    @DisplayName("Locale detection helper")
    class LocaleDetectionHelper {

        @Test
        @DisplayName("should detect zh-TW locale correctly")
        void shouldDetectZhTwLocale() {
            assertThat(DiceGameMessages.isZhTw(DiscordLocale.CHINESE_TAIWAN)).isTrue();
        }

        @Test
        @DisplayName("should return false for non zh-TW locales")
        void shouldReturnFalseForNonZhTwLocales() {
            assertThat(DiceGameMessages.isZhTw(DiscordLocale.ENGLISH_US)).isFalse();
            assertThat(DiceGameMessages.isZhTw(DiscordLocale.CHINESE_CHINA)).isFalse();
            assertThat(DiceGameMessages.isZhTw(DiscordLocale.JAPANESE)).isFalse();
            assertThat(DiceGameMessages.isZhTw(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Token range error messages")
    class TokenRangeErrorMessages {

        @Test
        @DisplayName("should format token range error message in zh-TW")
        void shouldFormatTokenRangeErrorMessageInZhTw() {
            // When
            String message = DiceGameMessages.formatTokenRangeError(
                    100L, 5L, 50L, DiscordLocale.CHINESE_TAIWAN);

            // Then
            assertThat(message).contains("代幣投入數量");
            assertThat(message).contains("100");
            assertThat(message).contains("5");
            assertThat(message).contains("50");
        }

        @Test
        @DisplayName("should format token range error message in English")
        void shouldFormatTokenRangeErrorMessageInEnglish() {
            // When
            String message = DiceGameMessages.formatTokenRangeError(
                    100L, 5L, 50L, DiscordLocale.ENGLISH_US);

            // Then
            assertThat(message).contains("Token amount");
            assertThat(message).contains("100");
            assertThat(message).contains("5");
            assertThat(message).contains("50");
        }

        @Test
        @DisplayName("should format token below minimum error in zh-TW")
        void shouldFormatBelowMinimumErrorInZhTw() {
            // When
            String message = DiceGameMessages.formatTokenRangeError(
                    2L, 5L, 50L, DiscordLocale.CHINESE_TAIWAN);

            // Then
            assertThat(message).contains("2");
            assertThat(message).contains("5");
            assertThat(message).contains("50");
        }

        @Test
        @DisplayName("should format token above maximum error in English")
        void shouldFormatAboveMaximumErrorInEnglish() {
            // When
            String message = DiceGameMessages.formatTokenRangeError(
                    60L, 5L, 50L, DiscordLocale.ENGLISH_US);

            // Then
            assertThat(message).contains("60");
            assertThat(message).contains("5");
            assertThat(message).contains("50");
        }
    }
}
