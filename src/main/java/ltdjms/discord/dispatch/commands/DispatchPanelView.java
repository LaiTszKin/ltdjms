package ltdjms.discord.dispatch.commands;

import java.awt.Color;
import java.util.List;

import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.EmbedView;
import ltdjms.discord.discord.services.DiscordComponentRenderer;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;

/** 派單面板的視圖組裝工具。 */
public final class DispatchPanelView {

  private static final Color EMBED_COLOR = new Color(0x5865F2);
  private static final String NOT_SELECTED = "尚未選擇";

  public static final String SELECT_ESCORT_USER = "dispatch_select_escort_user";
  public static final String SELECT_CUSTOMER_USER = "dispatch_select_customer_user";

  public static final String BUTTON_CREATE_ORDER = "dispatch_create_order";
  public static final String BUTTON_HISTORY = "dispatch_history";
  public static final String BUTTON_CONFIRM_ORDER_PREFIX = "dispatch_confirm_order_";
  public static final String BUTTON_COMPLETE_ORDER_PREFIX = "dispatch_complete_order_";
  public static final String BUTTON_CUSTOMER_CONFIRM_COMPLETION_PREFIX =
      "dispatch_customer_confirm_completion_";
  public static final String BUTTON_CUSTOMER_REQUEST_AFTER_SALES_PREFIX =
      "dispatch_customer_request_after_sales_";
  public static final String BUTTON_AFTER_SALES_CLAIM_PREFIX = "dispatch_after_sales_claim_";
  public static final String BUTTON_AFTER_SALES_CLOSE_PREFIX = "dispatch_after_sales_close_";

  private DispatchPanelView() {
    // Utility class
  }

  public static MessageEmbed buildPanelEmbed(
      String escortUserMention, String customerUserMention, String statusMessage) {
    List<EmbedView.FieldView> fields =
        new java.util.ArrayList<>(
            List.of(
                new EmbedView.FieldView("護航者", fallback(escortUserMention), true),
                new EmbedView.FieldView("客戶", fallback(customerUserMention), true)));

    if (statusMessage != null && !statusMessage.isBlank()) {
      fields.add(new EmbedView.FieldView("狀態", statusMessage, false));
    }

    return DiscordComponentRenderer.buildEmbed(
        new EmbedView(
            "🛡️ 護航派單面板", "請選擇護航者與客戶，完成後點擊「建立派單」。", EMBED_COLOR, fields, "限制：護航者與客戶不可為同一人"));
  }

  public static List<ActionRow> buildPanelComponents(boolean canCreateOrder) {
    EntitySelectMenu escortUserSelect =
        EntitySelectMenu.create(SELECT_ESCORT_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("選擇護航者")
            .setRequiredRange(1, 1)
            .build();

    EntitySelectMenu customerUserSelect =
        EntitySelectMenu.create(SELECT_CUSTOMER_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("選擇客戶")
            .setRequiredRange(1, 1)
            .build();

    return List.of(
        DiscordComponentRenderer.buildRow(escortUserSelect),
        DiscordComponentRenderer.buildRow(customerUserSelect),
        DiscordComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(BUTTON_CREATE_ORDER, "✅ 建立派單", ButtonStyle.SUCCESS, !canCreateOrder),
                new ButtonView(BUTTON_HISTORY, "📜 歷史記錄", ButtonStyle.SECONDARY, false))));
  }

  private static String fallback(String value) {
    return value == null || value.isBlank() ? NOT_SELECTED : value;
  }
}
