package ltdjms.discord.currency.commands;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.currency.services.CurrencyConfigService;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handler for the /currency-config slash command.
 * Allows administrators to configure the guild's currency name and icon.
 */
public class CurrencyConfigCommandHandler implements SlashCommandListener.CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CurrencyConfigCommandHandler.class);

    private final CurrencyConfigService configService;

    public CurrencyConfigCommandHandler(CurrencyConfigService configService) {
        this.configService = configService;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();

        LOG.debug("Processing /currency-config for guildId={}", guildId);

        // Get options
        OptionMapping nameOption = event.getOption("name");
        OptionMapping iconOption = event.getOption("icon");

        String name = nameOption != null ? nameOption.getAsString() : null;
        String icon = extractIconValue(iconOption);

        // If no options provided, show current config
        if (name == null && icon == null) {
            showCurrentConfig(event, guildId);
            return;
        }

        try {
            GuildCurrencyConfig updated = configService.updateConfig(guildId, name, icon);

            String message = String.format(
                    "✅ Currency configuration updated!\n" +
                            "Name: **%s**\n" +
                            "Icon: %s",
                    updated.currencyName(),
                    updated.currencyIcon()
            );
            event.reply(message).queue();

            BotErrorHandler.logSuccess(event, "name=" + updated.currencyName() + ", icon=" + updated.currencyIcon());

        } catch (IllegalArgumentException e) {
            BotErrorHandler.handleInvalidInput(event, e.getMessage());
        } catch (RepositoryException e) {
            BotErrorHandler.handleDatabaseError(event, e);
        } catch (Exception e) {
            BotErrorHandler.handleUnexpectedError(event, e);
        }
    }

    /**
     * Extracts the icon value from the option mapping.
     * If the option contains a rendered custom emoji (from Discord's autocomplete or paste),
     * extracts the formatted string (e.g., {@code <:name:id>}) from Mentions.
     * Otherwise, returns the raw string value.
     *
     * @param iconOption the icon option mapping, may be null
     * @return the icon string value, or null if option is null
     */
    private String extractIconValue(OptionMapping iconOption) {
        if (iconOption == null) {
            return null;
        }

        // Check if the option contains rendered custom emoji(s) via Mentions
        List<CustomEmoji> customEmojis = iconOption.getMentions().getCustomEmojis();
        if (!customEmojis.isEmpty()) {
            // Use the first custom emoji's formatted string
            CustomEmoji emoji = customEmojis.get(0);
            String formatted = emoji.getFormatted();
            LOG.debug("Extracted custom emoji from Mentions: {} -> {}", emoji.getName(), formatted);
            return formatted;
        }

        // No custom emoji in Mentions, use the raw string value
        // This handles: Unicode emoji, manually typed <:name:id>, or other text
        return iconOption.getAsString();
    }

    private void showCurrentConfig(SlashCommandInteractionEvent event, long guildId) {
        try {
            GuildCurrencyConfig config = configService.getConfig(guildId);

            String message = String.format(
                    "**Current Currency Configuration**\n" +
                            "Name: **%s**\n" +
                            "Icon: %s\n\n" +
                            "_Use `/currency-config name:<name> icon:<emoji>` to update_",
                    config.currencyName(),
                    config.currencyIcon()
            );
            event.reply(message).setEphemeral(true).queue();

        } catch (RepositoryException e) {
            BotErrorHandler.handleDatabaseError(event, e);
        } catch (Exception e) {
            BotErrorHandler.handleUnexpectedError(event, e);
        }
    }
}
