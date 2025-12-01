package ltdjms.discord.panel.unit;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService.TransactionPage;
import ltdjms.discord.panel.commands.UserPanelButtonHandler;
import ltdjms.discord.panel.services.UserPanelService;
import ltdjms.discord.panel.services.UserPanelView;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserPanelService.
 * Tests the service logic without JDA event handling.
 */
class UserPanelServiceTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final long TEST_USER_ID = 987654321098765432L;

    private BalanceService balanceService;
    private GameTokenService gameTokenService;
    private GameTokenTransactionService gameTokenTransactionService;
    private CurrencyTransactionService currencyTransactionService;
    private UserPanelService userPanelService;

    @BeforeEach
    void setUp() {
        balanceService = mock(BalanceService.class);
        gameTokenService = mock(GameTokenService.class);
        gameTokenTransactionService = mock(GameTokenTransactionService.class);
        currencyTransactionService = mock(CurrencyTransactionService.class);
        userPanelService = new UserPanelService(
                balanceService, gameTokenService, gameTokenTransactionService, currencyTransactionService);
    }

    @Nested
    @DisplayName("getUserPanelView")
    class GetUserPanelView {

        @Test
        @DisplayName("should return view with currency balance and game tokens when both exist")
        void shouldReturnViewWithBothBalances() {
            // Given
            BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1000L, "Gold", "💰");
            when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(Result.ok(balanceView));
            when(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(50L);

            // When
            Result<UserPanelView, DomainError> result = userPanelService.getUserPanelView(TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isOk()).isTrue();
            UserPanelView view = result.getValue();
            assertThat(view.guildId()).isEqualTo(TEST_GUILD_ID);
            assertThat(view.userId()).isEqualTo(TEST_USER_ID);
            assertThat(view.currencyBalance()).isEqualTo(1000L);
            assertThat(view.currencyName()).isEqualTo("Gold");
            assertThat(view.currencyIcon()).isEqualTo("💰");
            assertThat(view.gameTokens()).isEqualTo(50L);
        }

        @Test
        @DisplayName("should return view with zero game tokens when account does not exist")
        void shouldReturnViewWithZeroGameTokens() {
            // Given
            BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 500L, "Diamonds", "💎");
            when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(Result.ok(balanceView));
            when(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(0L);

            // When
            Result<UserPanelView, DomainError> result = userPanelService.getUserPanelView(TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isOk()).isTrue();
            assertThat(result.getValue().gameTokens()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should propagate error when balance service fails")
        void shouldPropagateErrorWhenBalanceServiceFails() {
            // Given
            DomainError error = DomainError.persistenceFailure("Database connection failed", null);
            when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(Result.err(error));

            // When
            Result<UserPanelView, DomainError> result = userPanelService.getUserPanelView(TEST_GUILD_ID, TEST_USER_ID);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
        }
    }

    @Nested
    @DisplayName("UserPanelView formatting")
    class UserPanelViewFormatting {

        @Test
        @DisplayName("should format embed title correctly")
        void shouldFormatEmbedTitleCorrectly() {
            // Given
            UserPanelView view = new UserPanelView(
                    TEST_GUILD_ID, TEST_USER_ID, 1000L, "Gold", "💰", 50L);

            // When
            String title = view.getEmbedTitle();

            // Then
            assertThat(title).isEqualTo("個人面板");
        }

        @Test
        @DisplayName("should format currency field correctly")
        void shouldFormatCurrencyFieldCorrectly() {
            // Given
            UserPanelView view = new UserPanelView(
                    TEST_GUILD_ID, TEST_USER_ID, 1234567L, "Gold", "💰", 50L);

            // When
            String currencyField = view.formatCurrencyField();

            // Then
            assertThat(currencyField).contains("💰");
            assertThat(currencyField).contains("1,234,567");
            assertThat(currencyField).contains("Gold");
        }

        @Test
        @DisplayName("should format game tokens field correctly")
        void shouldFormatGameTokensFieldCorrectly() {
            // Given
            UserPanelView view = new UserPanelView(
                    TEST_GUILD_ID, TEST_USER_ID, 1000L, "Gold", "💰", 999L);

            // When
            String tokenField = view.formatGameTokensField();

            // Then
            assertThat(tokenField).contains("🎮");
            assertThat(tokenField).contains("999");
            assertThat(tokenField).contains("遊戲代幣");
        }

        @Test
        @DisplayName("should format zero balances correctly")
        void shouldFormatZeroBalancesCorrectly() {
            // Given
            UserPanelView view = new UserPanelView(
                    TEST_GUILD_ID, TEST_USER_ID, 0L, "Coins", "🪙", 0L);

            // When
            String currencyField = view.formatCurrencyField();
            String tokenField = view.formatGameTokensField();

            // Then
            assertThat(currencyField).contains("0");
            assertThat(tokenField).contains("0");
        }

        @Test
        @DisplayName("should include currency name in currency field name")
        void shouldIncludeCurrencyNameInFieldName() {
            // Given - user has custom currency "星幣" with icon "✨"
            UserPanelView view = new UserPanelView(
                    TEST_GUILD_ID, TEST_USER_ID, 1000L, "星幣", "✨", 50L);

            // When
            String fieldName = view.getCurrencyFieldName();

            // Then - field name should include the custom currency name
            assertThat(fieldName).contains("星幣");
            assertThat(fieldName).contains("餘額");
        }

        @Test
        @DisplayName("should return currency icon for button display")
        void shouldReturnCurrencyIconForButton() {
            // Given - user has custom currency with icon "✨"
            UserPanelView view = new UserPanelView(
                    TEST_GUILD_ID, TEST_USER_ID, 1000L, "星幣", "✨", 50L);

            // When
            String buttonLabel = view.getCurrencyHistoryButtonLabel();

            // Then - button label should use the custom currency icon
            assertThat(buttonLabel).contains("✨");
            assertThat(buttonLabel).contains("流水");
        }
    }

    @Nested
    @DisplayName("getTokenTransactionPage")
    class GetTokenTransactionPage {

        @Test
        @DisplayName("should return transaction page from transaction service")
        void shouldReturnTransactionPage() {
            // Given
            TransactionPage expectedPage = new TransactionPage(
                    Collections.emptyList(), 1, 1, 0, 10);
            when(gameTokenTransactionService.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 1, 10))
                    .thenReturn(expectedPage);

            // When
            TransactionPage result = userPanelService.getTokenTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 1);

            // Then
            assertThat(result).isEqualTo(expectedPage);
        }
    }

    @Nested
    @DisplayName("getCurrencyTransactionPage")
    class GetCurrencyTransactionPage {

        @Test
        @DisplayName("should return currency transaction page from transaction service")
        void shouldReturnCurrencyTransactionPage() {
            // Given
            CurrencyTransactionService.TransactionPage expectedPage =
                    new CurrencyTransactionService.TransactionPage(
                            Collections.emptyList(), 1, 1, 0, 10);
            when(currencyTransactionService.getTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 1, 10))
                    .thenReturn(expectedPage);

            // When
            CurrencyTransactionService.TransactionPage result =
                    userPanelService.getCurrencyTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 1);

            // Then
            assertThat(result).isEqualTo(expectedPage);
        }
    }

    @Nested
    @DisplayName("UserPanelButtonHandler constants")
    class UserPanelButtonHandlerConstants {

        @Test
        @DisplayName("should define back to panel button ID")
        void shouldDefineBackToPanelButtonId() {
            // Verify the constant is defined and has the expected value
            assertThat(UserPanelButtonHandler.BUTTON_BACK_TO_PANEL)
                    .isEqualTo("user_panel_back");
        }

        @Test
        @DisplayName("should define button prefix for token pages")
        void shouldDefineTokenPageButtonPrefix() {
            assertThat(UserPanelButtonHandler.BUTTON_PREFIX_TOKEN_PAGE)
                    .isEqualTo("user_panel_token_page_");
        }

        @Test
        @DisplayName("should define button prefix for currency pages")
        void shouldDefineCurrencyPageButtonPrefix() {
            assertThat(UserPanelButtonHandler.BUTTON_PREFIX_CURRENCY_PAGE)
                    .isEqualTo("user_panel_currency_page_");
        }
    }
}
