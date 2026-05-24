package ltdjms.discord.panel.commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.EmbedView;
import ltdjms.discord.discord.services.SelectMenuUtil;
import ltdjms.discord.panel.components.PanelComponentRenderer;
import ltdjms.discord.product.domain.EscortOrderOptionCatalog;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.redemption.domain.RedemptionCode;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.redemption.services.RedemptionService;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

final class AdminProductPanelViewFactory {

  private static final Color EMBED_COLOR = new Color(0xED4245);

  private AdminProductPanelViewFactory() {}

  static MessageEmbed buildProductListEmbed(List<Product> products) {
    if (products.isEmpty()) {
      return PanelComponentRenderer.buildEmbed(
          new EmbedView("📦 商品管理", "目前沒有任何商品\n\n點擊「建立商品」新增第一個商品", EMBED_COLOR, List.of(), null));
    }

    StringBuilder sb = new StringBuilder();
    sb.append("共 ").append(products.size()).append(" 個商品\n\n");

    for (Product product : products) {
      sb.append("**").append(product.name()).append("**");
      if (product.hasReward()) {
        sb.append(" — ").append(product.formatReward());
      }
      sb.append("\n");
    }

    return PanelComponentRenderer.buildEmbed(
        new EmbedView("📦 商品管理", sb.toString(), EMBED_COLOR, List.of(), "從下拉選單選擇商品查看詳情"));
  }

  static MessageEmbed buildProductDetailEmbed(
      Product product, RedemptionCodeRepository.CodeStats stats) {
    List<EmbedView.FieldView> fields = new ArrayList<>();

    if (product.hasReward()) {
      String rewardTypeName =
          switch (product.rewardType()) {
            case CURRENCY -> "貨幣";
            case TOKEN -> "代幣";
          };
      fields.add(new EmbedView.FieldView("獎勵類型", rewardTypeName, true));
      fields.add(
          new EmbedView.FieldView("獎勵數量", String.format("%,d", product.rewardAmount()), true));
    } else {
      fields.add(new EmbedView.FieldView("獎勵", "無自動獎勵（僅限人工處理）", false));
    }

    fields.add(
        new EmbedView.FieldView(
            "貨幣價格", product.hasCurrencyPrice() ? product.formatCurrencyPrice() : "不可用貨幣購買", true));
    fields.add(
        new EmbedView.FieldView(
            "實際價值（TWD）", product.hasFiatPriceTwd() ? product.formatFiatPriceTwd() : "未設定", true));
    fields.add(
        new EmbedView.FieldView(
            "自動護航開單",
            product.shouldAutoCreateEscortOrder()
                ? "已啟用\n選項代碼：" + product.escortOptionCode()
                : "未啟用",
            false));
    fields.add(
        new EmbedView.FieldView(
            "兌換碼統計",
            String.format(
                "總數：%d\n已使用：%d\n未使用：%d",
                stats.totalCount(), stats.redeemedCount(), stats.unusedCount()),
            false));

    String description =
        product.description() != null && !product.description().isBlank()
            ? product.description()
            : null;
    return PanelComponentRenderer.buildEmbed(
        new EmbedView(
            "📦 " + product.name(), description, EMBED_COLOR, fields, "ID: " + product.id()));
  }

  static List<ActionRow> buildProductDetailComponents(
      Product product, RedemptionCodeRepository.CodeStats stats) {
    return PanelComponentRenderer.buildActionRows(
        List.of(
            List.of(
                new ButtonView(
                    AdminProductPanelHandler.BUTTON_GENERATE_CODES,
                    "🎫 生成兌換碼",
                    ButtonStyle.SUCCESS,
                    false),
                new ButtonView(
                    AdminProductPanelHandler.BUTTON_VIEW_CODES,
                    "📋 查看兌換碼",
                    ButtonStyle.PRIMARY,
                    stats.totalCount() <= 0)),
            List.of(
                new ButtonView(
                    AdminProductPanelHandler.BUTTON_PREFIX_EDIT_PRODUCT + product.id(),
                    "✏️ 編輯",
                    ButtonStyle.SECONDARY,
                    false),
                new ButtonView(
                    AdminProductPanelHandler.BUTTON_PREFIX_SET_FIAT_VALUE + product.id(),
                    "💵 設定實際價值",
                    ButtonStyle.SECONDARY,
                    false),
                new ButtonView(
                    AdminProductPanelHandler.BUTTON_PREFIX_INTEGRATION_CONFIG + product.id(),
                    "🔗 接入設定",
                    ButtonStyle.SECONDARY,
                    false),
                new ButtonView(
                    AdminProductPanelHandler.BUTTON_PREFIX_DELETE_PRODUCT + product.id(),
                    "🗑️ 刪除",
                    ButtonStyle.DANGER,
                    false),
                new ButtonView(
                    AdminProductPanelHandler.BUTTON_PRODUCT_BACK,
                    "⬅️ 返回列表",
                    ButtonStyle.SECONDARY,
                    false))));
  }

