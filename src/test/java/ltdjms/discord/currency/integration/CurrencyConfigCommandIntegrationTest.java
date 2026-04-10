package ltdjms.discord.currency.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqGuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqMemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.currency.services.DefaultBalanceService;
import ltdjms.discord.currency.services.EmojiValidator;
import ltdjms.discord.currency.services.NoOpEmojiValidator;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.cache.DefaultCacheKeyGenerator;
import ltdjms.discord.shared.cache.NoOpCacheService;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * Integration tests for currency configuration commands. Verifies admin configuration updates and
 * their effect on balance views.
 */
@SuppressWarnings(
    "deprecation") // intentionally calls deprecated balance/config APIs to verify legacy flows
class CurrencyConfigCommandIntegrationTest extends PostgresIntegrationTestBase {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  private CurrencyConfigService configService;
  private DefaultBalanceService balanceService;
  private GuildCurrencyConfigRepository configRepository;
  private MemberCurrencyAccountRepository accountRepository;
  private EmojiValidator emojiValidator;

  @BeforeEach
  void setUp() {
    configRepository = new JooqGuildCurrencyConfigRepository(dslContext);
    accountRepository = new JooqMemberCurrencyAccountRepository(dslContext);
    emojiValidator = new NoOpEmojiValidator();
    DomainEventPublisher eventPublisher = new DomainEventPublisher();
    configService = new CurrencyConfigService(configRepository, emojiValidator, eventPublisher);
    CacheService cacheService = NoOpCacheService.getInstance();
    CacheKeyGenerator cacheKeyGenerator = new DefaultCacheKeyGenerator();
    balanceService =
        new DefaultBalanceService(
            accountRepository, configRepository, cacheService, cacheKeyGenerator);
  }

  @Test
  @DisplayName("should update currency config and reflect in balance view")
  void shouldUpdateConfigAndReflectInBalanceView() {
    // Given - default configuration
    BalanceView initialBalance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(initialBalance.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);

    // When - admin updates currency config
    configService.updateConfig(TEST_GUILD_ID, "Gold", "💰");

    // Then - balance view reflects new config
    BalanceView updatedBalance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(updatedBalance.currencyName()).isEqualTo("Gold");
    assertThat(updatedBalance.currencyIcon()).isEqualTo("💰");
  }

  @Test
  @DisplayName("should create new config for guild without existing config")
  void shouldCreateNewConfigForGuild() {
    // Given - no existing config
    assertThat(configRepository.findByGuildId(TEST_GUILD_ID)).isEmpty();

    // When
    GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, "Diamonds", "💎");

