package ltdjms.discord.gametoken.commands;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.GameTokenAccount;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.DiceGame1Service.DiceGameResult;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the /dice-game-1 slash command.
 * Allows players to play the dice mini-game by spending game tokens.
 *
 * <p>All predictable errors are handled via Result&lt;T, DomainError&gt; pattern.
 * This handler does not catch domain exceptions directly; instead it relies on
 * the Result-based service API for all expected error conditions.</p>
 */
public class DiceGame1CommandHandler implements SlashCommandListener.CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DiceGame1CommandHandler.class);

    private final GameTokenService tokenService;
    private final DiceGame1Service diceGameService;
    private final DiceGame1ConfigRepository configRepository;
    private final GuildCurrencyConfigRepository currencyConfigRepository;

    public DiceGame1CommandHandler(
            GameTokenService tokenService,
            DiceGame1Service diceGameService,
            DiceGame1ConfigRepository configRepository,
            GuildCurrencyConfigRepository currencyConfigRepository) {
        this.tokenService = tokenService;
        this.diceGameService = diceGameService;
        this.configRepository = configRepository;
        this.currencyConfigRepository = currencyConfigRepository;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();

        LOG.debug("Processing /dice-game-1 for guildId={}, userId={}", guildId, userId);

        // Get the token cost for this guild
        DiceGame1Config gameConfig = configRepository.findOrCreateDefault(guildId);
        long tokenCost = gameConfig.tokensPerPlay();

        // Check if player has enough tokens
        if (!tokenService.hasEnoughTokens(guildId, userId, tokenCost)) {
            long currentTokens = tokenService.getBalance(guildId, userId);
            event.reply(String.format(
                    "You don't have enough game tokens to play!\n" +
                            "Required: %,d tokens\n" +
                            "Your balance: %,d tokens",
                    tokenCost, currentTokens
            )).setEphemeral(true).queue();
            return;
        }

        // Deduct tokens using Result-based API
        Result<GameTokenAccount, DomainError> deductResult =
                tokenService.tryDeductTokens(guildId, userId, tokenCost);

        if (deductResult.isErr()) {
            BotErrorHandler.handleDomainError(event, deductResult.getError());
            return;
        }

        // Play the game
        DiceGameResult result = diceGameService.play(guildId, userId);

        // Get currency config for display
        GuildCurrencyConfig currencyConfig = currencyConfigRepository.findByGuildId(guildId)
                .orElse(GuildCurrencyConfig.createDefault(guildId));

        // Format and send response
        String message = result.formatMessage(
                currencyConfig.currencyIcon(),
                currencyConfig.currencyName()
        );
        event.reply(message).queue();

        BotErrorHandler.logSuccess(event,
                "rolls=" + result.diceRolls() + ", reward=" + result.totalReward() +
                        ", newBalance=" + result.newBalance());
    }
}
