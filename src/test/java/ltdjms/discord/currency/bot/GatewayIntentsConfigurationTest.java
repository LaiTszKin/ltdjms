package ltdjms.discord.currency.bot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Meta-test to guard against accidentally enabling privileged gateway intents
 * (such as GUILD_MEMBERS) without an explicit configuration mechanism.
 *
 * The recent production issue showed that requesting GUILD_MEMBERS without
 * enabling the corresponding privileged intent on the Discord developer portal
 * causes the bot to fail at startup with CloseCode(4014) DISALLOWED_INTENTS.
 *
 * This test simply asserts that the main bot bootstrap class does not
 * hard-code GUILD_MEMBERS, so the bot can run with the default non-privileged
 * intents out of the box.
 */
class GatewayIntentsConfigurationTest {

    @Test
    @DisplayName("DiscordCurrencyBot should not hard-code GUILD_MEMBERS privileged intent")
    void shouldNotHardCodeGuildMembersIntent() throws IOException {
        // Read the source file directly to verify configuration choices.
        Path sourcePath = Path.of("src/main/java/ltdjms/discord/currency/bot/DiscordCurrencyBot.java");
        String source = Files.readString(sourcePath);

        assertThat(source)
                .as("DiscordCurrencyBot should not directly enable GUILD_MEMBERS intent " +
                        "to avoid DISALLOWED_INTENTS (4014) errors on startup")
                .doesNotContain("GatewayIntent.GUILD_MEMBERS");
    }
}

