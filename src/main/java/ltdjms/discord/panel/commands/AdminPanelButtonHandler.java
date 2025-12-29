package ltdjms.discord.panel.commands;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
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

  // Button IDs
  public static final String BUTTON_BALANCE = "admin_panel_balance";
  public static final String BUTTON_TOKENS = "admin_panel_tokens";
  public static final String BUTTON_GAMES = "admin_panel_games";
  public static final String BUTTON_BACK = "admin_panel_back";
  public static final String BUTTON_OPEN_BALANCE_MODAL = "admin_open_balance_modal";
  public static final String BUTTON_OPEN_TOKEN_MODAL = "admin_open_token_modal";
  public static final String BUTTON_AI_CHANNEL_CONFIG = "admin_panel_ai_channel";
  public static final String BUTTON_AI_ADD_CHANNEL = "admin_ai_add_channel";
  public static final String BUTTON_AI_REMOVE_CHANNEL = "admin_ai_remove_channel";

  // Modal IDs
  public static final String MODAL_BALANCE_ADJUST = "admin_modal_balance_adjust";
  public static final String MODAL_TOKEN_ADJUST = "admin_modal_token_adjust";
  public static final String MODAL_GAME_1_TOKENS = "admin_modal_game1_tokens";
  public static final String MODAL_GAME_1_REWARD = "admin_modal_game1_reward";
  public static final String MODAL_GAME_2_TOKENS = "admin_modal_game2_tokens";
  public static final String MODAL_GAME_2_MULTIPLIERS = "admin_modal_game2_multipliers";
  public static final String MODAL_GAME_2_BONUSES = "admin_modal_game2_bonuses";

  // Select Menu IDs
  public static final String SELECT_GAME = "admin_select_game";
  public static final String SELECT_BALANCE_USER = "admin_select_balance_user";
  public static final String SELECT_BALANCE_MODE = "admin_select_balance_mode";
  public static final String SELECT_TOKEN_USER = "admin_select_token_user";
  public static final String SELECT_TOKEN_MODE = "admin_select_token_mode";
  public static final String SELECT_GAME_SETTING = "admin_select_game_setting";
  public static final String SELECT_AI_ADD_CHANNEL = "admin_select_ai_add_channel";
  public static final String SELECT_AI_REMOVE_CHANNEL = "admin_select_ai_remove_channel";

  private final AdminPanelService adminPanelService;
  private final AdminPanelSessionManager adminPanelSessionManager;

  // Session state for user selections (keyed by interactionId or uniqueId)
  private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

  public AdminPanelButtonHandler(
      AdminPanelService adminPanelService, AdminPanelSessionManager adminPanelSessionManager) {
    this.adminPanelService = adminPanelService;
    this.adminPanelSessionManager = adminPanelSessionManager;
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
        case BUTTON_BACK -> showMainPanel(event);
        case BUTTON_OPEN_BALANCE_MODAL -> openBalanceModal(event);
        case BUTTON_OPEN_TOKEN_MODAL -> openTokenModal(event);
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

    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);

    try {
      switch (selectId) {
        case SELECT_GAME -> handleGameSelect(event, guildId);
        case SELECT_BALANCE_MODE -> handleBalanceModeSelect(event, sessionKey, guildId);
        case SELECT_TOKEN_MODE -> handleTokenModeSelect(event, sessionKey, guildId);
        case SELECT_GAME_SETTING -> handleGameSettingSelect(event, guildId);
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

    // Initialize or reset session state
    sessionStates.put(sessionKey, new SessionState(ManagementType.BALANCE));

    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();
    MessageEmbed embed = buildBalanceManagementEmbed(null, null, null, currencyIcon);

    EntitySelectMenu userSelect =
        EntitySelectMenu.create(SELECT_BALANCE_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("選擇要調整的成員")
            .setRequiredRange(1, 1)
            .build();

    StringSelectMenu modeSelect =
        StringSelectMenu.create(SELECT_BALANCE_MODE)
            .setPlaceholder("選擇調整模式")
            .addOption("增加餘額", "add", "在現有餘額基礎上增加指定金額")
            .addOption("扣除餘額", "deduct", "從現有餘額扣除指定金額")
            .addOption("設定餘額", "adjust", "將餘額直接設定為指定金額")
            .build();

    event
        .editMessageEmbeds(embed)
        .setComponents(
            ActionRow.of(userSelect),
            ActionRow.of(modeSelect),
            ActionRow.of(
                Button.primary(BUTTON_OPEN_BALANCE_MODAL, currencyIcon + " 輸入金額").asDisabled(),
                Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
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

    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();
    MessageEmbed embed =
        buildBalanceManagementEmbed(
            selectedUser.getAsMention(), currentBalance, state.selectedMode, currencyIcon);

    boolean canOpenModal = state.selectedUserId != null && state.selectedMode != null;

    EntitySelectMenu userSelect =
        EntitySelectMenu.create(SELECT_BALANCE_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("選擇要調整的成員")
            .setRequiredRange(1, 1)
            .build();

    StringSelectMenu modeSelect =
        StringSelectMenu.create(SELECT_BALANCE_MODE)
            .setPlaceholder("選擇調整模式")
            .addOption("增加餘額", "add", "在現有餘額基礎上增加指定金額")
            .addOption("扣除餘額", "deduct", "從現有餘額扣除指定金額")
            .addOption("設定餘額", "adjust", "將餘額直接設定為指定金額")
            .setDefaultValues(state.selectedMode != null ? List.of(state.selectedMode) : List.of())
            .build();

    event
        .editMessageEmbeds(embed)
        .setComponents(
            ActionRow.of(userSelect),
            ActionRow.of(modeSelect),
            ActionRow.of(
                canOpenModal
                    ? Button.primary(BUTTON_OPEN_BALANCE_MODAL, currencyIcon + " 輸入金額")
                    : Button.primary(BUTTON_OPEN_BALANCE_MODAL, currencyIcon + " 輸入金額")
                        .asDisabled(),
                Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
        .queue();
  }

  private void handleBalanceModeSelect(
      StringSelectInteractionEvent event, String sessionKey, long guildId) {
    String mode = event.getValues().get(0);

    SessionState state =
        sessionStates.computeIfAbsent(sessionKey, k -> new SessionState(ManagementType.BALANCE));
    state.selectedMode = mode;

    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();
    MessageEmbed embed =
        buildBalanceManagementEmbed(
            state.selectedUserMention, state.currentValue, mode, currencyIcon);

    boolean canOpenModal = state.selectedUserId != null && state.selectedMode != null;

    EntitySelectMenu userSelect =
        EntitySelectMenu.create(SELECT_BALANCE_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("選擇要調整的成員")
            .setRequiredRange(1, 1)
            .build();

    StringSelectMenu modeSelect =
        StringSelectMenu.create(SELECT_BALANCE_MODE)
            .setPlaceholder("選擇調整模式")
            .addOption("增加餘額", "add", "在現有餘額基礎上增加指定金額")
            .addOption("扣除餘額", "deduct", "從現有餘額扣除指定金額")
            .addOption("設定餘額", "adjust", "將餘額直接設定為指定金額")
            .setDefaultValues(List.of(mode))
            .build();

    event
        .editMessageEmbeds(embed)
        .setComponents(
            ActionRow.of(userSelect),
            ActionRow.of(modeSelect),
            ActionRow.of(
                canOpenModal
                    ? Button.primary(BUTTON_OPEN_BALANCE_MODAL, currencyIcon + " 輸入金額")
                    : Button.primary(BUTTON_OPEN_BALANCE_MODAL, currencyIcon + " 輸入金額")
                        .asDisabled(),
                Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
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

  private MessageEmbed buildBalanceManagementEmbed(
      String userMention, Long currentBalance, String mode, String currencyIcon) {
    EmbedBuilder builder =
        new EmbedBuilder()
            .setTitle(currencyIcon + " 使用者餘額管理")
            .setColor(EMBED_COLOR)
            .setDescription("選擇要調整餘額的成員和調整模式");

    if (userMention != null) {
      builder.addField("選取成員", userMention, true);
      if (currentBalance != null) {
        builder.addField("目前餘額", String.format("%s %,d", currencyIcon, currentBalance), true);
      } else {
        builder.addField("目前餘額", "（無法取得）", true);
      }
    }

    if (mode != null) {
      builder.addField("調整模式", getModeLabel(mode), false);
    }

    builder.setFooter("選擇成員和模式後點擊「輸入金額」按鈕");

    return builder.build();
  }

  // ===== Token Management =====

  private void showTokenManagement(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);

    // Initialize or reset session state
    sessionStates.put(sessionKey, new SessionState(ManagementType.TOKEN));

    MessageEmbed embed = buildTokenManagementEmbed(null, null, null);

    EntitySelectMenu userSelect =
        EntitySelectMenu.create(SELECT_TOKEN_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("選擇要調整的成員")
            .setRequiredRange(1, 1)
            .build();

    StringSelectMenu modeSelect =
        StringSelectMenu.create(SELECT_TOKEN_MODE)
            .setPlaceholder("選擇調整模式")
            .addOption("增加代幣", "add", "在現有代幣基礎上增加指定數量")
            .addOption("扣除代幣", "deduct", "從現有代幣扣除指定數量")
            .addOption("設定代幣", "adjust", "將代幣直接設定為指定數量")
            .build();

    event
        .editMessageEmbeds(embed)
        .setComponents(
            ActionRow.of(userSelect),
            ActionRow.of(modeSelect),
            ActionRow.of(
                Button.primary(BUTTON_OPEN_TOKEN_MODAL, "🎮 輸入數量").asDisabled(),
                Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
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

    EntitySelectMenu userSelect =
        EntitySelectMenu.create(SELECT_TOKEN_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("選擇要調整的成員")
            .setRequiredRange(1, 1)
            .build();

    StringSelectMenu modeSelect =
        StringSelectMenu.create(SELECT_TOKEN_MODE)
            .setPlaceholder("選擇調整模式")
            .addOption("增加代幣", "add", "在現有代幣基礎上增加指定數量")
            .addOption("扣除代幣", "deduct", "從現有代幣扣除指定數量")
            .addOption("設定代幣", "adjust", "將代幣直接設定為指定數量")
            .setDefaultValues(state.selectedMode != null ? List.of(state.selectedMode) : List.of())
            .build();

    event
        .editMessageEmbeds(embed)
        .setComponents(
            ActionRow.of(userSelect),
            ActionRow.of(modeSelect),
            ActionRow.of(
                canOpenModal
                    ? Button.primary(BUTTON_OPEN_TOKEN_MODAL, "🎮 輸入數量")
                    : Button.primary(BUTTON_OPEN_TOKEN_MODAL, "🎮 輸入數量").asDisabled(),
                Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
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

    EntitySelectMenu userSelect =
        EntitySelectMenu.create(SELECT_TOKEN_USER, EntitySelectMenu.SelectTarget.USER)
            .setPlaceholder("選擇要調整的成員")
            .setRequiredRange(1, 1)
            .build();

    StringSelectMenu modeSelect =
        StringSelectMenu.create(SELECT_TOKEN_MODE)
            .setPlaceholder("選擇調整模式")
            .addOption("增加代幣", "add", "在現有代幣基礎上增加指定數量")
            .addOption("扣除代幣", "deduct", "從現有代幣扣除指定數量")
            .addOption("設定代幣", "adjust", "將代幣直接設定為指定數量")
            .setDefaultValues(List.of(mode))
            .build();

    event
        .editMessageEmbeds(embed)
        .setComponents(
            ActionRow.of(userSelect),
            ActionRow.of(modeSelect),
            ActionRow.of(
                canOpenModal
                    ? Button.primary(BUTTON_OPEN_TOKEN_MODAL, "🎮 輸入數量")
                    : Button.primary(BUTTON_OPEN_TOKEN_MODAL, "🎮 輸入數量").asDisabled(),
                Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
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

  private MessageEmbed buildTokenManagementEmbed(
      String userMention, Long currentTokens, String mode) {
    EmbedBuilder builder =
        new EmbedBuilder()
            .setTitle("🎮 遊戲代幣管理")
            .setColor(EMBED_COLOR)
            .setDescription("選擇要調整代幣的成員和調整模式");

    if (userMention != null) {
      builder.addField("選取成員", userMention, true);
      if (currentTokens != null) {
        builder.addField("目前代幣", String.format("🎮 %,d", currentTokens), true);
      } else {
        builder.addField("目前代幣", "（無法取得）", true);
      }
    }

    if (mode != null) {
      builder.addField("調整模式", getTokenModeLabel(mode), false);
    }

    builder.setFooter("選擇成員和模式後點擊「輸入數量」按鈕");

    return builder.build();
  }

  // ===== Game Management =====

  private void showGameManagement(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    DiceGame1Config game1Config = adminPanelService.getDiceGame1Config(guildId);
    DiceGame2Config game2Config = adminPanelService.getDiceGame2Config(guildId);
    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();

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
            ActionRow.of(gameSelect), ActionRow.of(Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
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
    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();

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
            ActionRow.of(settingSelect),
            ActionRow.of(
                Button.secondary(BUTTON_GAMES, "🎲 返回遊戲列表"),
                Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
        .queue();
  }

  private void showDiceGame2Settings(StringSelectInteractionEvent event, long guildId) {
    DiceGame2Config config = adminPanelService.getDiceGame2Config(guildId);
    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();

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
            ActionRow.of(settingSelect),
            ActionRow.of(
                Button.secondary(BUTTON_GAMES, "🎲 返回遊戲列表"),
                Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
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
    return new EmbedBuilder()
        .setTitle("🎲 遊戲設定管理")
        .setColor(EMBED_COLOR)
        .setDescription("選擇要調整設定的遊戲")
        .addField(
            "摘星手",
            String.format(
                "代幣範圍：🎮 %,d ~ %,d\n單骰倍率：%s %,d",
                game1Config.minTokensPerPlay(),
                game1Config.maxTokensPerPlay(),
                currencyIcon,
                game1Config.rewardPerDiceValue()),
            false)
        .addField(
            "神龍擺尾",
            String.format(
                "代幣範圍：🎮 %,d ~ %,d\n順子倍率：%s %,d\n基礎倍率：%s %,d",
                game2Config.minTokensPerPlay(),
                game2Config.maxTokensPerPlay(),
                currencyIcon,
                game2Config.straightMultiplier(),
                currencyIcon,
                game2Config.baseMultiplier()),
            false)
        .setFooter("選擇遊戲以查看詳細設定")
        .build();
  }

  private MessageEmbed buildDiceGame1SettingsEmbed(DiceGame1Config config, String currencyIcon) {
    return new EmbedBuilder()
        .setTitle("🎲 摘星手設定")
        .setColor(EMBED_COLOR)
        .addField(
            "代幣範圍",
            String.format(
                "最小：🎮 %,d\n最大：🎮 %,d", config.minTokensPerPlay(), config.maxTokensPerPlay()),
            true)
        .addField(
            "獎勵設定",
            String.format(
                "單骰獎勵倍率：%s %,d\n（1 點 = %,d、6 點 = %,d）",
                currencyIcon,
                config.rewardPerDiceValue(),
                config.rewardPerDiceValue(),
                config.rewardPerDiceValue() * 6),
            true)
        .setFooter("選擇要調整的設定類別")
        .build();
  }

  private MessageEmbed buildDiceGame2SettingsEmbed(DiceGame2Config config, String currencyIcon) {
    return new EmbedBuilder()
        .setTitle("🎲 神龍擺尾設定")
        .setColor(EMBED_COLOR)
        .addField(
            "代幣範圍",
            String.format(
                "最小：🎮 %,d\n最大：🎮 %,d", config.minTokensPerPlay(), config.maxTokensPerPlay()),
            true)
        .addField(
            "獎勵倍率",
            String.format(
                "順子倍率：%s %,d\n基礎倍率：%s %,d",
                currencyIcon, config.straightMultiplier(), currencyIcon, config.baseMultiplier()),
            true)
        .addField(
            "豹子獎勵",
            String.format(
                "小豹子（<10）：%s %,d\n大豹子（≥10）：%s %,d",
                currencyIcon, config.tripleLowBonus(), currencyIcon, config.tripleHighBonus()),
            false)
        .setFooter("選擇要調整的設定類別")
        .build();
  }

  // ===== Main Panel =====

  private void showMainPanel(ButtonInteractionEvent event) {
    // Clear session state
    long guildId = event.getGuild().getIdLong();
    String sessionKey = getSessionKey(event.getUser().getIdLong(), guildId);
    sessionStates.remove(sessionKey);

    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();
    MessageEmbed embed = buildMainPanelEmbed(currencyIcon);
    List<ActionRow> components = buildMainPanelComponents(currencyIcon);

    event.editMessageEmbeds(embed).setComponents(components).queue();
  }

  MessageEmbed buildMainPanelEmbed(String currencyIcon) {
    return new EmbedBuilder()
        .setTitle("🔧 管理面板")
        .setDescription("選擇要管理的項目：")
        .setColor(EMBED_COLOR)
        .addField(currencyIcon + " 使用者餘額管理", "調整成員的貨幣餘額", false)
        .addField("🎮 遊戲代幣管理", "調整成員的遊戲代幣餘額", false)
        .addField("🎲 遊戲設定管理", "調整遊戲的代幣消耗設定", false)
        .addField("📦 商品與兌換碼管理", "建立商品、生成兌換碼、查詢兌換狀態", false)
        .setFooter("點擊下方按鈕進入對應功能")
        .build();
  }

  List<ActionRow> buildMainPanelComponents(String currencyIcon) {
    return List.of(
        ActionRow.of(
            Button.primary(BUTTON_BALANCE, currencyIcon + " 使用者餘額管理"),
            Button.primary(BUTTON_TOKENS, "🎮 遊戲代幣管理")),
        ActionRow.of(
            Button.primary(BUTTON_GAMES, "🎲 遊戲設定管理"),
            Button.primary(AdminProductPanelHandler.BUTTON_PRODUCTS, "📦 商品與兌換碼管理")));
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

    Result<AdminPanelService.BalanceAdjustmentResult, DomainError> result =
        adminPanelService.adjustBalance(guildId, userId, mode, amount);

    if (result.isErr()) {
      event.reply("調整失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    AdminPanelService.BalanceAdjustmentResult adjustResult = result.getValue();
    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();

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

    Result<AdminPanelService.TokenAdjustmentResult, DomainError> result =
        adminPanelService.adjustTokens(guildId, userId, mode, amount);

    if (result.isErr()) {
      event.reply("調整失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    AdminPanelService.TokenAdjustmentResult adjustResult = result.getValue();

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
    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();

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
    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();

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
    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();

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
    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();

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
    String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();

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

  // ===== AI 頻道設定管理 =====

  /** 顯示 AI 頻道設定頁面。 */
  private void showAIChannelConfig(ButtonInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    var channelsResult = adminPanelService.getAllowedChannels(guildId);
    MessageEmbed embed;

    if (channelsResult.isOk()) {
      var channels = channelsResult.getValue();
      embed = buildAIChannelConfigEmbed(guildId, channels);
    } else {
      embed = buildAIChannelConfigEmbed(guildId, Set.of());
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

    event
        .editMessageEmbeds(embed)
        .setComponents(
            ActionRow.of(addChannelSelect),
            ActionRow.of(removeChannelSelect),
            ActionRow.of(Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
        .queue();
  }

  /** 建立 AI 頻道設定的 Embed。 */
  private MessageEmbed buildAIChannelConfigEmbed(
      long guildId, Set<ltdjms.discord.aichat.domain.AllowedChannel> channels) {
    var embedBuilder = new EmbedBuilder().setTitle("🤖 AI 頻道設定").setColor(EMBED_COLOR);

    if (channels.isEmpty()) {
      embedBuilder
          .setDescription("**未設定任何頻道限制**")
          .addField("狀態", "AI 可在所有頻道使用", false)
          .addField("說明", "使用下方的選單新增允許的頻道以啟用限制模式", false);
    } else {
      StringBuilder channelList = new StringBuilder();
      for (var channel : channels) {
        channelList.append(
            String.format("<#%d> - %s\n", channel.channelId(), channel.channelName()));
      }

      embedBuilder
          .setDescription("**已啟用頻道限制**")
          .addField("允許的頻道", channelList.toString(), false)
          .addField("總計", channels.size() + " 個頻道", false);
    }

    return embedBuilder.build();
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
      if (updatedChannels.isOk()) {
        MessageEmbed embed = buildAIChannelConfigEmbed(guildId, updatedChannels.getValue());

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
                ActionRow.of(addChannelSelect),
                ActionRow.of(removeChannelSelect),
                ActionRow.of(Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
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
      if (updatedChannels.isOk()) {
        MessageEmbed embed = buildAIChannelConfigEmbed(guildId, updatedChannels.getValue());

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
                ActionRow.of(addChannelSelect),
                ActionRow.of(removeChannelSelect),
                ActionRow.of(Button.secondary(BUTTON_BACK, "⬅️ 返回主選單")))
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
}
