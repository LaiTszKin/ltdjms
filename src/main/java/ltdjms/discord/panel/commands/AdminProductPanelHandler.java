package ltdjms.discord.panel.commands;

import java.awt.Color;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.EmbedView;
import ltdjms.discord.panel.components.PanelComponentRenderer;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import ltdjms.discord.product.domain.EscortOrderOptionCatalog;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.redemption.domain.RedemptionCode;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.redemption.services.RedemptionService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

/**
 * Handles button, select menu, and modal interactions for product and redemption code management.
 */
public class AdminProductPanelHandler extends ListenerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(AdminProductPanelHandler.class);

  private static final Color EMBED_COLOR = new Color(0xED4245);
  private static final int PAGE_SIZE = 10;

  // Button IDs
  public static final String BUTTON_PRODUCTS = "admin_panel_products";
  public static final String BUTTON_CREATE_PRODUCT = "admin_create_product";
  public static final String BUTTON_GENERATE_CODES = "admin_generate_codes";
  public static final String BUTTON_VIEW_CODES = "admin_view_codes";
  public static final String BUTTON_PRODUCT_BACK = "admin_product_back";
  public static final String BUTTON_PREFIX_EDIT_PRODUCT = "admin_edit_product_";
  public static final String BUTTON_PREFIX_DELETE_PRODUCT = "admin_delete_product_";
  public static final String BUTTON_PREFIX_SET_FIAT_VALUE = "admin_set_fiat_value_";
  public static final String BUTTON_PREFIX_INTEGRATION_CONFIG = "admin_integration_config_";
  public static final String BUTTON_PREFIX_CODE_PAGE = "admin_code_page_";
  public static final String BUTTON_CODE_BACK = "admin_code_back";
  public static final String BUTTON_INTEGRATION_PANEL_EDIT_BACKEND =
      "admin_integration_panel_edit_backend";
  public static final String BUTTON_INTEGRATION_PANEL_CONFIRM = "admin_integration_panel_confirm";
  public static final String BUTTON_INTEGRATION_PANEL_CLOSE = "admin_integration_panel_close";

  // Select Menu IDs
  public static final String SELECT_PRODUCT = "admin_select_product";
  public static final String SELECT_INTEGRATION_PANEL_AUTO_ESCORT =
      "admin_select_integration_panel_auto_escort";
  public static final String SELECT_INTEGRATION_PANEL_ESCORT_OPTION =
      "admin_select_integration_panel_escort_option";
  public static final String SELECT_INTEGRATION_PANEL_ESCORT_OPTION_EXTRA =
      "admin_select_integration_panel_escort_option_extra";

  // Modal IDs
  public static final String MODAL_CREATE_PRODUCT = "admin_modal_create_product";
  public static final String MODAL_EDIT_PRODUCT = "admin_modal_edit_product_";
  public static final String MODAL_SET_FIAT_VALUE = "admin_modal_set_fiat_value_";
  public static final String MODAL_INTEGRATION_CONFIG = "admin_modal_integration_config_";
  public static final String MODAL_GENERATE_CODES = "admin_modal_generate_codes_";
  public static final String MODAL_INTEGRATION_PANEL_BACKEND_URL =
      "admin_modal_integration_panel_backend_url";

  private final ProductService productService;
  private final RedemptionService redemptionService;
  private final AdminPanelSessionManager adminPanelSessionManager;

  // Session state for product selection (keyed by "userId_guildId")
  private final Map<String, ProductSessionState> productSessions = new ConcurrentHashMap<>();
  private final Map<String, IntegrationConfigSessionState> integrationConfigSessions =
      new ConcurrentHashMap<>();

  public AdminProductPanelHandler(
      ProductService productService,
      RedemptionService redemptionService,
      AdminPanelSessionManager adminPanelSessionManager) {
    this.productService = productService;
    this.redemptionService = redemptionService;
    this.adminPanelSessionManager = adminPanelSessionManager;
  }

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String buttonId = event.getComponentId();

    if (!isProductPanelButton(buttonId)) {
      return;
    }

    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    if (!isAdmin(event.getMember(), event.getGuild())) {
      event.reply("你沒有權限使用管理面板").setEphemeral(true).queue();
      return;
    }

    LOG.debug(
        "Processing product panel button: buttonId={}, userId={}",
        buttonId,
        event.getUser().getIdLong());

    try {
      if (buttonId.equals(BUTTON_PRODUCTS)) {
        showProductList(event);
      } else if (buttonId.equals(BUTTON_CREATE_PRODUCT)) {
        openCreateProductModal(event);
      } else if (buttonId.equals(BUTTON_GENERATE_CODES)) {
        openGenerateCodesModal(event);
      } else if (buttonId.equals(BUTTON_VIEW_CODES)) {
        showCodeList(event, 1);
      } else if (buttonId.equals(BUTTON_PRODUCT_BACK)) {
        showProductList(event);
      } else if (buttonId.equals(BUTTON_CODE_BACK)) {
        showProductDetail(event);
      } else if (buttonId.equals(BUTTON_INTEGRATION_PANEL_EDIT_BACKEND)) {
        openIntegrationPanelBackendModal(event);
      } else if (buttonId.equals(BUTTON_INTEGRATION_PANEL_CONFIRM)) {
        handleIntegrationPanelConfirm(event);
      } else if (buttonId.equals(BUTTON_INTEGRATION_PANEL_CLOSE)) {
        handleIntegrationPanelClose(event);
      } else if (buttonId.startsWith(BUTTON_PREFIX_EDIT_PRODUCT)) {
        String productIdStr = buttonId.substring(BUTTON_PREFIX_EDIT_PRODUCT.length());
        openEditProductModal(event, Long.parseLong(productIdStr));
      } else if (buttonId.startsWith(BUTTON_PREFIX_DELETE_PRODUCT)) {
        String productIdStr = buttonId.substring(BUTTON_PREFIX_DELETE_PRODUCT.length());
        handleDeleteProduct(event, Long.parseLong(productIdStr));
      } else if (buttonId.startsWith(BUTTON_PREFIX_SET_FIAT_VALUE)) {
        String productIdStr = buttonId.substring(BUTTON_PREFIX_SET_FIAT_VALUE.length());
        openSetFiatValueModal(event, Long.parseLong(productIdStr));
      } else if (buttonId.startsWith(BUTTON_PREFIX_INTEGRATION_CONFIG)) {
        String productIdStr = buttonId.substring(BUTTON_PREFIX_INTEGRATION_CONFIG.length());
        openIntegrationConfigPanel(event, Long.parseLong(productIdStr));
      } else if (buttonId.startsWith(BUTTON_PREFIX_CODE_PAGE)) {
        String pageStr = buttonId.substring(BUTTON_PREFIX_CODE_PAGE.length());
        showCodeList(event, Integer.parseInt(pageStr));
      }
    } catch (Exception e) {
      LOG.error("Error handling product panel button: {}", buttonId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  @Override
  public void onStringSelectInteraction(StringSelectInteractionEvent event) {
    String selectId = event.getComponentId();

    if (!selectId.equals(SELECT_PRODUCT)
        && !selectId.equals(SELECT_INTEGRATION_PANEL_AUTO_ESCORT)
        && !selectId.equals(SELECT_INTEGRATION_PANEL_ESCORT_OPTION)
        && !selectId.equals(SELECT_INTEGRATION_PANEL_ESCORT_OPTION_EXTRA)) {
      return;
    }

    if (!event.isFromGuild() || event.getGuild() == null) {
      return;
    }

    if (!isAdmin(event.getMember(), event.getGuild())) {
      event.reply("你沒有權限使用管理面板").setEphemeral(true).queue();
      return;
    }

    try {
      if (selectId.equals(SELECT_PRODUCT)) {
        handleProductSelect(event);
      } else if (selectId.equals(SELECT_INTEGRATION_PANEL_AUTO_ESCORT)) {
        handleIntegrationPanelAutoEscortSelect(event);
      } else if (selectId.equals(SELECT_INTEGRATION_PANEL_ESCORT_OPTION)
          || selectId.equals(SELECT_INTEGRATION_PANEL_ESCORT_OPTION_EXTRA)) {
        handleIntegrationPanelEscortOptionSelect(event);
      }
    } catch (Exception e) {
      LOG.error("Error handling string select: {}", selectId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  @Override
  public void onModalInteraction(ModalInteractionEvent event) {
    String modalId = event.getModalId();

    if (!modalId.startsWith("admin_modal_")) {
      return;
    }

    if (!isProductPanelModal(modalId)) {
      return;
    }

    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    if (!isAdmin(event.getMember(), event.getGuild())) {
      event.reply("你沒有權限使用管理面板").setEphemeral(true).queue();
      return;
    }

    try {
      if (modalId.equals(MODAL_CREATE_PRODUCT)) {
        handleCreateProductModal(event);
      } else if (modalId.startsWith(MODAL_EDIT_PRODUCT)) {
        handleEditProductModal(event);
      } else if (modalId.startsWith(MODAL_SET_FIAT_VALUE)) {
        handleSetFiatValueModal(event);
      } else if (modalId.startsWith(MODAL_INTEGRATION_CONFIG)) {
        handleIntegrationConfigModal(event);
      } else if (modalId.startsWith(MODAL_GENERATE_CODES)) {
        handleGenerateCodesModal(event);
      } else if (modalId.startsWith(MODAL_INTEGRATION_PANEL_BACKEND_URL)) {
        handleIntegrationPanelBackendModal(event);
      }
    } catch (Exception e) {
      LOG.error("Error handling modal: {}", modalId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  private boolean isProductPanelButton(String buttonId) {
    return buttonId.equals(BUTTON_PRODUCTS)
        || buttonId.equals(BUTTON_CREATE_PRODUCT)
        || buttonId.equals(BUTTON_GENERATE_CODES)
        || buttonId.equals(BUTTON_VIEW_CODES)
        || buttonId.equals(BUTTON_PRODUCT_BACK)
        || buttonId.equals(BUTTON_CODE_BACK)
        || buttonId.equals(BUTTON_INTEGRATION_PANEL_EDIT_BACKEND)
        || buttonId.equals(BUTTON_INTEGRATION_PANEL_CONFIRM)
        || buttonId.equals(BUTTON_INTEGRATION_PANEL_CLOSE)
        || buttonId.startsWith(BUTTON_PREFIX_EDIT_PRODUCT)
        || buttonId.startsWith(BUTTON_PREFIX_DELETE_PRODUCT)
        || buttonId.startsWith(BUTTON_PREFIX_SET_FIAT_VALUE)
        || buttonId.startsWith(BUTTON_PREFIX_INTEGRATION_CONFIG)
        || buttonId.startsWith(BUTTON_PREFIX_CODE_PAGE);
  }

  private boolean isProductPanelModal(String modalId) {
    return modalId.equals(MODAL_CREATE_PRODUCT)
        || modalId.startsWith(MODAL_EDIT_PRODUCT)
        || modalId.startsWith(MODAL_SET_FIAT_VALUE)
        || modalId.startsWith(MODAL_INTEGRATION_CONFIG)
        || modalId.startsWith(MODAL_GENERATE_CODES)
        || modalId.startsWith(MODAL_INTEGRATION_PANEL_BACKEND_URL);
  }

  // ===== Product List =====

  private void showProductList(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    productSessions.remove(sessionKey);
    integrationConfigSessions.remove(sessionKey);

    List<Product> products = productService.getProducts(guildId);

    MessageEmbed embed = buildProductListEmbed(products);
    event.editMessageEmbeds(embed).setComponents(buildProductListComponents(products)).queue();
  }

  private MessageEmbed buildProductListEmbed(List<Product> products) {
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

  // ===== Product Selection and Detail =====

  private void handleProductSelect(StringSelectInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);

    long productId = Long.parseLong(event.getValues().get(0));
    productService
        .getProduct(productId)
        .ifPresentOrElse(
            product -> {
              productSessions.put(
                  sessionKey, new ProductSessionState(productId, ProductView.DETAIL, 1));
              showProductDetailEmbed(event, product);
            },
            () -> event.reply("找不到該商品").setEphemeral(true).queue());
  }

  private void showProductDetail(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);

    ProductSessionState session = productSessions.get(sessionKey);
    if (session == null) {
      showProductList(event);
      return;
    }

    productSessions.put(sessionKey, session.withView(ProductView.DETAIL, 1));

    productService
        .getProduct(session.productId)
        .ifPresentOrElse(
            product -> {
              MessageEmbed embed = buildProductDetailEmbed(product);
              RedemptionCodeRepository.CodeStats stats =
                  redemptionService.getCodeStats(product.id());

              event
                  .editMessageEmbeds(embed)
                  .setComponents(buildProductDetailComponents(product, stats))
                  .queue();
            },
            () -> {
              productSessions.remove(sessionKey);
              showProductList(event);
            });
  }

  private void showProductDetailEmbed(StringSelectInteractionEvent event, Product product) {
    String sessionKey = getSessionKey(event.getUser().getIdLong(), event.getGuild().getIdLong());
    productSessions.put(sessionKey, new ProductSessionState(product.id(), ProductView.DETAIL, 1));

    MessageEmbed embed = buildProductDetailEmbed(product);
    RedemptionCodeRepository.CodeStats stats = redemptionService.getCodeStats(product.id());

    event
        .editMessageEmbeds(embed)
        .setComponents(buildProductDetailComponents(product, stats))
        .queue();
  }

  private MessageEmbed buildProductDetailEmbed(Product product) {
    List<EmbedView.FieldView> fields = new java.util.ArrayList<>();

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
            "後端履約 API",
            product.hasBackendApiIntegration() ? product.backendApiUrl() : "未設定",
            false));
    fields.add(
        new EmbedView.FieldView(
            "自動護航開單",
            product.shouldAutoCreateEscortOrder()
                ? "已啟用\n選項代碼：" + product.escortOptionCode()
                : "未啟用",
            false));

    RedemptionCodeRepository.CodeStats stats = redemptionService.getCodeStats(product.id());
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

  private List<ActionRow> buildProductDetailComponents(
      Product product, RedemptionCodeRepository.CodeStats stats) {
    return PanelComponentRenderer.buildActionRows(
        List.of(
            List.of(
                new ButtonView(BUTTON_GENERATE_CODES, "🎫 生成兌換碼", ButtonStyle.SUCCESS, false),
                new ButtonView(
                    BUTTON_VIEW_CODES, "📋 查看兌換碼", ButtonStyle.PRIMARY, stats.totalCount() <= 0)),
            List.of(
                new ButtonView(
                    BUTTON_PREFIX_EDIT_PRODUCT + product.id(),
                    "✏️ 編輯",
                    ButtonStyle.SECONDARY,
                    false),
                new ButtonView(
                    BUTTON_PREFIX_SET_FIAT_VALUE + product.id(),
                    "💵 設定實際價值",
                    ButtonStyle.SECONDARY,
                    false),
                new ButtonView(
                    BUTTON_PREFIX_INTEGRATION_CONFIG + product.id(),
                    "🔗 接入設定",
                    ButtonStyle.SECONDARY,
                    false),
                new ButtonView(
                    BUTTON_PREFIX_DELETE_PRODUCT + product.id(),
                    "🗑️ 刪除",
                    ButtonStyle.DANGER,
                    false),
                new ButtonView(BUTTON_PRODUCT_BACK, "⬅️ 返回列表", ButtonStyle.SECONDARY, false))));
  }

  // ===== Create Product =====

  private void openCreateProductModal(ButtonInteractionEvent event) {
    TextInput nameInput =
        TextInput.create("name", "商品名稱", TextInputStyle.SHORT)
            .setPlaceholder("輸入商品名稱")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(100)
            .build();

    TextInput rewardTypeInput =
        TextInput.create("reward_type", "獎勵類型", TextInputStyle.SHORT)
            .setPlaceholder("CURRENCY 或 TOKEN（留空表示無自動獎勵）")
            .setRequired(false)
            .setMaxLength(20)
            .build();

    TextInput rewardAmountInput =
        TextInput.create("reward_amount", "獎勵數量", TextInputStyle.SHORT)
            .setPlaceholder("輸入獎勵數量（留空表示無自動獎勵）")
            .setRequired(false)
            .setMaxLength(15)
            .build();

    TextInput currencyPriceInput =
        TextInput.create("currency_price", "貨幣價格", TextInputStyle.SHORT)
            .setPlaceholder("輸入貨幣購買價格（留空表示不可用貨幣購買）")
            .setRequired(false)
            .setMaxLength(15)
            .build();

    TextInput fiatPriceInput =
        TextInput.create("fiat_price_twd", "實際價值（TWD）", TextInputStyle.SHORT)
            .setPlaceholder("輸入新台幣金額（留空表示非法幣商品）")
            .setRequired(false)
            .setMaxLength(15)
            .build();

    Modal modal =
        Modal.create(MODAL_CREATE_PRODUCT, "建立商品")
            .addComponents(
                ActionRow.of(nameInput),
                ActionRow.of(rewardTypeInput),
                ActionRow.of(rewardAmountInput),
                ActionRow.of(currencyPriceInput),
                ActionRow.of(fiatPriceInput))
            .build();

    event.replyModal(modal).queue();
  }

  private void handleCreateProductModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    String name = event.getValue("name").getAsString().trim();
    String description = null;
    String rewardTypeStr = getModalValueOrNull(event, "reward_type");
    String rewardAmountStr = getModalValueOrNull(event, "reward_amount");
    String currencyPriceStr = getModalValueOrNull(event, "currency_price");
    String fiatPriceTwdStr = getModalValueOrNull(event, "fiat_price_twd");

    // Parse reward type and amount
    Product.RewardType rewardType = null;
    Long rewardAmount = null;

    if (rewardTypeStr != null && !rewardTypeStr.isBlank()) {
      try {
        rewardType = Product.RewardType.valueOf(rewardTypeStr.toUpperCase());
      } catch (IllegalArgumentException e) {
        event.reply("獎勵類型無效，請輸入 CURRENCY 或 TOKEN").setEphemeral(true).queue();
        return;
      }
    }

    if (rewardAmountStr != null && !rewardAmountStr.isBlank()) {
      try {
        rewardAmount = Long.parseLong(rewardAmountStr);
      } catch (NumberFormatException e) {
        event.reply("獎勵數量格式錯誤，請輸入有效數字").setEphemeral(true).queue();
        return;
      }
    }

    // Parse currency price
    Long currencyPrice = null;
    if (currencyPriceStr != null && !currencyPriceStr.isBlank()) {
      try {
        currencyPrice = Long.parseLong(currencyPriceStr);
      } catch (NumberFormatException e) {
        event.reply("貨幣價格格式錯誤，請輸入有效數字").setEphemeral(true).queue();
        return;
      }
    }

    Long fiatPriceTwd = null;
    if (fiatPriceTwdStr != null && !fiatPriceTwdStr.isBlank()) {
      try {
        fiatPriceTwd = Long.parseLong(fiatPriceTwdStr);
      } catch (NumberFormatException e) {
        event.reply("實際價值（TWD）格式錯誤，請輸入有效數字").setEphemeral(true).queue();
        return;
      }
    }

    Result<Product, DomainError> result =
        productService.createProduct(
            guildId, name, description, rewardType, rewardAmount, currencyPrice, fiatPriceTwd);

    if (result.isErr()) {
      event.reply("建立失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    Product product = result.getValue();

    long adminId = event.getUser().getIdLong();
    adminPanelSessionManager.updatePanel(
        guildId, adminId, hook -> refreshProductListView(hook, guildId, adminId));

    event.reply(String.format("✅ 商品「%s」建立成功！", product.name())).setEphemeral(true).queue();
  }

  // ===== Edit Product =====

  private void openEditProductModal(ButtonInteractionEvent event, long productId) {
    productService
        .getProduct(productId)
        .ifPresentOrElse(
            product -> {
              TextInput nameInput =
                  TextInput.create("name", "商品名稱", TextInputStyle.SHORT)
                      .setValue(product.name())
                      .setRequired(true)
                      .setMinLength(1)
                      .setMaxLength(100)
                      .build();

              String description = product.description();
              TextInput descInput =
                  TextInput.create("description", "商品描述", TextInputStyle.PARAGRAPH)
                      .setPlaceholder("輸入商品描述（選填）")
                      .setRequired(false)
                      .setMaxLength(500)
                      .build();
              if (description != null && !description.isBlank()) {
                descInput =
                    TextInput.create("description", "商品描述", TextInputStyle.PARAGRAPH)
                        .setValue(description)
                        .setRequired(false)
                        .setMaxLength(500)
                        .build();
              }

              String rewardTypeValue =
                  product.rewardType() != null ? product.rewardType().name() : "";
              TextInput rewardTypeInput =
                  TextInput.create("reward_type", "獎勵類型", TextInputStyle.SHORT)
                      .setPlaceholder("CURRENCY 或 TOKEN（留空表示無自動獎勵）")
                      .setRequired(false)
                      .setMaxLength(20)
                      .build();
              if (!rewardTypeValue.isBlank()) {
                rewardTypeInput =
                    TextInput.create("reward_type", "獎勵類型", TextInputStyle.SHORT)
                        .setPlaceholder("CURRENCY 或 TOKEN（留空表示無自動獎勵）")
                        .setValue(rewardTypeValue)
                        .setRequired(false)
                        .setMaxLength(20)
                        .build();
              }

              String rewardAmountValue =
                  product.rewardAmount() != null ? String.valueOf(product.rewardAmount()) : "";
              TextInput rewardAmountInput =
                  TextInput.create("reward_amount", "獎勵數量", TextInputStyle.SHORT)
                      .setPlaceholder("輸入獎勵數量")
                      .setRequired(false)
                      .setMaxLength(15)
                      .build();
              if (!rewardAmountValue.isBlank()) {
                rewardAmountInput =
                    TextInput.create("reward_amount", "獎勵數量", TextInputStyle.SHORT)
                        .setPlaceholder("輸入獎勵數量")
                        .setValue(rewardAmountValue)
                        .setRequired(false)
                        .setMaxLength(15)
                        .build();
              }

              String currencyPriceValue =
                  product.currencyPrice() != null ? String.valueOf(product.currencyPrice()) : "";
              TextInput currencyPriceInput =
                  TextInput.create("currency_price", "貨幣價格", TextInputStyle.SHORT)
                      .setPlaceholder("輸入貨幣購買價格（留空表示不可用貨幣購買）")
                      .setRequired(false)
                      .setMaxLength(15)
                      .build();
              if (!currencyPriceValue.isBlank()) {
                currencyPriceInput =
                    TextInput.create("currency_price", "貨幣價格", TextInputStyle.SHORT)
                        .setPlaceholder("輸入貨幣購買價格（留空表示不可用貨幣購買）")
                        .setValue(currencyPriceValue)
                        .setRequired(false)
                        .setMaxLength(15)
                        .build();
              }
              Modal modal =
                  Modal.create(MODAL_EDIT_PRODUCT + productId, "編輯商品")
                      .addComponents(
                          ActionRow.of(nameInput),
                          ActionRow.of(descInput),
                          ActionRow.of(rewardTypeInput),
                          ActionRow.of(rewardAmountInput),
                          ActionRow.of(currencyPriceInput))
                      .build();

              event.replyModal(modal).queue();
            },
            () -> event.reply("找不到該商品").setEphemeral(true).queue());
  }

  private void handleEditProductModal(ModalInteractionEvent event) {
    String modalId = event.getModalId();
    long productId = Long.parseLong(modalId.substring(MODAL_EDIT_PRODUCT.length()));

    String name = event.getValue("name").getAsString().trim();
    String description = getModalValueOrNull(event, "description");
    String rewardTypeStr = getModalValueOrNull(event, "reward_type");
    String rewardAmountStr = getModalValueOrNull(event, "reward_amount");
    String currencyPriceStr = getModalValueOrNull(event, "currency_price");

    // Parse reward type and amount
    Product.RewardType rewardType = null;
    Long rewardAmount = null;

    if (rewardTypeStr != null && !rewardTypeStr.isBlank()) {
      try {
        rewardType = Product.RewardType.valueOf(rewardTypeStr.toUpperCase());
      } catch (IllegalArgumentException e) {
        event.reply("獎勵類型無效，請輸入 CURRENCY 或 TOKEN").setEphemeral(true).queue();
        return;
      }
    }

    if (rewardAmountStr != null && !rewardAmountStr.isBlank()) {
      try {
        rewardAmount = Long.parseLong(rewardAmountStr);
      } catch (NumberFormatException e) {
        event.reply("獎勵數量格式錯誤，請輸入有效數字").setEphemeral(true).queue();
        return;
      }
    }

    // Parse currency price
    Long currencyPrice = null;
    if (currencyPriceStr != null && !currencyPriceStr.isBlank()) {
      try {
        currencyPrice = Long.parseLong(currencyPriceStr);
      } catch (NumberFormatException e) {
        event.reply("貨幣價格格式錯誤，請輸入有效數字").setEphemeral(true).queue();
        return;
      }
    }
    Product existing = productService.getProduct(productId).orElse(null);
    if (existing == null) {
      event.reply("找不到該商品").setEphemeral(true).queue();
      return;
    }

    Result<Product, DomainError> result =
        productService.updateProduct(
            productId,
            name,
            description,
            rewardType,
            rewardAmount,
            currencyPrice,
            existing.fiatPriceTwd());

    if (result.isErr()) {
      event.reply("更新失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    event.reply("✅ 商品更新成功！").setEphemeral(true).queue();
  }

  private void openSetFiatValueModal(ButtonInteractionEvent event, long productId) {
    productService
        .getProduct(productId)
        .ifPresentOrElse(
            product -> {
              String currentValue =
                  product.fiatPriceTwd() != null ? String.valueOf(product.fiatPriceTwd()) : "";
              TextInput.Builder builder =
                  TextInput.create("fiat_price_twd", "實際價值（TWD）", TextInputStyle.SHORT)
                      .setPlaceholder("輸入新台幣金額（留空可清除）")
                      .setRequired(false)
                      .setMaxLength(15);
              if (!currentValue.isBlank()) {
                builder.setValue(currentValue);
              }
              Modal modal =
                  Modal.create(MODAL_SET_FIAT_VALUE + productId, "設定實際價值（TWD）")
                      .addComponents(ActionRow.of(builder.build()))
                      .build();
              event.replyModal(modal).queue();
            },
            () -> event.reply("找不到該商品").setEphemeral(true).queue());
  }

  private void handleSetFiatValueModal(ModalInteractionEvent event) {
    String modalId = event.getModalId();
    long productId = Long.parseLong(modalId.substring(MODAL_SET_FIAT_VALUE.length()));
    Product existing = productService.getProduct(productId).orElse(null);
    if (existing == null) {
      event.reply("找不到該商品").setEphemeral(true).queue();
      return;
    }

    String fiatPriceTwdStr = getModalValueOrNull(event, "fiat_price_twd");
    Long fiatPriceTwd = null;
    if (fiatPriceTwdStr != null && !fiatPriceTwdStr.isBlank()) {
      try {
        fiatPriceTwd = Long.parseLong(fiatPriceTwdStr);
      } catch (NumberFormatException e) {
        event.reply("實際價值（TWD）格式錯誤，請輸入有效數字").setEphemeral(true).queue();
        return;
      }
    }

    Result<Product, DomainError> result =
        productService.updateProduct(
            productId,
            existing.name(),
            existing.description(),
            existing.rewardType(),
            existing.rewardAmount(),
            existing.currencyPrice(),
            fiatPriceTwd);
    if (result.isErr()) {
      event.reply("更新失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }
    if (fiatPriceTwd == null) {
      event.reply("✅ 已清除實際價值（TWD）").setEphemeral(true).queue();
      return;
    }
    event.reply("✅ 已更新實際價值（TWD）").setEphemeral(true).queue();
  }

  private void openIntegrationConfigPanel(ButtonInteractionEvent event, long productId) {
    productService
        .getProduct(productId)
        .ifPresentOrElse(
            product -> {
              long guildId = event.getGuild().getIdLong();
              String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
              IntegrationConfigSessionState state =
                  new IntegrationConfigSessionState(
                      product.id(),
                      product.name(),
                      product.backendApiUrl(),
                      product.autoCreateEscortOrder(),
                      product.escortOptionCode(),
                      null);
              integrationConfigSessions.put(sessionKey, state);

              event
                  .replyEmbeds(buildIntegrationConfigPanelEmbed(state))
                  .setComponents(buildIntegrationConfigPanelComponents(state))
                  .setEphemeral(true)
                  .queue(
                      hook -> {
                        state.panelHook = hook;
                        integrationConfigSessions.put(sessionKey, state);
                      });
            },
            () -> event.reply("找不到該商品").setEphemeral(true).queue());
  }

  private void handleIntegrationPanelAutoEscortSelect(StringSelectInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    IntegrationConfigSessionState state = integrationConfigSessions.get(sessionKey);
    if (state == null) {
      event.reply("設定面板已過期，請重新開啟").setEphemeral(true).queue();
      return;
    }

    state.autoCreateEscortOrder = Boolean.parseBoolean(event.getValues().get(0));
    if (!state.autoCreateEscortOrder) {
      state.escortOptionCode = null;
    }
    state.statusMessage = "✅ 已更新自動護航開單設定（尚未送出）";

    event
        .editMessageEmbeds(buildIntegrationConfigPanelEmbed(state))
        .setComponents(buildIntegrationConfigPanelComponents(state))
        .queue();
  }

  private void handleIntegrationPanelEscortOptionSelect(StringSelectInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    IntegrationConfigSessionState state = integrationConfigSessions.get(sessionKey);
    if (state == null) {
      event.reply("設定面板已過期，請重新開啟").setEphemeral(true).queue();
      return;
    }

    String selected = event.getValues().get(0);
    state.escortOptionCode = "__none__".equals(selected) ? null : selected;
    state.statusMessage = "✅ 已更新護航選項（尚未送出）";

    event
        .editMessageEmbeds(buildIntegrationConfigPanelEmbed(state))
        .setComponents(buildIntegrationConfigPanelComponents(state))
        .queue();
  }

  private void openIntegrationPanelBackendModal(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    IntegrationConfigSessionState state = integrationConfigSessions.get(sessionKey);
    if (state == null) {
      event.reply("設定面板已過期，請重新開啟").setEphemeral(true).queue();
      return;
    }

    TextInput.Builder inputBuilder =
        TextInput.create("backend_api_url", "後端 API URL", TextInputStyle.SHORT)
            .setPlaceholder("https://example.com/fulfillment")
            .setRequired(false)
            .setMaxLength(500);
    if (state.backendApiUrl != null && !state.backendApiUrl.isBlank()) {
      inputBuilder.setValue(state.backendApiUrl);
    }

    Modal modal =
        Modal.create(MODAL_INTEGRATION_PANEL_BACKEND_URL, "設定後端 API URL")
            .addComponents(ActionRow.of(inputBuilder.build()))
            .build();
    event.replyModal(modal).queue();
  }

  private void handleIntegrationPanelBackendModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    IntegrationConfigSessionState state = integrationConfigSessions.get(sessionKey);
    if (state == null) {
      event.reply("設定面板已過期，請重新開啟").setEphemeral(true).queue();
      return;
    }

    state.backendApiUrl = getModalValueOrNull(event, "backend_api_url");
    state.statusMessage = "✅ 已更新後端 API URL（尚未送出）";
    if (state.panelHook != null) {
      state
          .panelHook
          .editOriginalEmbeds(buildIntegrationConfigPanelEmbed(state))
          .setComponents(buildIntegrationConfigPanelComponents(state))
          .queue(
              msg ->
                  LOG.trace("Updated integration config panel for productId={}", state.productId),
              err -> LOG.warn("Failed to update integration config panel", err));
    }

    event.reply("✅ 已回填後端 API URL，請回設定面板按「確認送出」").setEphemeral(true).queue();
  }

  private void handleIntegrationPanelConfirm(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    IntegrationConfigSessionState state = integrationConfigSessions.get(sessionKey);
    if (state == null) {
      event.reply("設定面板已過期，請重新開啟").setEphemeral(true).queue();
      return;
    }
    if (!canSubmitIntegrationConfig(state)) {
      state.statusMessage = "❌ 請先完成必要欄位（後端 URL、護航選項）";
      event
          .editMessageEmbeds(buildIntegrationConfigPanelEmbed(state))
          .setComponents(buildIntegrationConfigPanelComponents(state))
          .queue();
      return;
    }

    Product existing = productService.getProduct(state.productId).orElse(null);
    if (existing == null) {
      state.statusMessage = "❌ 找不到商品，請返回商品列表後重試";
      event
          .editMessageEmbeds(buildIntegrationConfigPanelEmbed(state))
          .setComponents(buildIntegrationConfigPanelComponents(state))
          .queue();
      return;
    }

    String finalEscortOptionCode = state.autoCreateEscortOrder ? state.escortOptionCode : null;
    Result<Product, DomainError> result =
        productService.updateProduct(
            state.productId,
            existing.name(),
            existing.description(),
            existing.rewardType(),
            existing.rewardAmount(),
            existing.currencyPrice(),
            existing.fiatPriceTwd(),
            state.backendApiUrl,
            state.autoCreateEscortOrder,
            finalEscortOptionCode);
    if (result.isErr()) {
      state.statusMessage = "❌ 更新失敗：" + result.getError().message();
      event
          .editMessageEmbeds(buildIntegrationConfigPanelEmbed(state))
          .setComponents(buildIntegrationConfigPanelComponents(state))
          .queue();
      return;
    }

    Product updated = result.getValue();
    state.backendApiUrl = updated.backendApiUrl();
    state.autoCreateEscortOrder = updated.autoCreateEscortOrder();
    state.escortOptionCode = updated.escortOptionCode();
    String backendStatus =
        updated.backendApiUrl() == null || updated.backendApiUrl().isBlank() ? "未設定" : "已設定";
    String escortStatus =
        updated.autoCreateEscortOrder() ? "已啟用（" + updated.escortOptionCode() + "）" : "未啟用";
    state.statusMessage =
        String.format("✅ 接入設定已更新：後端 API %s，自動護航開單 %s", backendStatus, escortStatus);

    refreshProductPanels(guildId);
    event
        .editMessageEmbeds(buildIntegrationConfigPanelEmbed(state))
        .setComponents(buildIntegrationConfigPanelComponents(state))
        .queue();
  }

  private void handleIntegrationPanelClose(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    integrationConfigSessions.remove(sessionKey);

    MessageEmbed closedEmbed =
        PanelComponentRenderer.buildEmbed(
            new EmbedView("🔗 接入設定面板", "已關閉設定面板", EMBED_COLOR, List.of(), null));
    event.editMessageEmbeds(closedEmbed).setComponents(List.of()).queue();
  }

  private MessageEmbed buildIntegrationConfigPanelEmbed(IntegrationConfigSessionState state) {
    List<EmbedView.FieldView> fields =
        new java.util.ArrayList<>(
            List.of(
                new EmbedView.FieldView(
                    "商品", state.productName + " (`" + state.productId + "`)", false),
                new EmbedView.FieldView(
                    "後端 API URL",
                    state.backendApiUrl == null || state.backendApiUrl.isBlank()
                        ? "未設定"
                        : state.backendApiUrl,
                    false),
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

  private List<ActionRow> buildIntegrationConfigPanelComponents(
      IntegrationConfigSessionState state) {
    StringSelectMenu autoEscortSelect =
        StringSelectMenu.create(SELECT_INTEGRATION_PANEL_AUTO_ESCORT)
            .setPlaceholder("選擇是否啟用自動護航開單")
            .addOption("啟用", "true", "需要設定後端 API 與護航選項")
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
        StringSelectMenu.create(SELECT_INTEGRATION_PANEL_ESCORT_OPTION)
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
          StringSelectMenu.create(SELECT_INTEGRATION_PANEL_ESCORT_OPTION_EXTRA)
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

    List<ActionRow> rows = new java.util.ArrayList<>();
    rows.add(PanelComponentRenderer.buildRow(autoEscortSelect));
    rows.add(PanelComponentRenderer.buildRow(escortOptionPrimary));
    if (escortOptionExtra != null) {
      rows.add(PanelComponentRenderer.buildRow(escortOptionExtra));
    }
    rows.add(
        PanelComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(
                    BUTTON_INTEGRATION_PANEL_EDIT_BACKEND,
                    "🌐 設定後端 URL",
                    ButtonStyle.SECONDARY,
                    false),
                new ButtonView(
                    BUTTON_INTEGRATION_PANEL_CONFIRM,
                    "✅ 確認送出",
                    ButtonStyle.SUCCESS,
                    !canSubmitIntegrationConfig(state)),
                new ButtonView(
                    BUTTON_INTEGRATION_PANEL_CLOSE, "✖ 關閉", ButtonStyle.SECONDARY, false))));
    return rows;
  }

  private boolean canSubmitIntegrationConfig(IntegrationConfigSessionState state) {
    if (!state.autoCreateEscortOrder) {
      return true;
    }
    return state.backendApiUrl != null
        && !state.backendApiUrl.isBlank()
        && state.escortOptionCode != null
        && !state.escortOptionCode.isBlank();
  }

  private void openIntegrationConfigModal(ButtonInteractionEvent event, long productId) {
    productService
        .getProduct(productId)
        .ifPresentOrElse(
            product -> {
              TextInput backendApiInput =
                  TextInput.create("backend_api_url", "後端 API URL", TextInputStyle.SHORT)
                      .setPlaceholder("https://example.com/fulfillment")
                      .setRequired(false)
                      .setMaxLength(500)
                      .build();
              if (product.backendApiUrl() != null && !product.backendApiUrl().isBlank()) {
                backendApiInput =
                    TextInput.create("backend_api_url", "後端 API URL", TextInputStyle.SHORT)
                        .setRequired(false)
                        .setMaxLength(500)
                        .setValue(product.backendApiUrl())
                        .build();
              }

              TextInput autoEscortInput =
                  TextInput.create("auto_create_escort_order", "自動護航開單", TextInputStyle.SHORT)
                      .setPlaceholder("true / false")
                      .setRequired(false)
                      .setMaxLength(10)
                      .setValue(Boolean.toString(product.autoCreateEscortOrder()))
                      .build();

              TextInput escortOptionInput =
                  TextInput.create("escort_option_code", "護航選項代碼", TextInputStyle.SHORT)
                      .setPlaceholder("例如：CONF_DAM_300W")
                      .setRequired(false)
                      .setMaxLength(120)
                      .build();
              if (product.escortOptionCode() != null && !product.escortOptionCode().isBlank()) {
                escortOptionInput =
                    TextInput.create("escort_option_code", "護航選項代碼", TextInputStyle.SHORT)
                        .setRequired(false)
                        .setMaxLength(120)
                        .setValue(product.escortOptionCode())
                        .build();
              }

              Modal modal =
                  Modal.create(MODAL_INTEGRATION_CONFIG + productId, "接入設定")
                      .addComponents(
                          ActionRow.of(backendApiInput),
                          ActionRow.of(autoEscortInput),
                          ActionRow.of(escortOptionInput))
                      .build();
              event.replyModal(modal).queue();
            },
            () -> event.reply("找不到該商品").setEphemeral(true).queue());
  }

  private void handleIntegrationConfigModal(ModalInteractionEvent event) {
    String modalId = event.getModalId();
    long productId = Long.parseLong(modalId.substring(MODAL_INTEGRATION_CONFIG.length()));

    Product existing = productService.getProduct(productId).orElse(null);
    if (existing == null) {
      event.reply("找不到該商品").setEphemeral(true).queue();
      return;
    }

    String backendApiUrl = getModalValueOrNull(event, "backend_api_url");
    String autoEscortRaw = getModalValueOrNull(event, "auto_create_escort_order");
    String escortOptionCode = getModalValueOrNull(event, "escort_option_code");

    Result<Boolean, DomainError> autoEscortResult = parseBooleanInput(autoEscortRaw, false);
    if (autoEscortResult.isErr()) {
      event.reply("更新失敗：" + autoEscortResult.getError().message()).setEphemeral(true).queue();
      return;
    }
    boolean autoCreateEscortOrder = autoEscortResult.getValue();

    Result<Product, DomainError> result =
        productService.updateProduct(
            productId,
            existing.name(),
            existing.description(),
            existing.rewardType(),
            existing.rewardAmount(),
            existing.currencyPrice(),
            existing.fiatPriceTwd(),
            backendApiUrl,
            autoCreateEscortOrder,
            escortOptionCode);
    if (result.isErr()) {
      event.reply("更新失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    Product updated = result.getValue();
    String backendStatus =
        updated.backendApiUrl() == null || updated.backendApiUrl().isBlank() ? "未設定" : "已設定";
    String escortStatus =
        updated.autoCreateEscortOrder() ? "已啟用（" + updated.escortOptionCode() + "）" : "未啟用";
    event
        .reply(String.format("✅ 接入設定已更新\n後端 API：%s\n自動護航開單：%s", backendStatus, escortStatus))
        .setEphemeral(true)
        .queue();
  }

  // ===== Delete Product =====

  private void handleDeleteProduct(ButtonInteractionEvent event, long productId) {
    Result<Unit, DomainError> result = productService.deleteProduct(productId);

    if (result.isErr()) {
      event.reply("刪除失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    // Clear session and show product list
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    productSessions.remove(sessionKey);

    event.reply("✅ 商品已刪除").setEphemeral(true).queue();
  }

  // ===== Generate Codes =====

  private void openGenerateCodesModal(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);

    ProductSessionState session = productSessions.get(sessionKey);
    if (session == null) {
      event.reply("請先選擇商品").setEphemeral(true).queue();
      return;
    }

    TextInput countInput =
        TextInput.create("count", "生成數量", TextInputStyle.SHORT)
            .setPlaceholder("輸入要生成的兌換碼數量（1-100）")
            .setValue("10")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(3)
            .build();

    TextInput quantityInput =
        TextInput.create("quantity", "每個碼可兌換數量", TextInputStyle.SHORT)
            .setPlaceholder("每個兌換碼可兌換的商品數量（1-1000，預設為 1）")
            .setValue("1")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(4)
            .build();

    TextInput expiresInput =
        TextInput.create("expires", "到期日期", TextInputStyle.SHORT)
            .setPlaceholder("格式：YYYY-MM-DD（留空表示永不過期）")
            .setRequired(false)
            .setMaxLength(10)
            .build();

    Modal modal =
        Modal.create(MODAL_GENERATE_CODES + session.productId, "生成兌換碼")
            .addComponents(
                ActionRow.of(countInput), ActionRow.of(quantityInput), ActionRow.of(expiresInput))
            .build();

    event.replyModal(modal).queue();
  }

  private void handleGenerateCodesModal(ModalInteractionEvent event) {
    String modalId = event.getModalId();
    long productId = Long.parseLong(modalId.substring(MODAL_GENERATE_CODES.length()));

    String countStr = event.getValue("count").getAsString().trim();
    String quantityStr = event.getValue("quantity").getAsString().trim();
    String expiresStr = getModalValueOrNull(event, "expires");

    int count;
    try {
      count = Integer.parseInt(countStr);
    } catch (NumberFormatException e) {
      event.reply("數量格式錯誤，請輸入有效數字").setEphemeral(true).queue();
      return;
    }

    int quantity;
    try {
      quantity = Integer.parseInt(quantityStr);
    } catch (NumberFormatException e) {
      event.reply("兌換數量格式錯誤，請輸入有效數字").setEphemeral(true).queue();
      return;
    }

    Instant expiresAt = null;
    if (expiresStr != null && !expiresStr.isBlank()) {
      try {
        LocalDate date = LocalDate.parse(expiresStr, DateTimeFormatter.ISO_LOCAL_DATE);
        expiresAt = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
      } catch (DateTimeParseException e) {
        event.reply("日期格式錯誤，請使用 YYYY-MM-DD 格式").setEphemeral(true).queue();
        return;
      }
    }

    Result<List<RedemptionCode>, DomainError> result =
        redemptionService.generateCodes(productId, count, expiresAt, quantity);

    if (result.isErr()) {
      event.reply("生成失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    List<RedemptionCode> codes = result.getValue();

    // Build response with generated codes
    StringBuilder sb = new StringBuilder();
    sb.append("✅ 成功生成 ").append(codes.size()).append(" 個兌換碼：\n\n```\n");
    for (RedemptionCode code : codes) {
      sb.append(code.code()).append("\n");
    }
    sb.append("```");

    if (quantity > 1) {
      sb.append("\n每個碼可兌換數量：").append(quantity);
    }

    if (expiresAt != null) {
      sb.append("\n到期日期：").append(expiresStr);
    }

    // Discord message length limit
    String response = sb.toString();
    if (response.length() > 2000) {
      response = String.format("✅ 成功生成 %d 個兌換碼！\n\n由於數量較多，請至兌換碼列表查看。", codes.size());
    }

    event.reply(response).setEphemeral(true).queue();
  }

  // ===== View Codes =====

  private void showCodeList(ButtonInteractionEvent event, int page) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);

    ProductSessionState session = productSessions.get(sessionKey);
    if (session == null) {
      showProductList(event);
      return;
    }

    productService
        .getProduct(session.productId)
        .ifPresentOrElse(
            product -> {
              RedemptionService.CodePage codePage =
                  redemptionService.getCodePage(session.productId, page, PAGE_SIZE);

              MessageEmbed embed = buildCodeListEmbed(product, codePage);
              List<ActionRow> components = buildCodeListComponents(codePage);

              event.editMessageEmbeds(embed).setComponents(components).queue();
            },
            () -> {
              productSessions.remove(sessionKey);
              showProductList(event);
            });
  }

  private MessageEmbed buildCodeListEmbed(Product product, RedemptionService.CodePage codePage) {
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

  private List<ActionRow> buildCodeListComponents(RedemptionService.CodePage codePage) {
    List<ButtonView> navButtons = new java.util.ArrayList<>();
    navButtons.add(new ButtonView(BUTTON_CODE_BACK, "⬅️ 返回商品", ButtonStyle.SECONDARY, false));

    if (codePage.hasPreviousPage()) {
      navButtons.add(
          new ButtonView(
              BUTTON_PREFIX_CODE_PAGE + (codePage.currentPage() - 1),
              "上一頁",
              ButtonStyle.SECONDARY,
              false));
    }

    if (codePage.hasNextPage()) {
      navButtons.add(
          new ButtonView(
              BUTTON_PREFIX_CODE_PAGE + (codePage.currentPage() + 1),
              "下一頁",
              ButtonStyle.SECONDARY,
              false));
    }

    return List.of(PanelComponentRenderer.buildActionRow(navButtons));
  }

  // ===== Helpers =====

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, Math.max(0, maxLength - 3)) + "...";
  }

  private String getSessionKey(long userId, long guildId) {
    return userId + "_" + guildId;
  }

  private String getModalValueOrNull(ModalInteractionEvent event, String id) {
    var mapping = event.getValue(id);
    if (mapping == null) {
      return null;
    }
    String value = mapping.getAsString();
    return value.isBlank() ? null : value.trim();
  }

  private Result<Boolean, DomainError> parseBooleanInput(String raw, boolean defaultValue) {
    if (raw == null || raw.isBlank()) {
      return Result.ok(defaultValue);
    }
    String normalized = raw.trim().toLowerCase();
    return switch (normalized) {
      case "true", "1", "yes", "y", "on" -> Result.ok(true);
      case "false", "0", "no", "n", "off" -> Result.ok(false);
      default ->
          Result.err(DomainError.invalidInput("自動護航開單僅接受 true/false, 1/0, yes/no, y/n, on/off"));
    };
  }

  /** 目前商品面板的檢視狀態。 */
  public enum ProductView {
    DETAIL,
    CODE_LIST
  }

  /** 用於追蹤管理員在商品面板的狀態，支援即時刷新。 */
  static class ProductSessionState {
    final long productId;
    final ProductView view;
    final int page;

    ProductSessionState(long productId, ProductView view, int page) {
      this.productId = productId;
      this.view = view;
      this.page = page;
    }

    ProductSessionState withView(ProductView view, int page) {
      return new ProductSessionState(this.productId, view, page);
    }
  }

  static class IntegrationConfigSessionState {
    final long productId;
    final String productName;
    String backendApiUrl;
    boolean autoCreateEscortOrder;
    String escortOptionCode;
    String statusMessage;
    InteractionHook panelHook;

    IntegrationConfigSessionState(
        long productId,
        String productName,
        String backendApiUrl,
        boolean autoCreateEscortOrder,
        String escortOptionCode,
        String statusMessage) {
      this.productId = productId;
      this.productName = productName;
      this.backendApiUrl = backendApiUrl;
      this.autoCreateEscortOrder = autoCreateEscortOrder;
      this.escortOptionCode = escortOptionCode;
      this.statusMessage = statusMessage;
    }
  }

  /** 供測試設定 session 狀態，避免繁瑣事件建構。 */
  void setProductSessionForTest(
      long adminId, long guildId, ProductView view, long productId, int page) {
    productSessions.put(
        getSessionKey(adminId, guildId), new ProductSessionState(productId, view, page));
  }

  /** 事件發生後刷新目前已開啟商品面板的管理員畫面。 */
  public void refreshProductPanels(long guildId) {
    String suffix = "_" + guildId;
    productSessions.forEach(
        (key, state) -> {
          if (!key.endsWith(suffix)) {
            return;
          }
          String[] parts = key.split("_", 2);
          if (parts.length != 2) {
            return;
          }
          long adminId;
          try {
            adminId = Long.parseLong(parts[0]);
          } catch (NumberFormatException e) {
            return;
          }

          adminPanelSessionManager.updatePanel(
              guildId,
              adminId,
              hook -> {
                try {
                  if (state.view == ProductView.CODE_LIST) {
                    refreshCodeListView(hook, guildId, adminId, state);
                  } else {
                    refreshProductDetailView(hook, guildId, adminId, state);
                  }
                } catch (Exception e) {
                  LOG.warn(
                      "Failed to refresh product panel for adminId={}, guildId={}",
                      adminId,
                      guildId,
                      e);
                }
              });
        });
  }

  private void refreshProductDetailView(
      InteractionHook hook, long guildId, long adminId, ProductSessionState state) {
    productService
        .getProduct(state.productId)
        .ifPresentOrElse(
            product -> {
              var stats = redemptionService.getCodeStats(product.id());
              hook.editOriginalEmbeds(buildProductDetailEmbed(product))
                  .setComponents(buildProductDetailComponents(product, stats))
                  .queue(
                      msg ->
                          LOG.trace(
                              "Refreshed product detail for adminId={}, guildId={}",
                              adminId,
                              guildId),
                      err ->
                          LOG.warn(
                              "Failed to edit product detail message for adminId={}, guildId={}",
                              adminId,
                              guildId,
                              err));
            },
            () -> refreshProductListView(hook, guildId, adminId));
  }

  private void refreshCodeListView(
      InteractionHook hook, long guildId, long adminId, ProductSessionState state) {
    productService
        .getProduct(state.productId)
        .ifPresentOrElse(
            product -> {
              var codePage = redemptionService.getCodePage(state.productId, state.page, PAGE_SIZE);
              hook.editOriginalEmbeds(buildCodeListEmbed(product, codePage))
                  .setComponents(buildCodeListComponents(codePage))
                  .queue(
                      msg ->
                          LOG.trace(
                              "Refreshed code list for adminId={}, guildId={}", adminId, guildId),
                      err ->
                          LOG.warn(
                              "Failed to edit code list message for adminId={}, guildId={}",
                              adminId,
                              guildId,
                              err));
            },
            () -> refreshProductListView(hook, guildId, adminId));
  }

  private void refreshProductListView(InteractionHook hook, long guildId, long adminId) {
    var products = productService.getProducts(guildId);
    hook.editOriginalEmbeds(buildProductListEmbed(products))
        .setComponents(buildProductListComponents(products))
        .queue(
            msg -> LOG.trace("Refreshed product list for adminId={}, guildId={}", adminId, guildId),
            err ->
                LOG.warn(
                    "Failed to edit product list message for adminId={}, guildId={}",
                    adminId,
                    guildId,
                    err));
  }

  private List<ActionRow> buildProductListComponents(List<Product> products) {
    if (products.isEmpty()) {
      return List.of(
          PanelComponentRenderer.buildActionRow(
              List.of(
                  new ButtonView(BUTTON_CREATE_PRODUCT, "➕ 建立商品", ButtonStyle.SUCCESS, false),
                  new ButtonView(
                      AdminPanelButtonHandler.BUTTON_BACK,
                      "⬅️ 返回主選單",
                      ButtonStyle.SECONDARY,
                      false))));
    }

    StringSelectMenu.Builder menuBuilder =
        StringSelectMenu.create(SELECT_PRODUCT).setPlaceholder("選擇商品查看詳情");

    for (Product product : products) {
      String label = product.name();
      if (label.length() > 25) {
        label = label.substring(0, 22) + "...";
      }
      String description = product.hasReward() ? product.formatReward() : "無自動獎勵";
      if (description.length() > 50) {
        description = description.substring(0, 47) + "...";
      }
      menuBuilder.addOption(label, String.valueOf(product.id()), description);
    }

    return List.of(
        PanelComponentRenderer.buildRow(menuBuilder.build()),
        PanelComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(BUTTON_CREATE_PRODUCT, "➕ 建立商品", ButtonStyle.SUCCESS, false),
                new ButtonView(
                    AdminPanelButtonHandler.BUTTON_BACK,
                    "⬅️ 返回主選單",
                    ButtonStyle.SECONDARY,
                    false))));
  }

  private boolean isAdmin(Member member, Guild guild) {
    if (member == null || guild == null) {
      return false;
    }
    if (member.hasPermission(Permission.ADMINISTRATOR)) {
      return true;
    }
    try {
      return guild.getOwnerIdLong() == member.getIdLong();
    } catch (Exception ignored) {
      return false;
    }
  }
}
