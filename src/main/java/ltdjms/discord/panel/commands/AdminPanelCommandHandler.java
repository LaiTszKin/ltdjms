package ltdjms.discord.panel.commands;

import java.awt.Color;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.EmbedView;
import ltdjms.discord.panel.components.PanelComponentRenderer;
import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

/**
 * Handler for the /admin-panel slash command. Shows the admin panel with buttons to manage
 * balances, tokens, and game settings.
 */
public class AdminPanelCommandHandler implements SlashCommandListener.CommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(AdminPanelCommandHandler.class);

  private static final Color EMBED_COLOR = new Color(0xED4245); // Discord red for admin
  private static final String PANEL_TITLE = "🔧 管理面板";
  private static final String PANEL_DESCRIPTION = "選擇要管理的項目：";
  private static final String PANEL_FOOTER = "點擊下方按鈕進入對應功能";

  public static final String BUTTON_BALANCE_MANAGEMENT = AdminPanelButtonHandler.BUTTON_BALANCE;
  public static final String BUTTON_TOKEN_MANAGEMENT = AdminPanelButtonHandler.BUTTON_TOKENS;
  public static final String BUTTON_GAME_MANAGEMENT = AdminPanelButtonHandler.BUTTON_GAMES;
  public static final String BUTTON_PRODUCT_MANAGEMENT = AdminProductPanelHandler.BUTTON_PRODUCTS;
  public static final String BUTTON_AI_CHANNEL_CONFIG =
      AdminPanelButtonHandler.BUTTON_AI_CHANNEL_CONFIG;
  public static final String BUTTON_AI_AGENT_CONFIG =
      AdminPanelButtonHandler.BUTTON_AI_AGENT_CONFIG;
  public static final String BUTTON_DISPATCH_AFTER_SALES_CONFIG =
      AdminPanelButtonHandler.BUTTON_DISPATCH_AFTER_SALES_CONFIG;
  public static final String BUTTON_ESCORT_PRICING_CONFIG =
      AdminPanelButtonHandler.BUTTON_ESCORT_PRICING_CONFIG;
  public static final String BUTTON_ESCORT_CATALOG = AdminPanelButtonHandler.BUTTON_ESCORT_CATALOG;

  private final AdminPanelService adminPanelService;
  private final AdminPanelSessionManager adminPanelSessionManager;

  public AdminPanelCommandHandler(
      AdminPanelService adminPanelService, AdminPanelSessionManager adminPanelSessionManager) {
    this.adminPanelService = adminPanelService;
    this.adminPanelSessionManager = adminPanelSessionManager;
  }

  private String getCurrencyIcon(long guildId) {
    Result<GuildCurrencyConfig, DomainError> result = adminPanelService.getCurrencyConfig(guildId);
    return result.isOk() ? result.getValue().currencyIcon() : "💰";
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    Guild guild = event.getGuild();
    if (guild == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }
    long guildId = guild.getIdLong();

    LOG.debug("Processing /admin-panel for guildId={}", guildId);

    if (!isAdmin(event.getMember(), guild)) {
      event.reply("你沒有權限使用管理面板").setEphemeral(true).queue();
      return;
    }

    String currencyIcon = getCurrencyIcon(guildId);
    MessageEmbed embed = buildMainPanelEmbed(currencyIcon);
    List<ActionRow> rows = buildMainActionRows(currencyIcon);

    event
        .replyEmbeds(embed)
        .setComponents(rows)
        .setEphemeral(true)
        .queue(
            hook -> {
              long adminId = event.getUser().getIdLong();
              adminPanelSessionManager.registerSession(guildId, adminId, hook);
              LOG.info("Admin panel opened for guildId={} by userId={}", guildId, adminId);
            });
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

  static MessageEmbed buildMainPanelEmbed(String currencyIcon) {
    return PanelComponentRenderer.buildEmbed(
        new EmbedView(
            PANEL_TITLE,
            PANEL_DESCRIPTION,
            EMBED_COLOR,
            List.of(
                new EmbedView.FieldView(currencyIcon + " 使用者餘額管理", "調整成員的貨幣餘額", false),
                new EmbedView.FieldView("🎮 遊戲代幣管理", "調整成員的遊戲代幣餘額", false),
                new EmbedView.FieldView("🎲 遊戲設定管理", "調整遊戲的代幣消耗設定", false),
                new EmbedView.FieldView("📦 商品與兌換碼管理", "建立商品、生成兌換碼、查詢兌換狀態", false),
                new EmbedView.FieldView("🤖 AI 頻道設定", "設定允許使用 AI 功能的頻道", false),
                new EmbedView.FieldView("🤖 AI Agent 配置", "管理哪些頻道啟用 AI Agent 模式", false),
                new EmbedView.FieldView("🧰 派單售後設定", "設定派單系統的售後人員名單", false),
                new EmbedView.FieldView("🛡️ 護航定價設定", "調整各護航訂單類型的實際收費", false),
                new EmbedView.FieldView("📋 護航價目表管理", "新增、編輯、刪除護航項目", false)),
            PANEL_FOOTER));
  }

  static List<Button> buildMainActionButtons(String currencyIcon) {
    return PanelComponentRenderer.buildButtons(buildMainActionButtonViews(currencyIcon));
  }

  static List<ActionRow> buildMainActionRows(String currencyIcon) {
    List<ButtonView> buttons = buildMainActionButtonViews(currencyIcon);
    return PanelComponentRenderer.buildActionRows(
        List.of(
            buttons.subList(0, 2),
            buttons.subList(2, 4),
            buttons.subList(4, 6),
            buttons.subList(6, 8),
            buttons.subList(8, 9)));
  }

  private static List<ButtonView> buildMainActionButtonViews(String currencyIcon) {
    return List.of(
        new ButtonView(
            BUTTON_BALANCE_MANAGEMENT, currencyIcon + " 使用者餘額管理", ButtonStyle.PRIMARY, false),
        new ButtonView(BUTTON_TOKEN_MANAGEMENT, "🎮 遊戲代幣管理", ButtonStyle.PRIMARY, false),
        new ButtonView(BUTTON_GAME_MANAGEMENT, "🎲 遊戲設定管理", ButtonStyle.PRIMARY, false),
        new ButtonView(BUTTON_PRODUCT_MANAGEMENT, "📦 商品與兌換碼管理", ButtonStyle.PRIMARY, false),
        new ButtonView(BUTTON_AI_CHANNEL_CONFIG, "🤖 AI 頻道設定", ButtonStyle.PRIMARY, false),
        new ButtonView(BUTTON_AI_AGENT_CONFIG, "🤖 AI Agent 配置", ButtonStyle.PRIMARY, false),
        new ButtonView(BUTTON_DISPATCH_AFTER_SALES_CONFIG, "🧰 派單售後設定", ButtonStyle.PRIMARY, false),
        new ButtonView(BUTTON_ESCORT_PRICING_CONFIG, "🛡️ 護航定價設定", ButtonStyle.PRIMARY, false),
        new ButtonView(BUTTON_ESCORT_CATALOG, "📋 護航價目表管理", ButtonStyle.PRIMARY, false));
  }
}
