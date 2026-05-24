package ltdjms.discord.panel.commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.EmbedView;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.panel.components.PanelComponentRenderer;
import ltdjms.discord.product.domain.EscortOptionCatalog;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

final class AdminPanelViewFactory {

  private static final Color EMBED_COLOR = new Color(0xED4245);
  private static final int ITEMS_PER_PAGE = 10;

  private AdminPanelViewFactory() {}

  static List<ActionRow> buildBalanceManagementComponents(
      String currencyIcon, String selectedMode, boolean canOpenModal) {
    return buildManagementComponents(
        buildManagementUserSelect(AdminPanelButtonHandler.SELECT_BALANCE_USER),
        buildBalanceModeSelect(selectedMode),
        AdminPanelButtonHandler.BUTTON_OPEN_BALANCE_MODAL,
        currencyIcon + " 輸入金額",
        canOpenModal);
  }

  static MessageEmbed buildBalanceManagementEmbed(
      String userMention, Long currentBalance, String modeLabel, String currencyIcon) {
    List<EmbedView.FieldView> fields = new ArrayList<>();
    if (userMention != null) {
      fields.add(new EmbedView.FieldView("選取成員", userMention, true));
      fields.add(
          new EmbedView.FieldView(
              "目前餘額",
              currentBalance != null
                  ? String.format("%s %,d", currencyIcon, currentBalance)
                  : "（無法取得）",
              true));
    }

    if (modeLabel != null) {
      fields.add(new EmbedView.FieldView("調整模式", modeLabel, false));
    }

    return PanelComponentRenderer.buildEmbed(
        new EmbedView(
            currencyIcon + " 使用者餘額管理",
            "選擇要調整餘額的成員和調整模式",
            EMBED_COLOR,
            fields,
            "選擇成員和模式後點擊「輸入金額」按鈕"));
  }

  static List<ActionRow> buildTokenManagementComponents(String selectedMode, boolean canOpenModal) {
    return buildManagementComponents(
        buildManagementUserSelect(AdminPanelButtonHandler.SELECT_TOKEN_USER),
        buildTokenModeSelect(selectedMode),
        AdminPanelButtonHandler.BUTTON_OPEN_TOKEN_MODAL,
        "🎮 輸入數量",
        canOpenModal);
  }

  static MessageEmbed buildTokenManagementEmbed(
      String userMention, Long currentTokens, String modeLabel) {
    List<EmbedView.FieldView> fields = new ArrayList<>();
    if (userMention != null) {
      fields.add(new EmbedView.FieldView("選取成員", userMention, true));
      fields.add(
          new EmbedView.FieldView(
              "目前代幣",
              currentTokens != null ? String.format("🎮 %,d", currentTokens) : "（無法取得）",
              true));
    }

    if (modeLabel != null) {
      fields.add(new EmbedView.FieldView("調整模式", modeLabel, false));
    }

    return PanelComponentRenderer.buildEmbed(
        new EmbedView("🎮 遊戲代幣管理", "選擇要調整代幣的成員和調整模式", EMBED_COLOR, fields, "選擇成員和模式後點擊「輸入數量」按鈕"));
  }

  static MessageEmbed buildGameManagementEmbed(
      DiceGame1Config game1Config, DiceGame2Config game2Config, String currencyIcon) {
    return buildAdminEmbed(
        "🎲 遊戲設定管理",
        "選擇要調整設定的遊戲",
        List.of(
            new EmbedView.FieldView(
                "摘星手",
                String.format(
                    "代幣範圍：🎮 %,d ~ %,d\n單骰倍率：%s %,d",
                    game1Config.minTokensPerPlay(),
                    game1Config.maxTokensPerPlay(),
                    currencyIcon,
                    game1Config.rewardPerDiceValue()),
                false),
            new EmbedView.FieldView(
                "神龍擺尾",
                String.format(
                    "代幣範圍：🎮 %,d ~ %,d\n順子倍率：%s %,d\n基礎倍率：%s %,d",
                    game2Config.minTokensPerPlay(),
                    game2Config.maxTokensPerPlay(),
                    currencyIcon,
                    game2Config.straightMultiplier(),
                    currencyIcon,
                    game2Config.baseMultiplier()),
                false)),
        "選擇遊戲以查看詳細設定");
  }

