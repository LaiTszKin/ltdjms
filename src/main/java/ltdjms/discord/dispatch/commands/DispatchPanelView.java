package ltdjms.discord.dispatch.commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.EmbedView;
import ltdjms.discord.discord.services.DiscordComponentRenderer;
import ltdjms.discord.discord.services.SelectMenuUtil;
import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.product.domain.EscortOrderOptionCatalog;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

/** 派單面板的視圖組裝工具。 */
public final class DispatchPanelView {

  private static final Color EMBED_COLOR = new Color(0x5865F2);
  private static final String NOT_SELECTED = "尚未選擇";
  private static final String NO_PENDING_ORDER_VALUE = "__none__";

  public static final String SELECT_MODE = "dispatch_select_mode";
  public static final String SELECT_ESCORT_USER = "dispatch_select_escort_user";
  public static final String SELECT_CUSTOMER_USER = "dispatch_select_customer_user";
  public static final String SELECT_ORDER_OPTION = "dispatch_select_order_option";
  public static final String SELECT_ORDER_OPTION_EXTRA = "dispatch_select_order_option_extra";
  public static final String SELECT_PENDING_ORDER = "dispatch_select_pending_order";

  public static final String MODE_CREATE = "create";
  public static final String MODE_ASSIGN = "assign";

  public static final String BUTTON_CREATE_ORDER = "dispatch_create_order";
  public static final String BUTTON_ASSIGN_ORDER = "dispatch_assign_order";
  public static final String BUTTON_BACK_TO_MODE = "dispatch_back_to_mode";
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

  public static MessageEmbed buildModeEmbed(String statusMessage) {
    List<EmbedView.FieldView> fields = new ArrayList<>();
    if (statusMessage != null && !statusMessage.isBlank()) {
      fields.add(new EmbedView.FieldView("狀態", statusMessage, false));
    }
    return DiscordComponentRenderer.buildEmbed(
        new EmbedView("🛡️ 護航派單面板", "請先選擇要手動開立護航訂單，或將現有待派單訂單派發給護航者。", EMBED_COLOR, fields, null));
  }

  public static List<ActionRow> buildModeComponents() {
    StringSelectMenu modeSelect =
        StringSelectMenu.create(SELECT_MODE)
            .setPlaceholder("選擇操作")
            .addOption("開單", MODE_CREATE, "手動建立任意護航品類的待派單訂單")
            .addOption("派單", MODE_ASSIGN, "將現有待派單護航訂單派給護航人員")
            .build();

    return List.of(
        DiscordComponentRenderer.buildRow(modeSelect),
        DiscordComponentRenderer.buildActionRow(
            List.of(new ButtonView(BUTTON_HISTORY, "📜 歷史記錄", ButtonStyle.SECONDARY, false))));
  }

  public static MessageEmbed buildPanelEmbed(
      String escortUserMention, String customerUserMention, String statusMessage) {
    return buildPanelEmbed(escortUserMention, customerUserMention, statusMessage, List.of());
  }

  public static MessageEmbed buildPanelEmbed(
      String escortUserMention,
      String customerUserMention,
      String statusMessage,
      List<EscortDispatchOrder> pendingAssignmentOrders) {
    List<EmbedView.FieldView> fields =
        new java.util.ArrayList<>(
            List.of(
                new EmbedView.FieldView("護航者", fallback(escortUserMention), true),
                new EmbedView.FieldView("客戶", fallback(customerUserMention), true)));

    fields.add(
        new EmbedView.FieldView(
            "待派單護航訂單", formatPendingAssignmentOrders(pendingAssignmentOrders), false));

    if (statusMessage != null && !statusMessage.isBlank()) {
      fields.add(new EmbedView.FieldView("狀態", statusMessage, false));
    }

    return DiscordComponentRenderer.buildEmbed(
        new EmbedView(
            "🛡️ 護航派單面板", "請選擇護航者與客戶，完成後點擊「建立派單」。", EMBED_COLOR, fields, "限制：護航者與客戶不可為同一人"));
  }

  public static MessageEmbed buildCreateOrderEmbed(
      String customerUserMention, String selectedOptionCode, String statusMessage) {
    List<EmbedView.FieldView> fields =
        new ArrayList<>(
            List.of(
                new EmbedView.FieldView("客戶", fallback(customerUserMention), true),
                new EmbedView.FieldView("護航品類", fallback(selectedOptionCode), true)));

    if (statusMessage != null && !statusMessage.isBlank()) {
      fields.add(new EmbedView.FieldView("狀態", statusMessage, false));
    }

    return DiscordComponentRenderer.buildEmbed(
        new EmbedView(
            "🧾 開立護航訂單",
            "選擇客戶與護航品類後建立訂單。新訂單會先進入待派單清單，不會立即通知護航者。",
            EMBED_COLOR,
            fields,
            "建立後請回到「派單」流程指派護航人員"));
  }

