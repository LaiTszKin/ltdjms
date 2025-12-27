package ltdjms.discord.panel.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.panel.services.PanelSessionManager;
import ltdjms.discord.panel.services.UserPanelEmbedBuilder;
import ltdjms.discord.panel.services.UserPanelService;
import ltdjms.discord.panel.services.UserPanelView;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Handler for the /user-panel slash command. Shows the user's personal panel with currency balance,
 * game tokens, and provides interactive buttons for viewing token transaction history.
 */
public class UserPanelCommandHandler implements SlashCommandListener.CommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(UserPanelCommandHandler.class);

  // Button IDs for interaction handling
  public static final String BUTTON_TOKEN_HISTORY = "user_panel_token_history";
  public static final String BUTTON_CURRENCY_HISTORY = "user_panel_currency_history";
  public static final String BUTTON_PRODUCT_REDEMPTION_HISTORY =
      "user_panel_product_redemption_history";

  private final UserPanelService userPanelService;
  private final PanelSessionManager panelSessionManager;

  public UserPanelCommandHandler(
      UserPanelService userPanelService, PanelSessionManager panelSessionManager) {
    this.userPanelService = userPanelService;
    this.panelSessionManager = panelSessionManager;
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
    MessageEmbed embed =
        UserPanelEmbedBuilder.buildPanelEmbed(panelView, event.getUser().getAsMention());

    event
        .replyEmbeds(embed)
        .addComponents(
            UserPanelEmbedBuilder.buildPanelComponents(
                BUTTON_CURRENCY_HISTORY,
                BUTTON_TOKEN_HISTORY,
                BUTTON_PRODUCT_REDEMPTION_HISTORY,
                UserPanelButtonHandler.BUTTON_REDEEM,
                panelView.getCurrencyHistoryButtonLabel()))
        .setEphemeral(true)
        .queue(
            hook -> {
              // Register session for real-time updates upon successful reply
              panelSessionManager.registerSession(
                  guildId, userId, hook, event.getUser().getAsMention());
            });

    BotErrorHandler.logSuccess(
        event,
        String.format(
            "currency=%d, tokens=%d", panelView.currencyBalance(), panelView.gameTokens()));
  }
}
