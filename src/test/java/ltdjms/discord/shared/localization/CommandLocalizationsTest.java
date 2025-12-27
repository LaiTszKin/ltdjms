package ltdjms.discord.shared.localization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.interactions.DiscordLocale;

/**
 * Unit tests for CommandLocalizations to verify zh-TW localization mappings for all currency and
 * game-related slash commands.
 */
class CommandLocalizationsTest {

  @Nested
  @DisplayName("Command name localizations")
  class CommandNameLocalizations {

    @Test
    @DisplayName("should provide zh-TW localization for /balance command")
    void shouldProvideZhTwLocalizationForBalanceCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("balance");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("餘額");
    }

    @Test
    @DisplayName("should provide zh-TW localization for /currency-config command")
    void shouldProvideZhTwLocalizationForCurrencyConfigCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("currency-config");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("貨幣設定");
    }

    @Test
    @DisplayName("should provide zh-TW localization for /adjust-balance command")
    void shouldProvideZhTwLocalizationForAdjustBalanceCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("adjust-balance");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("調整餘額");
    }

    @Test
    @DisplayName("should provide zh-TW localization for /game-token-adjust command")
    void shouldProvideZhTwLocalizationForGameTokenAdjustCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("game-token-adjust");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("調整遊戲代幣");
    }

    @Test
    @DisplayName("should provide zh-TW localization for /dice-game-1 command")
    void shouldProvideZhTwLocalizationForDiceGame1Command() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("dice-game-1");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("摘星手");
    }

    @Test
    @DisplayName("should provide zh-TW localization for /dice-game-1-config command")
    void shouldProvideZhTwLocalizationForDiceGame1ConfigCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("dice-game-1-config");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("摘星手設定");
    }

    @Test
    @DisplayName("should provide zh-TW localization for /dice-game-2 command")
    void shouldProvideZhTwLocalizationForDiceGame2Command() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("dice-game-2");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("神龍擺尾");
    }

    @Test
    @DisplayName("should provide zh-TW localization for /dice-game-2-config command")
    void shouldProvideZhTwLocalizationForDiceGame2ConfigCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("dice-game-2-config");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("神龍擺尾設定");
    }

    @Test
    @DisplayName("should provide zh-TW localization for /user-panel command")
    void shouldProvideZhTwLocalizationForUserPanelCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("user-panel");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("個人面板");
    }

    @Test
    @DisplayName("should provide zh-TW localization for /admin-panel command")
    void shouldProvideZhTwLocalizationForAdminPanelCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("admin-panel");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("管理面板");
    }

    @Test
    @DisplayName("should return empty map for unknown command")
    void shouldReturnEmptyMapForUnknownCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getNameLocalizations("unknown-command");

      assertThat(localizations).isEmpty();
    }
  }

  @Nested
  @DisplayName("Command description localizations")
  class CommandDescriptionLocalizations {

    @Test
    @DisplayName("should provide zh-TW description localization for /balance command")
    void shouldProvideZhTwDescriptionForBalanceCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("balance");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("查看您目前的貨幣餘額");
    }

    @Test
    @DisplayName("should provide zh-TW description localization for /currency-config command")
    void shouldProvideZhTwDescriptionForCurrencyConfigCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("currency-config");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("設定伺服器的貨幣名稱與圖示");
    }

    @Test
    @DisplayName("should provide zh-TW description localization for /adjust-balance command")
    void shouldProvideZhTwDescriptionForAdjustBalanceCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("adjust-balance");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("調整成員的貨幣餘額");
    }

    @Test
    @DisplayName("should provide zh-TW description localization for /game-token-adjust command")
    void shouldProvideZhTwDescriptionForGameTokenAdjustCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("game-token-adjust");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("調整成員的遊戲代幣餘額");
    }

    @Test
    @DisplayName("should provide zh-TW description localization for /dice-game-1 command")
    void shouldProvideZhTwDescriptionForDiceGame1Command() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("dice-game-1");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("玩摘星手小遊戲（消耗遊戲代幣）");
    }

    @Test
    @DisplayName("should provide zh-TW description localization for /dice-game-1-config command")
    void shouldProvideZhTwDescriptionForDiceGame1ConfigCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("dice-game-1-config");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("設定摘星手的代幣消耗");
    }

    @Test
    @DisplayName("should provide zh-TW description localization for /dice-game-2 command")
    void shouldProvideZhTwDescriptionForDiceGame2Command() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("dice-game-2");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN))
          .isEqualTo("玩神龍擺尾小遊戲，有順子和三條獎勵（消耗遊戲代幣）");
    }

    @Test
    @DisplayName("should provide zh-TW description localization for /dice-game-2-config command")
    void shouldProvideZhTwDescriptionForDiceGame2ConfigCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("dice-game-2-config");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("設定神龍擺尾的代幣消耗");
    }

    @Test
    @DisplayName("should provide zh-TW description localization for /user-panel command")
    void shouldProvideZhTwDescriptionForUserPanelCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("user-panel");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("查看您的貨幣餘額、遊戲代幣與流水紀錄");
    }

    @Test
    @DisplayName("should provide zh-TW description localization for /admin-panel command")
    void shouldProvideZhTwDescriptionForAdminPanelCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("admin-panel");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("管理成員餘額、遊戲代幣與遊戲設定");
    }

    @Test
    @DisplayName("should return empty map for unknown command")
    void shouldReturnEmptyMapForUnknownCommand() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getDescriptionLocalizations("unknown-command");

      assertThat(localizations).isEmpty();
    }
  }

  @Nested
  @DisplayName("Option localizations")
  class OptionLocalizations {

    @Test
    @DisplayName("should provide zh-TW localization for 'name' option")
    void shouldProvideZhTwLocalizationForNameOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionNameLocalizations("name");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("名稱");
    }

    @Test
    @DisplayName("should provide zh-TW localization for 'icon' option")
    void shouldProvideZhTwLocalizationForIconOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionNameLocalizations("icon");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("圖示");
    }

    @Test
    @DisplayName("should provide zh-TW localization for 'mode' option")
    void shouldProvideZhTwLocalizationForModeOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionNameLocalizations("mode");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("模式");
    }

    @Test
    @DisplayName("should provide zh-TW localization for 'member' option")
    void shouldProvideZhTwLocalizationForMemberOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionNameLocalizations("member");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("成員");
    }

    @Test
    @DisplayName("should provide zh-TW localization for 'amount' option")
    void shouldProvideZhTwLocalizationForAmountOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionNameLocalizations("amount");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("數量");
    }

    @Test
    @DisplayName("should provide zh-TW localization for 'token-cost' option")
    void shouldProvideZhTwLocalizationForTokenCostOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionNameLocalizations("token-cost");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("代幣消耗");
    }
  }

  @Nested
  @DisplayName("Option description localizations")
  class OptionDescriptionLocalizations {

    @Test
    @DisplayName("should provide zh-TW description for 'name' option")
    void shouldProvideZhTwDescriptionForNameOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionDescriptionLocalizations("name");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("貨幣名稱（例如：金幣）");
    }

    @Test
    @DisplayName("should provide zh-TW description for 'icon' option")
    void shouldProvideZhTwDescriptionForIconOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionDescriptionLocalizations("icon");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("貨幣圖示/表情符號（例如：💰）");
    }

    @Test
    @DisplayName("should provide zh-TW description for 'mode' option")
    void shouldProvideZhTwDescriptionForModeOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionDescriptionLocalizations("mode");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("調整模式");
    }

    @Test
    @DisplayName("should provide zh-TW description for 'member' option")
    void shouldProvideZhTwDescriptionForMemberOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionDescriptionLocalizations("member");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("要調整的成員");
    }

    @Test
    @DisplayName("should provide zh-TW description for 'amount' option")
    void shouldProvideZhTwDescriptionForAmountOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionDescriptionLocalizations("amount");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("增加/扣除的數量，或調整模式下的目標餘額");
    }

    @Test
    @DisplayName("should provide zh-TW description for 'token-cost' option")
    void shouldProvideZhTwDescriptionForTokenCostOption() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getOptionDescriptionLocalizations("token-cost");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("每次遊玩所需的遊戲代幣數量");
    }
  }

  @Nested
  @DisplayName("Choice localizations")
  class ChoiceLocalizations {

    @Test
    @DisplayName("should provide zh-TW localization for 'add' choice")
    void shouldProvideZhTwLocalizationForAddChoice() {
      Map<DiscordLocale, String> localizations = CommandLocalizations.getChoiceLocalizations("add");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("增加");
    }

    @Test
    @DisplayName("should provide zh-TW localization for 'deduct' choice")
    void shouldProvideZhTwLocalizationForDeductChoice() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getChoiceLocalizations("deduct");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("扣除");
    }

    @Test
    @DisplayName("should provide zh-TW localization for 'adjust' choice")
    void shouldProvideZhTwLocalizationForAdjustChoice() {
      Map<DiscordLocale, String> localizations =
          CommandLocalizations.getChoiceLocalizations("adjust");

      assertThat(localizations).containsKey(DiscordLocale.CHINESE_TAIWAN);
      assertThat(localizations.get(DiscordLocale.CHINESE_TAIWAN)).isEqualTo("設為");
    }
  }
}
