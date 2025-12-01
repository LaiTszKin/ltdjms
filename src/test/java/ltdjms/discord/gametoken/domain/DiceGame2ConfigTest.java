package ltdjms.discord.gametoken.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiceGame2ConfigTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;

    @Test
    @DisplayName("should not allow negative tokensPerPlay")
    void shouldRejectNegativeTokensPerPlay() {
        assertThatThrownBy(() -> new DiceGame2Config(TEST_GUILD_ID, -1L, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokensPerPlay cannot be negative");
    }

    @Test
    @DisplayName("createDefault should use DEFAULT_TOKENS_PER_PLAY and set timestamps")
    void createDefaultShouldUseDefaults() {
        DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

        assertThat(config.guildId()).isEqualTo(TEST_GUILD_ID);
        assertThat(config.tokensPerPlay()).isEqualTo(DiceGame2Config.DEFAULT_TOKENS_PER_PLAY);
        assertThat(config.createdAt()).isNotNull();
        assertThat(config.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("withTokensPerPlay should update tokensPerPlay and updatedAt but keep createdAt")
    void withTokensPerPlayShouldUpdateValueAndTimestamp() {
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2024-06-01T00:00:00Z");
        DiceGame2Config original = new DiceGame2Config(TEST_GUILD_ID, 1L, createdAt, updatedAt);

        DiceGame2Config updated = original.withTokensPerPlay(5L);

        assertThat(updated.guildId()).isEqualTo(TEST_GUILD_ID);
        assertThat(updated.tokensPerPlay()).isEqualTo(5L);
        assertThat(updated.createdAt()).isEqualTo(createdAt);
        assertThat(updated.updatedAt()).isAfter(updatedAt);
    }
}

