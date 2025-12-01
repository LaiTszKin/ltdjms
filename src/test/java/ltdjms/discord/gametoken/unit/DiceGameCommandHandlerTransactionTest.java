package ltdjms.discord.gametoken.unit;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.gametoken.commands.DiceGame1CommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame2CommandHandler;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.gametoken.domain.GameTokenAccount;
import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.DiceGame1Service.DiceGameResult;
import ltdjms.discord.gametoken.services.DiceGame2Service;
import ltdjms.discord.gametoken.services.DiceGame2Service.DiceGame2Result;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Command handler tests focusing on game token transaction recording.
 * Verifies that playing dice games creates game token transaction records,
 * so they can appear in the user panel token history.
 */
class DiceGameCommandHandlerTransactionTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final long TEST_USER_ID = 987654321098765432L;

    @Nested
    @DisplayName("Dice-game-1")
    class DiceGame1Tests {

        @Test
        @DisplayName("should record game token transaction when game 1 is played")
        void shouldRecordTransactionWhenGame1Played() {
            // Given
            long tokenCost = 5L;
            long previousTokens = 10L;
            long newTokenBalance = previousTokens - tokenCost;

            StubGameTokenAccountRepository accountRepository =
                    new StubGameTokenAccountRepository(previousTokens);
            GameTokenService tokenService = new GameTokenService(accountRepository);
            DiceGame1Service diceGameService = mock(DiceGame1Service.class);
            ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository configRepository =
                    mock(ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository.class);
            GuildCurrencyConfigRepository currencyConfigRepository = mock(GuildCurrencyConfigRepository.class);
            GameTokenTransactionService transactionService = mock(GameTokenTransactionService.class);

            DiceGame1Config config = new DiceGame1Config(
                    TEST_GUILD_ID, tokenCost, tokenCost,
                    DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE,
                    java.time.Instant.now(), java.time.Instant.now()
            );
            when(configRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

            DiceGameResult gameResult = new DiceGameResult(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(1, 2, 3, 4, 5),
                    1_000_000L, 0L, 1_000_000L
            );
            when(diceGameService.play(eq(TEST_GUILD_ID), eq(TEST_USER_ID), any(DiceGame1Config.class), anyInt()))
                    .thenReturn(gameResult);

            when(currencyConfigRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.of(
                    new GuildCurrencyConfig(
                            TEST_GUILD_ID,
                            "Gold",
                            "💰",
                            Instant.now(),
                            Instant.now())
            ));

            DiceGame1CommandHandler handler = new DiceGame1CommandHandler(
                    tokenService,
                    diceGameService,
                    configRepository,
                    currencyConfigRepository,
                    transactionService
            );

            // Now tokens are required - use createMockSlashEventWithTokens
            SlashCommandInteractionEvent event = createMockSlashEventWithTokens("dice-game-1", tokenCost);

            // When
            handler.handle(event);

            // Then - verify a game-play transaction was recorded
            verify(transactionService).recordTransaction(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    -tokenCost,
                    newTokenBalance,
                    GameTokenTransaction.Source.DICE_GAME_1_PLAY,
                    null
            );
        }

        @Test
        @DisplayName("should use player-specified tokens when provided and within valid range")
        void shouldUsePlayerSpecifiedTokensWhenValid() {
            // Given
            long playerSpecifiedTokens = 7L;
            long previousTokens = 20L;
            long newTokenBalance = previousTokens - playerSpecifiedTokens;

            StubGameTokenAccountRepository accountRepository =
                    new StubGameTokenAccountRepository(previousTokens);
            GameTokenService tokenService = new GameTokenService(accountRepository);
            DiceGame1Service diceGameService = mock(DiceGame1Service.class);
            ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository configRepository =
                    mock(ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository.class);
            GuildCurrencyConfigRepository currencyConfigRepository = mock(GuildCurrencyConfigRepository.class);
            GameTokenTransactionService transactionService = mock(GameTokenTransactionService.class);

            // Config allows 1-10 tokens
            DiceGame1Config config = new DiceGame1Config(
                    TEST_GUILD_ID, 1L, 10L,
                    DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE,
                    java.time.Instant.now(), java.time.Instant.now()
            );
            when(configRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

            DiceGameResult gameResult = new DiceGameResult(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(1, 2, 3, 4, 5, 6, 1),
                    1_000_000L, 0L, 1_000_000L
            );
            when(diceGameService.play(eq(TEST_GUILD_ID), eq(TEST_USER_ID), any(DiceGame1Config.class), anyInt()))
                    .thenReturn(gameResult);

            when(currencyConfigRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            DiceGame1CommandHandler handler = new DiceGame1CommandHandler(
                    tokenService,
                    diceGameService,
                    configRepository,
                    currencyConfigRepository,
                    transactionService
            );

            SlashCommandInteractionEvent event = createMockSlashEventWithTokens("dice-game-1", playerSpecifiedTokens);

            // When
            handler.handle(event);

            // Then - verify player-specified token amount was used
            verify(transactionService).recordTransaction(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    -playerSpecifiedTokens,
                    newTokenBalance,
                    GameTokenTransaction.Source.DICE_GAME_1_PLAY,
                    null
            );
        }

        @Test
        @DisplayName("should reject tokens below minimum with warning message")
        void shouldRejectTokensBelowMinimum() {
            // Given
            long playerSpecifiedTokens = 2L;  // Below minimum of 5
            long previousTokens = 20L;

            StubGameTokenAccountRepository accountRepository =
                    new StubGameTokenAccountRepository(previousTokens);
            GameTokenService tokenService = new GameTokenService(accountRepository);
            DiceGame1Service diceGameService = mock(DiceGame1Service.class);
            ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository configRepository =
                    mock(ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository.class);
            GuildCurrencyConfigRepository currencyConfigRepository = mock(GuildCurrencyConfigRepository.class);
            GameTokenTransactionService transactionService = mock(GameTokenTransactionService.class);

            // Config allows 5-10 tokens
            DiceGame1Config config = new DiceGame1Config(
                    TEST_GUILD_ID, 5L, 10L,
                    DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE,
                    java.time.Instant.now(), java.time.Instant.now()
            );
            when(configRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

            DiceGame1CommandHandler handler = new DiceGame1CommandHandler(
                    tokenService,
                    diceGameService,
                    configRepository,
                    currencyConfigRepository,
                    transactionService
            );

            SlashCommandInteractionEvent event = createMockSlashEventWithTokens("dice-game-1", playerSpecifiedTokens);

            // When
            handler.handle(event);

            // Then - verify no transaction and no game played
            verifyNoInteractions(transactionService);
            verifyNoInteractions(diceGameService);
            // Warning message sent as ephemeral reply
            verify(event).reply(argThat((String msg) ->
                    msg.contains("5") && msg.contains("10")));
        }

        @Test
        @DisplayName("should reject tokens above maximum with warning message")
        void shouldRejectTokensAboveMaximum() {
            // Given
            long playerSpecifiedTokens = 15L;  // Above maximum of 10
            long previousTokens = 20L;

            StubGameTokenAccountRepository accountRepository =
                    new StubGameTokenAccountRepository(previousTokens);
            GameTokenService tokenService = new GameTokenService(accountRepository);
            DiceGame1Service diceGameService = mock(DiceGame1Service.class);
            ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository configRepository =
                    mock(ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository.class);
            GuildCurrencyConfigRepository currencyConfigRepository = mock(GuildCurrencyConfigRepository.class);
            GameTokenTransactionService transactionService = mock(GameTokenTransactionService.class);

            // Config allows 5-10 tokens
            DiceGame1Config config = new DiceGame1Config(
                    TEST_GUILD_ID, 5L, 10L,
                    DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE,
                    java.time.Instant.now(), java.time.Instant.now()
            );
            when(configRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

            DiceGame1CommandHandler handler = new DiceGame1CommandHandler(
                    tokenService,
                    diceGameService,
                    configRepository,
                    currencyConfigRepository,
                    transactionService
            );

            SlashCommandInteractionEvent event = createMockSlashEventWithTokens("dice-game-1", playerSpecifiedTokens);

            // When
            handler.handle(event);

            // Then - verify no transaction and no game played
            verifyNoInteractions(transactionService);
            verifyNoInteractions(diceGameService);
            // Warning message sent as ephemeral reply
            verify(event).reply(argThat((String msg) ->
                    msg.contains("5") && msg.contains("10")));
        }

        @Test
        @DisplayName("should reject missing tokens with error message prompting user to specify")
        void shouldRejectMissingTokensWithErrorMessage() {
            // Given
            long previousTokens = 20L;

            StubGameTokenAccountRepository accountRepository =
                    new StubGameTokenAccountRepository(previousTokens);
            GameTokenService tokenService = new GameTokenService(accountRepository);
            DiceGame1Service diceGameService = mock(DiceGame1Service.class);
            ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository configRepository =
                    mock(ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository.class);
            GuildCurrencyConfigRepository currencyConfigRepository = mock(GuildCurrencyConfigRepository.class);
            GameTokenTransactionService transactionService = mock(GameTokenTransactionService.class);

            // Config allows 1-10 tokens
            DiceGame1Config config = new DiceGame1Config(
                    TEST_GUILD_ID, 1L, 10L,
                    DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE,
                    java.time.Instant.now(), java.time.Instant.now()
            );
            when(configRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

            DiceGame1CommandHandler handler = new DiceGame1CommandHandler(
                    tokenService,
                    diceGameService,
                    configRepository,
                    currencyConfigRepository,
                    transactionService
            );

            // Event without tokens option
            SlashCommandInteractionEvent event = createMockSlashEvent("dice-game-1");

            // When
            handler.handle(event);

            // Then - verify no transaction and no game played
            verifyNoInteractions(transactionService);
            verifyNoInteractions(diceGameService);
            // Error message sent as ephemeral reply prompting user to enter tokens
            verify(event).reply(argThat((String msg) ->
                    msg.contains("1") && msg.contains("10")));
        }

        @Test
        @DisplayName("should roll correct number of dice based on tokens spent (1 dice per token)")
        void shouldRollCorrectDiceCountBasedOnTokens() {
            // Given
            long tokensToSpend = 7L;
            long previousTokens = 20L;
            long newTokenBalance = previousTokens - tokensToSpend;

            StubGameTokenAccountRepository accountRepository =
                    new StubGameTokenAccountRepository(previousTokens);
            GameTokenService tokenService = new GameTokenService(accountRepository);
            DiceGame1Service diceGameService = mock(DiceGame1Service.class);
            ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository configRepository =
                    mock(ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository.class);
            GuildCurrencyConfigRepository currencyConfigRepository = mock(GuildCurrencyConfigRepository.class);
            GameTokenTransactionService transactionService = mock(GameTokenTransactionService.class);

            // Config allows 1-10 tokens
            DiceGame1Config config = new DiceGame1Config(
                    TEST_GUILD_ID, 1L, 10L,
                    DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE,
                    java.time.Instant.now(), java.time.Instant.now()
            );
            when(configRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

            // Return result with 7 dice (matching tokens spent)
            DiceGameResult gameResult = new DiceGameResult(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(1, 2, 3, 4, 5, 6, 1),  // 7 dice
                    1_000_000L, 0L, 1_000_000L
            );
            when(diceGameService.play(eq(TEST_GUILD_ID), eq(TEST_USER_ID), any(DiceGame1Config.class), eq((int) tokensToSpend)))
                    .thenReturn(gameResult);

            when(currencyConfigRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            DiceGame1CommandHandler handler = new DiceGame1CommandHandler(
                    tokenService,
                    diceGameService,
                    configRepository,
                    currencyConfigRepository,
                    transactionService
            );

            SlashCommandInteractionEvent event = createMockSlashEventWithTokens("dice-game-1", tokensToSpend);

            // When
            handler.handle(event);

            // Then - verify service was called with correct dice count
            verify(diceGameService).play(TEST_GUILD_ID, TEST_USER_ID, config, (int) tokensToSpend);
            verify(transactionService).recordTransaction(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    -tokensToSpend,
                    newTokenBalance,
                    GameTokenTransaction.Source.DICE_GAME_1_PLAY,
                    null
            );
        }
    }

    @Nested
    @DisplayName("Dice-game-2")
    class DiceGame2Tests {

        @Test
        @DisplayName("should record game token transaction when game 2 is played")
        void shouldRecordTransactionWhenGame2Played() {
            // Given
            long tokenCost = 5L;  // Use default min tokens (5)
            long previousTokens = 20L;
            long newTokenBalance = previousTokens - tokenCost;

            StubGameTokenAccountRepository accountRepository =
                    new StubGameTokenAccountRepository(previousTokens);
            GameTokenService tokenService = new GameTokenService(accountRepository);
            DiceGame2Service diceGameService = mock(DiceGame2Service.class);
            ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository configRepository =
                    mock(ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository.class);
            GuildCurrencyConfigRepository currencyConfigRepository = mock(GuildCurrencyConfigRepository.class);
            GameTokenTransactionService transactionService = mock(GameTokenTransactionService.class);

            DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);
            when(configRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

            DiceGame2Result gameResult = new DiceGame2Result(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6, 1, 2, 3),
                    1_000_000L, 0L, 1_000_000L,
                    List.of(), List.of(),
                    0L, 1_000_000L, 0L
            );
            when(diceGameService.play(eq(TEST_GUILD_ID), eq(TEST_USER_ID), any(DiceGame2Config.class), anyInt()))
                    .thenReturn(gameResult);

            when(currencyConfigRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            DiceGame2CommandHandler handler = new DiceGame2CommandHandler(
                    tokenService,
                    diceGameService,
                    configRepository,
                    currencyConfigRepository,
                    transactionService
            );

            // Now tokens are required - use createMockSlashEventWithTokens
            SlashCommandInteractionEvent event = createMockSlashEventWithTokens("dice-game-2", tokenCost);

            // When
            handler.handle(event);

            // Then - verify a game-play transaction was recorded
            verify(transactionService).recordTransaction(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    -tokenCost,
                    newTokenBalance,
                    GameTokenTransaction.Source.DICE_GAME_2_PLAY,
                    null
            );
        }

        @Test
        @DisplayName("should use player-specified tokens when provided and within valid range")
        void shouldUsePlayerSpecifiedTokensWhenValid() {
            // Given
            long playerSpecifiedTokens = 30L;
            long previousTokens = 100L;
            long newTokenBalance = previousTokens - playerSpecifiedTokens;

            StubGameTokenAccountRepository accountRepository =
                    new StubGameTokenAccountRepository(previousTokens);
            GameTokenService tokenService = new GameTokenService(accountRepository);
            DiceGame2Service diceGameService = mock(DiceGame2Service.class);
            ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository configRepository =
                    mock(ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository.class);
            GuildCurrencyConfigRepository currencyConfigRepository = mock(GuildCurrencyConfigRepository.class);
            GameTokenTransactionService transactionService = mock(GameTokenTransactionService.class);

            // Config allows 5-50 tokens, default is 5
            DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);
            when(configRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

            DiceGame2Result gameResult = new DiceGame2Result(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6, 1, 2, 3),
                    1_000_000L, 0L, 1_000_000L,
                    List.of(), List.of(),
                    0L, 1_000_000L, 0L
            );
            when(diceGameService.play(eq(TEST_GUILD_ID), eq(TEST_USER_ID), any(DiceGame2Config.class), anyInt()))
                    .thenReturn(gameResult);

            when(currencyConfigRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            DiceGame2CommandHandler handler = new DiceGame2CommandHandler(
                    tokenService,
                    diceGameService,
                    configRepository,
                    currencyConfigRepository,
                    transactionService
            );

            SlashCommandInteractionEvent event = createMockSlashEventWithTokens("dice-game-2", playerSpecifiedTokens);

            // When
            handler.handle(event);

            // Then - verify player-specified token amount was used
            verify(transactionService).recordTransaction(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    -playerSpecifiedTokens,
                    newTokenBalance,
                    GameTokenTransaction.Source.DICE_GAME_2_PLAY,
                    null
            );
        }

        @Test
        @DisplayName("should reject tokens below minimum with warning message")
        void shouldRejectTokensBelowMinimum() {
            // Given
            long playerSpecifiedTokens = 2L;  // Below minimum of 5
            long previousTokens = 100L;

            StubGameTokenAccountRepository accountRepository =
                    new StubGameTokenAccountRepository(previousTokens);
            GameTokenService tokenService = new GameTokenService(accountRepository);
            DiceGame2Service diceGameService = mock(DiceGame2Service.class);
            ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository configRepository =
                    mock(ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository.class);
            GuildCurrencyConfigRepository currencyConfigRepository = mock(GuildCurrencyConfigRepository.class);
            GameTokenTransactionService transactionService = mock(GameTokenTransactionService.class);

            // Config allows 5-50 tokens
            DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);
            when(configRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

            DiceGame2CommandHandler handler = new DiceGame2CommandHandler(
                    tokenService,
                    diceGameService,
                    configRepository,
                    currencyConfigRepository,
                    transactionService
            );

            SlashCommandInteractionEvent event = createMockSlashEventWithTokens("dice-game-2", playerSpecifiedTokens);

            // When
            handler.handle(event);

            // Then - verify no transaction and no game played
            verifyNoInteractions(transactionService);
            verifyNoInteractions(diceGameService);
            // Warning message sent as ephemeral reply
            verify(event).reply(argThat((String msg) ->
                    msg.contains("5") && msg.contains("50")));
        }

        @Test
        @DisplayName("should reject tokens above maximum with warning message")
        void shouldRejectTokensAboveMaximum() {
            // Given
            long playerSpecifiedTokens = 60L;  // Above maximum of 50
            long previousTokens = 100L;

            StubGameTokenAccountRepository accountRepository =
                    new StubGameTokenAccountRepository(previousTokens);
            GameTokenService tokenService = new GameTokenService(accountRepository);
            DiceGame2Service diceGameService = mock(DiceGame2Service.class);
            ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository configRepository =
                    mock(ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository.class);
            GuildCurrencyConfigRepository currencyConfigRepository = mock(GuildCurrencyConfigRepository.class);
            GameTokenTransactionService transactionService = mock(GameTokenTransactionService.class);

            // Config allows 5-50 tokens
            DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);
            when(configRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

            DiceGame2CommandHandler handler = new DiceGame2CommandHandler(
                    tokenService,
                    diceGameService,
                    configRepository,
                    currencyConfigRepository,
                    transactionService
            );

            SlashCommandInteractionEvent event = createMockSlashEventWithTokens("dice-game-2", playerSpecifiedTokens);

            // When
            handler.handle(event);

            // Then - verify no transaction and no game played
            verifyNoInteractions(transactionService);
            verifyNoInteractions(diceGameService);
            // Warning message sent as ephemeral reply
            verify(event).reply(argThat((String msg) ->
                    msg.contains("5") && msg.contains("50")));
        }
    }

    /**
     * Creates a minimal mocked SlashCommandInteractionEvent.
     */
    private SlashCommandInteractionEvent createMockSlashEvent(String commandName) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        User user = mock(User.class);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);

        when(guild.getIdLong()).thenReturn(TEST_GUILD_ID);
        when(user.getIdLong()).thenReturn(TEST_USER_ID);

        when(event.getName()).thenReturn(commandName);
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getOption("tokens")).thenReturn(null);
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);

        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doAnswer(invocation -> null).when(replyAction).queue();

        return event;
    }

    /**
     * Creates a mocked SlashCommandInteractionEvent with a tokens option.
     */
    private SlashCommandInteractionEvent createMockSlashEventWithTokens(String commandName, long tokens) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        User user = mock(User.class);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);
        OptionMapping tokenOption = mock(OptionMapping.class);

        when(guild.getIdLong()).thenReturn(TEST_GUILD_ID);
        when(user.getIdLong()).thenReturn(TEST_USER_ID);

        when(event.getName()).thenReturn(commandName);
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_US);

        when(tokenOption.getAsLong()).thenReturn(tokens);
        when(event.getOption("tokens")).thenReturn(tokenOption);

        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doAnswer(invocation -> null).when(replyAction).queue();

        return event;
    }

    /**
     * Simple in-memory implementation of GameTokenAccountRepository for tests.
     */
    static class StubGameTokenAccountRepository implements GameTokenAccountRepository {
        private GameTokenAccount account;

        StubGameTokenAccountRepository(long initialTokens) {
            Instant now = Instant.now();
            this.account = new GameTokenAccount(TEST_GUILD_ID, TEST_USER_ID, initialTokens, now, now);
        }

        @Override
        public java.util.Optional<GameTokenAccount> findByGuildIdAndUserId(long guildId, long userId) {
            if (account.guildId() == guildId && account.userId() == userId) {
                return java.util.Optional.of(account);
            }
            return java.util.Optional.empty();
        }

        @Override
        public GameTokenAccount save(GameTokenAccount account) {
            this.account = account;
            return account;
        }

        @Override
        public GameTokenAccount findOrCreate(long guildId, long userId) {
            if (account == null || account.guildId() != guildId || account.userId() != userId) {
                account = GameTokenAccount.createNew(guildId, userId);
            }
            return account;
        }

        @Override
        public GameTokenAccount adjustTokens(long guildId, long userId, long amount) {
            account = account.withAdjustedTokens(amount);
            return account;
        }

        @Override
        public Result<GameTokenAccount, DomainError> tryAdjustTokens(long guildId, long userId, long amount) {
            GameTokenAccount updated = adjustTokens(guildId, userId, amount);
            return Result.ok(updated);
        }

        @Override
        public GameTokenAccount setTokens(long guildId, long userId, long newTokens) {
            Instant now = Instant.now();
            account = new GameTokenAccount(guildId, userId, newTokens, account.createdAt(), now);
            return account;
        }

        @Override
        public boolean deleteByGuildIdAndUserId(long guildId, long userId) {
            return false;
        }
    }
}
