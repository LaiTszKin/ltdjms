package ltdjms.discord.currency.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

/**
 * Unit tests for SlashCommandListener command definition generation and command sync behavior.
 * Verifies that buildAllCommandDefinitions produces correct command data and that the command sync
 * logic behaves correctly.
 *
 * <p>Note: Legacy commands (/balance, /adjust-balance, /game-token-adjust, /dice-game-1-config,
 * /dice-game-2-config) have been removed.
 */
class SlashCommandListenerTest {

  private SlashCommandListener listener;

  @BeforeEach
  void setUp() {
    // Create listener with null handlers for command definition tests
    // (handlers are not used when building command definitions)
    listener = new SlashCommandListener(null, null, null, null, null, null, null);
  }

  @Nested
  @DisplayName("buildAllCommandDefinitions")
  class BuildAllCommandDefinitionsTests {

    @Test
    @DisplayName("should return exactly 7 command definitions")
    void shouldReturnExpectedNumberOfCommands() {
      List<SlashCommandData> commands = listener.buildAllCommandDefinitions();

      assertThat(commands).hasSize(7);
    }

    @Test
    @DisplayName("should include all expected command names")
    void shouldIncludeAllExpectedCommandNames() {
      List<SlashCommandData> commands = listener.buildAllCommandDefinitions();
      Set<String> commandNames =
          commands.stream().map(SlashCommandData::getName).collect(Collectors.toSet());

      assertThat(commandNames)
          .containsExactlyInAnyOrder(
              "currency-config",
              "dice-game-1",
              "dice-game-2",
              "user-panel",
              "admin-panel",
              "shop",
              "dispatch-panel");
    }

    @Test
    @DisplayName("should not include legacy commands")
    void shouldNotIncludeLegacyCommands() {
      List<SlashCommandData> commands = listener.buildAllCommandDefinitions();
      Set<String> commandNames =
          commands.stream().map(SlashCommandData::getName).collect(Collectors.toSet());

      // Verify legacy commands are not included
      assertThat(commandNames)
          .doesNotContain(
              "balance",
              "adjust-balance",
              "game-token-adjust",
              "dice-game-1-config",
              "dice-game-2-config");
    }

    @Test
    @DisplayName("should have zh-TW localization for all commands")
    void shouldHaveZhTwLocalizationForAllCommands() {
      List<SlashCommandData> commands = listener.buildAllCommandDefinitions();

      for (SlashCommandData command : commands) {
        assertThat(command.getNameLocalizations().toMap())
            .as("Command '%s' should have zh-TW name localization", command.getName())
            .containsKey(DiscordLocale.CHINESE_TAIWAN);

        assertThat(command.getDescriptionLocalizations().toMap())
            .as("Command '%s' should have zh-TW description localization", command.getName())
            .containsKey(DiscordLocale.CHINESE_TAIWAN);
      }
    }

    @Test
    @DisplayName("should return the same definitions on multiple calls")
    void shouldReturnConsistentDefinitions() {
      List<SlashCommandData> firstCall = listener.buildAllCommandDefinitions();
      List<SlashCommandData> secondCall = listener.buildAllCommandDefinitions();

      assertThat(firstCall).hasSize(secondCall.size());

      Set<String> firstNames =
          firstCall.stream().map(SlashCommandData::getName).collect(Collectors.toSet());
      Set<String> secondNames =
          secondCall.stream().map(SlashCommandData::getName).collect(Collectors.toSet());

      assertThat(firstNames).isEqualTo(secondNames);
    }

    @Test
    @DisplayName("should not include any sync-commands type command")
    void shouldNotIncludeSyncCommandsCommand() {
      List<SlashCommandData> commands = listener.buildAllCommandDefinitions();
      Set<String> commandNames =
          commands.stream().map(SlashCommandData::getName).collect(Collectors.toSet());

      // Verify no sync-related commands are exposed to users
      assertThat(commandNames).doesNotContain("sync-commands", "sync", "register-commands");
    }
  }

