package ltdjms.discord.currency.commands;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handler for the /currency-config slash command.
 * Allows administrators to configure the guild's currency name and icon.
 *
 * <p>All predictable errors are handled via Result&lt;T, DomainError&gt; pattern.
 * This handler does not catch domain exceptions directly; instead it relies on
 * the Result-based service API for all expected error conditions.</p>
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

        Result<GuildCurrencyConfig, DomainError> result = configService.tryUpdateConfig(guildId, name, icon);

        if (result.isErr()) {
            BotErrorHandler.handleDomainError(event, result.getError());
            return;
        }

        GuildCurrencyConfig updated = result.getValue();
        String message = String.format(
                "✅ Currency configuration updated!\n" +
                        "Name: **%s**\n" +
                        "Icon: %s",
                updated.currencyName(),
                updated.currencyIcon()
        );
        event.reply(message).queue();

        BotErrorHandler.logSuccess(event, "name=" + updated.currencyName() + ", icon=" + updated.currencyIcon());
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
        Result<GuildCurrencyConfig, DomainError> result = configService.tryGetConfig(guildId);

        if (result.isErr()) {
            BotErrorHandler.handleDomainError(event, result.getError());
            return;
        }

        GuildCurrencyConfig config = result.getValue();
        String message = String.format(
                "**Current Currency Configuration**\n" +
                        "Name: **%s**\n" +
                        "Icon: %s\n\n" +
                        "_Use `/currency-config name:<name> icon:<emoji>` to update_",
                config.currencyName(),
                config.currencyIcon()
        );
        event.reply(message).setEphemeral(true).queue();
    }
}
