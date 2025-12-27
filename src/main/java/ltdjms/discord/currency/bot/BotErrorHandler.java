package ltdjms.discord.currency.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.discord.domain.DiscordInteraction;
import ltdjms.discord.shared.DomainError;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Utilities for handling errors in bot interactions. Provides consistent error logging and
 * user-friendly error messages.
 */
public final class BotErrorHandler {

  private static final Logger LOG = LoggerFactory.getLogger(BotErrorHandler.class);

  private static final String GENERIC_ERROR_MESSAGE =
      "An unexpected error occurred. Please try again later.";
  private static final String PERMISSION_ERROR_MESSAGE =
      "You don't have permission to perform this action.";
  private static final String INVALID_INPUT_MESSAGE =
      "Invalid input. Please check your command and try again.";

  private BotErrorHandler() {
    // Utility class
  }

  /**
   * Handles unexpected exceptions by logging them and sending a user-friendly message.
   *
   * @param event the slash command event
   * @param error the exception that occurred
   */
  public static void handleUnexpectedError(SlashCommandInteractionEvent event, Throwable error) {
    LOG.error(
        "Unexpected error in command {} for user={} in guild={}",
        event.getName(),
        event.getUser().getIdLong(),
        event.getGuild() != null ? event.getGuild().getIdLong() : "DM",
        error);

    replyWithError(event, GENERIC_ERROR_MESSAGE);
  }

  /**
   * Handles permission-related errors.
   *
   * @param event the slash command event
   */
  public static void handlePermissionError(SlashCommandInteractionEvent event) {
    LOG.warn(
        "Permission denied for command {} for user={} in guild={}",
        event.getName(),
        event.getUser().getIdLong(),
        event.getGuild() != null ? event.getGuild().getIdLong() : "DM");

    replyWithError(event, PERMISSION_ERROR_MESSAGE);
  }

  /**
   * Handles invalid input errors with a custom message.
   *
   * @param event the slash command event
   * @param message a user-friendly message describing the problem
   */
  public static void handleInvalidInput(SlashCommandInteractionEvent event, String message) {
    LOG.warn(
        "Invalid input for command {} for user={} in guild={}: {}",
        event.getName(),
        event.getUser().getIdLong(),
        event.getGuild() != null ? event.getGuild().getIdLong() : "DM",
        message);

    replyWithError(event, message != null ? message : INVALID_INPUT_MESSAGE);
  }

  /**
   * Handles database-related errors.
   *
   * @param event the slash command event
   * @param error the database exception
   */
  public static void handleDatabaseError(SlashCommandInteractionEvent event, Throwable error) {
    LOG.error(
        "Database error in command {} for user={} in guild={}",
        event.getName(),
        event.getUser().getIdLong(),
        event.getGuild() != null ? event.getGuild().getIdLong() : "DM",
        error);

    replyWithError(event, "A database error occurred. Please try again later.");
  }

  /**
   * Sends an ephemeral error reply to the user.
   *
   * @param event the slash command event
   * @param message the error message to display
   */
  public static void replyWithError(SlashCommandInteractionEvent event, String message) {
    if (event.isAcknowledged()) {
      event
          .getHook()
          .sendMessage("❌ " + message)
          .setEphemeral(true)
          .queue(success -> {}, failure -> LOG.error("Failed to send error hook message", failure));
    } else {
      event
          .reply("❌ " + message)
          .setEphemeral(true)
          .queue(success -> {}, failure -> LOG.error("Failed to send error reply", failure));
    }
  }

  /**
   * Logs a successful command execution.
   *
   * @param event the slash command event
   * @param details additional details about the operation
   */
  public static void logSuccess(SlashCommandInteractionEvent event, String details) {
    LOG.info(
        "Command {} executed successfully for user={} in guild={}: {}",
        event.getName(),
        event.getUser().getIdLong(),
        event.getGuild() != null ? event.getGuild().getIdLong() : "DM",
        details);
  }

  /**
   * Handles a DomainError by mapping it to the appropriate user message and log level.
   *
   * @param event the slash command event
   * @param error the DomainError to handle
   */
  public static void handleDomainError(SlashCommandInteractionEvent event, DomainError error) {
    switch (error.category()) {
      case INVALID_INPUT -> handleInvalidInput(event, error.message());

      case INSUFFICIENT_BALANCE -> {
        LOG.warn(
            "Insufficient balance for command {} for user={} in guild={}: {}",
            event.getName(),
            event.getUser().getIdLong(),
            event.getGuild() != null ? event.getGuild().getIdLong() : "DM",
            error.message());
        replyWithError(
            event,
            "Cannot reduce balance below zero. Current balance is insufficient for this"
                + " deduction.");
      }

      case INSUFFICIENT_TOKENS -> {
        LOG.warn(
            "Insufficient tokens for command {} for user={} in guild={}: {}",
            event.getName(),
            event.getUser().getIdLong(),
            event.getGuild() != null ? event.getGuild().getIdLong() : "DM",
            error.message());
        replyWithError(event, "Not enough game tokens for this operation.");
      }

      case PERSISTENCE_FAILURE -> handleDatabaseError(event, error.cause());

      case UNEXPECTED_FAILURE -> handleUnexpectedError(event, error.cause());
    }
  }