  static MessageEmbed buildDiceGame1SettingsEmbed(DiceGame1Config config, String currencyIcon) {
    return buildAdminEmbed(
        "🎲 摘星手設定",
        null,
        List.of(
            new EmbedView.FieldView(
                "代幣範圍",
                String.format(
                    "最小：🎮 %,d\n最大：🎮 %,d", config.minTokensPerPlay(), config.maxTokensPerPlay()),
                true),
            new EmbedView.FieldView(
                "獎勵設定",
                String.format(
                    "單骰獎勵倍率：%s %,d\n（1 點 = %,d、6 點 = %,d）",
                    currencyIcon,
                    config.rewardPerDiceValue(),
                    config.rewardPerDiceValue(),
                    config.rewardPerDiceValue() * 6),
                true)),
        "選擇要調整的設定類別");
  }

  static MessageEmbed buildDiceGame2SettingsEmbed(DiceGame2Config config, String currencyIcon) {
    return buildAdminEmbed(
        "🎲 神龍擺尾設定",
        null,
        List.of(
            new EmbedView.FieldView(
                "代幣範圍",
                String.format(
                    "最小：🎮 %,d\n最大：🎮 %,d", config.minTokensPerPlay(), config.maxTokensPerPlay()),
                true),
            new EmbedView.FieldView(
                "獎勵倍率",
                String.format(
                    "順子倍率：%s %,d\n基礎倍率：%s %,d",
                    currencyIcon,
                    config.straightMultiplier(),
                    currencyIcon,
                    config.baseMultiplier()),
                true),
            new EmbedView.FieldView(
                "豹子獎勵",
                String.format(
                    "小豹子（<10）：%s %,d\n大豹子（≥10）：%s %,d",
                    currencyIcon, config.tripleLowBonus(), currencyIcon, config.tripleHighBonus()),
                false)),
        "選擇要調整的設定類別");
  }

  static MessageEmbed buildDispatchAfterSalesConfigEmbed(
      Set<Long> staffUserIds, String statusMessage) {
    List<EmbedView.FieldView> fields = new ArrayList<>();

    if (staffUserIds.isEmpty()) {
      fields.add(new EmbedView.FieldView("目前售後名單", "尚未設定任何售後人員", false));
    } else {
      StringBuilder users = new StringBuilder();
      for (Long userId : staffUserIds) {
        users.append("<@").append(userId).append(">\n");
      }
      fields.add(
          new EmbedView.FieldView("目前售後名單 (" + staffUserIds.size() + ")", users.toString(), false));
    }

    if (statusMessage != null && !statusMessage.isBlank()) {
      fields.add(new EmbedView.FieldView("狀態", statusMessage, false));
    }

    return buildAdminEmbed("🧰 派單售後人員設定", "設定可接手派單售後案件的成員", fields, "可設定多位售後；有售後申請時會優先通知在線售後");
  }

  static List<ActionRow> buildDispatchAfterSalesConfigComponents() {
    EntitySelectMenu addUserSelect =
        EntitySelectMenu.create(
                AdminPanelButtonHandler.SELECT_DISPATCH_AFTER_SALES_ADD_USER,
                EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("新增售後人員")
            .setRequiredRange(1, 1)
            .build();

    EntitySelectMenu removeUserSelect =
        EntitySelectMenu.create(
                AdminPanelButtonHandler.SELECT_DISPATCH_AFTER_SALES_REMOVE_USER,
                EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("移除售後人員")
            .setRequiredRange(1, 1)
            .build();

    return List.of(
        PanelComponentRenderer.buildRow(addUserSelect),
        PanelComponentRenderer.buildRow(removeUserSelect),
        PanelComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(
                    AdminPanelButtonHandler.BUTTON_BACK,
                    "⬅️ 返回主選單",
                    ButtonStyle.SECONDARY,
                    false))));
  }