  public static List<ActionRow> buildCreateOrderComponents(
      boolean canCreateOrder, String selectedOptionCode) {
    EntitySelectMenu customerUserSelect =
        EntitySelectMenu.create(SELECT_CUSTOMER_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("選擇客戶")
            .setRequiredRange(1, 1)
            .build();

    List<ActionRow> rows = new ArrayList<>();
    rows.add(DiscordComponentRenderer.buildRow(customerUserSelect));
    rows.addAll(buildEscortOptionRows(selectedOptionCode));
    rows.add(
        DiscordComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(
                    BUTTON_CREATE_ORDER, "✅ 建立護航訂單", ButtonStyle.SUCCESS, !canCreateOrder),
                new ButtonView(BUTTON_BACK_TO_MODE, "↩ 返回", ButtonStyle.SECONDARY, false),
                new ButtonView(BUTTON_HISTORY, "📜 歷史記錄", ButtonStyle.SECONDARY, false))));
    return rows;
  }

  public static MessageEmbed buildAssignOrderEmbed(
      List<EscortDispatchOrder> pendingOrders,
      String selectedOrderNumber,
      String escortUserMention,
      String statusMessage) {
    List<EmbedView.FieldView> fields =
        new ArrayList<>(
            List.of(
                new EmbedView.FieldView(
                    "待派單護航訂單", formatPendingAssignmentOrders(pendingOrders), false),
                new EmbedView.FieldView("已選訂單", fallback(selectedOrderNumber), true),
                new EmbedView.FieldView("護航者", fallback(escortUserMention), true)));

    if (statusMessage != null && !statusMessage.isBlank()) {
      fields.add(new EmbedView.FieldView("狀態", statusMessage, false));
    }

    return DiscordComponentRenderer.buildEmbed(
        new EmbedView(
            "📨 派發護航訂單",
            "選擇一張待派單訂單與護航人員後派發。系統會私訊護航者確認接單。",
            EMBED_COLOR,
            fields,
            "限制：護航者與客戶不可為同一人"));
  }

  public static List<ActionRow> buildAssignOrderComponents(
      List<EscortDispatchOrder> pendingOrders, String selectedOrderNumber, boolean canAssign) {
    List<ActionRow> rows = new ArrayList<>();

    if (pendingOrders == null || pendingOrders.isEmpty()) {
      StringSelectMenu emptySelect =
          StringSelectMenu.create(SELECT_PENDING_ORDER)
              .setPlaceholder("選擇待派單訂單")
              .addOption("目前沒有待派單訂單", NO_PENDING_ORDER_VALUE, "請先開單或等待商品訂單交接")
              .setDisabled(true)
              .build();
      rows.add(DiscordComponentRenderer.buildRow(emptySelect));
    } else {
      List<StringSelectMenu> orderMenus =
          SelectMenuUtil.splitSelectMenu(
              SELECT_PENDING_ORDER,
              "選擇待派單訂單",
              pendingOrders,
              (builder, order) ->
                  builder.addOption(
                      truncate(order.orderNumber() + "｜" + formatPendingOrderLabel(order), 100),
                      order.orderNumber(),
                      truncate(formatSourceSummary(order), 100)));
      for (StringSelectMenu menu : orderMenus) {
        rows.add(DiscordComponentRenderer.buildRow(menu));
      }
    }

    rows.add(
        DiscordComponentRenderer.buildRow(
            EntitySelectMenu.create(SELECT_ESCORT_USER, EntitySelectMenu.SelectTarget.USER)
                .setPlaceholder("選擇護航者")
                .setRequiredRange(1, 1)
                .build()));
    rows.add(
        DiscordComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(BUTTON_ASSIGN_ORDER, "✅ 派發訂單", ButtonStyle.SUCCESS, !canAssign),
                new ButtonView(BUTTON_BACK_TO_MODE, "↩ 返回", ButtonStyle.SECONDARY, false),
                new ButtonView(BUTTON_HISTORY, "📜 歷史記錄", ButtonStyle.SECONDARY, false))));

    return rows;
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

  private static List<ActionRow> buildEscortOptionRows(String selectedOptionCode) {
    List<EscortOrderOptionCatalog.EscortOrderOption> allOptions =
        EscortOrderOptionCatalog.allOptions();
    int primaryLimit = Math.min(25, allOptions.size());
    List<EscortOrderOptionCatalog.EscortOrderOption> primaryOptions =
        allOptions.subList(0, primaryLimit);
    List<EscortOrderOptionCatalog.EscortOrderOption> extraOptions =
        allOptions.size() > primaryLimit
            ? allOptions.subList(primaryLimit, allOptions.size())
            : List.of();

    boolean selectedInPrimary =
        selectedOptionCode != null
            && primaryOptions.stream().anyMatch(option -> option.code().equals(selectedOptionCode));
    boolean selectedInExtra =
        selectedOptionCode != null
            && !selectedInPrimary
            && extraOptions.stream().anyMatch(option -> option.code().equals(selectedOptionCode));

    StringSelectMenu.Builder primaryBuilder =
        StringSelectMenu.create(SELECT_ORDER_OPTION).setPlaceholder("選擇護航品類");
    primaryOptions.forEach(option -> addEscortOption(primaryBuilder, option));
    if (selectedInPrimary) {
      primaryBuilder.setDefaultValues(List.of(selectedOptionCode));
    }

    List<ActionRow> rows = new ArrayList<>();
    rows.add(DiscordComponentRenderer.buildRow(primaryBuilder.build()));

    if (!extraOptions.isEmpty()) {
      StringSelectMenu.Builder extraBuilder =
          StringSelectMenu.create(SELECT_ORDER_OPTION_EXTRA).setPlaceholder("選擇護航品類（更多）");
      extraOptions.forEach(option -> addEscortOption(extraBuilder, option));
      if (selectedInExtra) {
        extraBuilder.setDefaultValues(List.of(selectedOptionCode));
      }
      rows.add(DiscordComponentRenderer.buildRow(extraBuilder.build()));
    }
    return rows;
  }

  private static void addEscortOption(
      StringSelectMenu.Builder builder, EscortOrderOptionCatalog.EscortOrderOption option) {
    builder.addOption(
        truncate(option.code() + "｜" + option.target(), 100),
        option.code(),
        truncate(
            String.format("%s｜%s｜NT$%,d", option.type(), option.level(), option.priceTwd()), 100));
  }

  private static String formatPendingAssignmentOrders(List<EscortDispatchOrder> orders) {
    if (orders == null || orders.isEmpty()) {
      return "目前沒有待派單的自動護航訂單。";
    }

    StringBuilder builder = new StringBuilder();
    int index = 1;
    for (EscortDispatchOrder order : orders) {
      builder
          .append("**")
          .append(index++)
          .append(".** `")
          .append(order.orderNumber())
          .append("` | 客戶 ")
          .append(formatUserMention(order.customerUserId()))
          .append("\n")
          .append(formatSourceSummary(order))
          .append("\n")
          .append("建立：<t:")
          .append(order.createdAt().getEpochSecond())
          .append(":R>\n");
    }
    return builder.toString().trim();
  }

  private static String formatUserMention(long userId) {
    if (userId <= 0) {
      return "待指定";
    }
    return "<@" + userId + ">";
  }

  private static String formatSourceSummary(EscortDispatchOrder order) {
    if (order == null) {
      return "來源：未知";
    }
    if (order.isManualSource() && isBlank(order.sourceEscortOptionCode())) {
      return "來源：手動派單";
    }

    StringBuilder builder = new StringBuilder();
    builder.append("來源：").append(toSourceTypeText(order.sourceType()));
    if (order.sourceReference() != null && !order.sourceReference().isBlank()) {
      builder.append(" | `").append(order.sourceReference()).append("`");
    }
    if (order.sourceProductName() != null && !order.sourceProductName().isBlank()) {
      builder.append(" | ").append(order.sourceProductName());
    }
    if (order.sourceEscortOptionCode() != null && !order.sourceEscortOptionCode().isBlank()) {
      builder.append(" | ").append(order.sourceEscortOptionCode());
    }
    return builder.toString();
  }

  private static String formatPendingOrderLabel(EscortDispatchOrder order) {
    if (order == null) {
      return "未知";
    }
    if (!isBlank(order.sourceProductName())) {
      return order.sourceProductName();
    }
    if (!isBlank(order.sourceEscortOptionCode())) {
      return order.sourceEscortOptionCode();
    }
    return "手動派單";
  }

  private static String toSourceTypeText(EscortDispatchOrder.SourceType sourceType) {
    if (sourceType == null) {
      return "未知";
    }
    return switch (sourceType) {
      case MANUAL -> "手動派單";
      case CURRENCY_PURCHASE -> "貨幣購買";
      case FIAT_PAYMENT -> "法幣付款";
    };
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, Math.max(0, maxLength - 1)) + "…";
  }
}
