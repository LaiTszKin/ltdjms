package ltdjms.discord.gametoken.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * Handler for the /dice-game-2-config slash command. Allows administrators to configure tokens per
 * play range and reward multipliers for dice-game-2.
 */
public class DiceGame2ConfigCommandHandler implements SlashCommandListener.CommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(DiceGame2ConfigCommandHandler.class);

  private final DiceGame2ConfigRepository configRepository;

  public DiceGame2ConfigCommandHandler(DiceGame2ConfigRepository configRepository) {
    this.configRepository = configRepository;
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    long guildId = event.getGuild().getIdLong();

    LOG.debug("Processing /dice-game-2-config for guildId={}", guildId);

    // Check which subcommand or option is provided
    OptionMapping minTokensOption = event.getOption("min-tokens");
    OptionMapping maxTokensOption = event.getOption("max-tokens");
    OptionMapping straightMultiplierOption = event.getOption("straight-multiplier");
    OptionMapping baseMultiplierOption = event.getOption("base-multiplier");
    OptionMapping tripleLowBonusOption = event.getOption("triple-low-bonus");
    OptionMapping tripleHighBonusOption = event.getOption("triple-high-bonus");

    // If no options provided, show current configuration
    if (minTokensOption == null
        && maxTokensOption == null
        && straightMultiplierOption == null
        && baseMultiplierOption == null
        && tripleLowBonusOption == null
        && tripleHighBonusOption == null) {
      showCurrentConfig(event, guildId);
      return;
    }

    try {
      DiceGame2Config currentConfig = configRepository.findOrCreateDefault(guildId);
      DiceGame2Config updatedConfig = currentConfig;

      // Update tokens per play range if any token option is provided
      if (minTokensOption != null || maxTokensOption != null) {
        long minTokens =
            minTokensOption != null
                ? minTokensOption.getAsLong()
                : currentConfig.minTokensPerPlay();
        long maxTokens =
            maxTokensOption != null
                ? maxTokensOption.getAsLong()
                : currentConfig.maxTokensPerPlay();

        updatedConfig = configRepository.updateTokensPerPlayRange(guildId, minTokens, maxTokens);
      }

      // Update multipliers if any multiplier option is provided
      if (straightMultiplierOption != null || baseMultiplierOption != null) {
        long straightMultiplier =
            straightMultiplierOption != null
                ? straightMultiplierOption.getAsLong()
                : updatedConfig.straightMultiplier();
        long baseMultiplier =
            baseMultiplierOption != null
                ? baseMultiplierOption.getAsLong()
                : updatedConfig.baseMultiplier();

        updatedConfig =
            configRepository.updateMultipliers(guildId, straightMultiplier, baseMultiplier);
      }

      // Update triple bonuses if any bonus option is provided
      if (tripleLowBonusOption != null || tripleHighBonusOption != null) {
        long tripleLowBonus =
            tripleLowBonusOption != null
                ? tripleLowBonusOption.getAsLong()
                : updatedConfig.tripleLowBonus();
        long tripleHighBonus =
            tripleHighBonusOption != null
                ? tripleHighBonusOption.getAsLong()
                : updatedConfig.tripleHighBonus();

        updatedConfig =
            configRepository.updateTripleBonuses(guildId, tripleLowBonus, tripleHighBonus);
      }

      String message = formatConfigUpdateMessage(updatedConfig);
      event.reply(message).queue();

      BotErrorHandler.logSuccess(
          event,
          "minTokens="
              + updatedConfig.minTokensPerPlay()
              + ", maxTokens="
              + updatedConfig.maxTokensPerPlay()
              + ", straightMultiplier="
              + updatedConfig.straightMultiplier()
              + ", baseMultiplier="
              + updatedConfig.baseMultiplier()
              + ", tripleLowBonus="
              + updatedConfig.tripleLowBonus()
              + ", tripleHighBonus="
              + updatedConfig.tripleHighBonus());

    } catch (IllegalArgumentException e) {
      BotErrorHandler.handleInvalidInput(event, e.getMessage());
    } catch (RepositoryException e) {
      BotErrorHandler.handleDatabaseError(event, e);
    } catch (Exception e) {
      BotErrorHandler.handleUnexpectedError(event, e);
    }
  }

  private void showCurrentConfig(SlashCommandInteractionEvent event, long guildId) {
    try {
      DiceGame2Config config = configRepository.findOrCreateDefault(guildId);

      String message =
          String.format(
              "**Dice Game 2 Configuration**\n\n"
                  + "**Token Settings:**\n"
                  + "• Minimum tokens per play: %,d\n"
                  + "• Maximum tokens per play: %,d\n\n"
                  + "**Reward Multipliers:**\n"
                  + "• Straight multiplier: %,d\n"
                  + "• Base multiplier: %,d\n\n"
                  + "**Triple Bonuses:**\n"
                  + "• Triple low bonus (1-1-1): %,d\n"
                  + "• Triple high bonus (6-6-6): %,d\n\n"
                  + "Use options to update settings:\n"
                  + "`/dice-game-2-config min-tokens:<n> max-tokens:<n>`\n"
                  + "`/dice-game-2-config straight-multiplier:<n> base-multiplier:<n>`\n"
                  + "`/dice-game-2-config triple-low-bonus:<n> triple-high-bonus:<n>`",
              config.minTokensPerPlay(),
              config.maxTokensPerPlay(),
              config.straightMultiplier(),
              config.baseMultiplier(),
              config.tripleLowBonus(),
              config.tripleHighBonus());
      event.reply(message).queue();

    } catch (RepositoryException e) {
      BotErrorHandler.handleDatabaseError(event, e);
    } catch (Exception e) {
      BotErrorHandler.handleUnexpectedError(event, e);
    }
  }

  private String formatConfigUpdateMessage(DiceGame2Config config) {
    return String.format(
        "Dice Game 2 configuration updated!\n\n"
            + "**Token Settings:**\n"
            + "• Minimum tokens per play: %,d\n"
            + "• Maximum tokens per play: %,d\n\n"
            + "**Reward Multipliers:**\n"
            + "• Straight multiplier: %,d\n"
            + "• Base multiplier: %,d\n\n"
            + "**Triple Bonuses:**\n"
            + "• Triple low bonus (1-1-1): %,d\n"
            + "• Triple high bonus (6-6-6): %,d",
        config.minTokensPerPlay(),
        config.maxTokensPerPlay(),
        config.straightMultiplier(),
        config.baseMultiplier(),
        config.tripleLowBonus(),
        config.tripleHighBonus());
  }
}