  static MessageEmbed buildAIChannelConfigEmbed(
      Set<ltdjms.discord.aichat.domain.AllowedChannel> channels,
      Set<ltdjms.discord.aichat.domain.AllowedCategory> categories) {
    List<EmbedView.FieldView> fields = new ArrayList<>();
    String description;

    if (channels.isEmpty() && categories.isEmpty()) {
      description = "**未設定任何頻道限制**";
      fields.add(new EmbedView.FieldView("狀態", "AI 可在所有頻道使用", false));
      fields.add(new EmbedView.FieldView("說明", "使用下方的選單新增允許的頻道以啟用限制模式", false));
    } else {
      StringBuilder channelList = new StringBuilder();
      for (var channel : channels) {
        channelList.append(
            String.format("<#%d> - %s\n", channel.channelId(), channel.channelName()));
      }
      description = "**已啟用頻道限制**";
      fields.add(new EmbedView.FieldView("允許的頻道", channelList.toString(), false));
      fields.add(new EmbedView.FieldView("頻道總計", channels.size() + " 個頻道", false));
    }

    if (!categories.isEmpty()) {
      StringBuilder categoryList = new StringBuilder();
      for (var category : categories) {
        categoryList.append(
            String.format("📁 %s (ID: %d)\n", category.categoryName(), category.categoryId()));
      }
      fields.add(new EmbedView.FieldView("允許的類別", categoryList.toString(), false));
      fields.add(new EmbedView.FieldView("類別總計", categories.size() + " 個類別", true));
    }

    return buildAdminEmbed("🤖 AI 頻道設定", description, fields, null);
  }

  static MessageEmbed buildAIAgentConfigOverviewEmbed(List<Long> enabledChannels) {
    List<EmbedView.FieldView> fields = new ArrayList<>();
    if (enabledChannels.isEmpty()) {
      fields.add(new EmbedView.FieldView("已啟用頻道", "目前沒有啟用 AI Agent 的頻道", false));
    } else {
      StringBuilder sb = new StringBuilder();
      for (Long channelId : enabledChannels) {
        sb.append("<#").append(channelId).append(">\n");
      }
      fields.add(
          new EmbedView.FieldView("已啟用頻道 (" + enabledChannels.size() + ")", sb.toString(), false));
    }
    return buildAdminEmbed("🤖 AI Agent 頻道配置", "管理哪些頻道啟用 AI Agent 模式", fields, null);
  }

  static MessageEmbed buildAIAgentChannelEmbed(
      long channelId, boolean isEnabled, String statusMessage) {
    return buildAdminEmbed(
        "🤖 AI Agent 頻道設定",
        "頻道：<#" + channelId + ">\n狀態：" + (isEnabled ? "✅ 已啟用" : "❌ 未啟用"),
        List.of(new EmbedView.FieldView("目前狀態", statusMessage, false)),
        null);
  }

  static long extractChannelIdFromDescription(String description) {
    if (description == null) {
      return 0;
    }
    int start = description.indexOf("<#");
    if (start == -1) {
      return 0;
    }
    int end = description.indexOf(">", start);
    if (end == -1) {
      return 0;
    }
    try {
      return Long.parseLong(description.substring(start + 2, end));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  // ========== Escort Catalog 護航價目表管理 ==========

  /**
   * Builds the escort catalog list embed with pagination.
   *
   * @param items the catalog items for the current page
   * @param page the current page number (0-based)
   * @param totalPages the total number of pages
   * @return the embed
   */
  static MessageEmbed buildEscortCatalogListEmbed(
      List<EscortOptionCatalog> items, int page, int totalPages) {
    List<EmbedView.FieldView> fields = new ArrayList<>();

    if (items.isEmpty()) {
      fields.add(new EmbedView.FieldView("目前項目", "暫無護航項目", false));
    } else {
      int startIndex = page * ITEMS_PER_PAGE + 1;
      for (int i = 0; i < items.size(); i++) {
        EscortOptionCatalog item = items.get(i);
        String line =
            String.format(
                "%d. **%s**\n訂單類型：%s / 服務範圍：%s / 服務價格：NT$%,d",
                startIndex + i, item.code(), item.type(), item.level(), item.priceTwd());
        fields.add(new EmbedView.FieldView("", line, false));
      }
    }

    String footer;
    if (totalPages <= 0) {
      footer = "暫無項目";
    } else {
      footer = String.format("第 %d / %d 頁", page + 1, totalPages);
    }

    return buildAdminEmbed("📋 護航價目表管理", "所有護航項目列表（點擊下方按鈕管理）", fields, footer);
  }

  /**
   * Builds action components for the escort catalog page.
   *
   * @param page the current page number (0-based)
   * @param totalPages the total number of pages
   * @return list of action rows
   */
  static List<ActionRow> buildEscortCatalogComponents(int page, int totalPages) {
    List<ButtonView> navButtons = new ArrayList<>();
    navButtons.add(
        new ButtonView(
            AdminPanelButtonHandler.BUTTON_ESCORT_CATALOG_PREV,
            "⬅️ 上一頁",
            ButtonStyle.SECONDARY,
            page <= 0));
    navButtons.add(
        new ButtonView(
            AdminPanelButtonHandler.BUTTON_ESCORT_CATALOG_NEXT,
            "下一頁 ➡️",
            ButtonStyle.SECONDARY,
            page >= totalPages - 1));

    List<ButtonView> actionButtons = new ArrayList<>();
    actionButtons.add(
        new ButtonView(
            AdminPanelButtonHandler.BUTTON_ESCORT_CATALOG_CREATE,
            "➕ 新增項目",
            ButtonStyle.SUCCESS,
            false));
    actionButtons.add(
        new ButtonView(
            AdminPanelButtonHandler.BUTTON_ESCORT_CATALOG_REFRESH,
            "🔄 重新整理",
            ButtonStyle.SECONDARY,
            false));
    actionButtons.add(
        new ButtonView(
            AdminPanelButtonHandler.BUTTON_ESCORT_CATALOG_SELECT,
            "✏️ 選擇編輯/刪除",
            ButtonStyle.PRIMARY,
            false));

    return List.of(
        PanelComponentRenderer.buildActionRow(navButtons),
        PanelComponentRenderer.buildActionRow(actionButtons),
        PanelComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(
                    AdminPanelButtonHandler.BUTTON_BACK,
                    "⬅️ 返回主選單",
                    ButtonStyle.SECONDARY,
                    false))));
  }

