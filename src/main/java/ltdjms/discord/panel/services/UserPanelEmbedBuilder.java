package ltdjms.discord.panel.services;

import ltdjms.discord.discord.domain.DiscordEmbedBuilder;
import ltdjms.discord.discord.services.JdaDiscordEmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.util.List;

/**
 * Static utility class for building user panel embeds and components.
 * Ensures consistent rendering across command and button handlers.
 *
 * <p>已重構使用 DiscordEmbedBuilder 抽象介面，提供統一的 Embed 建構方式。
 */
public final class UserPanelEmbedBuilder {

    private static final Color EMBED_COLOR = new Color(0x5865F2); // Discord blurple

    private UserPanelEmbedBuilder() {
        // Static utility class - prevent instantiation
    }

    /**
     * Builds the main panel embed for displaying user account information.
     *
     * <p>使用 DiscordEmbedBuilder 抽象介面建構 Embed，自動處理長度限制。
     *
     * @param view        the user panel view containing account data
     * @param userMention the Discord mention string for the user (e.g., "@User")
     * @return the formatted embed message
     */
    public static MessageEmbed buildPanelEmbed(UserPanelView view, String userMention) {
        DiscordEmbedBuilder builder = new JdaDiscordEmbedBuilder();

        return builder
                .setTitle(view.getEmbedTitle())
                .setDescription(userMention + " 的帳戶資訊")
                .setColor(EMBED_COLOR)
                .addField(view.getCurrencyFieldName(), view.formatCurrencyField(), true)
                .addField(view.getGameTokensFieldName(), view.formatGameTokensField(), true)
                .setFooter("點擊下方按鈕查看流水紀錄或兌換碼")
                .build();
    }

    /**
     * Builds the action rows with buttons for the main panel.
     * This ensures consistent button layout across command and button handlers.
     *
     * @param currencyHistoryButtonId   the button ID for currency history
     * @param tokenHistoryButtonId      the button ID for token history
     * @param productRedemptionButtonId the button ID for product redemption history
     * @param redeemButtonId            the button ID for redemption
     * @param currencyHistoryLabel      the label for the currency history button
     * @return list of action rows containing the panel buttons
     */
    public static List<ActionRow> buildPanelComponents(
            String currencyHistoryButtonId,
            String tokenHistoryButtonId,
            String productRedemptionButtonId,
            String redeemButtonId,
            String currencyHistoryLabel) {

        return List.of(
                ActionRow.of(
                        Button.secondary(currencyHistoryButtonId, currencyHistoryLabel),
                        Button.secondary(tokenHistoryButtonId, "📜 查看遊戲代幣流水"),
                        Button.secondary(productRedemptionButtonId, "🛒 查看商品流水")
                ),
                ActionRow.of(
                        Button.success(redeemButtonId, "🎫 兌換碼")
                )
        );
    }
}
