package ltdjms.discord.panel.commands;

import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

/**
 * Handler for the /admin-panel slash command.
 * Shows the admin panel with buttons to manage balances, tokens, and game settings.
 */
public class AdminPanelCommandHandler implements SlashCommandListener.CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AdminPanelCommandHandler.class);

    private static final Color EMBED_COLOR = new Color(0xED4245); // Discord red for admin

    // Button IDs
    public static final String BUTTON_BALANCE_MANAGEMENT = "admin_panel_balance";
    public static final String BUTTON_TOKEN_MANAGEMENT = "admin_panel_tokens";
    public static final String BUTTON_GAME_MANAGEMENT = "admin_panel_games";

    private final AdminPanelService adminPanelService;
    private final AdminPanelSessionManager adminPanelSessionManager;

    public AdminPanelCommandHandler(AdminPanelService adminPanelService,
                                    AdminPanelSessionManager adminPanelSessionManager) {
        this.adminPanelService = adminPanelService;
        this.adminPanelSessionManager = adminPanelSessionManager;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();

        LOG.debug("Processing /admin-panel for guildId={}", guildId);

        String currencyIcon = adminPanelService.getCurrencyConfig(guildId).currencyIcon();
        MessageEmbed embed = buildMainPanelEmbed(currencyIcon);

        event.replyEmbeds(embed)
                .addActionRow(
                        Button.primary(BUTTON_BALANCE_MANAGEMENT, currencyIcon + " 使用者餘額管理"),
                        Button.primary(BUTTON_TOKEN_MANAGEMENT, "🎮 遊戲代幣管理"),
                        Button.primary(BUTTON_GAME_MANAGEMENT, "🎲 遊戲設定管理")
                )
                .setEphemeral(true)
                .queue(hook -> {
                    long adminId = event.getUser().getIdLong();
                    // 註冊管理面板 session，之後可以透過 hook 安全地更新這則 ephemeral 面板訊息
                    adminPanelSessionManager.registerSession(guildId, adminId, hook);
                    LOG.info("Admin panel opened for guildId={} by userId={}", guildId, adminId);
                });
    }

    private MessageEmbed buildMainPanelEmbed(String currencyIcon) {
        return new EmbedBuilder()
                .setTitle("🔧 管理面板")
                .setDescription("選擇要管理的項目：")
                .setColor(EMBED_COLOR)
                .addField(currencyIcon + " 使用者餘額管理", "調整成員的貨幣餘額", false)
                .addField("🎮 遊戲代幣管理", "調整成員的遊戲代幣餘額", false)
                .addField("🎲 遊戲設定管理", "調整遊戲的代幣消耗設定", false)
                .setFooter("點擊下方按鈕進入對應功能")
                .build();
    }
}
