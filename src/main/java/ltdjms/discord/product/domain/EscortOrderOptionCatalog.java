package ltdjms.discord.product.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Built-in escort order option catalog and pricing table (TWD). */
public final class EscortOrderOptionCatalog {

  private static final Map<String, EscortOrderOption> OPTIONS = createOptions();

  private EscortOrderOptionCatalog() {
    // Utility class
  }

  public static Optional<EscortOrderOption> findByCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(OPTIONS.get(code.trim().toUpperCase()));
  }

  public static boolean isSupported(String code) {
    return findByCode(code).isPresent();
  }

  public static Set<String> supportedCodes() {
    return OPTIONS.keySet();
  }

  public static List<EscortOrderOption> allOptions() {
    return List.copyOf(OPTIONS.values());
  }

  public static String supportedCodesForDisplay() {
    return String.join(", ", OPTIONS.keySet());
  }

  private static Map<String, EscortOrderOption> createOptions() {
    Map<String, EscortOrderOption> options = new LinkedHashMap<>();

    // 包本單
    put(options, "CONF_DAM_300W", "包本單", "機密護", "機密大壩", "300 萬目標", 500L);
    put(options, "CONF_DAM_600W", "包本單", "機密護", "機密大壩", "600 萬目標", 1100L);
    put(options, "CONF_SPACE_300W", "包本單", "機密護", "機密航天", "300 萬目標", 600L);
    put(options, "CONF_SPACE_500W", "包本單", "機密護", "機密航天", "500 萬目標", 1200L);
    put(options, "SECRET_SPACE_400W", "包本單", "絕密護", "絕密航天", "400 萬目標", 800L);
    put(options, "SECRET_SPACE_700W", "包本單", "絕密護", "絕密航天", "700 萬目標", 1500L);

    // 小時單
    put(options, "CONF_HOURLY_1H", "小時單", "機密護", "機密大壩、機密航天", "1 小時", 600L);
    put(options, "CONF_HOURLY_2H", "小時單", "機密護", "機密大壩、機密航天", "2 小時", 1300L);
    put(options, "SECRET_HOURLY_1H", "小時單", "絕密護", "絕密航天、絕密巴克什、監獄", "1 小時", 800L);
    put(options, "SECRET_HOURLY_2H", "小時單", "絕密護", "絕密航天、絕密巴克什、監獄", "2 小時", 1800L);

    // 哈夫幣累積單
    put(options, "HAVOC_3000W", "哈夫幣累積單", "不限", "不限", "3,000 萬哈夫幣", 3500L);
    put(options, "HAVOC_6000W", "哈夫幣累積單", "不限", "不限", "6,000 萬哈夫幣", 5500L);
    put(options, "HAVOC_8000W", "哈夫幣累積單", "不限", "不限", "8,000 萬哈夫幣", 10000L);

    // 特殊單 / 指定大紅
    put(options, "SPECIAL_SAFEBOX50", "特殊條件", "不限", "不限", "不開夠 50 個保險箱不結單", 4888L);
    put(options, "RED_MANDEL_UNIT", "指定大紅", "不限", "不限", "曼德爾超算單元", 6888L);
    put(options, "RED_PORTABLE_RADAR", "指定大紅", "不限", "不限", "便攜軍用雷達", 6888L);
    put(options, "RED_SECRET_SERVER", "指定大紅", "不限", "不限", "絕密伺服器", 7000L);
    put(options, "RED_AED", "指定大紅", "不限", "不限", "自動體外心臟去顫器", 7500L);
    put(options, "RED_ARMORED_BATTERY", "指定大紅", "不限", "不限", "裝甲車電池", 8000L);
    put(options, "RED_LAPTOP", "指定大紅", "不限", "不限", "筆記型電腦", 8200L);
    put(options, "RED_GOLDEN_TEAR_CROWN", "指定大紅", "不限", "不限", "萬金淚冠", 8500L);
    put(options, "RED_ZONGHENG", "指定大紅", "不限", "不限", "縱橫", 9000L);
    put(options, "RED_MICRO_REACTOR", "指定大紅", "不限", "不限", "微型反應爐", 10000L);
    put(options, "RED_RESPIRATOR", "指定大紅", "不限", "不限", "復甦呼吸機", 19999L);
    put(options, "RED_HEART_FOREVER", "指定大紅", "不限", "不限", "洲之所向，真心永傳", 30000L);
    put(options, "RED_TIDE_TEAR", "指定大紅", "不限", "不限", "人魚隱沒，潮汐之淚", 35000L);

    return Map.copyOf(options);
  }

  private static void put(
      Map<String, EscortOrderOption> options,
      String code,
      String type,
      String level,
      String mapScope,
      String target,
      long priceTwd) {
    options.put(code, new EscortOrderOption(code, type, level, mapScope, target, priceTwd));
  }

  public record EscortOrderOption(
      String code, String type, String level, String mapScope, String target, long priceTwd) {
    public String toDisplayText() {
      return String.format("%s | %s | %s | %s | NT$%,d", type, level, mapScope, target, priceTwd);
    }
  }
}
