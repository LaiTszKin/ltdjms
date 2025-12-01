package ltdjms.discord.currency.commands;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the /balance slash command.
 * Shows the user's current currency balance in the guild.
 *
 * <p>All predictable errors are handled via Result&lt;T, DomainError&gt; pattern.
 * This handler does not catch domain exceptions directly; instead it relies on
 * the Result-based service API for all expected error conditions.</p>
 */
public class BalanceCommandHandler implements SlashCommandListener.CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BalanceCommandHandler.class);

    private final BalanceService balanceService;

    public BalanceCommandHandler(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();

        LOG.debug("Processing /balance for guildId={}, userId={}", guildId, userId);

        Result<BalanceView, DomainError> result = balanceService.tryGetBalance(guildId, userId);

        if (result.isErr()) {
            BotErrorHandler.handleDomainError(event, result.getError());
            return;
        }

        BalanceView balanceView = result.getValue();
        String message = balanceView.formatMessage();
        event.reply(message).queue();

        BotErrorHandler.logSuccess(event, "balance=" + balanceView.balance());
    }
}
