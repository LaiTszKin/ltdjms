package ltdjms.discord.currency.unit;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.currency.services.EmojiValidator;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CurrencyConfigService.
 * Tests configuration retrieval, update, and validation logic.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation") // validates both deprecated getConfig/updateConfig and new Result-based APIs
class CurrencyConfigServiceTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;

    @Mock
    private GuildCurrencyConfigRepository configRepository;

    @Mock
    private EmojiValidator emojiValidator;

    private CurrencyConfigService configService;

    @BeforeEach
    void setUp() {
        configService = new CurrencyConfigService(configRepository, emojiValidator);
    }

    @Test
    @DisplayName("should return default config when none exists")
    void shouldReturnDefaultConfigWhenNoneExists() {
        // Given
        when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

        // When
        GuildCurrencyConfig config = configService.getConfig(TEST_GUILD_ID);

        // Then
        assertThat(config.guildId()).isEqualTo(TEST_GUILD_ID);
        assertThat(config.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
        assertThat(config.currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
    }

    @Test
    @DisplayName("should return existing config when it exists")
    void shouldReturnExistingConfigWhenExists() {
        // Given
        Instant now = Instant.now();
        GuildCurrencyConfig existingConfig = new GuildCurrencyConfig(
                TEST_GUILD_ID, "Gold", "💰", now, now);
        when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.of(existingConfig));

        // When
        GuildCurrencyConfig config = configService.getConfig(TEST_GUILD_ID);

        // Then
        assertThat(config.currencyName()).isEqualTo("Gold");
        assertThat(config.currencyIcon()).isEqualTo("💰");
    }

    @Test
    @DisplayName("should update config with new name")
    void shouldUpdateConfigWithNewName() {
        // Given
        when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());
        when(configRepository.saveOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, "Gold", null);

        // Then
        assertThat(updated.currencyName()).isEqualTo("Gold");
        assertThat(updated.currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
        verify(configRepository).saveOrUpdate(any());
    }

    @Test
    @DisplayName("should update config with new icon")
    void shouldUpdateConfigWithNewIcon() {
        // Given
        when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());
        when(configRepository.saveOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, null, "💰");

        // Then
        assertThat(updated.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
        assertThat(updated.currencyIcon()).isEqualTo("💰");
    }

    @Test
    @DisplayName("should update both name and icon")
    void shouldUpdateBothNameAndIcon() {
        // Given
        when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());
        when(configRepository.saveOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, "Diamonds", "💎");

        // Then
        assertThat(updated.currencyName()).isEqualTo("Diamonds");
        assertThat(updated.currencyIcon()).isEqualTo("💎");
    }

    @Test
    @DisplayName("should reject blank name")
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, "   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("should reject name exceeding maximum length")
    void shouldRejectNameExceedingMaxLength() {
        String tooLongName = "A".repeat(51);

        assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, tooLongName, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    @Test
    @DisplayName("should reject blank icon")
    void shouldRejectBlankIcon() {
        assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, null, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("should reject icon exceeding maximum length")
    void shouldRejectIconExceedingMaxLength() {
        String tooLongIcon = "A".repeat(65);

        assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, null, tooLongIcon))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
    }

    @Test
    @DisplayName("should preserve existing values when updating only one field")
    void shouldPreserveExistingValuesWhenUpdatingOneField() {
        // Given - existing config with custom values
        Instant now = Instant.now();
        GuildCurrencyConfig existingConfig = new GuildCurrencyConfig(
                TEST_GUILD_ID, "Gold", "💰", now, now);
        when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.of(existingConfig));
        when(configRepository.saveOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

        // When - update only name
        GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, "Silver", null);

        // Then
        assertThat(updated.currencyName()).isEqualTo("Silver");
        assertThat(updated.currencyIcon()).isEqualTo("💰"); // Preserved
    }

    @Test
    @DisplayName("should isolate config between guilds")
    void shouldIsolateConfigBetweenGuilds() {
        // Given - two different guilds
        long guild1 = TEST_GUILD_ID;
        long guild2 = TEST_GUILD_ID + 1;

        when(configRepository.findByGuildId(guild1)).thenReturn(Optional.empty());
        when(configRepository.findByGuildId(guild2)).thenReturn(Optional.empty());

        // When
        GuildCurrencyConfig config1 = configService.getConfig(guild1);
        GuildCurrencyConfig config2 = configService.getConfig(guild2);

        // Then
        assertThat(config1.guildId()).isEqualTo(guild1);
        assertThat(config2.guildId()).isEqualTo(guild2);
        verify(configRepository).findByGuildId(guild1);
        verify(configRepository).findByGuildId(guild2);
    }

    @Nested
    @DisplayName("Discord Custom Emoji Validation")
    class DiscordCustomEmojiValidation {

        @Test
        @DisplayName("should accept valid Discord custom emoji when validator returns true")
        void shouldAcceptValidDiscordCustomEmoji() {
            // Given
            String customEmoji = "<:gold_coin:1234567890123456789>";
            when(emojiValidator.isValidCustomEmoji(customEmoji)).thenReturn(true);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());
            when(configRepository.saveOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, null, customEmoji);

            // Then
            assertThat(updated.currencyIcon()).isEqualTo(customEmoji);
            verify(emojiValidator).isValidCustomEmoji(customEmoji);
        }

        @Test
        @DisplayName("should accept valid animated Discord custom emoji")
        void shouldAcceptValidAnimatedDiscordCustomEmoji() {
            // Given
            String animatedEmoji = "<a:spinning_coin:9876543210987654321>";
            when(emojiValidator.isValidCustomEmoji(animatedEmoji)).thenReturn(true);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());
            when(configRepository.saveOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, null, animatedEmoji);

            // Then
            assertThat(updated.currencyIcon()).isEqualTo(animatedEmoji);
        }

        @Test
        @DisplayName("should reject invalid Discord custom emoji when validator returns false")
        void shouldRejectInvalidDiscordCustomEmoji() {
            // Given - emoji with invalid ID
            String invalidEmoji = "<:invalid:abc>";
            when(emojiValidator.isValidCustomEmoji(invalidEmoji)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, null, invalidEmoji))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid Discord custom emoji");

            verify(configRepository, never()).saveOrUpdate(any());
        }

        @Test
        @DisplayName("should not validate non-custom-emoji strings")
        void shouldNotValidateNonCustomEmojiStrings() {
            // Given - regular Unicode emoji, not a custom emoji format
            String unicodeEmoji = "💰";
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());
            when(configRepository.saveOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, null, unicodeEmoji);

            // Then - validator should not be called for non-custom emoji
            verify(emojiValidator, never()).isValidCustomEmoji(any());
            assertThat(updated.currencyIcon()).isEqualTo(unicodeEmoji);
        }

        @Test
        @DisplayName("should not validate text-only icons")
        void shouldNotValidateTextOnlyIcons() {
            // Given - pure text icon
            String textIcon = "Gold Coins";
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());
            when(configRepository.saveOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            GuildCurrencyConfig updated = configService.updateConfig(TEST_GUILD_ID, null, textIcon);

            // Then - validator should not be called for plain text
            verify(emojiValidator, never()).isValidCustomEmoji(any());
            assertThat(updated.currencyIcon()).isEqualTo(textIcon);
        }

        @Test
        @DisplayName("should reject custom emoji format with malformed ID")
        void shouldRejectCustomEmojiWithMalformedId() {
            // Given - looks like custom emoji but has invalid ID
            String malformedEmoji = "<:coin:not_a_number>";
            when(emojiValidator.isValidCustomEmoji(malformedEmoji)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, null, malformedEmoji))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid Discord custom emoji");
        }
    }

    @Nested
    @DisplayName("Result-based API (tryGetConfig / tryUpdateConfig)")
    class ResultBasedApiTests {

        @Test
        @DisplayName("tryGetConfig 應在設定存在時回傳 Ok")
        void tryGetConfigShouldReturnOkWhenConfigExists() {
            Instant now = Instant.now();
            GuildCurrencyConfig existingConfig = new GuildCurrencyConfig(
                    TEST_GUILD_ID, "Gold", "💰", now, now);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.of(existingConfig));

            Result<GuildCurrencyConfig, DomainError> result = configService.tryGetConfig(TEST_GUILD_ID);

            assertThat(result.isOk()).isTrue();
            assertThat(result.getValue()).isEqualTo(existingConfig);
        }

        @Test
        @DisplayName("tryGetConfig 應在沒有設定時回傳預設值")
        void tryGetConfigShouldReturnDefaultWhenMissing() {
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            Result<GuildCurrencyConfig, DomainError> result = configService.tryGetConfig(TEST_GUILD_ID);

            assertThat(result.isOk()).isTrue();
            GuildCurrencyConfig config = result.getValue();
            assertThat(config.guildId()).isEqualTo(TEST_GUILD_ID);
            assertThat(config.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
            assertThat(config.currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
        }

        @Test
        @DisplayName("tryGetConfig 應將 RepositoryException 映射為 PERSISTENCE_FAILURE")
        void tryGetConfigShouldMapRepositoryExceptionToPersistenceFailure() {
            when(configRepository.findByGuildId(TEST_GUILD_ID))
                    .thenThrow(new RepositoryException("DB error"));

            Result<GuildCurrencyConfig, DomainError> result = configService.tryGetConfig(TEST_GUILD_ID);

            assertThat(result.isErr()).isTrue();
            DomainError error = result.getError();
            assertThat(error.category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
            assertThat(error.message()).contains("Failed to retrieve currency configuration");
        }

        @Test
        @DisplayName("tryUpdateConfig 應在輸入合法時回傳 Ok 並更新數值")
        void tryUpdateConfigShouldReturnOkForValidInput() {
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());
            when(configRepository.saveOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

            Result<GuildCurrencyConfig, DomainError> result =
                    configService.tryUpdateConfig(TEST_GUILD_ID, "Gold", "💰");

            assertThat(result.isOk()).isTrue();
            GuildCurrencyConfig updated = result.getValue();
            assertThat(updated.currencyName()).isEqualTo("Gold");
            assertThat(updated.currencyIcon()).isEqualTo("💰");
            // 非 custom emoji，不應呼叫 validator
            verify(emojiValidator, never()).isValidCustomEmoji(any());
        }

        @Test
        @DisplayName("tryUpdateConfig 應對空白名稱回傳 INVALID_INPUT")
        void tryUpdateConfigShouldReturnInvalidInputForBlankName() {
            Result<GuildCurrencyConfig, DomainError> result =
                    configService.tryUpdateConfig(TEST_GUILD_ID, "   ", "💰");

            assertThat(result.isErr()).isTrue();
            DomainError error = result.getError();
            assertThat(error.category()).isEqualTo(DomainError.Category.INVALID_INPUT);
            assertThat(error.message()).contains("cannot be blank");
        }

        @Test
        @DisplayName("tryUpdateConfig 應對過長名稱回傳 INVALID_INPUT")
        void tryUpdateConfigShouldReturnInvalidInputForTooLongName() {
            String tooLongName = "A".repeat(GuildCurrencyConfig.MAX_NAME_LENGTH + 1);

            Result<GuildCurrencyConfig, DomainError> result =
                    configService.tryUpdateConfig(TEST_GUILD_ID, tooLongName, "💰");

            assertThat(result.isErr()).isTrue();
            DomainError error = result.getError();
            assertThat(error.category()).isEqualTo(DomainError.Category.INVALID_INPUT);
            assertThat(error.message()).contains("cannot exceed");
        }

        @Test
        @DisplayName("tryUpdateConfig 應對 validator 拒絕的自訂表情回傳 INVALID_INPUT")
        void tryUpdateConfigShouldReturnInvalidInputForInvalidCustomEmoji() {
            String invalidEmoji = "<:invalid:abc>";
            when(emojiValidator.isValidCustomEmoji(invalidEmoji)).thenReturn(false);

            Result<GuildCurrencyConfig, DomainError> result =
                    configService.tryUpdateConfig(TEST_GUILD_ID, null, invalidEmoji);

            assertThat(result.isErr()).isTrue();
            DomainError error = result.getError();
            assertThat(error.category()).isEqualTo(DomainError.Category.INVALID_INPUT);
            assertThat(error.message()).contains("Invalid Discord custom emoji");
            verify(configRepository, never()).saveOrUpdate(any());
        }

        @Test
        @DisplayName("tryUpdateConfig 應在 repository 發生錯誤時回傳 PERSISTENCE_FAILURE")
        void tryUpdateConfigShouldReturnPersistenceFailureOnRepositoryError() {
            Instant now = Instant.now();
            GuildCurrencyConfig existingConfig = new GuildCurrencyConfig(
                    TEST_GUILD_ID, "Gold", "💰", now, now);

            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.of(existingConfig));
            when(configRepository.saveOrUpdate(any()))
                    .thenThrow(new RepositoryException("DB error"));

            Result<GuildCurrencyConfig, DomainError> result =
                    configService.tryUpdateConfig(TEST_GUILD_ID, "Silver", null);

            assertThat(result.isErr()).isTrue();
            DomainError error = result.getError();
            assertThat(error.category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
            assertThat(error.message()).contains("Failed to update currency configuration");
        }

        @Test
        @DisplayName("tryUpdateConfig 應對非自訂表情圖示略過 JDA 驗證")
        void tryUpdateConfigShouldSkipValidatorForNonCustomEmoji() {
            String textIcon = "Gold Coins";
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());
            when(configRepository.saveOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

            Result<GuildCurrencyConfig, DomainError> result =
                    configService.tryUpdateConfig(TEST_GUILD_ID, null, textIcon);

            assertThat(result.isOk()).isTrue();
            GuildCurrencyConfig updated = result.getValue();
            assertThat(updated.currencyIcon()).isEqualTo(textIcon);
            verify(emojiValidator, never()).isValidCustomEmoji(any());
        }
    }
}
