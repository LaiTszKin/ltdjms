package ltdjms.discord.panel.commands;

import java.awt.Color;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/**
 * Handler for the /admin-panel slash command. Shows the admin panel with buttons to manage
 * balances, tokens, and game settings.
 */
public class AdminPanelCommandHandler implements SlashCommandListener.CommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(AdminPanelCommandHandler.class);

  private static final Color EMBED_COLOR = new Color(0xED4245); // Discord red for admin

  // Button IDs
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

  private final AdminPanelService adminPanelService;
  private final AdminPanelSessionManager adminPanelSessionManager;

  public AdminPanelCommandHandler(
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
              // 註冊管理面板 session，之後可以透過 hook 安全地更新這則 ephemeral 面板訊息
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
    return new EmbedBuilder()
        .setTitle("🔧 管理面板")
        .setDescription("選擇要管理的項目：")
        .setColor(EMBED_COLOR)
        .addField(currencyIcon + " 使用者餘額管理", "調整成員的貨幣餘額", false)
        .addField("🎮 遊戲代幣管理", "調整成員的遊戲代幣餘額", false)
        .addField("🎲 遊戲設定管理", "調整遊戲的代幣消耗設定", false)
        .addField("📦 商品與兌換碼管理", "建立商品、生成兌換碼、查詢兌換狀態", false)
        .addField("🤖 AI 頻道設定", "設定允許使用 AI 功能的頻道", false)
        .addField("🤖 AI Agent 配置", "管理哪些頻道啟用 AI Agent 模式", false)
        .addField("🧰 派單售後設定", "設定派單系統的售後人員名單", false)
        .setFooter("點擊下方按鈕進入對應功能")
        .build();
  }

  /** 建立主選單的按鈕列，保持測試可驗證性。 */
  static List<Button> buildMainActionButtons(String currencyIcon) {
    return List.of(
        Button.primary(BUTTON_BALANCE_MANAGEMENT, currencyIcon + " 使用者餘額管理"),
        Button.primary(BUTTON_TOKEN_MANAGEMENT, "🎮 遊戲代幣管理"),
        Button.primary(BUTTON_GAME_MANAGEMENT, "🎲 遊戲設定管理"),
        Button.primary(BUTTON_PRODUCT_MANAGEMENT, "📦 商品與兌換碼管理"),
        Button.primary(BUTTON_AI_CHANNEL_CONFIG, "🤖 AI 頻道設定"),
        Button.primary(BUTTON_AI_AGENT_CONFIG, "🤖 AI Agent 配置"),
        Button.primary(BUTTON_DISPATCH_AFTER_SALES_CONFIG, "🧰 派單售後設定"));
  }

  /** 建立主選單的 ActionRow。 */
  static List<ActionRow> buildMainActionRows(String currencyIcon) {
    List<Button> buttons = buildMainActionButtons(currencyIcon);
    return List.of(
        ActionRow.of(buttons.get(0), buttons.get(1)),
        ActionRow.of(buttons.get(2), buttons.get(3)),
        ActionRow.of(buttons.get(4), buttons.get(5)),
        ActionRow.of(buttons.get(6)));
  }
}
