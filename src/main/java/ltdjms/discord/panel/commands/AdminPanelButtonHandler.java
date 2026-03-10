package ltdjms.discord.panel.commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.EmbedView;
import ltdjms.discord.dispatch.services.EscortOptionPricingService;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.panel.components.PanelComponentRenderer;
import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import ltdjms.discord.panel.services.CurrencyManagementFacade;
import ltdjms.discord.panel.services.GameTokenManagementFacade;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

/**
 * Handles button, select menu, and modal interactions for the admin panel. Supports user selection
 * via EntitySelectMenu, mode selection via StringSelectMenu, and amount input via Modal for balance
 * and token management.
 */
public class AdminPanelButtonHandler extends ListenerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(AdminPanelButtonHandler.class);

  private static final Color EMBED_COLOR = new Color(0xED4245);
  private static final int EMBED_FIELD_VALUE_LIMIT = 1024;

  // Button IDs
  public static final String BUTTON_BALANCE = "admin_panel_balance";
  public static final String BUTTON_TOKENS = "admin_panel_tokens";
  public static final String BUTTON_GAMES = "admin_panel_games";
  public static final String BUTTON_BACK = "admin_panel_back";
  public static final String BUTTON_OPEN_BALANCE_MODAL = "admin_open_balance_modal";
  public static final String BUTTON_OPEN_TOKEN_MODAL = "admin_open_token_modal";

  // AI 頻道限制按鈕（aichat 模組）
  public static final String BUTTON_AI_CHANNEL_CONFIG = "admin_panel_ai_channel";
  public static final String BUTTON_AI_ADD_CHANNEL = "admin_ai_add_channel";
  public static final String BUTTON_AI_REMOVE_CHANNEL = "admin_ai_remove_channel";
  public static final String BUTTON_AI_ADD_CATEGORY = "admin_ai_add_category";
  public static final String BUTTON_AI_REMOVE_CATEGORY = "admin_ai_remove_category";

  // AI Agent 配置按鈕（aiagent 模組）
  public static final String BUTTON_AI_AGENT_CONFIG = "admin_panel_ai_agent";
  public static final String BUTTON_AI_AGENT_ENABLE = "admin_ai_agent_enable";
  public static final String BUTTON_AI_AGENT_DISABLE = "admin_ai_agent_disable";
  public static final String BUTTON_AI_AGENT_REMOVE = "admin_ai_agent_remove";

  // Dispatch 售後設定（dispatch 模組）
  public static final String BUTTON_DISPATCH_AFTER_SALES_CONFIG =
      "admin_panel_dispatch_after_sales";
  public static final String BUTTON_ESCORT_PRICING_CONFIG = "admin_panel_escort_pricing";
  public static final String BUTTON_ESCORT_PRICING_EDIT = "admin_escort_pricing_edit";
  public static final String BUTTON_ESCORT_PRICING_RESET = "admin_escort_pricing_reset";
  public static final String BUTTON_ESCORT_PRICING_REFRESH = "admin_escort_pricing_refresh";
  public static final String BUTTON_ESCORT_PRICING_PANEL_INPUT_PRICE =
      "admin_escort_pricing_panel_input_price";
  public static final String BUTTON_ESCORT_PRICING_PANEL_CONFIRM =
      "admin_escort_pricing_panel_confirm";
  public static final String BUTTON_ESCORT_PRICING_PANEL_CLOSE = "admin_escort_pricing_panel_close";

  // Modal IDs
  public static final String MODAL_BALANCE_ADJUST = "admin_modal_balance_adjust";
  public static final String MODAL_TOKEN_ADJUST = "admin_modal_token_adjust";
  public static final String MODAL_GAME_1_TOKENS = "admin_modal_game1_tokens";
  public static final String MODAL_GAME_1_REWARD = "admin_modal_game1_reward";
  public static final String MODAL_GAME_2_TOKENS = "admin_modal_game2_tokens";
  public static final String MODAL_GAME_2_MULTIPLIERS = "admin_modal_game2_multipliers";
  public static final String MODAL_GAME_2_BONUSES = "admin_modal_game2_bonuses";
  public static final String MODAL_ESCORT_PRICING_EDIT = "admin_modal_escort_pricing_edit";
  public static final String MODAL_ESCORT_PRICING_RESET = "admin_modal_escort_pricing_reset";
  public static final String MODAL_ESCORT_PRICING_PANEL_PRICE =
      "admin_modal_escort_pricing_panel_price";

  // Select Menu IDs
  public static final String SELECT_GAME = "admin_select_game";
  public static final String SELECT_BALANCE_USER = "admin_select_balance_user";
  public static final String SELECT_BALANCE_MODE = "admin_select_balance_mode";
  public static final String SELECT_TOKEN_USER = "admin_select_token_user";
  public static final String SELECT_TOKEN_MODE = "admin_select_token_mode";
  public static final String SELECT_GAME_SETTING = "admin_select_game_setting";

  // AI 頻道限制 Select Menu（aichat 模組）
  public static final String SELECT_AI_ADD_CHANNEL = "admin_select_ai_add_channel";
  public static final String SELECT_AI_REMOVE_CHANNEL = "admin_select_ai_remove_channel";
  public static final String SELECT_AI_ADD_CATEGORY = "admin_select_ai_add_category";
  public static final String SELECT_AI_REMOVE_CATEGORY = "admin_select_ai_remove_category";

  // AI Agent 配置 Select Menu（aiagent 模組）
  public static final String SELECT_AI_AGENT_CHANNEL = "admin_select_ai_agent_channel";

  // Dispatch 售後設定 Select Menu（dispatch 模組）
  public static final String SELECT_DISPATCH_AFTER_SALES_ADD_USER =
      "admin_select_dispatch_after_sales_add_user";
  public static final String SELECT_DISPATCH_AFTER_SALES_REMOVE_USER =
      "admin_select_dispatch_after_sales_remove_user";
  public static final String SELECT_ESCORT_PRICING_PANEL_ACTION =
      "admin_select_escort_pricing_panel_action";
  public static final String SELECT_ESCORT_PRICING_PANEL_OPTION =
      "admin_select_escort_pricing_panel_option";
  public static final String SELECT_ESCORT_PRICING_PANEL_OPTION_EXTRA =
      "admin_select_escort_pricing_panel_option_extra";

  private static final String ESCORT_PRICING_ACTION_UPDATE = "update";
  private static final String ESCORT_PRICING_ACTION_RESET = "reset";

  private final AdminPanelService adminPanelService;
  private final AdminPanelSessionManager adminPanelSessionManager;

  // Session state for user selections (keyed by interactionId or uniqueId)
  private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();
  private final Map<String, EscortPricingPanelState> escortPricingPanelStates =
      new ConcurrentHashMap<>();

  public AdminPanelButtonHandler(
      AdminPanelService adminPanelService, AdminPanelSessionManager adminPanelSessionManager) {
    this.adminPanelService = adminPanelService;
    this.adminPanelSessionManager = adminPanelSessionManager;
  }

  /** Helper method to safely get currency icon from config result. */
  private String getCurrencyIcon(long guildId) {
    Result<ltdjms.discord.currency.domain.GuildCurrencyConfig, DomainError> result =
        adminPanelService.getCurrencyConfig(guildId);
    return result.isOk() ? result.getValue().currencyIcon() : "💰";
  }

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String buttonId = event.getComponentId();

    if (!buttonId.startsWith("admin_")) {
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
        "Processing admin panel button: buttonId={}, userId={}",
        buttonId,
        event.getUser().getIdLong());

    try {
      switch (buttonId) {
        case BUTTON_BALANCE -> showBalanceManagement(event);
        case BUTTON_TOKENS -> showTokenManagement(event);
        case BUTTON_GAMES -> showGameManagement(event);
        case BUTTON_AI_CHANNEL_CONFIG -> showAIChannelConfig(event);
        case BUTTON_AI_AGENT_CONFIG -> showAIAgentConfig(event);
        case BUTTON_DISPATCH_AFTER_SALES_CONFIG -> showDispatchAfterSalesConfig(event);
        case BUTTON_ESCORT_PRICING_CONFIG -> showEscortPricingConfig(event);
        case BUTTON_ESCORT_PRICING_EDIT -> openEscortPricingEditModal(event);
        case BUTTON_ESCORT_PRICING_RESET -> openEscortPricingResetModal(event);
        case BUTTON_ESCORT_PRICING_REFRESH -> showEscortPricingConfig(event);
        case BUTTON_ESCORT_PRICING_PANEL_INPUT_PRICE -> openEscortPricingPanelPriceModal(event);
        case BUTTON_ESCORT_PRICING_PANEL_CONFIRM -> handleEscortPricingPanelConfirm(event);
        case BUTTON_ESCORT_PRICING_PANEL_CLOSE -> handleEscortPricingPanelClose(event);
        case BUTTON_BACK -> showMainPanel(event);
        case BUTTON_OPEN_BALANCE_MODAL -> openBalanceModal(event);
        case BUTTON_OPEN_TOKEN_MODAL -> openTokenModal(event);
        case BUTTON_AI_AGENT_ENABLE -> handleAIAgentEnable(event);
        case BUTTON_AI_AGENT_DISABLE -> handleAIAgentDisable(event);
        case BUTTON_AI_AGENT_REMOVE -> handleAIAgentRemove(event);
        default -> LOG.warn("Unknown admin panel button: {}", buttonId);
      }
    } catch (Exception e) {
      LOG.error("Error handling admin panel button: {}", buttonId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  @Override
  public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
    String selectId = event.getComponentId();

    if (!selectId.startsWith("admin_select_")) {
      return;
    }

    if (!event.isFromGuild() || event.getGuild() == null) {
      return;
    }

    if (!isAdmin(event.getMember(), event.getGuild())) {
      event.reply("你沒有權限使用管理面板").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);

    try {
      if (selectId.equals(SELECT_BALANCE_USER)) {
        handleBalanceUserSelect(event, sessionKey, guildId);
      } else if (selectId.equals(SELECT_TOKEN_USER)) {
        handleTokenUserSelect(event, sessionKey, guildId);
      } else if (selectId.equals(SELECT_AI_ADD_CHANNEL)) {
        handleAddChannelSelect(event, guildId);
      } else if (selectId.equals(SELECT_AI_REMOVE_CHANNEL)) {
        handleRemoveChannelSelect(event, guildId);
      } else if (selectId.equals(SELECT_AI_ADD_CATEGORY)) {
        handleAddCategorySelect(event, guildId);
      } else if (selectId.equals(SELECT_AI_REMOVE_CATEGORY)) {
        handleRemoveCategorySelect(event, guildId);
      } else if (selectId.equals(SELECT_AI_AGENT_CHANNEL)) {
        handleAIAgentChannelSelect(event, guildId);
      } else if (selectId.equals(SELECT_DISPATCH_AFTER_SALES_ADD_USER)) {
        handleDispatchAfterSalesAddUserSelect(event, guildId);
      } else if (selectId.equals(SELECT_DISPATCH_AFTER_SALES_REMOVE_USER)) {
        handleDispatchAfterSalesRemoveUserSelect(event, guildId);
      }
    } catch (Exception e) {
      LOG.error("Error handling entity select: {}", selectId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  @Override
  public void onStringSelectInteraction(StringSelectInteractionEvent event) {
    String selectId = event.getComponentId();

    if (!selectId.startsWith("admin_select_")) {
      return;
    }

    if (!event.isFromGuild() || event.getGuild() == null) {
      return;
    }

    if (!isAdmin(event.getMember(), event.getGuild())) {
      event.reply("你沒有權限使用管理面板").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);

    try {
      switch (selectId) {
        case SELECT_GAME -> handleGameSelect(event, guildId);
        case SELECT_BALANCE_MODE -> handleBalanceModeSelect(event, sessionKey, guildId);
        case SELECT_TOKEN_MODE -> handleTokenModeSelect(event, sessionKey, guildId);
        case SELECT_GAME_SETTING -> handleGameSettingSelect(event, guildId);
        case SELECT_ESCORT_PRICING_PANEL_ACTION ->
            handleEscortPricingPanelActionSelect(event, sessionKey, guildId);
        case SELECT_ESCORT_PRICING_PANEL_OPTION ->
            handleEscortPricingPanelOptionSelect(event, sessionKey, guildId);
        case SELECT_ESCORT_PRICING_PANEL_OPTION_EXTRA ->
            handleEscortPricingPanelOptionSelect(event, sessionKey, guildId);
        default -> LOG.warn("Unknown string select: {}", selectId);
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

    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    if (!isAdmin(event.getMember(), event.getGuild())) {
      event.reply("你沒有權限使用管理面板").setEphemeral(true).queue();
      return;
    }

    try {
      if (modalId.startsWith(MODAL_BALANCE_ADJUST)) {
        handleBalanceAdjustModal(event);
      } else if (modalId.startsWith(MODAL_TOKEN_ADJUST)) {
        handleTokenAdjustModal(event);
      } else if (modalId.startsWith(MODAL_GAME_1_TOKENS)) {
        handleGame1TokensModal(event);
      } else if (modalId.startsWith(MODAL_GAME_1_REWARD)) {
        handleGame1RewardModal(event);
      } else if (modalId.startsWith(MODAL_GAME_2_TOKENS)) {
        handleGame2TokensModal(event);
      } else if (modalId.startsWith(MODAL_GAME_2_MULTIPLIERS)) {
        handleGame2MultipliersModal(event);
      } else if (modalId.startsWith(MODAL_GAME_2_BONUSES)) {
        handleGame2BonusesModal(event);
      } else if (modalId.startsWith(MODAL_ESCORT_PRICING_EDIT)) {
        handleEscortPricingEditModal(event);
      } else if (modalId.startsWith(MODAL_ESCORT_PRICING_RESET)) {
        handleEscortPricingResetModal(event);
      } else if (modalId.startsWith(MODAL_ESCORT_PRICING_PANEL_PRICE)) {
        handleEscortPricingPanelPriceModal(event);
      }
    } catch (Exception e) {
      LOG.error("Error handling modal: {}", modalId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  // ===== Balance Management =====

  private void showBalanceManagement(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);

    sessionStates.put(sessionKey, new SessionState(ManagementType.BALANCE));

    String currencyIcon = getCurrencyIcon(guildId);
    MessageEmbed embed = buildBalanceManagementEmbed(null, null, null, currencyIcon);

    event
        .editMessageEmbeds(embed)
        .setComponents(buildBalanceManagementComponents(currencyIcon, null, false))
        .queue();
  }

  private void handleBalanceUserSelect(
      EntitySelectInteractionEvent event, String sessionKey, long guildId) {
    List<User> selectedUsers = event.getMentions().getUsers();
    if (selectedUsers.isEmpty()) {
      event.reply("請選擇一位成員").setEphemeral(true).queue();
      return;
    }

    User selectedUser = selectedUsers.get(0);
    SessionState state =
        sessionStates.computeIfAbsent(sessionKey, k -> new SessionState(ManagementType.BALANCE));
    state.selectedUserId = selectedUser.getIdLong();
    state.selectedUserMention = selectedUser.getAsMention();

    // Get current balance
    Result<Long, DomainError> balanceResult =
        adminPanelService.getMemberBalance(guildId, selectedUser.getIdLong());
    Long currentBalance = balanceResult.isOk() ? balanceResult.getValue() : null;
    state.currentValue = currentBalance;

    String currencyIcon = getCurrencyIcon(guildId);
    MessageEmbed embed =
        buildBalanceManagementEmbed(
            selectedUser.getAsMention(), currentBalance, state.selectedMode, currencyIcon);

    boolean canOpenModal = state.selectedUserId != null && state.selectedMode != null;

    event
        .editMessageEmbeds(embed)
        .setComponents(
            buildBalanceManagementComponents(currencyIcon, state.selectedMode, canOpenModal))
        .queue();
  }

  private void handleBalanceModeSelect(
      StringSelectInteractionEvent event, String sessionKey, long guildId) {
    String mode = event.getValues().get(0);

    SessionState state =
        sessionStates.computeIfAbsent(sessionKey, k -> new SessionState(ManagementType.BALANCE));
    state.selectedMode = mode;

    String currencyIcon = getCurrencyIcon(guildId);
    MessageEmbed embed =
        buildBalanceManagementEmbed(
            state.selectedUserMention, state.currentValue, mode, currencyIcon);

    boolean canOpenModal = state.selectedUserId != null && state.selectedMode != null;

    event
        .editMessageEmbeds(embed)
        .setComponents(buildBalanceManagementComponents(currencyIcon, mode, canOpenModal))
        .queue();
  }

  private void openBalanceModal(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    SessionState state = sessionStates.get(sessionKey);

    if (state == null || state.selectedUserId == null || state.selectedMode == null) {
      event.reply("請先選擇成員和調整模式").setEphemeral(true).queue();
      return;
    }

    String modeLabel = getModeLabel(state.selectedMode);
    String modalTitle = String.format("%s - %s", modeLabel, state.selectedUserMention);
    if (modalTitle.length() > 45) {
      modalTitle = modeLabel;
    }

    TextInput amountInput =
        TextInput.create("amount", "金額", TextInputStyle.SHORT)
            .setPlaceholder(state.selectedMode.equals("adjust") ? "輸入目標餘額" : "輸入調整金額")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    Modal modal =
        Modal.create(
                MODAL_BALANCE_ADJUST + ":" + state.selectedUserId + ":" + state.selectedMode,
                modalTitle)
            .addComponents(ActionRow.of(amountInput))
            .build();

    event.replyModal(modal).queue();
  }

  private List<ActionRow> buildBalanceManagementComponents(
      String currencyIcon, String selectedMode, boolean canOpenModal) {
    return buildManagementComponents(
        buildManagementUserSelect(SELECT_BALANCE_USER),
        buildBalanceModeSelect(selectedMode),
        BUTTON_OPEN_BALANCE_MODAL,
        currencyIcon + " 輸入金額",
        canOpenModal);
  }

  private EntitySelectMenu buildManagementUserSelect(String selectId) {
    return EntitySelectMenu.create(selectId, EntitySelectMenu.SelectTarget.USER)
        .setPlaceholder("選擇要調整的成員")
        .setRequiredRange(1, 1)
        .build();
  }

  private StringSelectMenu buildBalanceModeSelect(String selectedMode) {
    StringSelectMenu.Builder builder =
        StringSelectMenu.create(SELECT_BALANCE_MODE)
            .setPlaceholder("選擇調整模式")
            .addOption("增加餘額", "add", "在現有餘額基礎上增加指定金額")
            .addOption("扣除餘額", "deduct", "從現有餘額扣除指定金額")
            .addOption("設定餘額", "adjust", "將餘額直接設定為指定金額");
    if (selectedMode != null) {
      builder.setDefaultValues(List.of(selectedMode));
    }
    return builder.build();
  }

  private List<ActionRow> buildManagementComponents(
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
                new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))));
    return rows;
  }

  private MessageEmbed buildBalanceManagementEmbed(
      String userMention, Long currentBalance, String mode, String currencyIcon) {
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

    if (mode != null) {
      fields.add(new EmbedView.FieldView("調整模式", getModeLabel(mode), false));
    }

    return PanelComponentRenderer.buildEmbed(
        new EmbedView(
            currencyIcon + " 使用者餘額管理",
            "選擇要調整餘額的成員和調整模式",
            EMBED_COLOR,
            fields,
            "選擇成員和模式後點擊「輸入金額」按鈕"));
  }

  // ===== Token Management =====

  private void showTokenManagement(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);

    sessionStates.put(sessionKey, new SessionState(ManagementType.TOKEN));

    MessageEmbed embed = buildTokenManagementEmbed(null, null, null);

    event
        .editMessageEmbeds(embed)
        .setComponents(buildTokenManagementComponents(null, false))
        .queue();
  }

  private void handleTokenUserSelect(
      EntitySelectInteractionEvent event, String sessionKey, long guildId) {
    List<User> selectedUsers = event.getMentions().getUsers();
    if (selectedUsers.isEmpty()) {
      event.reply("請選擇一位成員").setEphemeral(true).queue();
      return;
    }

    User selectedUser = selectedUsers.get(0);
    SessionState state =
        sessionStates.computeIfAbsent(sessionKey, k -> new SessionState(ManagementType.TOKEN));
    state.selectedUserId = selectedUser.getIdLong();
    state.selectedUserMention = selectedUser.getAsMention();

    // Get current token balance
    long currentTokens = adminPanelService.getMemberTokens(guildId, selectedUser.getIdLong());
    state.currentValue = currentTokens;

    MessageEmbed embed =
        buildTokenManagementEmbed(selectedUser.getAsMention(), currentTokens, state.selectedMode);

    boolean canOpenModal = state.selectedUserId != null && state.selectedMode != null;

    event
        .editMessageEmbeds(embed)
        .setComponents(buildTokenManagementComponents(state.selectedMode, canOpenModal))
        .queue();
  }

  private void handleTokenModeSelect(
      StringSelectInteractionEvent event, String sessionKey, long guildId) {
    String mode = event.getValues().get(0);

    SessionState state =
        sessionStates.computeIfAbsent(sessionKey, k -> new SessionState(ManagementType.TOKEN));
    state.selectedMode = mode;

    MessageEmbed embed =
        buildTokenManagementEmbed(state.selectedUserMention, state.currentValue, mode);

    boolean canOpenModal = state.selectedUserId != null && state.selectedMode != null;

    event
        .editMessageEmbeds(embed)
        .setComponents(buildTokenManagementComponents(mode, canOpenModal))
        .queue();
  }

  private void openTokenModal(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    SessionState state = sessionStates.get(sessionKey);

    if (state == null || state.selectedUserId == null || state.selectedMode == null) {
      event.reply("請先選擇成員和調整模式").setEphemeral(true).queue();
      return;
    }

    String modeLabel = getTokenModeLabel(state.selectedMode);
    String modalTitle = String.format("%s - %s", modeLabel, state.selectedUserMention);
    if (modalTitle.length() > 45) {
      modalTitle = modeLabel;
    }

    TextInput amountInput =
        TextInput.create("amount", "數量", TextInputStyle.SHORT)
            .setPlaceholder(state.selectedMode.equals("adjust") ? "輸入目標代幣數量" : "輸入調整數量")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    Modal modal =
        Modal.create(
                MODAL_TOKEN_ADJUST + ":" + state.selectedUserId + ":" + state.selectedMode,
                modalTitle)
            .addComponents(ActionRow.of(amountInput))
            .build();

    event.replyModal(modal).queue();
  }

  private List<ActionRow> buildTokenManagementComponents(
      String selectedMode, boolean canOpenModal) {
    return buildManagementComponents(
        buildManagementUserSelect(SELECT_TOKEN_USER),
        buildTokenModeSelect(selectedMode),
        BUTTON_OPEN_TOKEN_MODAL,
        "🎮 輸入數量",
        canOpenModal);
  }

  private StringSelectMenu buildTokenModeSelect(String selectedMode) {
    StringSelectMenu.Builder builder =
        StringSelectMenu.create(SELECT_TOKEN_MODE)
            .setPlaceholder("選擇調整模式")
            .addOption("增加代幣", "add", "在現有代幣基礎上增加指定數量")
            .addOption("扣除代幣", "deduct", "從現有代幣扣除指定數量")
            .addOption("設定代幣", "adjust", "將代幣直接設定為指定數量");
    if (selectedMode != null) {
      builder.setDefaultValues(List.of(selectedMode));
    }
    return builder.build();
  }

  private MessageEmbed buildTokenManagementEmbed(
      String userMention, Long currentTokens, String mode) {
    List<EmbedView.FieldView> fields = new ArrayList<>();
    if (userMention != null) {
      fields.add(new EmbedView.FieldView("選取成員", userMention, true));
      fields.add(
          new EmbedView.FieldView(
              "目前代幣",
              currentTokens != null ? String.format("🎮 %,d", currentTokens) : "（無法取得）",
              true));
    }

    if (mode != null) {
      fields.add(new EmbedView.FieldView("調整模式", getTokenModeLabel(mode), false));
    }

    return PanelComponentRenderer.buildEmbed(
        new EmbedView("🎮 遊戲代幣管理", "選擇要調整代幣的成員和調整模式", EMBED_COLOR, fields, "選擇成員和模式後點擊「輸入數量」按鈕"));
  }

  // ===== Game Management =====

  private void showGameManagement(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    DiceGame1Config game1Config = adminPanelService.getDiceGame1Config(guildId);
    DiceGame2Config game2Config = adminPanelService.getDiceGame2Config(guildId);
    String currencyIcon = getCurrencyIcon(guildId);

    MessageEmbed embed = buildGameManagementEmbed(game1Config, game2Config, currencyIcon);

    StringSelectMenu gameSelect =
        StringSelectMenu.create(SELECT_GAME)
            .setPlaceholder("選擇遊戲")
            .addOption("摘星手", "dice-game-1", "查看與調整摘星手的設定")
            .addOption("神龍擺尾", "dice-game-2", "查看與調整神龍擺尾的設定")
            .build();

    event
        .editMessageEmbeds(embed)
        .setComponents(
            PanelComponentRenderer.buildRow(gameSelect),
            PanelComponentRenderer.buildActionRow(
                List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))))
        .queue();
  }

  private void handleGameSelect(StringSelectInteractionEvent event, long guildId) {
    String gameType = event.getValues().get(0);

    if (gameType.equals("dice-game-1")) {
      showDiceGame1Settings(event, guildId);
    } else if (gameType.equals("dice-game-2")) {
      showDiceGame2Settings(event, guildId);
    }
  }

  private void showDiceGame1Settings(StringSelectInteractionEvent event, long guildId) {
    DiceGame1Config config = adminPanelService.getDiceGame1Config(guildId);
    String currencyIcon = getCurrencyIcon(guildId);

    MessageEmbed embed = buildDiceGame1SettingsEmbed(config, currencyIcon);

    StringSelectMenu settingSelect =
        StringSelectMenu.create(SELECT_GAME_SETTING)
            .setPlaceholder("選擇要調整的設定")
            .addOption("代幣範圍", "game1-tokens", "調整每局可投入的代幣數量範圍")
            .addOption("獎勵倍率", "game1-reward", "調整單骰獎勵的基礎倍率")
            .build();

    event
        .editMessageEmbeds(embed)
        .setComponents(
            PanelComponentRenderer.buildRow(settingSelect),
            PanelComponentRenderer.buildActionRow(
                List.of(
                    new ButtonView(BUTTON_GAMES, "🎲 返回遊戲列表", ButtonStyle.SECONDARY, false),
                    new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))))
        .queue();
  }

  private void showDiceGame2Settings(StringSelectInteractionEvent event, long guildId) {
    DiceGame2Config config = adminPanelService.getDiceGame2Config(guildId);
    String currencyIcon = getCurrencyIcon(guildId);

    MessageEmbed embed = buildDiceGame2SettingsEmbed(config, currencyIcon);

    StringSelectMenu settingSelect =
        StringSelectMenu.create(SELECT_GAME_SETTING)
            .setPlaceholder("選擇要調整的設定")
            .addOption("代幣範圍", "game2-tokens", "調整每局可投入的代幣數量範圍")
            .addOption("獎勵倍率", "game2-multipliers", "調整順子和基礎獎勵倍率")
            .addOption("豹子獎勵", "game2-bonuses", "調整小豹子和大豹子的獎勵金額")
            .build();

    event
        .editMessageEmbeds(embed)
        .setComponents(
            PanelComponentRenderer.buildRow(settingSelect),
            PanelComponentRenderer.buildActionRow(
                List.of(
                    new ButtonView(BUTTON_GAMES, "🎲 返回遊戲列表", ButtonStyle.SECONDARY, false),
                    new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))))
        .queue();
  }

  private void handleGameSettingSelect(StringSelectInteractionEvent event, long guildId) {
    String setting = event.getValues().get(0);

    switch (setting) {
      case "game1-tokens" -> openGame1TokensModal(event, guildId);
      case "game1-reward" -> openGame1RewardModal(event, guildId);
      case "game2-tokens" -> openGame2TokensModal(event, guildId);
      case "game2-multipliers" -> openGame2MultipliersModal(event, guildId);
      case "game2-bonuses" -> openGame2BonusesModal(event, guildId);
      default -> LOG.warn("Unknown game setting: {}", setting);
    }
  }

  private void openGame1TokensModal(StringSelectInteractionEvent event, long guildId) {
    DiceGame1Config config = adminPanelService.getDiceGame1Config(guildId);

    TextInput minInput =
        TextInput.create("min", "最小代幣數", TextInputStyle.SHORT)
            .setPlaceholder("最小可投入代幣數量")
            .setValue(String.valueOf(config.minTokensPerPlay()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(10)
            .build();

    TextInput maxInput =
        TextInput.create("max", "最大代幣數", TextInputStyle.SHORT)
            .setPlaceholder("最大可投入代幣數量")
            .setValue(String.valueOf(config.maxTokensPerPlay()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(10)
            .build();

    Modal modal =
        Modal.create(MODAL_GAME_1_TOKENS, "摘星手 - 代幣範圍")
            .addComponents(ActionRow.of(minInput), ActionRow.of(maxInput))
            .build();

    event.replyModal(modal).queue();
  }

  private void openGame1RewardModal(StringSelectInteractionEvent event, long guildId) {
    DiceGame1Config config = adminPanelService.getDiceGame1Config(guildId);

    TextInput rewardInput =
        TextInput.create("reward", "單骰獎勵倍率", TextInputStyle.SHORT)
            .setPlaceholder("每點數的獎勵金額")
            .setValue(String.valueOf(config.rewardPerDiceValue()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    Modal modal =
        Modal.create(MODAL_GAME_1_REWARD, "摘星手 - 獎勵設定")
            .addComponents(ActionRow.of(rewardInput))
            .build();

    event.replyModal(modal).queue();
  }

  private void openGame2TokensModal(StringSelectInteractionEvent event, long guildId) {
    DiceGame2Config config = adminPanelService.getDiceGame2Config(guildId);

    TextInput minInput =
        TextInput.create("min", "最小代幣數", TextInputStyle.SHORT)
            .setPlaceholder("最小可投入代幣數量")
            .setValue(String.valueOf(config.minTokensPerPlay()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(10)
            .build();

    TextInput maxInput =
        TextInput.create("max", "最大代幣數", TextInputStyle.SHORT)
            .setPlaceholder("最大可投入代幣數量")
            .setValue(String.valueOf(config.maxTokensPerPlay()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(10)
            .build();

    Modal modal =
        Modal.create(MODAL_GAME_2_TOKENS, "神龍擺尾 - 代幣範圍")
            .addComponents(ActionRow.of(minInput), ActionRow.of(maxInput))
            .build();

    event.replyModal(modal).queue();
  }

  private void openGame2MultipliersModal(StringSelectInteractionEvent event, long guildId) {
    DiceGame2Config config = adminPanelService.getDiceGame2Config(guildId);

    TextInput straightInput =
        TextInput.create("straight", "順子倍率", TextInputStyle.SHORT)
            .setPlaceholder("順子獎勵的基礎倍率")
            .setValue(String.valueOf(config.straightMultiplier()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    TextInput baseInput =
        TextInput.create("base", "基礎倍率", TextInputStyle.SHORT)
            .setPlaceholder("非順子非豹子的基礎倍率")
            .setValue(String.valueOf(config.baseMultiplier()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    Modal modal =
        Modal.create(MODAL_GAME_2_MULTIPLIERS, "神龍擺尾 - 獎勵倍率")
            .addComponents(ActionRow.of(straightInput), ActionRow.of(baseInput))
            .build();

    event.replyModal(modal).queue();
  }

  private void openGame2BonusesModal(StringSelectInteractionEvent event, long guildId) {
    DiceGame2Config config = adminPanelService.getDiceGame2Config(guildId);

    TextInput lowInput =
        TextInput.create("low", "小豹子獎勵", TextInputStyle.SHORT)
            .setPlaceholder("小豹子（總和<10）的獎勵金額")
            .setValue(String.valueOf(config.tripleLowBonus()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    TextInput highInput =
        TextInput.create("high", "大豹子獎勵", TextInputStyle.SHORT)
            .setPlaceholder("大豹子（總和≥10）的獎勵金額")
            .setValue(String.valueOf(config.tripleHighBonus()))
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15)
            .build();

    Modal modal =
        Modal.create(MODAL_GAME_2_BONUSES, "神龍擺尾 - 豹子獎勵")
            .addComponents(ActionRow.of(lowInput), ActionRow.of(highInput))
            .build();

    event.replyModal(modal).queue();
  }

  private MessageEmbed buildGameManagementEmbed(
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

  private MessageEmbed buildDiceGame1SettingsEmbed(DiceGame1Config config, String currencyIcon) {
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

  private MessageEmbed buildDiceGame2SettingsEmbed(DiceGame2Config config, String currencyIcon) {
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

  // ===== Main Panel =====

  private void showMainPanel(ButtonInteractionEvent event) {
    // Clear session state
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    sessionStates.remove(sessionKey);
    escortPricingPanelStates.remove(sessionKey);

    String currencyIcon = getCurrencyIcon(guildId);
    MessageEmbed embed = buildMainPanelEmbed(currencyIcon);
    List<ActionRow> components = buildMainPanelComponents(currencyIcon);

    event.editMessageEmbeds(embed).setComponents(components).queue();
  }

  MessageEmbed buildMainPanelEmbed(String currencyIcon) {
    return AdminPanelCommandHandler.buildMainPanelEmbed(currencyIcon);
  }

  List<ActionRow> buildMainPanelComponents(String currencyIcon) {
    return AdminPanelCommandHandler.buildMainActionRows(currencyIcon);
  }

  // ===== Modal Handlers =====

  private void handleBalanceAdjustModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    // Parse userId and mode from modal ID
    String[] parts = event.getModalId().split(":");
    if (parts.length < 3) {
      event.reply("無效的操作").setEphemeral(true).queue();
      return;
    }

    long userId;
    String mode;
    try {
      userId = Long.parseLong(parts[1]);
      mode = parts[2];
    } catch (NumberFormatException e) {
      event.reply("無效的操作").setEphemeral(true).queue();
      return;
    }

    String amountStr = event.getValue("amount").getAsString().trim();

    long amount;
    try {
      amount = Long.parseLong(amountStr);
    } catch (NumberFormatException e) {
      event.reply("金額格式錯誤，請輸入有效數字").setEphemeral(true).queue();
      return;
    }

    if (amount < 0 && !mode.equals("adjust")) {
      event.reply("金額不可為負數").setEphemeral(true).queue();
      return;
    }

    Result<CurrencyManagementFacade.BalanceAdjustmentResult, DomainError> result =
        adminPanelService.adjustBalance(guildId, userId, mode, amount);

    if (result.isErr()) {
      event.reply("調整失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    CurrencyManagementFacade.BalanceAdjustmentResult adjustResult = result.getValue();
    String currencyIcon = getCurrencyIcon(guildId);

    // Update session state and refresh UI
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    SessionState state = sessionStates.get(sessionKey);

    if (state != null && state.selectedUserId != null && state.selectedUserId == userId) {
      state.currentValue = adjustResult.newBalance();
      MessageEmbed newEmbed =
          buildBalanceManagementEmbed(
              state.selectedUserMention, state.currentValue, state.selectedMode, currencyIcon);
      long adminId = event.getUser().getIdLong();
      adminPanelSessionManager.updatePanel(
          guildId,
          adminId,
          hook ->
              hook.editOriginalEmbeds(newEmbed)
                  .queue(
                      msg ->
                          LOG.trace(
                              "Updated admin panel balance embed for guildId={}, adminId={}",
                              guildId,
                              adminId),
                      error -> LOG.warn("Failed to edit admin panel balance embed", error)));
    }

    event
        .reply(
            String.format(
                "✅ 餘額調整成功！\n<@%d> 的餘額：\n調整前：%s %,d\n調整後：%s %,d",
                userId,
                currencyIcon,
                adjustResult.previousBalance(),
                currencyIcon,
                adjustResult.newBalance()))
        .setEphemeral(true)
        .queue();
  }

  private void handleTokenAdjustModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    // Parse userId and mode from modal ID
    String[] parts = event.getModalId().split(":");
    if (parts.length < 3) {
      event.reply("無效的操作").setEphemeral(true).queue();
      return;
    }

    long userId;
    String mode;
    try {
      userId = Long.parseLong(parts[1]);
      mode = parts[2];
    } catch (NumberFormatException e) {
      event.reply("無效的操作").setEphemeral(true).queue();
      return;
    }

    String amountStr = event.getValue("amount").getAsString().trim();

    long amount;
    try {
      amount = Long.parseLong(amountStr);
    } catch (NumberFormatException e) {
      event.reply("數量格式錯誤，請輸入有效數字").setEphemeral(true).queue();
      return;
    }

    if (amount < 0 && !mode.equals("adjust")) {
      event.reply("數量不可為負數").setEphemeral(true).queue();
      return;
    }

    Result<GameTokenManagementFacade.TokenAdjustmentResult, DomainError> result =
        adminPanelService.adjustTokens(guildId, userId, mode, amount);

    if (result.isErr()) {
      event.reply("調整失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    GameTokenManagementFacade.TokenAdjustmentResult adjustResult = result.getValue();

    // Update session state and refresh UI
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    SessionState state = sessionStates.get(sessionKey);

    if (state != null && state.selectedUserId != null && state.selectedUserId == userId) {
      state.currentValue = adjustResult.newTokens();
      MessageEmbed newEmbed =
          buildTokenManagementEmbed(
              state.selectedUserMention, state.currentValue, state.selectedMode);
      long adminId = event.getUser().getIdLong();
      adminPanelSessionManager.updatePanel(
          guildId,
          adminId,
          hook ->
              hook.editOriginalEmbeds(newEmbed)
                  .queue(
                      msg ->
                          LOG.trace(
                              "Updated admin panel token embed for guildId={}, adminId={}",
                              guildId,
                              adminId),
                      error -> LOG.warn("Failed to edit admin panel token embed", error)));
    }

    event
        .reply(
            String.format(
                "✅ 遊戲代幣調整成功！\n<@%d> 的代幣：\n調整前：🎮 %,d\n調整後：🎮 %,d",
                userId, adjustResult.previousTokens(), adjustResult.newTokens()))
        .setEphemeral(true)
        .queue();
  }

  private void handleGame1TokensModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    String minStr = event.getValue("min").getAsString().trim();
    String maxStr = event.getValue("max").getAsString().trim();

    long minTokens, maxTokens;
    try {
      minTokens = Long.parseLong(minStr);
      maxTokens = Long.parseLong(maxStr);
    } catch (NumberFormatException e) {
      event.reply("數值格式錯誤，請輸入有效數字").setEphemeral(true).queue();
      return;
    }

    // Validate
    if (minTokens < 0 || maxTokens < 0) {
      event.reply("數值不可為負數").setEphemeral(true).queue();
      return;
    }

    if (minTokens > maxTokens) {
      event.reply("最小值不可大於最大值").setEphemeral(true).queue();
      return;
    }

    DiceGame1Config oldConfig = adminPanelService.getDiceGame1Config(guildId);
    Result<DiceGame1Config, DomainError> result =
        adminPanelService.updateDiceGame1Config(guildId, minTokens, maxTokens, null);

    if (result.isErr()) {
      event.reply("更新失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    DiceGame1Config newConfig = result.getValue();
    String currencyIcon = getCurrencyIcon(guildId);

    MessageEmbed newEmbed = buildDiceGame1SettingsEmbed(newConfig, currencyIcon);
    long adminId = event.getUser().getIdLong();
    adminPanelSessionManager.updatePanel(
        guildId,
        adminId,
        hook ->
            hook.editOriginalEmbeds(newEmbed)
                .queue(
                    msg ->
                        LOG.trace(
                            "Updated dice-game-1 token range embed for guildId={}, adminId={}",
                            guildId,
                            adminId),
                    error -> LOG.warn("Failed to edit dice-game-1 token range embed", error)));

    event
        .reply(
            String.format(
                "✅ 摘星手代幣範圍更新成功！\n" + "最小：🎮 %,d → %,d\n" + "最大：🎮 %,d → %,d",
                oldConfig.minTokensPerPlay(),
                newConfig.minTokensPerPlay(),
                oldConfig.maxTokensPerPlay(),
                newConfig.maxTokensPerPlay()))
        .setEphemeral(true)
        .queue();
  }

  private void handleGame1RewardModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    String rewardStr = event.getValue("reward").getAsString().trim();

    long rewardPerDice;
    try {
      rewardPerDice = Long.parseLong(rewardStr);
    } catch (NumberFormatException e) {
      event.reply("數值格式錯誤，請輸入有效數字").setEphemeral(true).queue();
      return;
    }

    if (rewardPerDice < 0) {
      event.reply("獎勵倍率不可為負數").setEphemeral(true).queue();
      return;
    }

    DiceGame1Config oldConfig = adminPanelService.getDiceGame1Config(guildId);
    Result<DiceGame1Config, DomainError> result =
        adminPanelService.updateDiceGame1Config(guildId, null, null, rewardPerDice);

    if (result.isErr()) {
      event.reply("更新失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    DiceGame1Config newConfig = result.getValue();
    String currencyIcon = getCurrencyIcon(guildId);

    MessageEmbed newEmbed = buildDiceGame1SettingsEmbed(newConfig, currencyIcon);
    long adminId = event.getUser().getIdLong();
    adminPanelSessionManager.updatePanel(
        guildId,
        adminId,
        hook ->
            hook.editOriginalEmbeds(newEmbed)
                .queue(
                    msg ->
                        LOG.trace(
                            "Updated dice-game-1 reward embed for guildId={}, adminId={}",
                            guildId,
                            adminId),
                    error -> LOG.warn("Failed to edit dice-game-1 reward embed", error)));

    event
        .reply(
            String.format(
                "✅ 摘星手獎勵設定更新成功！\n" + "單骰倍率：%s %,d → %,d",
                currencyIcon, oldConfig.rewardPerDiceValue(), newConfig.rewardPerDiceValue()))
        .setEphemeral(true)
        .queue();
  }

  private void handleGame2TokensModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    String minStr = event.getValue("min").getAsString().trim();
    String maxStr = event.getValue("max").getAsString().trim();

    long minTokens, maxTokens;
    try {
      minTokens = Long.parseLong(minStr);
      maxTokens = Long.parseLong(maxStr);
    } catch (NumberFormatException e) {
      event.reply("數值格式錯誤，請輸入有效數字").setEphemeral(true).queue();
      return;
    }

    // Validate
    if (minTokens < 0 || maxTokens < 0) {
      event.reply("數值不可為負數").setEphemeral(true).queue();
      return;
    }

    if (minTokens > maxTokens) {
      event.reply("最小值不可大於最大值").setEphemeral(true).queue();
      return;
    }

    DiceGame2Config oldConfig = adminPanelService.getDiceGame2Config(guildId);
    Result<DiceGame2Config, DomainError> result =
        adminPanelService.updateDiceGame2Config(
            guildId, minTokens, maxTokens, null, null, null, null);

    if (result.isErr()) {
      event.reply("更新失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    DiceGame2Config newConfig = result.getValue();
    String currencyIcon = getCurrencyIcon(guildId);

    MessageEmbed newEmbed = buildDiceGame2SettingsEmbed(newConfig, currencyIcon);
    long adminId = event.getUser().getIdLong();
    adminPanelSessionManager.updatePanel(
        guildId,
        adminId,
        hook ->
            hook.editOriginalEmbeds(newEmbed)
                .queue(
                    msg ->
                        LOG.trace(
                            "Updated dice-game-2 token range embed for guildId={}, adminId={}",
                            guildId,
                            adminId),
                    error -> LOG.warn("Failed to edit dice-game-2 token range embed", error)));

    event
        .reply(
            String.format(
                "✅ 神龍擺尾代幣範圍更新成功！\n" + "最小：🎮 %,d → %,d\n" + "最大：🎮 %,d → %,d",
                oldConfig.minTokensPerPlay(),
                newConfig.minTokensPerPlay(),
                oldConfig.maxTokensPerPlay(),
                newConfig.maxTokensPerPlay()))
        .setEphemeral(true)
        .queue();
  }

  private void handleGame2MultipliersModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    String straightStr = event.getValue("straight").getAsString().trim();
    String baseStr = event.getValue("base").getAsString().trim();

    long straightMultiplier, baseMultiplier;
    try {
      straightMultiplier = Long.parseLong(straightStr);
      baseMultiplier = Long.parseLong(baseStr);
    } catch (NumberFormatException e) {
      event.reply("數值格式錯誤，請輸入有效數字").setEphemeral(true).queue();
      return;
    }

    if (straightMultiplier < 0 || baseMultiplier < 0) {
      event.reply("倍率不可為負數").setEphemeral(true).queue();
      return;
    }

    DiceGame2Config oldConfig = adminPanelService.getDiceGame2Config(guildId);
    Result<DiceGame2Config, DomainError> result =
        adminPanelService.updateDiceGame2Config(
            guildId, null, null, straightMultiplier, baseMultiplier, null, null);

    if (result.isErr()) {
      event.reply("更新失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    DiceGame2Config newConfig = result.getValue();
    String currencyIcon = getCurrencyIcon(guildId);

    MessageEmbed newEmbed = buildDiceGame2SettingsEmbed(newConfig, currencyIcon);
    long adminId = event.getUser().getIdLong();
    adminPanelSessionManager.updatePanel(
        guildId,
        adminId,
        hook ->
            hook.editOriginalEmbeds(newEmbed)
                .queue(
                    msg ->
                        LOG.trace(
                            "Updated dice-game-2 multipliers embed for guildId={}, adminId={}",
                            guildId,
                            adminId),
                    error -> LOG.warn("Failed to edit dice-game-2 multipliers embed", error)));

    event
        .reply(
            String.format(
                "✅ 神龍擺尾獎勵倍率更新成功！\n" + "順子倍率：%s %,d → %,d\n" + "基礎倍率：%s %,d → %,d",
                currencyIcon,
                oldConfig.straightMultiplier(),
                newConfig.straightMultiplier(),
                currencyIcon,
                oldConfig.baseMultiplier(),
                newConfig.baseMultiplier()))
        .setEphemeral(true)
        .queue();
  }

  private void handleGame2BonusesModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    String lowStr = event.getValue("low").getAsString().trim();
    String highStr = event.getValue("high").getAsString().trim();

    long tripleLowBonus, tripleHighBonus;
    try {
      tripleLowBonus = Long.parseLong(lowStr);
      tripleHighBonus = Long.parseLong(highStr);
    } catch (NumberFormatException e) {
      event.reply("數值格式錯誤，請輸入有效數字").setEphemeral(true).queue();
      return;
    }

    if (tripleLowBonus < 0 || tripleHighBonus < 0) {
      event.reply("獎勵金額不可為負數").setEphemeral(true).queue();
      return;
    }

    DiceGame2Config oldConfig = adminPanelService.getDiceGame2Config(guildId);
    Result<DiceGame2Config, DomainError> result =
        adminPanelService.updateDiceGame2Config(
            guildId, null, null, null, null, tripleLowBonus, tripleHighBonus);

    if (result.isErr()) {
      event.reply("更新失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    DiceGame2Config newConfig = result.getValue();
    String currencyIcon = getCurrencyIcon(guildId);

    MessageEmbed newEmbed = buildDiceGame2SettingsEmbed(newConfig, currencyIcon);
    long adminId = event.getUser().getIdLong();
    adminPanelSessionManager.updatePanel(
        guildId,
        adminId,
        hook ->
            hook.editOriginalEmbeds(newEmbed)
                .queue(
                    msg ->
                        LOG.trace(
                            "Updated dice-game-2 bonuses embed for guildId={}, adminId={}",
                            guildId,
                            adminId),
                    error -> LOG.warn("Failed to edit dice-game-2 bonuses embed", error)));

    event
        .reply(
            String.format(
                "✅ 神龍擺尾豹子獎勵更新成功！\n" + "小豹子：%s %,d → %,d\n" + "大豹子：%s %,d → %,d",
                currencyIcon,
                oldConfig.tripleLowBonus(),
                newConfig.tripleLowBonus(),
                currencyIcon,
                oldConfig.tripleHighBonus(),
                newConfig.tripleHighBonus()))
        .setEphemeral(true)
        .queue();
  }

  // ===== Helper Methods =====

  private String getSessionKey(long userId, long guildId) {
    return guildId + ":" + userId;
  }

  private String getModeLabel(String mode) {
    return switch (mode) {
      case "add" -> "增加餘額";
      case "deduct" -> "扣除餘額";
      case "adjust" -> "設定餘額";
      default -> mode;
    };
  }

  private String getTokenModeLabel(String mode) {
    return switch (mode) {
      case "add" -> "增加代幣";
      case "deduct" -> "扣除代幣";
      case "adjust" -> "設定代幣";
      default -> mode;
    };
  }

  // ===== Escort 定價設定 =====

  private void showEscortPricingConfig(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    Result<List<EscortOptionPricingService.OptionPriceView>, DomainError> result =
        adminPanelService.getEscortOptionPrices(guildId);

    if (result.isErr()) {
      event
          .editMessageEmbeds(
              buildEscortPricingEmbed(List.of(), "❌ 載入失敗：" + result.getError().message()))
          .setComponents(buildEscortPricingComponents())
          .queue();
      return;
    }

    event
        .editMessageEmbeds(buildEscortPricingEmbed(result.getValue(), null))
        .setComponents(buildEscortPricingComponents())
        .queue();
  }

  private void openEscortPricingEditModal(ButtonInteractionEvent event) {
    openEscortPricingPanel(event, ESCORT_PRICING_ACTION_UPDATE);
  }

  private void openEscortPricingResetModal(ButtonInteractionEvent event) {
    openEscortPricingPanel(event, ESCORT_PRICING_ACTION_RESET);
  }

  private void openEscortPricingPanel(ButtonInteractionEvent event, String defaultAction) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    EscortPricingPanelState state = escortPricingPanelStates.get(sessionKey);
    if (state == null) {
      state = new EscortPricingPanelState();
    }

    if (!ESCORT_PRICING_ACTION_RESET.equals(defaultAction)) {
      defaultAction = ESCORT_PRICING_ACTION_UPDATE;
    }
    state.action = defaultAction;

    Result<List<EscortOptionPricingService.OptionPriceView>, DomainError> result =
        adminPanelService.getEscortOptionPrices(guildId);
    if (result.isErr()) {
      event.reply("❌ 無法載入護航選項：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    Map<String, EscortOptionPricingService.OptionPriceView> optionMap =
        toEscortOptionMap(result.getValue());
    if (optionMap.isEmpty()) {
      event.reply("❌ 沒有可設定的護航選項").setEphemeral(true).queue();
      return;
    }

    if (state.optionCode == null || !optionMap.containsKey(state.optionCode)) {
      state.optionCode = result.getValue().get(0).optionCode();
      state.pendingPriceTwd = null;
    }
    state.statusMessage = null;

    EscortPricingPanelState capturedState = state;
    event
        .replyEmbeds(buildEscortPricingPanelEmbed(capturedState, optionMap))
        .setComponents(buildEscortPricingPanelComponents(capturedState, optionMap))
        .setEphemeral(true)
        .queue(
            hook -> {
              capturedState.panelHook = hook;
              escortPricingPanelStates.put(sessionKey, capturedState);
            });
  }

  private void handleEscortPricingEditModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    long adminId = event.getUser().getIdLong();
    String optionCode = event.getValue("option_code").getAsString().trim();
    String priceStr = event.getValue("price_twd").getAsString().trim();

    long priceTwd;
    try {
      priceTwd = Long.parseLong(priceStr);
    } catch (NumberFormatException e) {
      event.reply("價格格式錯誤，請輸入有效整數").setEphemeral(true).queue();
      return;
    }

    Result<EscortOptionPricingService.OptionPriceView, DomainError> updateResult =
        adminPanelService.updateEscortOptionPrice(guildId, adminId, optionCode, priceTwd);
    if (updateResult.isErr()) {
      event.reply("❌ 更新失敗：" + updateResult.getError().message()).setEphemeral(true).queue();
      return;
    }

    EscortOptionPricingService.OptionPriceView view = updateResult.getValue();
    event
        .reply(String.format("✅ 已更新 `%s` 為 NT$%,d", view.optionCode(), view.effectivePriceTwd()))
        .setEphemeral(true)
        .queue();
  }

  private void handleEscortPricingResetModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String optionCode = event.getValue("option_code").getAsString().trim();

    Result<Unit, DomainError> result =
        adminPanelService.resetEscortOptionPrice(guildId, optionCode);
    if (result.isErr()) {
      event.reply("❌ 重置失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    event.reply("✅ 已重置 `" + optionCode.toUpperCase() + "` 為預設價格").setEphemeral(true).queue();
  }

  private void openEscortPricingPanelPriceModal(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    EscortPricingPanelState state = escortPricingPanelStates.get(sessionKey);
    if (state == null) {
      event.reply("設定面板已過期，請重新開啟").setEphemeral(true).queue();
      return;
    }
    if (ESCORT_PRICING_ACTION_RESET.equals(state.action)) {
      event.reply("重置模式不需要輸入價格").setEphemeral(true).queue();
      return;
    }

    TextInput.Builder inputBuilder =
        TextInput.create("price_twd", "實際價格（TWD）", TextInputStyle.SHORT)
            .setPlaceholder("輸入新台幣整數，例如 1500")
            .setRequired(true)
            .setMinLength(1)
            .setMaxLength(15);
    if (state.pendingPriceTwd != null) {
      inputBuilder.setValue(String.valueOf(state.pendingPriceTwd));
    }
    Modal modal =
        Modal.create(MODAL_ESCORT_PRICING_PANEL_PRICE, "設定護航價格")
            .addComponents(ActionRow.of(inputBuilder.build()))
            .build();
    event.replyModal(modal).queue();
  }

  private void handleEscortPricingPanelPriceModal(ModalInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    EscortPricingPanelState state = escortPricingPanelStates.get(sessionKey);
    if (state == null) {
      event.reply("設定面板已過期，請重新開啟").setEphemeral(true).queue();
      return;
    }

    String priceStr = event.getValue("price_twd").getAsString().trim();
    long priceTwd;
    try {
      priceTwd = Long.parseLong(priceStr);
    } catch (NumberFormatException e) {
      event.reply("價格格式錯誤，請輸入有效整數").setEphemeral(true).queue();
      return;
    }
    if (priceTwd <= 0) {
      event.reply("護航價格必須大於 0").setEphemeral(true).queue();
      return;
    }

    state.pendingPriceTwd = priceTwd;
    state.statusMessage = String.format("✅ 已更新暫存價格：NT$%,d（尚未送出）", priceTwd);

    Result<List<EscortOptionPricingService.OptionPriceView>, DomainError> listResult =
        adminPanelService.getEscortOptionPrices(guildId);
    if (state.panelHook != null && listResult.isOk()) {
      Map<String, EscortOptionPricingService.OptionPriceView> optionMap =
          toEscortOptionMap(listResult.getValue());
      state
          .panelHook
          .editOriginalEmbeds(buildEscortPricingPanelEmbed(state, optionMap))
          .setComponents(buildEscortPricingPanelComponents(state, optionMap))
          .queue(
              success -> LOG.trace("Updated escort pricing panel for guildId={}", guildId),
              error -> LOG.warn("Failed to update escort pricing panel", error));
    }

    event.reply("✅ 已回填價格，請回設定面板按「確認送出」").setEphemeral(true).queue();
  }

  private void handleEscortPricingPanelConfirm(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    long adminId = event.getUser().getIdLong();
    String sessionKey = getSessionKey(adminId, guildId);
    EscortPricingPanelState state = escortPricingPanelStates.get(sessionKey);
    if (state == null) {
      event.reply("設定面板已過期，請重新開啟").setEphemeral(true).queue();
      return;
    }

    Result<List<EscortOptionPricingService.OptionPriceView>, DomainError> listResult =
        adminPanelService.getEscortOptionPrices(guildId);
    if (listResult.isErr()) {
      event.reply("❌ 讀取護航定價失敗：" + listResult.getError().message()).setEphemeral(true).queue();
      return;
    }
    Map<String, EscortOptionPricingService.OptionPriceView> optionMap =
        toEscortOptionMap(listResult.getValue());

    if (state.optionCode == null || !optionMap.containsKey(state.optionCode)) {
      state.statusMessage = "❌ 請先選擇護航選項";
      event
          .editMessageEmbeds(buildEscortPricingPanelEmbed(state, optionMap))
          .setComponents(buildEscortPricingPanelComponents(state, optionMap))
          .queue();
      return;
    }

    if (ESCORT_PRICING_ACTION_UPDATE.equals(state.action) && state.pendingPriceTwd == null) {
      state.statusMessage = "❌ 調整價格前請先輸入新的價格";
      event
          .editMessageEmbeds(buildEscortPricingPanelEmbed(state, optionMap))
          .setComponents(buildEscortPricingPanelComponents(state, optionMap))
          .queue();
      return;
    }

    if (ESCORT_PRICING_ACTION_RESET.equals(state.action)) {
      Result<Unit, DomainError> resetResult =
          adminPanelService.resetEscortOptionPrice(guildId, state.optionCode);
      if (resetResult.isErr()) {
        state.statusMessage = "❌ 重置失敗：" + resetResult.getError().message();
      } else {
        state.statusMessage = "✅ 已重置 `" + state.optionCode + "` 為預設價格";
        state.pendingPriceTwd = null;
      }
    } else {
      Result<EscortOptionPricingService.OptionPriceView, DomainError> updateResult =
          adminPanelService.updateEscortOptionPrice(
              guildId, adminId, state.optionCode, state.pendingPriceTwd);
      if (updateResult.isErr()) {
        state.statusMessage = "❌ 更新失敗：" + updateResult.getError().message();
      } else {
        EscortOptionPricingService.OptionPriceView updated = updateResult.getValue();
        state.statusMessage =
            String.format("✅ 已更新 `%s` 為 NT$%,d", updated.optionCode(), updated.effectivePriceTwd());
      }
    }

    refreshEscortPricingMainPanel(guildId, adminId, state.statusMessage);

    Result<List<EscortOptionPricingService.OptionPriceView>, DomainError> latestListResult =
        adminPanelService.getEscortOptionPrices(guildId);
    if (latestListResult.isOk()) {
      optionMap = toEscortOptionMap(latestListResult.getValue());
    }
    event
        .editMessageEmbeds(buildEscortPricingPanelEmbed(state, optionMap))
        .setComponents(buildEscortPricingPanelComponents(state, optionMap))
        .queue();
  }

  private void handleEscortPricingPanelClose(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    escortPricingPanelStates.remove(sessionKey);
    MessageEmbed closedEmbed = buildAdminEmbed("🛡️ 護航定價設定面板", "已關閉設定面板", List.of(), null);
    event.editMessageEmbeds(closedEmbed).setComponents(List.of()).queue();
  }

  private void handleEscortPricingPanelActionSelect(
      StringSelectInteractionEvent event, String sessionKey, long guildId) {
    EscortPricingPanelState state = escortPricingPanelStates.get(sessionKey);
    if (state == null) {
      event.reply("設定面板已過期，請重新開啟").setEphemeral(true).queue();
      return;
    }
    String action = event.getValues().get(0);
    state.action =
        ESCORT_PRICING_ACTION_RESET.equals(action)
            ? ESCORT_PRICING_ACTION_RESET
            : ESCORT_PRICING_ACTION_UPDATE;
    if (ESCORT_PRICING_ACTION_RESET.equals(state.action)) {
      state.pendingPriceTwd = null;
    }
    state.statusMessage = "✅ 已更新操作模式（尚未送出）";

    Result<List<EscortOptionPricingService.OptionPriceView>, DomainError> listResult =
        adminPanelService.getEscortOptionPrices(guildId);
    if (listResult.isErr()) {
      event.reply("❌ 讀取護航定價失敗：" + listResult.getError().message()).setEphemeral(true).queue();
      return;
    }
    Map<String, EscortOptionPricingService.OptionPriceView> optionMap =
        toEscortOptionMap(listResult.getValue());
    event
        .editMessageEmbeds(buildEscortPricingPanelEmbed(state, optionMap))
        .setComponents(buildEscortPricingPanelComponents(state, optionMap))
        .queue();
  }

  private void handleEscortPricingPanelOptionSelect(
      StringSelectInteractionEvent event, String sessionKey, long guildId) {
    EscortPricingPanelState state = escortPricingPanelStates.get(sessionKey);
    if (state == null) {
      event.reply("設定面板已過期，請重新開啟").setEphemeral(true).queue();
      return;
    }
    state.optionCode = event.getValues().get(0);
    state.statusMessage = "✅ 已更新護航選項（尚未送出）";

    Result<List<EscortOptionPricingService.OptionPriceView>, DomainError> listResult =
        adminPanelService.getEscortOptionPrices(guildId);
    if (listResult.isErr()) {
      event.reply("❌ 讀取護航定價失敗：" + listResult.getError().message()).setEphemeral(true).queue();
      return;
    }
    Map<String, EscortOptionPricingService.OptionPriceView> optionMap =
        toEscortOptionMap(listResult.getValue());
    event
        .editMessageEmbeds(buildEscortPricingPanelEmbed(state, optionMap))
        .setComponents(buildEscortPricingPanelComponents(state, optionMap))
        .queue();
  }

  private MessageEmbed buildEscortPricingPanelEmbed(
      EscortPricingPanelState state,
      Map<String, EscortOptionPricingService.OptionPriceView> optionMap) {
    List<EmbedView.FieldView> fields = new ArrayList<>();
    fields.add(
        new EmbedView.FieldView(
            "操作模式", ESCORT_PRICING_ACTION_RESET.equals(state.action) ? "重置為預設價格" : "調整實際價格", true));

    EscortOptionPricingService.OptionPriceView selected =
        state.optionCode == null ? null : optionMap.get(state.optionCode);
    fields.add(
        new EmbedView.FieldView(
            "護航選項",
            selected == null
                ? "尚未選擇"
                : String.format(
                    "`%s` %s｜%s",
                    selected.optionCode(), selected.option().type(), selected.option().target()),
            false));

    if (selected != null) {
      fields.add(
          new EmbedView.FieldView(
              "目前定價",
              String.format(
                  "生效：NT$%,d\n預設：NT$%,d", selected.effectivePriceTwd(), selected.defaultPriceTwd()),
              true));
    }

    if (ESCORT_PRICING_ACTION_UPDATE.equals(state.action)) {
      fields.add(
          new EmbedView.FieldView(
              "暫存價格",
              state.pendingPriceTwd == null
                  ? "尚未輸入"
                  : String.format("NT$%,d", state.pendingPriceTwd),
              true));
    }

    if (state.statusMessage != null && !state.statusMessage.isBlank()) {
      fields.add(new EmbedView.FieldView("狀態", state.statusMessage, false));
    }

    return buildAdminEmbed("🛡️ 護航定價設定面板", "先選擇操作與選項，再按確認送出變更", fields, "按下「確認送出」前不會更新實際設定");
  }

  private List<ActionRow> buildEscortPricingPanelComponents(
      EscortPricingPanelState state,
      Map<String, EscortOptionPricingService.OptionPriceView> optionMap) {
    StringSelectMenu actionSelect =
        StringSelectMenu.create(SELECT_ESCORT_PRICING_PANEL_ACTION)
            .setPlaceholder("選擇操作模式")
            .addOption("調整價格", ESCORT_PRICING_ACTION_UPDATE, "設定指定護航選項的價格")
            .addOption("重置為預設", ESCORT_PRICING_ACTION_RESET, "移除覆蓋價格並回到預設值")
            .setDefaultValues(List.of(state.action))
            .build();

    List<EscortOptionPricingService.OptionPriceView> allOptions =
        new java.util.ArrayList<>(optionMap.values());
    int primaryLimit = Math.min(25, allOptions.size());
    List<EscortOptionPricingService.OptionPriceView> primaryOptions =
        allOptions.subList(0, primaryLimit);
    List<EscortOptionPricingService.OptionPriceView> extraOptions =
        allOptions.size() > primaryLimit
            ? allOptions.subList(primaryLimit, allOptions.size())
            : List.of();

    String selectedCode =
        state.optionCode != null && optionMap.containsKey(state.optionCode)
            ? state.optionCode
            : null;
    boolean selectedInPrimary = false;
    boolean selectedInExtra = false;
    if (selectedCode != null) {
      selectedInPrimary =
          primaryOptions.stream().anyMatch(option -> option.optionCode().equals(selectedCode));
      if (!selectedInPrimary) {
        selectedInExtra =
            extraOptions.stream().anyMatch(option -> option.optionCode().equals(selectedCode));
      }
    }

    StringSelectMenu.Builder optionPrimaryBuilder =
        StringSelectMenu.create(SELECT_ESCORT_PRICING_PANEL_OPTION).setPlaceholder("選擇護航選項");
    primaryOptions.forEach(view -> addEscortPricingOption(optionPrimaryBuilder, view));
    if (selectedInPrimary) {
      optionPrimaryBuilder.setDefaultValues(List.of(selectedCode));
    }
    StringSelectMenu optionPrimary = optionPrimaryBuilder.build();

    StringSelectMenu optionExtra = null;
    if (!extraOptions.isEmpty()) {
      StringSelectMenu.Builder optionExtraBuilder =
          StringSelectMenu.create(SELECT_ESCORT_PRICING_PANEL_OPTION_EXTRA)
              .setPlaceholder("選擇護航選項（更多）");
      extraOptions.forEach(view -> addEscortPricingOption(optionExtraBuilder, view));
      if (selectedInExtra) {
        optionExtraBuilder.setDefaultValues(List.of(selectedCode));
      }
      optionExtra = optionExtraBuilder.build();
    }

    boolean canConfirm =
        state.optionCode != null
            && (ESCORT_PRICING_ACTION_RESET.equals(state.action) || state.pendingPriceTwd != null);
    List<ActionRow> rows = new java.util.ArrayList<>();
    rows.add(PanelComponentRenderer.buildRow(actionSelect));
    rows.add(PanelComponentRenderer.buildRow(optionPrimary));
    if (optionExtra != null) {
      rows.add(PanelComponentRenderer.buildRow(optionExtra));
    }
    rows.add(
        PanelComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(
                    BUTTON_ESCORT_PRICING_PANEL_INPUT_PRICE,
                    "💵 輸入價格",
                    ButtonStyle.SECONDARY,
                    ESCORT_PRICING_ACTION_RESET.equals(state.action)),
                new ButtonView(
                    BUTTON_ESCORT_PRICING_PANEL_CONFIRM,
                    "✅ 確認送出",
                    ButtonStyle.SUCCESS,
                    !canConfirm),
                new ButtonView(
                    BUTTON_ESCORT_PRICING_PANEL_CLOSE, "✖ 關閉", ButtonStyle.SECONDARY, false))));
    return rows;
  }

  private void addEscortPricingOption(
      StringSelectMenu.Builder optionBuilder, EscortOptionPricingService.OptionPriceView view) {
    optionBuilder.addOption(
        truncate(view.optionCode() + "｜" + view.option().target(), 100),
        view.optionCode(),
        truncate(
            String.format(
                "%s｜%s｜NT$%,d",
                view.option().type(), view.option().level(), view.effectivePriceTwd()),
            100));
  }

  private Map<String, EscortOptionPricingService.OptionPriceView> toEscortOptionMap(
      List<EscortOptionPricingService.OptionPriceView> optionPrices) {
    Map<String, EscortOptionPricingService.OptionPriceView> optionMap =
        new java.util.LinkedHashMap<>();
    for (EscortOptionPricingService.OptionPriceView view : optionPrices) {
      optionMap.put(view.optionCode(), view);
    }
    return optionMap;
  }

  private void refreshEscortPricingMainPanel(long guildId, long adminId, String statusMessage) {
    adminPanelSessionManager.updatePanel(
        guildId,
        adminId,
        hook -> {
          Result<List<EscortOptionPricingService.OptionPriceView>, DomainError> result =
              adminPanelService.getEscortOptionPrices(guildId);
          MessageEmbed embed =
              result.isErr()
                  ? buildEscortPricingEmbed(List.of(), "❌ 載入失敗：" + result.getError().message())
                  : buildEscortPricingEmbed(result.getValue(), statusMessage);
          hook.editOriginalEmbeds(embed)
              .setComponents(buildEscortPricingComponents())
              .queue(
                  success ->
                      LOG.trace(
                          "Refreshed escort pricing main panel for guildId={}, adminId={}",
                          guildId,
                          adminId),
                  error -> LOG.warn("Failed to refresh escort pricing main panel", error));
        });
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, Math.max(0, maxLength - 3)) + "...";
  }

  private MessageEmbed buildEscortPricingEmbed(
      List<EscortOptionPricingService.OptionPriceView> optionPrices, String statusMessage) {
    List<EmbedView.FieldView> fields = new ArrayList<>();

    if (optionPrices.isEmpty()) {
      fields.add(new EmbedView.FieldView("目前定價", "暫無資料", false));
    } else {
      addEscortPricingFields(fields, optionPrices);
    }

    if (statusMessage != null && !statusMessage.isBlank()) {
      fields.add(new EmbedView.FieldView("狀態", statusMessage, false));
    }

    return buildAdminEmbed("🛡️ 護航定價設定", "可調整各護航訂單類型的實際價格（TWD）", fields, "調整後會即時套用到後端護航開單請求");
  }

  private void addEscortPricingFields(
      List<EmbedView.FieldView> fields,
      List<EscortOptionPricingService.OptionPriceView> optionPrices) {
    String fieldName = "目前定價（含覆蓋狀態）";
    StringBuilder chunk = new StringBuilder();
    int chunkIndex = 1;

    for (EscortOptionPricingService.OptionPriceView view : optionPrices) {
      String line = view.toDisplayLine() + "\n";
      if (chunk.length() + line.length() > EMBED_FIELD_VALUE_LIMIT) {
        fields.add(
            new EmbedView.FieldView(
                chunkIndex == 1 ? fieldName : fieldName + "（續 " + chunkIndex + "）",
                chunk.toString(),
                false));
        chunk.setLength(0);
        chunkIndex++;
      }
      chunk.append(line);
    }

    if (chunk.length() > 0) {
      fields.add(
          new EmbedView.FieldView(
              chunkIndex == 1 ? fieldName : fieldName + "（續 " + chunkIndex + "）",
              chunk.toString(),
              false));
    }
  }

  private List<ActionRow> buildEscortPricingComponents() {
    return List.of(
        PanelComponentRenderer.buildActionRow(
            List.of(
                new ButtonView(BUTTON_ESCORT_PRICING_EDIT, "✏️ 調整面板", ButtonStyle.PRIMARY, false),
                new ButtonView(
                    BUTTON_ESCORT_PRICING_RESET, "♻️ 重置面板", ButtonStyle.SECONDARY, false),
                new ButtonView(
                    BUTTON_ESCORT_PRICING_REFRESH, "🔄 重新整理", ButtonStyle.SECONDARY, false))),
        PanelComponentRenderer.buildActionRow(
            List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))));
  }

  // ===== Dispatch 售後設定 =====

  private void showDispatchAfterSalesConfig(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    Result<Set<Long>, DomainError> staffResult =
        adminPanelService.getDispatchAfterSalesStaff(guildId);
    String statusMessage = null;

    Set<Long> staffUserIds = Set.of();
    if (staffResult.isOk()) {
      staffUserIds = staffResult.getValue();
    } else {
      statusMessage = "❌ 讀取售後名單失敗：" + staffResult.getError().message();
    }

    event
        .editMessageEmbeds(buildDispatchAfterSalesConfigEmbed(staffUserIds, statusMessage))
        .setComponents(buildDispatchAfterSalesConfigComponents())
        .queue();
  }

  private void handleDispatchAfterSalesAddUserSelect(
      EntitySelectInteractionEvent event, long guildId) {
    List<User> selectedUsers = event.getMentions().getUsers();
    if (selectedUsers.isEmpty()) {
      event.reply("請選擇一位成員").setEphemeral(true).queue();
      return;
    }

    User selectedUser = selectedUsers.get(0);
    Result<Unit, DomainError> addResult =
        adminPanelService.addDispatchAfterSalesStaff(guildId, selectedUser.getIdLong());

    if (addResult.isErr()) {
      event.reply("❌ 新增售後人員失敗：" + addResult.getError().message()).setEphemeral(true).queue();
      refreshDispatchAfterSalesConfigMessage(
          event, guildId, "❌ 新增失敗：" + addResult.getError().message());
      return;
    }

    event.reply("✅ 已新增售後人員：" + selectedUser.getAsMention()).setEphemeral(true).queue();
    refreshDispatchAfterSalesConfigMessage(event, guildId, "✅ 已新增 " + selectedUser.getAsMention());
  }

  private void handleDispatchAfterSalesRemoveUserSelect(
      EntitySelectInteractionEvent event, long guildId) {
    List<User> selectedUsers = event.getMentions().getUsers();
    if (selectedUsers.isEmpty()) {
      event.reply("請選擇一位成員").setEphemeral(true).queue();
      return;
    }

    User selectedUser = selectedUsers.get(0);
    Result<Unit, DomainError> removeResult =
        adminPanelService.removeDispatchAfterSalesStaff(guildId, selectedUser.getIdLong());

    if (removeResult.isErr()) {
      event.reply("❌ 移除售後人員失敗：" + removeResult.getError().message()).setEphemeral(true).queue();
      refreshDispatchAfterSalesConfigMessage(
          event, guildId, "❌ 移除失敗：" + removeResult.getError().message());
      return;
    }

    event.reply("✅ 已移除售後人員：" + selectedUser.getAsMention()).setEphemeral(true).queue();
    refreshDispatchAfterSalesConfigMessage(event, guildId, "✅ 已移除 " + selectedUser.getAsMention());
  }

  private void refreshDispatchAfterSalesConfigMessage(
      EntitySelectInteractionEvent event, long guildId, String statusMessage) {
    Result<Set<Long>, DomainError> staffResult =
        adminPanelService.getDispatchAfterSalesStaff(guildId);
    Set<Long> staffUserIds = staffResult.isOk() ? staffResult.getValue() : Set.of();
    String status =
        staffResult.isErr() ? "❌ 讀取售後名單失敗：" + staffResult.getError().message() : statusMessage;

    event
        .getMessage()
        .editMessageEmbeds(buildDispatchAfterSalesConfigEmbed(staffUserIds, status))
        .setComponents(buildDispatchAfterSalesConfigComponents())
        .queue();
  }

  private MessageEmbed buildDispatchAfterSalesConfigEmbed(
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

  private List<ActionRow> buildDispatchAfterSalesConfigComponents() {
    EntitySelectMenu addUserSelect =
        EntitySelectMenu.create(
                SELECT_DISPATCH_AFTER_SALES_ADD_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("新增售後人員")
            .setRequiredRange(1, 1)
            .build();

    EntitySelectMenu removeUserSelect =
        EntitySelectMenu.create(
                SELECT_DISPATCH_AFTER_SALES_REMOVE_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("移除售後人員")
            .setRequiredRange(1, 1)
            .build();

    return List.of(
        PanelComponentRenderer.buildRow(addUserSelect),
        PanelComponentRenderer.buildRow(removeUserSelect),
        PanelComponentRenderer.buildActionRow(
            List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))));
  }

  // ===== AI 頻道設定管理 =====

  /** 顯示 AI 頻道設定頁面。 */
  private void showAIChannelConfig(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    var channelsResult = adminPanelService.getAllowedChannels(guildId);
    var categoriesResult = adminPanelService.getAllowedCategories(guildId);
    MessageEmbed embed;

    if (channelsResult.isOk()) {
      var channels = channelsResult.getValue();
      var categories =
          categoriesResult.isOk()
              ? categoriesResult.getValue()
              : Set.<ltdjms.discord.aichat.domain.AllowedCategory>of();
      embed = buildAIChannelConfigEmbed(guildId, channels, categories);
    } else {
      var categories =
          categoriesResult.isOk()
              ? categoriesResult.getValue()
              : Set.<ltdjms.discord.aichat.domain.AllowedCategory>of();
      embed = buildAIChannelConfigEmbed(guildId, Set.of(), categories);
    }

    EntitySelectMenu addChannelSelect =
        EntitySelectMenu.create(SELECT_AI_ADD_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
            .setPlaceholder("新增允許的頻道")
            .setRequiredRange(1, 1)
            .build();

    EntitySelectMenu removeChannelSelect =
        EntitySelectMenu.create(SELECT_AI_REMOVE_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
            .setPlaceholder("移除允許的頻道")
            .setRequiredRange(1, 1)
            .build();

    EntitySelectMenu addCategorySelect =
        EntitySelectMenu.create(SELECT_AI_ADD_CATEGORY, EntitySelectMenu.SelectTarget.CHANNEL)
            .setChannelTypes(ChannelType.CATEGORY)
            .setPlaceholder("新增允許的類別")
            .setRequiredRange(1, 1)
            .build();

    EntitySelectMenu removeCategorySelect =
        EntitySelectMenu.create(SELECT_AI_REMOVE_CATEGORY, EntitySelectMenu.SelectTarget.CHANNEL)
            .setChannelTypes(ChannelType.CATEGORY)
            .setPlaceholder("移除允許的類別")
            .setRequiredRange(1, 1)
            .build();

    event
        .editMessageEmbeds(embed)
        .setComponents(
            PanelComponentRenderer.buildRow(addChannelSelect),
            PanelComponentRenderer.buildRow(removeChannelSelect),
            PanelComponentRenderer.buildRow(addCategorySelect),
            PanelComponentRenderer.buildRow(removeCategorySelect),
            PanelComponentRenderer.buildActionRow(
                List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))))
        .queue();
  }

  /** 建立 AI 頻道設定的 Embed。 */
  private MessageEmbed buildAIChannelConfigEmbed(
      long guildId,
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

  /** 處理新增頻道選擇。 */
  private void handleAddChannelSelect(EntitySelectInteractionEvent event, long guildId) {
    var selectedChannels = event.getMentions().getChannels();
    if (selectedChannels.isEmpty()) {
      event.reply("請選擇一個頻道").setEphemeral(true).queue();
      return;
    }

    var selectedChannel = selectedChannels.get(0);
    long channelId = selectedChannel.getIdLong();
    String channelName = selectedChannel.getName();

    var result = adminPanelService.addAllowedChannel(guildId, channelId, channelName);

    if (result.isOk()) {
      event.reply("✅ 已新增頻道 **" + channelName + "** 到允許清單").setEphemeral(true).queue();

      // 更新面板顯示
      var updatedChannels = adminPanelService.getAllowedChannels(guildId);
      var updatedCategories = adminPanelService.getAllowedCategories(guildId);
      if (updatedChannels.isOk() && updatedCategories.isOk()) {
        MessageEmbed embed =
            buildAIChannelConfigEmbed(
                guildId, updatedChannels.getValue(), updatedCategories.getValue());

        EntitySelectMenu addChannelSelect =
            EntitySelectMenu.create(SELECT_AI_ADD_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("新增允許的頻道")
                .setRequiredRange(1, 1)
                .build();

        EntitySelectMenu removeChannelSelect =
            EntitySelectMenu.create(SELECT_AI_REMOVE_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("移除允許的頻道")
                .setRequiredRange(1, 1)
                .build();

        event
            .getMessage()
            .editMessageEmbeds(embed)
            .setComponents(
                PanelComponentRenderer.buildRow(addChannelSelect),
                PanelComponentRenderer.buildRow(removeChannelSelect),
                PanelComponentRenderer.buildActionRow(
                    List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))))
            .queue();
      }
    } else {
      DomainError error = result.getError();
      String errorMessage =
          switch (error.category()) {
            case DUPLICATE_CHANNEL -> "⚠️ 此頻道已在允許清單中";
            case INSUFFICIENT_PERMISSIONS -> "⚠️ 機器人在此頻道沒有足夠的權限";
            default -> "❌ " + error.message();
          };
      event.reply(errorMessage).setEphemeral(true).queue();
    }
  }

  /** 處理移除頻道選擇。 */
  private void handleRemoveChannelSelect(EntitySelectInteractionEvent event, long guildId) {
    var selectedChannels = event.getMentions().getChannels();
    if (selectedChannels.isEmpty()) {
      event.reply("請選擇一個頻道").setEphemeral(true).queue();
      return;
    }

    var selectedChannel = selectedChannels.get(0);
    long channelId = selectedChannel.getIdLong();
    String channelName = selectedChannel.getName();

    var result = adminPanelService.removeAllowedChannel(guildId, channelId);

    if (result.isOk()) {
      event.reply("✅ 已從允許清單移除頻道 **" + channelName + "**").setEphemeral(true).queue();

      // 更新面板顯示
      var updatedChannels = adminPanelService.getAllowedChannels(guildId);
      var updatedCategories = adminPanelService.getAllowedCategories(guildId);
      if (updatedChannels.isOk() && updatedCategories.isOk()) {
        MessageEmbed embed =
            buildAIChannelConfigEmbed(
                guildId, updatedChannels.getValue(), updatedCategories.getValue());

        EntitySelectMenu addChannelSelect =
            EntitySelectMenu.create(SELECT_AI_ADD_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("新增允許的頻道")
                .setRequiredRange(1, 1)
                .build();

        EntitySelectMenu removeChannelSelect =
            EntitySelectMenu.create(SELECT_AI_REMOVE_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("移除允許的頻道")
                .setRequiredRange(1, 1)
                .build();

        event
            .getMessage()
            .editMessageEmbeds(embed)
            .setComponents(
                PanelComponentRenderer.buildRow(addChannelSelect),
                PanelComponentRenderer.buildRow(removeChannelSelect),
                PanelComponentRenderer.buildActionRow(
                    List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))))
            .queue();
      }
    } else {
      DomainError error = result.getError();
      String errorMessage =
          switch (error.category()) {
            case CHANNEL_NOT_FOUND -> "⚠️ 此頻道不在允許清單中";
            default -> "❌ " + error.message();
          };
      event.reply(errorMessage).setEphemeral(true).queue();
    }
  }

  /** 處理新增類別選擇。 */
  private void handleAddCategorySelect(EntitySelectInteractionEvent event, long guildId) {
    var selectedChannels = event.getMentions().getChannels();
    if (selectedChannels.isEmpty()) {
      event.reply("請選擇一個類別").setEphemeral(true).queue();
      return;
    }

    var category = selectedChannels.get(0);
    if (category.getType() != ChannelType.CATEGORY) {
      event.reply("請選擇類別類型").setEphemeral(true).queue();
      return;
    }

    long categoryId = category.getIdLong();
    String categoryName = category.getName();

    var result = adminPanelService.addAllowedCategory(guildId, categoryId, categoryName);

    if (result.isOk()) {
      event.reply("✅ 已新增類別 **" + categoryName + "** 到允許清單").setEphemeral(true).queue();

      var updatedChannels = adminPanelService.getAllowedChannels(guildId);
      var updatedCategories = adminPanelService.getAllowedCategories(guildId);
      if (updatedChannels.isOk() && updatedCategories.isOk()) {
        MessageEmbed embed =
            buildAIChannelConfigEmbed(
                guildId, updatedChannels.getValue(), updatedCategories.getValue());

        EntitySelectMenu addChannelSelect =
            EntitySelectMenu.create(SELECT_AI_ADD_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("新增允許的頻道")
                .setRequiredRange(1, 1)
                .build();

        EntitySelectMenu removeChannelSelect =
            EntitySelectMenu.create(SELECT_AI_REMOVE_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("移除允許的頻道")
                .setRequiredRange(1, 1)
                .build();

        EntitySelectMenu addCategorySelect =
            EntitySelectMenu.create(SELECT_AI_ADD_CATEGORY, EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder("新增允許的類別")
                .setRequiredRange(1, 1)
                .build();

        EntitySelectMenu removeCategorySelect =
            EntitySelectMenu.create(
                    SELECT_AI_REMOVE_CATEGORY, EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder("移除允許的類別")
                .setRequiredRange(1, 1)
                .build();

        event
            .getMessage()
            .editMessageEmbeds(embed)
            .setComponents(
                PanelComponentRenderer.buildRow(addChannelSelect),
                PanelComponentRenderer.buildRow(removeChannelSelect),
                PanelComponentRenderer.buildRow(addCategorySelect),
                PanelComponentRenderer.buildRow(removeCategorySelect),
                PanelComponentRenderer.buildActionRow(
                    List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))))
            .queue();
      }
    } else {
      DomainError error = result.getError();
      String errorMessage =
          switch (error.category()) {
            case DUPLICATE_CATEGORY -> "⚠️ 此類別已在允許清單中";
            default -> "❌ " + error.message();
          };
      event.reply(errorMessage).setEphemeral(true).queue();
    }
  }

  /** 處理移除類別選擇。 */
  private void handleRemoveCategorySelect(EntitySelectInteractionEvent event, long guildId) {
    var selectedChannels = event.getMentions().getChannels();
    if (selectedChannels.isEmpty()) {
      event.reply("請選擇一個類別").setEphemeral(true).queue();
      return;
    }

    var category = selectedChannels.get(0);
    if (category.getType() != ChannelType.CATEGORY) {
      event.reply("請選擇類別類型").setEphemeral(true).queue();
      return;
    }

    long categoryId = category.getIdLong();
    String categoryName = category.getName();

    var result = adminPanelService.removeAllowedCategory(guildId, categoryId);

    if (result.isOk()) {
      event.reply("✅ 已從允許清單移除類別 **" + categoryName + "**").setEphemeral(true).queue();

      var updatedChannels = adminPanelService.getAllowedChannels(guildId);
      var updatedCategories = adminPanelService.getAllowedCategories(guildId);
      if (updatedChannels.isOk() && updatedCategories.isOk()) {
        MessageEmbed embed =
            buildAIChannelConfigEmbed(
                guildId, updatedChannels.getValue(), updatedCategories.getValue());

        EntitySelectMenu addChannelSelect =
            EntitySelectMenu.create(SELECT_AI_ADD_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("新增允許的頻道")
                .setRequiredRange(1, 1)
                .build();

        EntitySelectMenu removeChannelSelect =
            EntitySelectMenu.create(SELECT_AI_REMOVE_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("移除允許的頻道")
                .setRequiredRange(1, 1)
                .build();

        EntitySelectMenu addCategorySelect =
            EntitySelectMenu.create(SELECT_AI_ADD_CATEGORY, EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder("新增允許的類別")
                .setRequiredRange(1, 1)
                .build();

        EntitySelectMenu removeCategorySelect =
            EntitySelectMenu.create(
                    SELECT_AI_REMOVE_CATEGORY, EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder("移除允許的類別")
                .setRequiredRange(1, 1)
                .build();

        event
            .getMessage()
            .editMessageEmbeds(embed)
            .setComponents(
                PanelComponentRenderer.buildRow(addChannelSelect),
                PanelComponentRenderer.buildRow(removeChannelSelect),
                PanelComponentRenderer.buildRow(addCategorySelect),
                PanelComponentRenderer.buildRow(removeCategorySelect),
                PanelComponentRenderer.buildActionRow(
                    List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))))
            .queue();
      }
    } else {
      DomainError error = result.getError();
      String errorMessage =
          switch (error.category()) {
            case CATEGORY_NOT_FOUND -> "⚠️ 此類別不在允許清單中";
            default -> "❌ " + error.message();
          };
      event.reply(errorMessage).setEphemeral(true).queue();
    }
  }

  // ===== AI Agent 配置處理 =====

  private void showAIAgentConfig(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    Result<java.util.List<Long>, DomainError> result =
        adminPanelService.getEnabledAgentChannels(guildId);

    if (result.isOk()) {
      java.util.List<Long> enabledChannels = result.getValue();
      EntitySelectMenu channelSelect =
          EntitySelectMenu.create(SELECT_AI_AGENT_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
              .setPlaceholder("選擇頻道進行操作")
              .setRequiredRange(1, 1)
              .build();

      event
          .editMessageEmbeds(buildAIAgentConfigOverviewEmbed(enabledChannels))
          .setComponents(
              PanelComponentRenderer.buildRow(channelSelect),
              PanelComponentRenderer.buildActionRow(
                  List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))))
          .queue();
    } else {
      event.reply("❌ 獲取 AI Agent 頻道配置失敗：" + result.getError().message()).setEphemeral(true).queue();
    }
  }

  private void handleAIAgentChannelSelect(EntitySelectInteractionEvent event, long guildId) {
    if (event.getValues().isEmpty()) {
      event.reply("⚠️ 請選擇一個頻道").setEphemeral(true).queue();
      return;
    }

    long channelId = event.getValues().get(0).getIdLong();
    boolean isEnabled = adminPanelService.isAgentEnabled(guildId, channelId);

    String statusMessage =
        isEnabled ? "此頻道的 AI Agent 模式已啟用，AI 可以在此頻道調用系統工具" : "此頻道的 AI Agent 模式已停用，AI 將無法調用工具";

    event
        .editMessageEmbeds(buildAIAgentChannelEmbed(channelId, isEnabled, statusMessage))
        .setComponents(
            PanelComponentRenderer.buildActionRows(
                List.of(
                    List.of(
                        new ButtonView(
                            BUTTON_AI_AGENT_ENABLE, "✅ 啟用 AI Agent", ButtonStyle.SUCCESS, false),
                        new ButtonView(
                            BUTTON_AI_AGENT_DISABLE, "❌ 停用 AI Agent", ButtonStyle.DANGER, false),
                        new ButtonView(
                            BUTTON_AI_AGENT_REMOVE, "🗑️ 移除配置", ButtonStyle.SECONDARY, false)),
                    List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回", ButtonStyle.SECONDARY, false)))))
        .queue();
  }

  private void handleAIAgentEnable(ButtonInteractionEvent event) {
    // 從之前的 embed 中獲取頻道 ID（透過 parsing 描述）
    String description = event.getMessage().getEmbeds().get(0).getDescription();
    long channelId = extractChannelIdFromDescription(description);

    if (channelId == 0) {
      event.reply("⚠️ 無法獲取頻道資訊，請重新操作").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    Result<Unit, DomainError> result = adminPanelService.enableAgentChannel(guildId, channelId);

    if (result.isOk()) {
      event.reply("✅ 已啟用頻道的 AI Agent 模式").setEphemeral(true).queue();
      // 更新 AI Agent 配置頁面（就地更新原面板）
      showAIAgentConfigAfterAction(event, guildId);
    } else {
      event.reply("❌ 啟用失敗：" + result.getError().message()).setEphemeral(true).queue();
    }
  }

  private void handleAIAgentDisable(ButtonInteractionEvent event) {
    String description = event.getMessage().getEmbeds().get(0).getDescription();
    long channelId = extractChannelIdFromDescription(description);

    if (channelId == 0) {
      event.reply("⚠️ 無法獲取頻道資訊，請重新操作").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    Result<Unit, DomainError> result = adminPanelService.disableAgentChannel(guildId, channelId);

    if (result.isOk()) {
      event.reply("✅ 已停用頻道的 AI Agent 模式").setEphemeral(true).queue();
      // 更新 AI Agent 配置頁面（就地更新原面板）
      showAIAgentConfigAfterAction(event, guildId);
    } else {
      event.reply("❌ 停用失敗：" + result.getError().message()).setEphemeral(true).queue();
    }
  }

  private void handleAIAgentRemove(ButtonInteractionEvent event) {
    String description = event.getMessage().getEmbeds().get(0).getDescription();
    long channelId = extractChannelIdFromDescription(description);

    if (channelId == 0) {
      event.reply("⚠️ 無法獲取頻道資訊，請重新操作").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    Result<Unit, DomainError> result = adminPanelService.removeAgentChannel(guildId, channelId);

    if (result.isOk()) {
      event.reply("✅ 已移除頻道的 AI Agent 配置").setEphemeral(true).queue();
      // 更新 AI Agent 配置頁面（就地更新原面板）
      showAIAgentConfigAfterAction(event, guildId);
    } else {
      event.reply("❌ 移除失敗：" + result.getError().message()).setEphemeral(true).queue();
    }
  }

  private MessageEmbed buildAdminEmbed(
      String title, String description, List<EmbedView.FieldView> fields, String footer) {
    return PanelComponentRenderer.buildEmbed(
        new EmbedView(title, description, EMBED_COLOR, fields, footer));
  }

  private MessageEmbed buildAIAgentConfigOverviewEmbed(List<Long> enabledChannels) {
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

  private MessageEmbed buildAIAgentChannelEmbed(
      long channelId, boolean isEnabled, String statusMessage) {
    return buildAdminEmbed(
        "🤖 AI Agent 頻道設定",
        "頻道：<#" + channelId + ">\n狀態：" + (isEnabled ? "✅ 已啟用" : "❌ 未啟用"),
        List.of(new EmbedView.FieldView("目前狀態", statusMessage, false)),
        null);
  }

  private long extractChannelIdFromDescription(String description) {
    if (description == null) return 0;
    // 描述格式: "頻道：<#123456789>\n..."
    int start = description.indexOf("<#");
    if (start == -1) return 0;
    int end = description.indexOf(">", start);
    if (end == -1) return 0;
    try {
      return Long.parseLong(description.substring(start + 2, end));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private void showAIAgentConfigAfterAction(ButtonInteractionEvent event, long guildId) {
    Result<java.util.List<Long>, DomainError> result =
        adminPanelService.getEnabledAgentChannels(guildId);

    if (result.isOk()) {
      java.util.List<Long> enabledChannels = result.getValue();
      EntitySelectMenu channelSelect =
          EntitySelectMenu.create(SELECT_AI_AGENT_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
              .setPlaceholder("選擇頻道進行操作")
              .setRequiredRange(1, 1)
              .build();

      event
          .getMessage()
          .editMessageEmbeds(buildAIAgentConfigOverviewEmbed(enabledChannels))
          .setComponents(
              PanelComponentRenderer.buildRow(channelSelect),
              PanelComponentRenderer.buildActionRow(
                  List.of(new ButtonView(BUTTON_BACK, "⬅️ 返回主選單", ButtonStyle.SECONDARY, false))))
          .queue(
              success -> LOG.trace("Updated AI agent config panel for guildId={}", guildId),
              failure -> LOG.warn("Failed to update AI agent config panel", failure));
    } else {
      LOG.warn("Failed to fetch AI agent channel config: {}", result.getError().message());
    }
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

  // ===== Session State =====

  private enum ManagementType {
    BALANCE,
    TOKEN
  }

  private static class SessionState {
    ManagementType type;
    Long selectedUserId;
    String selectedUserMention;
    String selectedMode;
    Long currentValue;

    SessionState(ManagementType type) {
      this.type = type;
    }
  }

  private static class EscortPricingPanelState {
    String action = ESCORT_PRICING_ACTION_UPDATE;
    String optionCode;
    Long pendingPriceTwd;
    String statusMessage;
    InteractionHook panelHook;
  }
}
