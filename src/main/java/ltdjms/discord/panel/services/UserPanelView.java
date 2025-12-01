package ltdjms.discord.panel.services;

/**
 * View model for the user panel, containing all data needed to render
 * the personal panel embed.
 *
 * @param guildId         the guild ID
 * @param userId          the user ID
 * @param currencyBalance the user's currency balance
 * @param currencyName    the currency name
 * @param currencyIcon    the currency icon/emoji
 * @param gameTokens      the user's game token balance
 */
public record UserPanelView(
        long guildId,
        long userId,
        long currencyBalance,
        String currencyName,
        String currencyIcon,
        long gameTokens
) {

    private static final String EMBED_TITLE = "個人面板";
    private static final String GAME_TOKEN_ICON = "🎮";
    private static final String GAME_TOKEN_NAME = "遊戲代幣";

    /**
     * Gets the embed title for the user panel.
     *
     * @return the embed title in zh-TW
     */
    public String getEmbedTitle() {
        return EMBED_TITLE;
    }

    /**
     * Formats the currency balance field for display in an embed.
     *
     * @return formatted currency field with icon, amount, and name
     */
    public String formatCurrencyField() {
        return String.format("%s %,d %s", currencyIcon, currencyBalance, currencyName);
    }

    /**
     * Formats the game tokens field for display in an embed.
     *
     * @return formatted game tokens field with icon and amount
     */
    public String formatGameTokensField() {
        return String.format("%s %,d %s", GAME_TOKEN_ICON, gameTokens, GAME_TOKEN_NAME);
    }

    /**
     * Gets the currency balance field name for the embed.
     * Uses the guild's custom currency name for consistency.
     *
     * @return the field name in zh-TW, e.g., "星幣餘額"
     */
    public String getCurrencyFieldName() {
        return currencyName + "餘額";
    }

    /**
     * Gets the button label for viewing currency transaction history.
     * Uses the guild's custom currency icon for consistency.
     *
     * @return the button label in zh-TW, e.g., "✨ 查看貨幣流水"
     */
    public String getCurrencyHistoryButtonLabel() {
        return currencyIcon + " 查看貨幣流水";
    }

    /**
     * Gets the game tokens field name for the embed.
     *
     * @return the field name in zh-TW
     */
    public String getGameTokensFieldName() {
        return "遊戲代幣餘額";
    }
}
