package ltdjms.discord.shared.localization;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.dv8tion.jda.api.interactions.DiscordLocale;

/**
 * Provides zh-TW localization mappings for all slash commands, options, and choices. This class
 * centralizes all localization definitions to ensure consistency across command registration and
 * provides easy maintenance of translations.
 *
 * <p>The English canonical names remain unchanged to maintain backwards compatibility with existing
 * specs and documentation. Localization is applied through Discord's built-in localization API,
 * allowing zh-TW clients to see Chinese names while other clients see the English defaults.
 */
public final class CommandLocalizations {

  private CommandLocalizations() {
    // Utility class - prevent instantiation
  }

  // Command name localizations (zh-TW)
  private static final Map<String, Map<DiscordLocale, String>> COMMAND_NAME_LOCALIZATIONS;
  private static final Map<String, Map<DiscordLocale, String>> COMMAND_DESCRIPTION_LOCALIZATIONS;
  private static final Map<String, Map<DiscordLocale, String>> OPTION_NAME_LOCALIZATIONS;
  private static final Map<String, Map<DiscordLocale, String>> OPTION_DESCRIPTION_LOCALIZATIONS;
  private static final Map<String, Map<DiscordLocale, String>> CHOICE_LOCALIZATIONS;

  static {
    // Initialize command name localizations
    COMMAND_NAME_LOCALIZATIONS = new HashMap<>();
    COMMAND_NAME_LOCALIZATIONS.put("balance", Map.of(DiscordLocale.CHINESE_TAIWAN, "餘額"));
    COMMAND_NAME_LOCALIZATIONS.put("currency-config", Map.of(DiscordLocale.CHINESE_TAIWAN, "貨幣設定"));
    COMMAND_NAME_LOCALIZATIONS.put("adjust-balance", Map.of(DiscordLocale.CHINESE_TAIWAN, "調整餘額"));
    COMMAND_NAME_LOCALIZATIONS.put(
        "game-token-adjust", Map.of(DiscordLocale.CHINESE_TAIWAN, "調整遊戲代幣"));
    COMMAND_NAME_LOCALIZATIONS.put("dice-game-1", Map.of(DiscordLocale.CHINESE_TAIWAN, "摘星手"));
    COMMAND_NAME_LOCALIZATIONS.put(
        "dice-game-1-config", Map.of(DiscordLocale.CHINESE_TAIWAN, "摘星手設定"));
    COMMAND_NAME_LOCALIZATIONS.put("dice-game-2", Map.of(DiscordLocale.CHINESE_TAIWAN, "神龍擺尾"));
    COMMAND_NAME_LOCALIZATIONS.put(
        "dice-game-2-config", Map.of(DiscordLocale.CHINESE_TAIWAN, "神龍擺尾設定"));
    COMMAND_NAME_LOCALIZATIONS.put("user-panel", Map.of(DiscordLocale.CHINESE_TAIWAN, "個人面板"));
    COMMAND_NAME_LOCALIZATIONS.put("admin-panel", Map.of(DiscordLocale.CHINESE_TAIWAN, "管理面板"));
    COMMAND_NAME_LOCALIZATIONS.put("shop", Map.of(DiscordLocale.CHINESE_TAIWAN, "商店"));
    COMMAND_NAME_LOCALIZATIONS.put("dispatch-panel", Map.of(DiscordLocale.CHINESE_TAIWAN, "派單面板"));

    // Initialize command description localizations
    COMMAND_DESCRIPTION_LOCALIZATIONS = new HashMap<>();
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "balance", Map.of(DiscordLocale.CHINESE_TAIWAN, "查看您目前的貨幣餘額"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "currency-config", Map.of(DiscordLocale.CHINESE_TAIWAN, "設定伺服器的貨幣名稱與圖示"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "adjust-balance", Map.of(DiscordLocale.CHINESE_TAIWAN, "調整成員的貨幣餘額"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "game-token-adjust", Map.of(DiscordLocale.CHINESE_TAIWAN, "調整成員的遊戲代幣餘額"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "dice-game-1", Map.of(DiscordLocale.CHINESE_TAIWAN, "玩摘星手小遊戲（消耗遊戲代幣）"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "dice-game-1-config", Map.of(DiscordLocale.CHINESE_TAIWAN, "設定摘星手的代幣消耗"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "dice-game-2", Map.of(DiscordLocale.CHINESE_TAIWAN, "玩神龍擺尾小遊戲，有順子和三條獎勵（消耗遊戲代幣）"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "dice-game-2-config", Map.of(DiscordLocale.CHINESE_TAIWAN, "設定神龍擺尾的代幣消耗"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "user-panel", Map.of(DiscordLocale.CHINESE_TAIWAN, "查看您的貨幣餘額、遊戲代幣與流水紀錄"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "admin-panel", Map.of(DiscordLocale.CHINESE_TAIWAN, "管理成員餘額、遊戲代幣與遊戲設定"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "shop", Map.of(DiscordLocale.CHINESE_TAIWAN, "瀏覽伺服器可購買的商品"));
    COMMAND_DESCRIPTION_LOCALIZATIONS.put(
        "dispatch-panel", Map.of(DiscordLocale.CHINESE_TAIWAN, "透過互動面板分派護航訂單"));

    // Initialize option name localizations
    OPTION_NAME_LOCALIZATIONS = new HashMap<>();
    OPTION_NAME_LOCALIZATIONS.put("name", Map.of(DiscordLocale.CHINESE_TAIWAN, "名稱"));
    OPTION_NAME_LOCALIZATIONS.put("icon", Map.of(DiscordLocale.CHINESE_TAIWAN, "圖示"));
    OPTION_NAME_LOCALIZATIONS.put("mode", Map.of(DiscordLocale.CHINESE_TAIWAN, "模式"));
    OPTION_NAME_LOCALIZATIONS.put("member", Map.of(DiscordLocale.CHINESE_TAIWAN, "成員"));
    OPTION_NAME_LOCALIZATIONS.put("amount", Map.of(DiscordLocale.CHINESE_TAIWAN, "數量"));
    OPTION_NAME_LOCALIZATIONS.put("token-cost", Map.of(DiscordLocale.CHINESE_TAIWAN, "代幣消耗"));
    OPTION_NAME_LOCALIZATIONS.put("tokens", Map.of(DiscordLocale.CHINESE_TAIWAN, "代幣數量"));

    // Initialize option description localizations
    OPTION_DESCRIPTION_LOCALIZATIONS = new HashMap<>();
    OPTION_DESCRIPTION_LOCALIZATIONS.put(
        "name", Map.of(DiscordLocale.CHINESE_TAIWAN, "貨幣名稱（例如：金幣）"));
    OPTION_DESCRIPTION_LOCALIZATIONS.put(
        "icon", Map.of(DiscordLocale.CHINESE_TAIWAN, "貨幣圖示/表情符號（例如：💰）"));
    OPTION_DESCRIPTION_LOCALIZATIONS.put("mode", Map.of(DiscordLocale.CHINESE_TAIWAN, "調整模式"));
    OPTION_DESCRIPTION_LOCALIZATIONS.put("member", Map.of(DiscordLocale.CHINESE_TAIWAN, "要調整的成員"));
    OPTION_DESCRIPTION_LOCALIZATIONS.put(
        "amount", Map.of(DiscordLocale.CHINESE_TAIWAN, "增加/扣除的數量，或調整模式下的目標餘額"));
    OPTION_DESCRIPTION_LOCALIZATIONS.put(
        "token-cost", Map.of(DiscordLocale.CHINESE_TAIWAN, "每次遊玩所需的遊戲代幣數量"));
    OPTION_DESCRIPTION_LOCALIZATIONS.put(
        "tokens", Map.of(DiscordLocale.CHINESE_TAIWAN, "本局投入的遊戲代幣數量（選填）"));

    // Initialize choice localizations
    CHOICE_LOCALIZATIONS = new HashMap<>();
    CHOICE_LOCALIZATIONS.put("add", Map.of(DiscordLocale.CHINESE_TAIWAN, "增加"));
    CHOICE_LOCALIZATIONS.put("deduct", Map.of(DiscordLocale.CHINESE_TAIWAN, "扣除"));
    CHOICE_LOCALIZATIONS.put("adjust", Map.of(DiscordLocale.CHINESE_TAIWAN, "設為"));
  }

  /**
   * Gets the name localizations for a command.
   *
   * @param commandName the English canonical command name
   * @return a map of locale to localized name, or empty map if not found
   */
  public static Map<DiscordLocale, String> getNameLocalizations(String commandName) {
    return COMMAND_NAME_LOCALIZATIONS.getOrDefault(commandName, Collections.emptyMap());
  }

  /**
   * Gets the description localizations for a command.
   *
   * @param commandName the English canonical command name
   * @return a map of locale to localized description, or empty map if not found
   */
  public static Map<DiscordLocale, String> getDescriptionLocalizations(String commandName) {
    return COMMAND_DESCRIPTION_LOCALIZATIONS.getOrDefault(commandName, Collections.emptyMap());
  }

  /**
   * Gets the name localizations for an option.
   *
   * @param optionName the English canonical option name
   * @return a map of locale to localized name, or empty map if not found
   */
  public static Map<DiscordLocale, String> getOptionNameLocalizations(String optionName) {
    return OPTION_NAME_LOCALIZATIONS.getOrDefault(optionName, Collections.emptyMap());
  }

  /**
   * Gets the description localizations for an option.
   *
   * @param optionName the English canonical option name
   * @return a map of locale to localized description, or empty map if not found
   */
  public static Map<DiscordLocale, String> getOptionDescriptionLocalizations(String optionName) {
    return OPTION_DESCRIPTION_LOCALIZATIONS.getOrDefault(optionName, Collections.emptyMap());
  }

  /**
   * Gets the localizations for a choice value.
   *
   * @param choiceValue the English canonical choice value
   * @return a map of locale to localized choice name, or empty map if not found
   */
  public static Map<DiscordLocale, String> getChoiceLocalizations(String choiceValue) {
    return CHOICE_LOCALIZATIONS.getOrDefault(choiceValue, Collections.emptyMap());
  }
}
