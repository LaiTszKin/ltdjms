package ltdjms.discord.shared.localization;

import ltdjms.discord.gametoken.services.DiceGame1Service.DiceGameResult;
import ltdjms.discord.gametoken.services.DiceGame2Service.DiceGame2Result;
import net.dv8tion.jda.api.interactions.DiscordLocale;

import java.util.List;

/**
 * Provides locale-aware message formatting for dice games.
 * Supports zh-TW localization for Taiwan users while maintaining English for others.
 */
public final class DiceGameMessages {

    private DiceGameMessages() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if the given locale is zh-TW (Chinese Taiwan).
     *
     * @param locale the Discord locale
     * @return true if the locale is zh-TW
     */
    public static boolean isZhTw(DiscordLocale locale) {
        return locale != null && locale == DiscordLocale.CHINESE_TAIWAN;
    }

    /**
     * Formats the dice game 1 result message based on the locale.
     *
     * @param result       the game result
     * @param currencyIcon the currency icon
     * @param currencyName the currency name
     * @param locale       the user's locale
     * @return the formatted message
     */
    public static String formatDiceGame1Result(
            DiceGameResult result,
            String currencyIcon,
            String currencyName,
            DiscordLocale locale) {

        if (isZhTw(locale)) {
            return formatDiceGame1ResultZhTw(result, currencyIcon, currencyName);
        }
        return result.formatMessage(currencyIcon, currencyName);
    }

    /**
     * Formats the dice game 2 result message based on the locale.
     *
     * @param result       the game result
     * @param currencyIcon the currency icon
     * @param currencyName the currency name
     * @param locale       the user's locale
     * @return the formatted message
     */
    public static String formatDiceGame2Result(
            DiceGame2Result result,
            String currencyIcon,
            String currencyName,
            DiscordLocale locale) {

        if (isZhTw(locale)) {
            return formatDiceGame2ResultZhTw(result, currencyIcon, currencyName);
        }
        return result.formatMessage(currencyIcon, currencyName);
    }

    /**
     * Formats an "insufficient tokens" error message based on the locale.
     *
     * @param required the required token amount
     * @param current  the current token balance
     * @param locale   the user's locale
     * @return the formatted error message
     */
    public static String formatInsufficientTokens(long required, long current, DiscordLocale locale) {
        if (isZhTw(locale)) {
            return String.format(
                    "遊戲代幣不足！\n" +
                            "需要：%,d 代幣\n" +
                            "目前餘額：%,d 代幣",
                    required, current
            );
        }
        return String.format(
                "You don't have enough game tokens to play!\n" +
                        "Required: %,d tokens\n" +
                        "Your balance: %,d tokens",
                required, current
        );
    }

    /**
     * Formats a "token amount out of range" error message based on the locale.
     *
     * @param playerInput the token amount the player specified
     * @param minTokens   the minimum allowed tokens
     * @param maxTokens   the maximum allowed tokens
     * @param locale      the user's locale
     * @return the formatted error message
     */
    public static String formatTokenRangeError(long playerInput, long minTokens, long maxTokens, DiscordLocale locale) {
        if (isZhTw(locale)) {
            return String.format(
                    "代幣投入數量超出範圍！\n" +
                            "您輸入的數量：%,d\n" +
                            "允許範圍：%,d ~ %,d 代幣",
                    playerInput, minTokens, maxTokens
            );
        }
        return String.format(
                "Token amount is out of valid range!\n" +
                        "You entered: %,d\n" +
                        "Valid range: %,d ~ %,d tokens",
                playerInput, minTokens, maxTokens
        );
    }

    /**
     * Formats a "missing tokens input" error message based on the locale.
     * Used when the player does not provide the required tokens parameter.
     *
     * @param minTokens the minimum allowed tokens
     * @param maxTokens the maximum allowed tokens
     * @param locale    the user's locale
     * @return the formatted error message
     */
    public static String formatMissingTokensError(long minTokens, long maxTokens, DiscordLocale locale) {
        if (isZhTw(locale)) {
            return String.format(
                    "請輸入本局要投入的遊戲代幣數量！\n" +
                            "必須介於 %,d ~ %,d 代幣之間",
                    minTokens, maxTokens
            );
        }
        return String.format(
                "Please specify how many game tokens you want to spend!\n" +
                        "Valid range: %,d ~ %,d tokens",
                minTokens, maxTokens
        );
    }

    // --- Private zh-TW formatting methods ---

    private static String formatDiceGame1ResultZhTw(
            DiceGameResult result,
            String currencyIcon,
            String currencyName) {

        StringBuilder sb = new StringBuilder();
        sb.append("**骰子遊戲結果**\n");
        sb.append("骰子結果：");

        appendDiceEmojis(sb, result.diceRolls());

        sb.append("\n\n");
        sb.append(String.format("總獎勵：%s %,d %s\n", currencyIcon, result.totalReward(), currencyName));
        sb.append(String.format("新餘額：%s %,d %s", currencyIcon, result.newBalance(), currencyName));

        return sb.toString();
    }

    private static String formatDiceGame2ResultZhTw(
            DiceGame2Result result,
            String currencyIcon,
            String currencyName) {

        StringBuilder sb = new StringBuilder();
        sb.append("**骰子遊戲2結果**\n");
        sb.append("骰子結果：");

        appendDiceEmojis(sb, result.diceRolls());

        sb.append("\n\n");

        // Show reward breakdown
        if (!result.straightSegments().isEmpty()) {
            sb.append(String.format("順子：%s %,d %s\n", currencyIcon, result.straightReward(), currencyName));
        }
        if (!result.tripleSegments().isEmpty()) {
            int groupCount = result.tripleSegments().size();
            sb.append(String.format("三條：%s %,d %s（%d 組）\n",
                    currencyIcon, result.tripleReward(), currencyName, groupCount));
        }
        if (result.nonStraightReward() > 0) {
            sb.append(String.format("基礎：%s %,d %s\n", currencyIcon, result.nonStraightReward(), currencyName));
        }

        sb.append("\n");
        sb.append(String.format("**總獎勵：** %s %,d %s\n", currencyIcon, result.totalReward(), currencyName));
        sb.append(String.format("**新餘額：** %s %,d %s", currencyIcon, result.newBalance(), currencyName));

        return sb.toString();
    }

    private static void appendDiceEmojis(StringBuilder sb, List<Integer> diceRolls) {
        for (int i = 0; i < diceRolls.size(); i++) {
            int roll = diceRolls.get(i);
            sb.append(diceEmoji(roll));
            if (i < diceRolls.size() - 1) {
                sb.append(" ");
            }
        }
    }

    private static String diceEmoji(int value) {
        return switch (value) {
            case 1 -> ":one:";
            case 2 -> ":two:";
            case 3 -> ":three:";
            case 4 -> ":four:";
            case 5 -> ":five:";
            case 6 -> ":six:";
            default -> String.valueOf(value);
        };
    }
}
