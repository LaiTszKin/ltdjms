package ltdjms.discord.currency.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceAdjustmentService.BalanceAdjustmentResult;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * Handler for the /adjust-balance slash command. Allows administrators to add, subtract, or set a
 * member's balance to a specific value. Supports three modes: add, deduct, and adjust.
 *
 * <p>All predictable errors are handled via Result&lt;T, DomainError&gt; pattern. This handler does
 * not catch domain exceptions directly; instead it relies on the Result-based service API for all
 * expected error conditions.
 */
public class BalanceAdjustmentCommandHandler implements SlashCommandListener.CommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(BalanceAdjustmentCommandHandler.class);

  private final BalanceAdjustmentService adjustmentService;

  public BalanceAdjustmentCommandHandler(BalanceAdjustmentService adjustmentService) {
    this.adjustmentService = adjustmentService;
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    LOG.debug("Processing /adjust-balance for guildId={}", guildId);

    // Get required options
    OptionMapping modeOption = event.getOption("mode");
    OptionMapping memberOption = event.getOption("member");
    OptionMapping amountOption = event.getOption("amount");

    if (modeOption == null || memberOption == null || amountOption == null) {
      BotErrorHandler.handleInvalidInput(event, "Mode, member, and amount are all required.");
      return;
    }

    String mode = modeOption.getAsString();
    User targetUser = memberOption.getAsUser();
    long targetUserId = targetUser.getIdLong();
    long amount = amountOption.getAsLong();

    Result<BalanceAdjustmentResult, DomainError> result =
        switch (mode) {
          case "add" -> handleAddMode(guildId, targetUserId, amount);
          case "deduct" -> handleDeductMode(guildId, targetUserId, amount);
          case "adjust" -> handleAdjustMode(guildId, targetUserId, amount);
          default -> Result.err(DomainError.invalidInput("Invalid mode: " + mode));
        };

    if (result.isErr()) {
      BotErrorHandler.handleDomainError(event, result.getError());
      return;
    }

    BalanceAdjustmentResult adjustmentResult = result.getValue();
    String message = formatResultMessage(mode, adjustmentResult, targetUser.getAsMention());
    event.reply(message).queue();

    BotErrorHandler.logSuccess(
        event,
        "mode="
            + mode
            + ", user="
            + targetUserId
            + ", amount="
            + amount
            + ", newBalance="
            + adjustmentResult.newBalance());
  }

  private Result<BalanceAdjustmentResult, DomainError> handleAddMode(
      long guildId, long userId, long amount) {
    if (amount <= 0) {
      return Result.err(DomainError.invalidInput("Amount must be positive for add mode."));
    }
    return adjustmentService.tryAdjustBalance(guildId, userId, amount);
  }

  private Result<BalanceAdjustmentResult, DomainError> handleDeductMode(
      long guildId, long userId, long amount) {
    if (amount <= 0) {
      return Result.err(DomainError.invalidInput("Amount must be positive for deduct mode."));
    }
    return adjustmentService.tryAdjustBalance(guildId, userId, -amount);
  }

  private Result<BalanceAdjustmentResult, DomainError> handleAdjustMode(
      long guildId, long userId, long targetBalance) {
    if (targetBalance < 0) {
      return Result.err(
          DomainError.invalidInput("Target balance cannot be negative for adjust mode."));
    }
    return adjustmentService.tryAdjustBalanceTo(guildId, userId, targetBalance);
  }

  private String formatResultMessage(
      String mode, BalanceAdjustmentResult result, String targetUserMention) {
    return switch (mode) {
      case "add" ->
          String.format(
              "Added %s %,d %s to %s\nNew balance: %s %,d %s",
              result.currencyIcon(),
              result.adjustment(),
              result.currencyName(),
              targetUserMention,
              result.currencyIcon(),
              result.newBalance(),
              result.currencyName());
      case "deduct" ->
          String.format(
              "Removed %s %,d %s from %s\nNew balance: %s %,d %s",
              result.currencyIcon(),
              Math.abs(result.adjustment()),
              result.currencyName(),
              targetUserMention,
              result.currencyIcon(),
              result.newBalance(),
              result.currencyName());
      case "adjust" ->
          String.format(
              "Adjusted %s balance from %s %,d to %s %,d %s (adjustment: %+d)\n"
                  + "New balance: %s %,d %s",
              targetUserMention,
              result.currencyIcon(),
              result.previousBalance(),
              result.currencyIcon(),
              result.newBalance(),
              result.currencyName(),
              result.adjustment(),
              result.currencyIcon(),
              result.newBalance(),
              result.currencyName());
      default -> result.formatMessage(targetUserMention);
    };
  }
}
