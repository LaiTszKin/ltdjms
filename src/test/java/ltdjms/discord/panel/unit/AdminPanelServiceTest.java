package ltdjms.discord.panel.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.services.AIChannelRestrictionService;
import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.panel.commands.AdminPanelButtonHandler;
import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.DomainEventPublisher;

/** Unit tests for AdminPanelService. */
class AdminPanelServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  private BalanceService balanceService;
  private BalanceAdjustmentService balanceAdjustmentService;
  private GameTokenService gameTokenService;
  private GameTokenTransactionService transactionService;
  private DiceGame1ConfigRepository diceGame1ConfigRepository;
  private DiceGame2ConfigRepository diceGame2ConfigRepository;
  private CurrencyConfigService currencyConfigService;
  private DomainEventPublisher eventPublisher;
  private AIChannelRestrictionService aiChannelRestrictionService;
  private AdminPanelService adminPanelService;

  @BeforeEach
  void setUp() {
    balanceService = mock(BalanceService.class);
    balanceAdjustmentService = mock(BalanceAdjustmentService.class);
    gameTokenService = mock(GameTokenService.class);
    transactionService = mock(GameTokenTransactionService.class);
    diceGame1ConfigRepository = mock(DiceGame1ConfigRepository.class);
    diceGame2ConfigRepository = mock(DiceGame2ConfigRepository.class);
    currencyConfigService = mock(CurrencyConfigService.class);
    eventPublisher = mock(DomainEventPublisher.class);
    aiChannelRestrictionService = mock(AIChannelRestrictionService.class);

    adminPanelService =
        new AdminPanelService(
            balanceService,
            balanceAdjustmentService,
            gameTokenService,
            transactionService,
            diceGame1ConfigRepository,
            diceGame2ConfigRepository,
            currencyConfigService,
            eventPublisher,
            aiChannelRestrictionService);
  }

  @Nested
  @DisplayName("getCurrencyConfig")
  class GetCurrencyConfig {

    @Test
    @DisplayName("should return custom currency config when available")
    void shouldReturnCustomCurrencyConfig() {
      // Given - guild has custom currency "星幣" with icon "✨"
      GuildCurrencyConfig customConfig =
          GuildCurrencyConfig.createDefault(TEST_GUILD_ID).withUpdates("星幣", "✨");
      when(currencyConfigService.getConfig(TEST_GUILD_ID)).thenReturn(customConfig);

      // When
      GuildCurrencyConfig result = adminPanelService.getCurrencyConfig(TEST_GUILD_ID);

      // Then
      assertThat(result.currencyName()).isEqualTo("星幣");
      assertThat(result.currencyIcon()).isEqualTo("✨");
    }

    @Test
    @DisplayName("should return default currency config when no custom config exists")
    void shouldReturnDefaultCurrencyConfig() {
      // Given - no custom config, returns default
      GuildCurrencyConfig defaultConfig = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);
      when(currencyConfigService.getConfig(TEST_GUILD_ID)).thenReturn(defaultConfig);

      // When
      GuildCurrencyConfig result = adminPanelService.getCurrencyConfig(TEST_GUILD_ID);

      // Then
      assertThat(result.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
      assertThat(result.currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
    }
  }

  @Nested
  @DisplayName("getMemberBalance")
  class GetMemberBalance {

    @Test
    @DisplayName("should return balance when found")
    void shouldReturnBalanceWhenFound() {
      // Given
      BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 5000L, "Gold", "💰");
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(Result.ok(balanceView));

      // When
      Result<Long, DomainError> result =
          adminPanelService.getMemberBalance(TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("should propagate error when balance service fails")
    void shouldPropagateErrorWhenFails() {
      // Given
      DomainError error = DomainError.persistenceFailure("Connection failed", null);
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(Result.err(error));

      // When
      Result<Long, DomainError> result =
          adminPanelService.getMemberBalance(TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(result.isErr()).isTrue();
    }
  }

  @Nested
  @DisplayName("getMemberTokens")
  class GetMemberTokens {

    @Test
    @DisplayName("should return token balance")
    void shouldReturnTokenBalance() {
      // Given
      when(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(100L);

      // When
      long tokens = adminPanelService.getMemberTokens(TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(tokens).isEqualTo(100L);
    }
  }

  @Nested
  @DisplayName("adjustBalance")
  class AdjustBalance {

    @Test
    @DisplayName("should add balance when mode is add")
    void shouldAddBalance() {
      // Given
      BalanceAdjustmentService.BalanceAdjustmentResult adjustResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 1000L, 2000L, 1000L, "Gold", "💰");
      when(balanceAdjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, 1000L))
          .thenReturn(Result.ok(adjustResult));

      // When
      Result<AdminPanelService.BalanceAdjustmentResult, DomainError> result =
          adminPanelService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, "add", 1000L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().previousBalance()).isEqualTo(1000L);
      assertThat(result.getValue().newBalance()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("should deduct balance when mode is deduct")
    void shouldDeductBalance() {
      // Given
      BalanceAdjustmentService.BalanceAdjustmentResult adjustResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 2000L, 1500L, -500L, "Gold", "💰");
      when(balanceAdjustmentService.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, -500L))
          .thenReturn(Result.ok(adjustResult));

      // When
      Result<AdminPanelService.BalanceAdjustmentResult, DomainError> result =
          adminPanelService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, "deduct", 500L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().newBalance()).isEqualTo(1500L);
    }

    @Test
    @DisplayName("should set balance when mode is adjust")
    void shouldSetBalance() {
      // Given
      BalanceAdjustmentService.BalanceAdjustmentResult adjustResult =
          new BalanceAdjustmentService.BalanceAdjustmentResult(
              TEST_GUILD_ID, TEST_USER_ID, 1000L, 5000L, 4000L, "Gold", "💰");
      when(balanceAdjustmentService.tryAdjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, 5000L))
          .thenReturn(Result.ok(adjustResult));

      // When
      Result<AdminPanelService.BalanceAdjustmentResult, DomainError> result =
          adminPanelService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, "adjust", 5000L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().newBalance()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("should return error for invalid mode")
    void shouldReturnErrorForInvalidMode() {
      // When
      Result<AdminPanelService.BalanceAdjustmentResult, DomainError> result =
          adminPanelService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, "invalid", 1000L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("Unknown adjustment mode");
    }
  }

  @Nested
  @DisplayName("adjustTokens")
  class AdjustTokens {

    @Test
    @DisplayName("should add tokens when mode is add")
    void shouldAddTokens() {
      // Given
      when(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(50L);
      when(gameTokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 20L))
          .thenReturn(
              Result.ok(
                  new GameTokenService.TokenAdjustmentResult(
                      TEST_GUILD_ID, TEST_USER_ID, 50L, 70L, 20L)));

      // When
      Result<AdminPanelService.TokenAdjustmentResult, DomainError> result =
          adminPanelService.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, "add", 20L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().previousTokens()).isEqualTo(50L);
      assertThat(result.getValue().newTokens()).isEqualTo(70L);
    }

    @Test
    @DisplayName("should deduct tokens when mode is deduct")
    void shouldDeductTokens() {
      // Given
      when(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(50L);
      when(gameTokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, -20L))
          .thenReturn(
              Result.ok(
                  new GameTokenService.TokenAdjustmentResult(
                      TEST_GUILD_ID, TEST_USER_ID, 50L, 30L, -20L)));

      // When
      Result<AdminPanelService.TokenAdjustmentResult, DomainError> result =
          adminPanelService.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, "deduct", 20L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().newTokens()).isEqualTo(30L);
    }

    @Test
    @DisplayName("should set tokens when mode is adjust")
    void shouldSetTokens() {
      // Given
      when(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(50L);
      when(gameTokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 50L))
          .thenReturn(
              Result.ok(
                  new GameTokenService.TokenAdjustmentResult(
                      TEST_GUILD_ID, TEST_USER_ID, 50L, 100L, 50L)));

      // When
      Result<AdminPanelService.TokenAdjustmentResult, DomainError> result =
          adminPanelService.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, "adjust", 100L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().newTokens()).isEqualTo(100L);
    }

    @Test
    @DisplayName("should reject negative target balance in adjust mode")
    void shouldRejectNegativeTargetBalance() {
      // Given
      when(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(50L);

      // When
      Result<AdminPanelService.TokenAdjustmentResult, DomainError> result =
          adminPanelService.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, "adjust", -10L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("負數");
    }

    @Test
    @DisplayName("should record transaction after successful adjustment")
    void shouldRecordTransaction() {
      // Given
      when(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(50L);
      when(gameTokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 20L))
          .thenReturn(
              Result.ok(
                  new GameTokenService.TokenAdjustmentResult(
                      TEST_GUILD_ID, TEST_USER_ID, 50L, 70L, 20L)));

      // When
      adminPanelService.adjustTokens(TEST_GUILD_ID, TEST_USER_ID, "add", 20L);

      // Then
      verify(transactionService)
          .recordTransaction(
              eq(TEST_GUILD_ID),
              eq(TEST_USER_ID),
              eq(20L),
              eq(70L),
              eq(GameTokenTransaction.Source.ADMIN_ADJUSTMENT),
              any());
    }
  }

  @Nested
  @DisplayName("Game configuration")
  class GameConfiguration {

    @Test
    @DisplayName("should get dice-game-1 config")
    void shouldGetDiceGame1Config() {
      // Given
      DiceGame1Config config = DiceGame1Config.createDefault(TEST_GUILD_ID);
      when(diceGame1ConfigRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

      // When
      DiceGame1Config result = adminPanelService.getDiceGame1Config(TEST_GUILD_ID);

      // Then
      assertThat(result.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(result.minTokensPerPlay()).isEqualTo(DiceGame1Config.DEFAULT_MIN_TOKENS_PER_PLAY);
      assertThat(result.maxTokensPerPlay()).isEqualTo(DiceGame1Config.DEFAULT_MAX_TOKENS_PER_PLAY);
    }

    @Test
    @DisplayName("should get dice-game-2 config")
    void shouldGetDiceGame2Config() {
      // Given
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);
      when(diceGame2ConfigRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(config);

      // When
      DiceGame2Config result = adminPanelService.getDiceGame2Config(TEST_GUILD_ID);

      // Then
      assertThat(result.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(result.minTokensPerPlay()).isEqualTo(DiceGame2Config.DEFAULT_MIN_TOKENS_PER_PLAY);
      assertThat(result.maxTokensPerPlay()).isEqualTo(DiceGame2Config.DEFAULT_MAX_TOKENS_PER_PLAY);
    }

    @Test
    @DisplayName("should update dice-game-1 config token range")
    void shouldUpdateDiceGame1Config() {
      // Given
      DiceGame1Config oldConfig = DiceGame1Config.createDefault(TEST_GUILD_ID);
      DiceGame1Config updatedConfig = oldConfig.withTokensPerPlayRange(2L, 20L);
      when(diceGame1ConfigRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(oldConfig);
      when(diceGame1ConfigRepository.updateTokensPerPlayRange(TEST_GUILD_ID, 2L, 20L))
          .thenReturn(updatedConfig);

      // When
      Result<DiceGame1Config, DomainError> result =
          adminPanelService.updateDiceGame1Config(TEST_GUILD_ID, 2L, 20L, null);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().minTokensPerPlay()).isEqualTo(2L);
      assertThat(result.getValue().maxTokensPerPlay()).isEqualTo(20L);
    }

    @Test
    @DisplayName("should update dice-game-1 reward")
    void shouldUpdateDiceGame1Reward() {
      // Given
      DiceGame1Config oldConfig = DiceGame1Config.createDefault(TEST_GUILD_ID);
      DiceGame1Config updatedConfig = oldConfig.withRewardPerDiceValue(500_000L);
      when(diceGame1ConfigRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(oldConfig);
      when(diceGame1ConfigRepository.updateRewardPerDiceValue(TEST_GUILD_ID, 500_000L))
          .thenReturn(updatedConfig);

      // When
      Result<DiceGame1Config, DomainError> result =
          adminPanelService.updateDiceGame1Config(TEST_GUILD_ID, null, null, 500_000L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().rewardPerDiceValue()).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("should update dice-game-2 token range")
    void shouldUpdateDiceGame2TokenRange() {
      // Given
      DiceGame2Config oldConfig = DiceGame2Config.createDefault(TEST_GUILD_ID);
      DiceGame2Config updatedConfig = oldConfig.withTokensPerPlayRange(10L, 40L);
      when(diceGame2ConfigRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(oldConfig);
      when(diceGame2ConfigRepository.updateTokensPerPlayRange(TEST_GUILD_ID, 10L, 40L))
          .thenReturn(updatedConfig);

      // When
      Result<DiceGame2Config, DomainError> result =
          adminPanelService.updateDiceGame2Config(TEST_GUILD_ID, 10L, 40L, null, null, null, null);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().minTokensPerPlay()).isEqualTo(10L);
      assertThat(result.getValue().maxTokensPerPlay()).isEqualTo(40L);
    }

    @Test
    @DisplayName("should update dice-game-2 multipliers")
    void shouldUpdateDiceGame2Multipliers() {
      // Given
      DiceGame2Config oldConfig = DiceGame2Config.createDefault(TEST_GUILD_ID);
      DiceGame2Config updatedConfig = oldConfig.withMultipliers(200_000L, 40_000L);
      when(diceGame2ConfigRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(oldConfig);
      when(diceGame2ConfigRepository.updateMultipliers(TEST_GUILD_ID, 200_000L, 40_000L))
          .thenReturn(updatedConfig);

      // When
      Result<DiceGame2Config, DomainError> result =
          adminPanelService.updateDiceGame2Config(
              TEST_GUILD_ID, null, null, 200_000L, 40_000L, null, null);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().straightMultiplier()).isEqualTo(200_000L);
      assertThat(result.getValue().baseMultiplier()).isEqualTo(40_000L);
    }

    @Test
    @DisplayName("should update dice-game-2 bonuses")
    void shouldUpdateDiceGame2Bonuses() {
      // Given
      DiceGame2Config oldConfig = DiceGame2Config.createDefault(TEST_GUILD_ID);
      DiceGame2Config updatedConfig = oldConfig.withTripleBonuses(3_000_000L, 5_000_000L);
      when(diceGame2ConfigRepository.findOrCreateDefault(TEST_GUILD_ID)).thenReturn(oldConfig);
      when(diceGame2ConfigRepository.updateTripleBonuses(TEST_GUILD_ID, 3_000_000L, 5_000_000L))
          .thenReturn(updatedConfig);

      // When
      Result<DiceGame2Config, DomainError> result =
          adminPanelService.updateDiceGame2Config(
              TEST_GUILD_ID, null, null, null, null, 3_000_000L, 5_000_000L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().tripleLowBonus()).isEqualTo(3_000_000L);
      assertThat(result.getValue().tripleHighBonus()).isEqualTo(5_000_000L);
    }
  }

  @Nested
  @DisplayName("Result formatting")
  class ResultFormatting {

    @Test
    @DisplayName("should format balance adjustment result")
    void shouldFormatBalanceAdjustmentResult() {
      // Given
      AdminPanelService.BalanceAdjustmentResult result =
          new AdminPanelService.BalanceAdjustmentResult(1000L, 2000L, 1000L);

      // When
      String message = result.formatMessage("Gold", "💰");

      // Then
      assertThat(message).contains("增加");
      assertThat(message).contains("1,000");
      assertThat(message).contains("2,000");
      assertThat(message).contains("💰");
    }

    @Test
    @DisplayName("should format token adjustment result")
    void shouldFormatTokenAdjustmentResult() {
      // Given
      AdminPanelService.TokenAdjustmentResult result =
          new AdminPanelService.TokenAdjustmentResult(50L, 70L, 20L);

      // When
      String message = result.formatMessage();

      // Then
      assertThat(message).contains("增加");
      assertThat(message).contains("20");
      assertThat(message).contains("50");
      assertThat(message).contains("70");
    }
  }

  @Nested
  @DisplayName("AdminPanelButtonHandler constants")
  class AdminPanelButtonHandlerConstants {

    @Test
    @DisplayName("should define all button IDs")
    void shouldDefineAllButtonIds() {
      assertThat(AdminPanelButtonHandler.BUTTON_BALANCE).isEqualTo("admin_panel_balance");
      assertThat(AdminPanelButtonHandler.BUTTON_TOKENS).isEqualTo("admin_panel_tokens");
      assertThat(AdminPanelButtonHandler.BUTTON_GAMES).isEqualTo("admin_panel_games");
      assertThat(AdminPanelButtonHandler.BUTTON_BACK).isEqualTo("admin_panel_back");
      assertThat(AdminPanelButtonHandler.BUTTON_OPEN_BALANCE_MODAL)
          .isEqualTo("admin_open_balance_modal");
      assertThat(AdminPanelButtonHandler.BUTTON_OPEN_TOKEN_MODAL)
          .isEqualTo("admin_open_token_modal");
    }

    @Test
    @DisplayName("should define all modal IDs")
    void shouldDefineAllModalIds() {
      assertThat(AdminPanelButtonHandler.MODAL_BALANCE_ADJUST)
          .isEqualTo("admin_modal_balance_adjust");
      assertThat(AdminPanelButtonHandler.MODAL_TOKEN_ADJUST).isEqualTo("admin_modal_token_adjust");
      assertThat(AdminPanelButtonHandler.MODAL_GAME_1_TOKENS).isEqualTo("admin_modal_game1_tokens");
      assertThat(AdminPanelButtonHandler.MODAL_GAME_1_REWARD).isEqualTo("admin_modal_game1_reward");
      assertThat(AdminPanelButtonHandler.MODAL_GAME_2_TOKENS).isEqualTo("admin_modal_game2_tokens");
      assertThat(AdminPanelButtonHandler.MODAL_GAME_2_MULTIPLIERS)
          .isEqualTo("admin_modal_game2_multipliers");
      assertThat(AdminPanelButtonHandler.MODAL_GAME_2_BONUSES)
          .isEqualTo("admin_modal_game2_bonuses");
    }

    @Test
    @DisplayName("should define all select menu IDs")
    void shouldDefineAllSelectMenuIds() {
      assertThat(AdminPanelButtonHandler.SELECT_GAME).isEqualTo("admin_select_game");
      assertThat(AdminPanelButtonHandler.SELECT_BALANCE_USER)
          .isEqualTo("admin_select_balance_user");
      assertThat(AdminPanelButtonHandler.SELECT_BALANCE_MODE)
          .isEqualTo("admin_select_balance_mode");
      assertThat(AdminPanelButtonHandler.SELECT_TOKEN_USER).isEqualTo("admin_select_token_user");
      assertThat(AdminPanelButtonHandler.SELECT_TOKEN_MODE).isEqualTo("admin_select_token_mode");
      assertThat(AdminPanelButtonHandler.SELECT_GAME_SETTING)
          .isEqualTo("admin_select_game_setting");
    }
  }
}
