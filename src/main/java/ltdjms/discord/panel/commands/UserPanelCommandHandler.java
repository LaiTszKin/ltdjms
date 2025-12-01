package ltdjms.discord.panel.commands;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.panel.services.UserPanelService;
import ltdjms.discord.panel.services.UserPanelView;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

/**
 * Handler for the /user-panel slash command.
 * Shows the user's personal panel with currency balance, game tokens,
 * and provides interactive buttons for viewing token transaction history.
 */
public class UserPanelCommandHandler implements SlashCommandListener.CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UserPanelCommandHandler.class);

    // Button IDs for interaction handling
    public static final String BUTTON_TOKEN_HISTORY = "user_panel_token_history";
    public static final String BUTTON_CURRENCY_HISTORY = "user_panel_currency_history";

    private static final Color EMBED_COLOR = new Color(0x5865F2); // Discord blurple

    private final UserPanelService userPanelService;

    public UserPanelCommandHandler(UserPanelService userPanelService) {
        this.userPanelService = userPanelService;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();

        LOG.debug("Processing /user-panel for guildId={}, userId={}", guildId, userId);

        Result<UserPanelView, DomainError> result = userPanelService.getUserPanelView(guildId, userId);

        if (result.isErr()) {
            BotErrorHandler.handleDomainError(event, result.getError());
            return;
        }

        UserPanelView panelView = result.getValue();
        MessageEmbed embed = buildPanelEmbed(panelView, event.getUser().getAsMention());

        event.replyEmbeds(embed)
                .addActionRow(
                        Button.secondary(BUTTON_CURRENCY_HISTORY, "💰 查看貨幣流水"),
                        Button.secondary(BUTTON_TOKEN_HISTORY, "📜 查看遊戲代幣流水")
                )
                .setEphemeral(true)
                .queue();

        BotErrorHandler.logSuccess(event, String.format("currency=%d, tokens=%d",
                panelView.currencyBalance(), panelView.gameTokens()));
    }

    private MessageEmbed buildPanelEmbed(UserPanelView view, String userMention) {
        return new EmbedBuilder()
                .setTitle(view.getEmbedTitle())
                .setDescription(userMention + " 的帳戶資訊")
                .setColor(EMBED_COLOR)
                .addField(view.getCurrencyFieldName(), view.formatCurrencyField(), true)
                .addField(view.getGameTokensFieldName(), view.formatGameTokensField(), true)
                .setFooter("點擊下方按鈕查看流水紀錄")
                .build();
    }
}
