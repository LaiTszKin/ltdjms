package ltdjms.discord.gametoken.commands;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the /dice-game-2-config slash command.
 * Allows administrators to configure the token cost for playing dice-game-2.
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

        // Get the token-cost option
        OptionMapping tokenCostOption = event.getOption("token-cost");

        if (tokenCostOption == null) {
            // Show current configuration
            showCurrentConfig(event, guildId);
            return;
        }

        long tokenCost = tokenCostOption.getAsLong();

        if (tokenCost < 0) {
            BotErrorHandler.handleInvalidInput(event, "Token cost cannot be negative.");
            return;
        }

        try {
            DiceGame2Config updated = configRepository.updateTokensPerPlay(guildId, tokenCost);

            String message = String.format(
                    "Dice game 2 configuration updated!\n" +
                            "Tokens required per play: %,d",
                    updated.tokensPerPlay()
            );
            event.reply(message).queue();

            BotErrorHandler.logSuccess(event, "tokensPerPlay=" + updated.tokensPerPlay());

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

            String message = String.format(
                    "**Dice Game 2 Configuration**\n" +
                            "Tokens required per play: %,d\n\n" +
                            "Use `/dice-game-2-config token-cost:<amount>` to change the setting.",
                    config.tokensPerPlay()
            );
            event.reply(message).queue();

        } catch (RepositoryException e) {
            BotErrorHandler.handleDatabaseError(event, e);
        } catch (Exception e) {
            BotErrorHandler.handleUnexpectedError(event, e);
        }
    }
}
