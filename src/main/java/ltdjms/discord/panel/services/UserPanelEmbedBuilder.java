package ltdjms.discord.panel.services;

import java.awt.Color;
import java.util.List;

import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.EmbedView;
import ltdjms.discord.panel.components.PanelComponentRenderer;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

/**
 * Static utility class for building user panel embeds and components. Ensures consistent rendering
 * across command and button handlers.
 */
public final class UserPanelEmbedBuilder {

  private static final Color EMBED_COLOR = new Color(0x5865F2); // Discord blurple
  private static final String DEFAULT_FOOTER = "點擊下方按鈕查看流水紀錄或兌換碼";
  private static final String TOKEN_HISTORY_LABEL = "📜 查看遊戲代幣流水";
  private static final String PRODUCT_HISTORY_LABEL = "🛒 查看商品流水";
  private static final String REDEEM_LABEL = "🎫 兌換碼";

  private UserPanelEmbedBuilder() {
    // Static utility class - prevent instantiation
  }

  public static MessageEmbed buildPanelEmbed(UserPanelView view, String userMention) {
    return buildPanelEmbed(view, userMention, DEFAULT_FOOTER);
  }

  public static MessageEmbed buildPanelEmbed(
      UserPanelView view, String userMention, String footer) {
    return PanelComponentRenderer.buildEmbed(
        new EmbedView(
            view.getEmbedTitle(),
            userMention + " 的帳戶資訊",
            EMBED_COLOR,
            List.of(
                new EmbedView.FieldView(
                    view.getCurrencyFieldName(), view.formatCurrencyField(), true),
                new EmbedView.FieldView(
                    view.getGameTokensFieldName(), view.formatGameTokensField(), true),
                new EmbedView.FieldView(
                    view.getMembershipFieldName(), view.formatMembershipField(), false)),
            footer));
  }

  public static List<ActionRow> buildPanelComponents(
      String currencyHistoryButtonId,
      String tokenHistoryButtonId,
      String productRedemptionButtonId,
      String redeemButtonId,
      String currencyHistoryLabel) {

    return PanelComponentRenderer.buildActionRows(
        List.of(
            List.of(
                new ButtonView(
                    currencyHistoryButtonId, currencyHistoryLabel, ButtonStyle.SECONDARY, false),
                new ButtonView(
                    tokenHistoryButtonId, TOKEN_HISTORY_LABEL, ButtonStyle.SECONDARY, false),
                new ButtonView(
                    productRedemptionButtonId,
                    PRODUCT_HISTORY_LABEL,
                    ButtonStyle.SECONDARY,
                    false)),
            List.of(new ButtonView(redeemButtonId, REDEEM_LABEL, ButtonStyle.SUCCESS, false))));
  }
}