  static MessageEmbed buildIntegrationConfigPanelEmbed(
      AdminProductPanelHandler.IntegrationConfigSessionState state) {
    List<EmbedView.FieldView> fields =
        new ArrayList<>(
            List.of(
                new EmbedView.FieldView(
                    "商品", state.productName + " (`" + state.productId + "`)", false),
                new EmbedView.FieldView(
                    "自動護航開單", state.autoCreateEscortOrder ? "已啟用" : "未啟用", true),
                new EmbedView.FieldView(
                    "護航選項代碼",
                    state.escortOptionCode == null || state.escortOptionCode.isBlank()
                        ? "未設定"
                        : "`" + state.escortOptionCode + "`",
                    true)));
    if (state.statusMessage != null && !state.statusMessage.isBlank()) {
      fields.add(new EmbedView.FieldView("狀態", state.statusMessage, false));
    }
    return PanelComponentRenderer.buildEmbed(
        new EmbedView("🔗 接入設定面板", "調整設定後按「確認送出」才會套用", EMBED_COLOR, fields, "確認前不會修改實際設定"));
  }

  static List<ActionRow> buildIntegrationConfigPanelComponents(
      AdminProductPanelHandler.IntegrationConfigSessionState state) {
    StringSelectMenu autoEscortSelect =
        StringSelectMenu.create(AdminProductPanelHandler.SELECT_INTEGRATION_PANEL_AUTO_ESCORT)
            .setPlaceholder("選擇是否啟用自動護航開單")
            .addOption("啟用", "true", "需要設定護航選項")
            .addOption("停用", "false", "不進行自動護航開單")
            .setDefaultValues(List.of(Boolean.toString(state.autoCreateEscortOrder)))
            .build();

    List<EscortOrderOptionCatalog.EscortOrderOption> allOptions =
        EscortOrderOptionCatalog.allOptions();
    int primaryLimit = Math.min(24, allOptions.size());
    List<EscortOrderOptionCatalog.EscortOrderOption> primaryOptions =
        allOptions.subList(0, primaryLimit);
    List<EscortOrderOptionCatalog.EscortOrderOption> extraOptions =
        allOptions.size() > primaryLimit
            ? allOptions.subList(primaryLimit, allOptions.size())
            : List.of();

    String selectedCode =
        state.escortOptionCode == null || state.escortOptionCode.isBlank()
            ? "__none__"
            : state.escortOptionCode;
    boolean selectedInPrimary = "__none__".equals(selectedCode);
    boolean selectedInExtra = false;
    for (var option : primaryOptions) {
      if (option.code().equals(selectedCode)) {
        selectedInPrimary = true;
        break;
      }
    }
    if (!selectedInPrimary) {
      for (var option : extraOptions) {
        if (option.code().equals(selectedCode)) {
          selectedInExtra = true;
          break;
        }
      }
    }

    StringSelectMenu.Builder escortOptionPrimaryBuilder =
        StringSelectMenu.create(AdminProductPanelHandler.SELECT_INTEGRATION_PANEL_ESCORT_OPTION)
            .setPlaceholder("選擇護航選項代碼（主列表）")
            .setDisabled(!state.autoCreateEscortOrder);
    escortOptionPrimaryBuilder.addOption("不設定", "__none__", "清除護航選項");
    for (var option : primaryOptions) {
      String label = truncate(option.code() + "｜" + option.target(), 100);
      String description =
          truncate(
              String.format("%s｜%s｜NT$%,d", option.type(), option.level(), option.priceTwd()), 100);
      escortOptionPrimaryBuilder.addOption(label, option.code(), description);
    }
    escortOptionPrimaryBuilder.setDefaultValues(
        List.of(selectedInPrimary ? selectedCode : "__none__"));
    StringSelectMenu escortOptionPrimary = escortOptionPrimaryBuilder.build();

    StringSelectMenu escortOptionExtra = null;
    if (!extraOptions.isEmpty()) {
      StringSelectMenu.Builder escortOptionExtraBuilder =
          StringSelectMenu.create(
                  AdminProductPanelHandler.SELECT_INTEGRATION_PANEL_ESCORT_OPTION_EXTRA)
              .setPlaceholder("選擇護航選項代碼（更多）")
              .setDisabled(!state.autoCreateEscortOrder);
      for (var option : extraOptions) {
        String label = truncate(option.code() + "｜" + option.target(), 100);
        String description =
            truncate(
                String.format("%s｜%s｜NT$%,d", option.type(), option.level(), option.priceTwd()),
                100);
        escortOptionExtraBuilder.addOption(label, option.code(), description);
      }
      if (selectedInExtra) {
        escortOptionExtraBuilder.setDefaultValues(List.of(selectedCode));
      }
      escortOptionExtra = escortOptionExtraBuilder.build();
    }

    List<ActionRow> rows = new ArrayList<>();
    rows.add(PanelComponentRenderer.buildRow(autoEscortSelect));
    rows.add(PanelComponentRenderer.buildRow(escortOptionPrimary));
    if (escortOptionExtra != null) {
      rows.add(PanelComponentRenderer.buildRow(escortOptionExtra));
    }
    rows.add(
        PanelComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(
                    AdminProductPanelHandler.BUTTON_INTEGRATION_PANEL_CONFIRM,
                    "✅ 確認送出",
                    ButtonStyle.SUCCESS,
                    !canSubmitIntegrationConfig(state)),
                new ButtonView(
                    AdminProductPanelHandler.BUTTON_INTEGRATION_PANEL_CLOSE,
                    "✖ 關閉",
                    ButtonStyle.SECONDARY,
                    false))));
    return rows;
  }

  static MessageEmbed buildCodeListEmbed(Product product, RedemptionService.CodePage codePage) {
    String description;
    if (codePage.isEmpty()) {
      description = "目前沒有任何兌換碼";
    } else {
      StringBuilder sb = new StringBuilder();
      for (RedemptionCode code : codePage.codes()) {
        sb.append("`").append(code.code()).append("`");
        if (code.isRedeemed()) {
          sb.append(" ✅ 已使用");
        } else if (code.isExpired()) {
          sb.append(" ⏰ 已過期");
        } else {
          sb.append(" 🟢 可使用");
        }
        sb.append(" (數量:").append(code.quantity()).append(")\n");
      }
      description = sb.toString();
    }

    return PanelComponentRenderer.buildEmbed(
        new EmbedView(
            "📋 " + product.name() + " 的兌換碼",
            description,
            EMBED_COLOR,
            List.of(),
            codePage.formatPageIndicator()));
  }

  static List<ActionRow> buildCodeListComponents(RedemptionService.CodePage codePage) {
    List<ButtonView> navButtons = new ArrayList<>();
    navButtons.add(
        new ButtonView(
            AdminProductPanelHandler.BUTTON_CODE_BACK, "⬅️ 返回商品", ButtonStyle.SECONDARY, false));

    if (codePage.hasPreviousPage()) {
      navButtons.add(
          new ButtonView(
              AdminProductPanelHandler.BUTTON_PREFIX_CODE_PAGE + (codePage.currentPage() - 1),
              "上一頁",
              ButtonStyle.SECONDARY,
              false));
    }

    if (codePage.hasNextPage()) {
      navButtons.add(
          new ButtonView(
              AdminProductPanelHandler.BUTTON_PREFIX_CODE_PAGE + (codePage.currentPage() + 1),
              "下一頁",
              ButtonStyle.SECONDARY,
              false));
    }

    return List.of(PanelComponentRenderer.buildActionRow(navButtons));
  }

  static List<ActionRow> buildProductListComponents(List<Product> products) {
    List<ActionRow> rows = new ArrayList<>();

    if (!products.isEmpty()) {
      rows.addAll(
          SelectMenuUtil.buildSelectRows(
              AdminProductPanelHandler.SELECT_PRODUCT,
              "選擇商品查看詳情",
              products,
              (builder, product) -> {
                String label = product.name();
                if (label.length() > 25) {
                  label = label.substring(0, 22) + "...";
                }
                String description = product.hasReward() ? product.formatReward() : "無自動獎勵";
                if (description.length() > 50) {
                  description = description.substring(0, 47) + "...";
                }
                builder.addOption(label, String.valueOf(product.id()), description);
              }));
    }

    rows.add(
        PanelComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(
                    AdminProductPanelHandler.BUTTON_CREATE_PRODUCT,
                    "➕ 建立商品",
                    ButtonStyle.SUCCESS,
                    false),
                new ButtonView(
                    AdminPanelButtonHandler.BUTTON_BACK,
                    "⬅️ 返回主選單",
                    ButtonStyle.SECONDARY,
                    false))));
    return rows;
  }

  static boolean canSubmitIntegrationConfig(
      AdminProductPanelHandler.IntegrationConfigSessionState state) {
    if (!state.autoCreateEscortOrder) {
      return true;
    }
    return state.escortOptionCode != null && !state.escortOptionCode.isBlank();
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, Math.max(0, maxLength - 3)) + "...";
  }
}