    // Then
    assertThat(updated.currencyName()).isEqualTo("Diamonds");
    assertThat(updated.currencyIcon()).isEqualTo("💎");
    assertThat(configRepository.findByGuildId(TEST_GUILD_ID)).isPresent();
  }

  @Test
  @DisplayName("should update existing config preserving unspecified values")
  void shouldUpdateExistingConfigPreservingUnspecifiedValues() {
    // Given - existing config
    configService.updateConfig(TEST_GUILD_ID, "Gold", "💰");

    // When - update only name
    GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, "Silver", null);

    // Then - icon should be preserved
    assertThat(updated.currencyName()).isEqualTo("Silver");
    assertThat(updated.currencyIcon()).isEqualTo("💰");
  }

  @Test
  @DisplayName("should isolate config between guilds")
  void shouldIsolateConfigBetweenGuilds() {
    // Given - two guilds with different configs
    long guild1 = TEST_GUILD_ID;
    long guild2 = TEST_GUILD_ID + 1;

    configService.updateConfig(guild1, "Gold", "💰");
    configService.updateConfig(guild2, "Silver", "🥈");

    // When
    BalanceView balance1 = balanceService.getBalance(guild1, TEST_USER_ID);
    BalanceView balance2 = balanceService.getBalance(guild2, TEST_USER_ID);

    // Then
    assertThat(balance1.currencyName()).isEqualTo("Gold");
    assertThat(balance1.currencyIcon()).isEqualTo("💰");
    assertThat(balance2.currencyName()).isEqualTo("Silver");
    assertThat(balance2.currencyIcon()).isEqualTo("🥈");
  }

  @Test
  @DisplayName("should show current config when queried")
  void shouldShowCurrentConfigWhenQueried() {
    // Given - set custom config
    configService.updateConfig(TEST_GUILD_ID, "Stars", "⭐");

    // When
    GuildCurrencyConfig config = configService.getConfig(TEST_GUILD_ID);

    // Then
    assertThat(config.currencyName()).isEqualTo("Stars");
    assertThat(config.currencyIcon()).isEqualTo("⭐");
  }

  @Test
  @DisplayName("should return default config when no custom config exists")
  void shouldReturnDefaultConfigWhenNoCustomConfigExists() {
    // When - query config for guild without custom config
    GuildCurrencyConfig config = configService.getConfig(TEST_GUILD_ID);

    // Then
    assertThat(config.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
    assertThat(config.currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
  }

  @Test
  @DisplayName("multiple config updates should only keep latest values")
  void multipleConfigUpdatesShouldOnlyKeepLatestValues() {
    // Given - multiple updates
    configService.updateConfig(TEST_GUILD_ID, "Gold", "💰");
    configService.updateConfig(TEST_GUILD_ID, "Silver", "🥈");
    configService.updateConfig(TEST_GUILD_ID, "Bronze", "🥉");

    // When
    GuildCurrencyConfig config = configService.getConfig(TEST_GUILD_ID);

    // Then - only latest values
    assertThat(config.currencyName()).isEqualTo("Bronze");
    assertThat(config.currencyIcon()).isEqualTo("🥉");
  }

  @Test
  @DisplayName("should accept max-length currency icon")
  void shouldAcceptMaxLengthCurrencyIcon() {
    // Given - icon at the maximum allowed length
    String longIcon = "X".repeat(GuildCurrencyConfig.MAX_ICON_LENGTH);

    // When - update config with max-length icon
    GuildCurrencyConfig updated =
        configService.updateConfig(TEST_GUILD_ID, "LongCurrency", longIcon);

    // Then - updated and persisted config should keep full icon value
    assertThat(updated.currencyIcon()).isEqualTo(longIcon);

    GuildCurrencyConfig reloaded = configService.getConfig(TEST_GUILD_ID);
    assertThat(reloaded.currencyIcon()).isEqualTo(longIcon);
  }

  @Test
  @DisplayName("should accept Discord custom emoji and persist correctly")
  void shouldAcceptDiscordCustomEmojiAndPersistCorrectly() {
    // Given - valid Discord custom emoji format
    String customEmoji = "<:gold_coin:1234567890123456789>";

    // When - update config with custom emoji
    GuildCurrencyConfig updated =
        configService.updateConfig(TEST_GUILD_ID, "Custom Gold", customEmoji);

    // Then - emoji should be persisted and retrieved correctly
    assertThat(updated.currencyIcon()).isEqualTo(customEmoji);

    GuildCurrencyConfig reloaded = configService.getConfig(TEST_GUILD_ID);
    assertThat(reloaded.currencyIcon()).isEqualTo(customEmoji);

    // And balance view should show the custom emoji
    BalanceView balance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(balance.currencyIcon()).isEqualTo(customEmoji);
  }

  @Test
  @DisplayName("should accept animated Discord custom emoji")
  void shouldAcceptAnimatedDiscordCustomEmoji() {
    // Given - animated custom emoji format
    String animatedEmoji = "<a:spinning_coin:9876543210987654321>";

    // When
    GuildCurrencyConfig updated =
        configService.updateConfig(TEST_GUILD_ID, "Spinning", animatedEmoji);

    // Then
    assertThat(updated.currencyIcon()).isEqualTo(animatedEmoji);

    GuildCurrencyConfig reloaded = configService.getConfig(TEST_GUILD_ID);
    assertThat(reloaded.currencyIcon()).isEqualTo(animatedEmoji);
  }

  @Test
  @DisplayName("should reject invalid Discord custom emoji format")
  void shouldRejectInvalidDiscordCustomEmojiFormat() {
    // Given - invalid emoji format (invalid ID)
    String invalidEmoji = "<:invalid:abc>";

    // When/Then - should reject
    assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, "Invalid", invalidEmoji))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Discord custom emoji");
  }

  @Test
  @DisplayName("should still accept Unicode emoji after custom emoji feature")
  void shouldStillAcceptUnicodeEmojiAfterCustomEmojiFeature() {
    // Given - various Unicode emoji types
    String[] unicodeEmojis = {"💰", "🪙", "👨‍👩‍👧‍👦", "🇺🇸", "👍🏽"};

    for (String emoji : unicodeEmojis) {
      // When
      GuildCurrencyConfig updated =
          configService.updateConfig(TEST_GUILD_ID, "Unicode Test", emoji);

      // Then
      assertThat(updated.currencyIcon()).isEqualTo(emoji);

      GuildCurrencyConfig reloaded = configService.getConfig(TEST_GUILD_ID);
      assertThat(reloaded.currencyIcon()).isEqualTo(emoji);
    }
  }

  @Test
  @DisplayName("should still accept emoji with text combination")
  void shouldStillAcceptEmojiWithTextCombination() {
    // Given - emoji + text combination
    String emojiWithText = "💎 Points";

    // When
    GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, "Gems", emojiWithText);

    // Then
    assertThat(updated.currencyIcon()).isEqualTo(emojiWithText);

    GuildCurrencyConfig reloaded = configService.getConfig(TEST_GUILD_ID);
    assertThat(reloaded.currencyIcon()).isEqualTo(emojiWithText);
  }
}