  private static EntitySelectMenu buildManagementUserSelect(String selectId) {
    return EntitySelectMenu.create(selectId, EntitySelectMenu.SelectTarget.USER)
        .setPlaceholder("選擇要調整的成員")
        .setRequiredRange(1, 1)
        .build();
  }

  private static StringSelectMenu buildBalanceModeSelect(String selectedMode) {
    StringSelectMenu.Builder builder =
        StringSelectMenu.create(AdminPanelButtonHandler.SELECT_BALANCE_MODE)
            .setPlaceholder("選擇調整模式")
            .addOption("增加餘額", "add", "在現有餘額基礎上增加指定金額")
            .addOption("扣除餘額", "deduct", "從現有餘額扣除指定金額")
            .addOption("設定餘額", "adjust", "將餘額直接設定為指定金額");
    if (selectedMode != null) {
      builder.setDefaultValues(List.of(selectedMode));
    }
    return builder.build();
  }

  private static StringSelectMenu buildTokenModeSelect(String selectedMode) {
    StringSelectMenu.Builder builder =
        StringSelectMenu.create(AdminPanelButtonHandler.SELECT_TOKEN_MODE)
            .setPlaceholder("選擇調整模式")
            .addOption("增加代幣", "add", "在現有代幣基礎上增加指定數量")
            .addOption("扣除代幣", "deduct", "從現有代幣扣除指定數量")
            .addOption("設定代幣", "adjust", "將代幣直接設定為指定數量");
    if (selectedMode != null) {
      builder.setDefaultValues(List.of(selectedMode));
    }
    return builder.build();
  }

  private static List<ActionRow> buildManagementComponents(
      EntitySelectMenu userSelect,
      StringSelectMenu modeSelect,
      String actionButtonId,
      String actionLabel,
      boolean actionEnabled) {
    List<ActionRow> rows = new ArrayList<>();
    rows.add(PanelComponentRenderer.buildRow(userSelect));
    rows.add(PanelComponentRenderer.buildRow(modeSelect));
    rows.add(
        PanelComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(actionButtonId, actionLabel, ButtonStyle.PRIMARY, !actionEnabled),
                new ButtonView(
                    AdminPanelButtonHandler.BUTTON_BACK,
                    "⬅️ 返回主選單",
                    ButtonStyle.SECONDARY,
                    false))));
    return rows;
  }

  static MessageEmbed buildAdminEmbed(
      String title, String description, List<EmbedView.FieldView> fields, String footer) {
    return PanelComponentRenderer.buildEmbed(
        new EmbedView(title, description, EMBED_COLOR, fields, footer));
  }
}