  @Nested
  @DisplayName("Command constants")
  class CommandConstantsTests {

    @Test
    @DisplayName("should have correct command name constants")
    void shouldHaveCorrectCommandNameConstants() {
      // Verify command constants match the actual command definitions
      assertThat(SlashCommandListener.CMD_CURRENCY_CONFIG).isEqualTo("currency-config");
      assertThat(SlashCommandListener.CMD_DICE_GAME_1).isEqualTo("dice-game-1");
      assertThat(SlashCommandListener.CMD_DICE_GAME_2).isEqualTo("dice-game-2");
      assertThat(SlashCommandListener.CMD_USER_PANEL).isEqualTo("user-panel");
      assertThat(SlashCommandListener.CMD_ADMIN_PANEL).isEqualTo("admin-panel");
      assertThat(SlashCommandListener.CMD_SHOP).isEqualTo("shop");
      assertThat(SlashCommandListener.CMD_DISPATCH_PANEL).isEqualTo("dispatch-panel");
    }

    @Test
    @DisplayName("should have exactly 7 command constants matching definitions")
    void shouldHaveMatchingCommandCount() {
      List<SlashCommandData> commands = listener.buildAllCommandDefinitions();

      // There should be exactly 7 commands
      assertThat(commands).hasSize(7);

      // Each command constant should have a matching definition
      Set<String> commandNames =
          commands.stream().map(SlashCommandData::getName).collect(Collectors.toSet());

      assertThat(commandNames)
          .contains(
              SlashCommandListener.CMD_CURRENCY_CONFIG,
              SlashCommandListener.CMD_DICE_GAME_1,
              SlashCommandListener.CMD_DICE_GAME_2,
              SlashCommandListener.CMD_USER_PANEL,
              SlashCommandListener.CMD_ADMIN_PANEL,
              SlashCommandListener.CMD_SHOP,
              SlashCommandListener.CMD_DISPATCH_PANEL);
    }
  }

  @Nested
  @DisplayName("registerCommands")
  class RegisterCommandsTests {

    @Test
    @DisplayName("should clear global commands before syncing guild commands")
    void shouldClearGlobalCommandsBeforeSyncingGuildCommands() {
      // Arrange
      JDA jda = mock(JDA.class);
      Guild guild1 = mock(Guild.class);
      Guild guild2 = mock(Guild.class);

      CommandListUpdateAction globalUpdateAction =
          mock(CommandListUpdateAction.class, RETURNS_SELF);
      CommandListUpdateAction guild1UpdateAction =
          mock(CommandListUpdateAction.class, RETURNS_SELF);
      CommandListUpdateAction guild2UpdateAction =
          mock(CommandListUpdateAction.class, RETURNS_SELF);

      when(jda.getGuilds()).thenReturn(List.of(guild1, guild2));
      when(jda.updateCommands()).thenReturn(globalUpdateAction);
      when(guild1.updateCommands()).thenReturn(guild1UpdateAction);
      when(guild2.updateCommands()).thenReturn(guild2UpdateAction);

      // Act
      listener.registerCommands(jda);

      // Assert: global commands are cleared exactly once
      verify(jda, times(1)).updateCommands();
      verify(globalUpdateAction, times(1)).queue();

      // Assert: each guild receives a single sync call
      verify(guild1, times(1)).updateCommands();
      verify(guild2, times(1)).updateCommands();
      verify(guild1UpdateAction, times(1)).addCommands(org.mockito.Mockito.anyList());
      verify(guild2UpdateAction, times(1)).addCommands(org.mockito.Mockito.anyList());

      // Assert: global clearing happens before guild syncs to avoid duplicates
      var order = inOrder(jda, globalUpdateAction, guild1, guild2);
      order.verify(jda).updateCommands();
      order.verify(globalUpdateAction).queue();
      order.verify(guild1).updateCommands();
      order.verify(guild2).updateCommands();
    }
  }
}
