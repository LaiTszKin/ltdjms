package ltdjms.discord.currency.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Meta-test guarding privileged gateway intent configuration.
 *
 * <p>Membership settlement requires {@code GUILD_MEMBERS} to record join dates. Requesting this
 * intent without enabling Server Members Intent on the Discord developer portal causes startup
 * failure with CloseCode(4014) DISALLOWED_INTENTS, so configuration docs must mention the
 * requirement.
 */
class GatewayIntentsConfigurationTest {

  @Test
  @DisplayName("DiscordCurrencyBot enables GUILD_MEMBERS and docs mention Server Members Intent")
  void shouldEnableGuildMembersIntentWithDocumentation() throws IOException {
    Path botSource = Path.of("src/main/java/ltdjms/discord/currency/bot/DiscordCurrencyBot.java");
    Path configDoc = Path.of("docs/configuration.md");

    String bot = Files.readString(botSource);
    String configuration = Files.readString(configDoc);

    assertThat(bot)
        .as("DiscordCurrencyBot must enable GUILD_MEMBERS for membership join tracking")
        .contains("GatewayIntent.GUILD_MEMBERS");

    assertThat(configuration)
        .as("docs/configuration.md must document Server Members Intent requirement")
        .contains("GUILD_MEMBERS")
        .contains("Server Members Intent");
  }
}
