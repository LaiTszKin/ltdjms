package ltdjms.discord.gametoken.commands;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.GameTokenAccount;
import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.DiceGame1Service.DiceGameResult;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.localization.DiceGameMessages;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the /dice-game-1 slash command.
 * Allows players to play the dice mini-game by spending game tokens.
 *
 * <p>Players must explicitly specify how many tokens to spend; the number of dice
 * rolled equals the number of tokens spent (1 token = 1 dice).</p>
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
    private final GameTokenTransactionService transactionService;

    public DiceGame1CommandHandler(
            GameTokenService tokenService,
            DiceGame1Service diceGameService,
            DiceGame1ConfigRepository configRepository,
            GuildCurrencyConfigRepository currencyConfigRepository,
            GameTokenTransactionService transactionService) {
        this.tokenService = tokenService;
        this.diceGameService = diceGameService;
        this.configRepository = configRepository;
        this.currencyConfigRepository = currencyConfigRepository;
        this.transactionService = transactionService;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();
        DiscordLocale locale = event.getUserLocale();

        LOG.debug("Processing /dice-game-1 for guildId={}, userId={}, locale={}", guildId, userId, locale);

        // Get the game configuration for this guild
        DiceGame1Config gameConfig = configRepository.findOrCreateDefault(guildId);

        // Require player to specify token amount - no default
        OptionMapping tokensOption = event.getOption("tokens");
        if (tokensOption == null) {
            String message = DiceGameMessages.formatMissingTokensError(
                    gameConfig.minTokensPerPlay(),
                    gameConfig.maxTokensPerPlay(),
                    locale
            );
            event.reply(message).setEphemeral(true).queue();
            return;
        }

        long tokenCost = tokensOption.getAsLong();

        // Validate player input against configured range
        if (!gameConfig.isValidTokenAmount(tokenCost)) {
            String message = DiceGameMessages.formatTokenRangeError(
                    tokenCost,
                    gameConfig.minTokensPerPlay(),
                    gameConfig.maxTokensPerPlay(),
                    locale
            );
            event.reply(message).setEphemeral(true).queue();
            return;
        }

        // Check if player has enough tokens
        if (!tokenService.hasEnoughTokens(guildId, userId, tokenCost)) {
            long currentTokens = tokenService.getBalance(guildId, userId);
            String message = DiceGameMessages.formatInsufficientTokens(tokenCost, currentTokens, locale);
            event.reply(message).setEphemeral(true).queue();
            return;
        }

        // Deduct tokens using Result-based API
        Result<GameTokenAccount, DomainError> deductResult =
                tokenService.tryDeductTokens(guildId, userId, tokenCost);

        if (deductResult.isErr()) {
            BotErrorHandler.handleDomainError(event, deductResult.getError());
            return;
        }

        GameTokenAccount updatedAccount = deductResult.getValue();

        // Record game token consumption so it appears in token history
        transactionService.recordTransaction(
                guildId,
                userId,
                -tokenCost,
                updatedAccount.tokens(),
                GameTokenTransaction.Source.DICE_GAME_1_PLAY,
                null
        );

        // Play the game with dice count = tokens spent
        int diceCount = (int) tokenCost;
        DiceGameResult result = diceGameService.play(guildId, userId, gameConfig, diceCount);

        // Get currency config for display
        GuildCurrencyConfig currencyConfig = currencyConfigRepository.findByGuildId(guildId)
                .orElse(GuildCurrencyConfig.createDefault(guildId));

        // Format and send response with locale-aware messages
        String message = DiceGameMessages.formatDiceGame1Result(
                result,
                currencyConfig.currencyIcon(),
                currencyConfig.currencyName(),
                locale
        );
        event.reply(message).queue();

        BotErrorHandler.logSuccess(event,
                "rolls=" + result.diceRolls() + ", reward=" + result.totalReward() +
                        ", newBalance=" + result.newBalance());
    }
}
