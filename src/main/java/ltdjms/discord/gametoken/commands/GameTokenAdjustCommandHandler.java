package ltdjms.discord.gametoken.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenService.TokenAdjustmentResult;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * Handler for the /game-token-adjust slash command. Allows administrators to add or subtract game
 * tokens from a member's account.
 *
 * <p>All predictable errors are handled via Result&lt;T, DomainError&gt; pattern. This handler does
 * not catch domain exceptions directly; instead it relies on the Result-based service API for all
 * expected error conditions.
 */
public class GameTokenAdjustCommandHandler implements SlashCommandListener.CommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GameTokenAdjustCommandHandler.class);

  private final GameTokenService tokenService;

  public GameTokenAdjustCommandHandler(GameTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    LOG.debug("Processing /game-token-adjust for guildId={}", guildId);

    // Get required options
    OptionMapping memberOption = event.getOption("member");
    OptionMapping amountOption = event.getOption("amount");

    if (memberOption == null || amountOption == null) {
      BotErrorHandler.handleInvalidInput(event, "Both member and amount are required.");
      return;
    }

    User targetUser = memberOption.getAsUser();
    long targetUserId = targetUser.getIdLong();
    long amount = amountOption.getAsLong();

    if (amount == 0) {
      BotErrorHandler.handleInvalidInput(event, "Amount cannot be zero.");
      return;
    }

    Result<TokenAdjustmentResult, DomainError> result =
        tokenService.tryAdjustTokens(guildId, targetUserId, amount);

    if (result.isErr()) {
      BotErrorHandler.handleDomainError(event, result.getError());
      return;
    }

    TokenAdjustmentResult adjustmentResult = result.getValue();
    String message = adjustmentResult.formatMessage(targetUser.getAsMention());
    event.reply(message).queue();

    BotErrorHandler.logSuccess(
        event,
        "user="
            + targetUserId
            + ", amount="
            + amount
            + ", newTokens="
            + adjustmentResult.newTokens());
  }
}
