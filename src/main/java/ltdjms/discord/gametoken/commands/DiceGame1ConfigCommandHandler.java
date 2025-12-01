package ltdjms.discord.gametoken.commands;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the /dice-game-1-config slash command.
 * Allows administrators to configure the token range and reward for playing dice-game-1.
 */
public class DiceGame1ConfigCommandHandler implements SlashCommandListener.CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DiceGame1ConfigCommandHandler.class);

    private final DiceGame1ConfigRepository configRepository;

    public DiceGame1ConfigCommandHandler(DiceGame1ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();

        LOG.debug("Processing /dice-game-1-config for guildId={}", guildId);

        // Get options
        OptionMapping minTokensOption = event.getOption("min-tokens");
        OptionMapping maxTokensOption = event.getOption("max-tokens");
        OptionMapping rewardOption = event.getOption("reward-multiplier");

        // If no options provided, show current configuration
        if (minTokensOption == null && maxTokensOption == null && rewardOption == null) {
            showCurrentConfig(event, guildId);
            return;
        }

        try {
            DiceGame1Config current = configRepository.findOrCreateDefault(guildId);

            // Update token range if any token option is provided
            if (minTokensOption != null || maxTokensOption != null) {
                long minTokens = minTokensOption != null
                        ? minTokensOption.getAsLong()
                        : current.minTokensPerPlay();
                long maxTokens = maxTokensOption != null
                        ? maxTokensOption.getAsLong()
                        : current.maxTokensPerPlay();

                current = configRepository.updateTokensPerPlayRange(guildId, minTokens, maxTokens);
            }

            // Update reward if provided
            if (rewardOption != null) {
                long reward = rewardOption.getAsLong();
                current = configRepository.updateRewardPerDiceValue(guildId, reward);
            }

            String message = String.format(
                    "**骰子遊戲 1 設定已更新！**\n" +
                            "代幣投入範圍：%,d - %,d\n" +
                            "單骰獎勵倍率：%,d",
                    current.minTokensPerPlay(),
                    current.maxTokensPerPlay(),
                    current.rewardPerDiceValue()
            );
            event.reply(message).queue();

            BotErrorHandler.logSuccess(event, String.format(
                    "min=%d, max=%d, reward=%d",
                    current.minTokensPerPlay(),
                    current.maxTokensPerPlay(),
                    current.rewardPerDiceValue()
            ));

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
            DiceGame1Config config = configRepository.findOrCreateDefault(guildId);

            String message = String.format(
                    "**骰子遊戲 1 設定**\n" +
                            "代幣投入範圍：%,d - %,d\n" +
                            "單骰獎勵倍率：%,d\n\n" +
                            "使用 `/dice-game-1-config` 加上參數來調整設定。",
                    config.minTokensPerPlay(),
                    config.maxTokensPerPlay(),
                    config.rewardPerDiceValue()
            );
            event.reply(message).queue();

        } catch (RepositoryException e) {
            BotErrorHandler.handleDatabaseError(event, e);
        } catch (Exception e) {
            BotErrorHandler.handleUnexpectedError(event, e);
        }
    }
}