  /**
   * Returns a user-friendly message based on the DomainError category. This can be used when the
   * caller wants to handle the reply themselves.
   *
   * @param error the DomainError
   * @return a user-friendly message
   */
  public static String getUserMessage(DomainError error) {
    return switch (error.category()) {
      case INVALID_INPUT -> error.message();
      case INSUFFICIENT_BALANCE ->
          "Cannot reduce balance below zero. Current balance is insufficient for this deduction.";
      case INSUFFICIENT_TOKENS -> "Not enough game tokens for this operation.";
      case PERSISTENCE_FAILURE -> "A database error occurred. Please try again later.";
      case UNEXPECTED_FAILURE -> GENERIC_ERROR_MESSAGE;
      case DISCORD_INTERACTION_TIMEOUT -> "Interaction 已超時，請重新執行。";
      case DISCORD_HOOK_EXPIRED -> "面板已過期，請重新開啟。";
      case DISCORD_UNKNOWN_MESSAGE -> "訊息不存在，請重新整理。";
      case DISCORD_RATE_LIMITED -> error.message();
      case DISCORD_MISSING_PERMISSIONS -> error.message();
      case DISCORD_INVALID_COMPONENT_ID -> error.message();
    };
  }

  /**
   * Handles a DomainError using the DiscordInteraction abstract interface.
   *
   * <p>這是新版本的錯誤處理方法，使用 {@link DiscordInteraction} 抽象介面， 而不直接依賴 JDA API。適合用於已遷移到抽象層的 Command
   * Handler。
   *
   * @param interaction the DiscordInteraction 抽象介面
   * @param error the DomainError to handle
   */
  public static void handleDomainError(DiscordInteraction interaction, DomainError error) {
    long guildId = interaction.getGuildId();
    long userId = interaction.getUserId();

    switch (error.category()) {
      case INVALID_INPUT -> {
        LOG.warn(
            "Invalid input for interaction in guild={} user={}: {}",
            guildId,
            userId,
            error.message());
        interaction.reply("❌ " + error.message());
      }

      case INSUFFICIENT_BALANCE -> {
        LOG.warn(
            "Insufficient balance for interaction in guild={} user={}: {}",
            guildId,
            userId,
            error.message());
        interaction.reply(
            "❌ Cannot reduce balance below zero. Current balance is insufficient for this"
                + " deduction.");
      }

      case INSUFFICIENT_TOKENS -> {
        LOG.warn(
            "Insufficient tokens for interaction in guild={} user={}: {}",
            guildId,
            userId,
            error.message());
        interaction.reply("❌ Not enough game tokens for this operation.");
      }

      case PERSISTENCE_FAILURE -> {
        LOG.error(
            "Persistence failure for interaction in guild={} user={}",
            guildId,
            userId,
            error.cause());
        interaction.reply("❌ A database error occurred. Please try again later.");
      }

      case UNEXPECTED_FAILURE -> {
        LOG.error(
            "Unexpected failure for interaction in guild={} user={}",
            guildId,
            userId,
            error.cause());
        interaction.reply("❌ " + GENERIC_ERROR_MESSAGE);
      }

      case DISCORD_INTERACTION_TIMEOUT -> {
        LOG.warn(
            "Discord interaction timeout for guild={} user={}: {}",
            guildId,
            userId,
            error.message());
        interaction.reply("❌ " + error.message());
      }

      case DISCORD_HOOK_EXPIRED -> {
        LOG.warn("Discord hook expired for guild={} user={}: {}", guildId, userId, error.message());
        interaction.reply("❌ " + error.message());
      }

      case DISCORD_UNKNOWN_MESSAGE -> {
        LOG.warn(
            "Discord unknown message for guild={} user={}: {}", guildId, userId, error.message());
        interaction.reply("❌ " + error.message());
      }

      case DISCORD_RATE_LIMITED -> {
        LOG.warn("Discord rate limited for guild={} user={}: {}", guildId, userId, error.message());
        interaction.reply("❌ " + error.message());
      }

      case DISCORD_MISSING_PERMISSIONS -> {
        LOG.warn(
            "Discord missing permissions for guild={} user={}: {}",
            guildId,
            userId,
            error.message());
        interaction.reply("❌ " + error.message());
      }

      case DISCORD_INVALID_COMPONENT_ID -> {
        LOG.warn(
            "Discord invalid component ID for guild={} user={}: {}",
            guildId,
            userId,
            error.message());
        interaction.reply("❌ " + error.message());
      }
    }
  }

  /**
   * Logs a successful interaction execution using the abstract interface.
   *
   * @param interaction the DiscordInteraction 抽象介面
   * @param details additional details about the operation
   */
  public static void logSuccess(DiscordInteraction interaction, String details) {
    LOG.info(
        "Interaction executed successfully for guild={} user={}: {}",
        interaction.getGuildId(),
        interaction.getUserId(),
        details);
  }
}
