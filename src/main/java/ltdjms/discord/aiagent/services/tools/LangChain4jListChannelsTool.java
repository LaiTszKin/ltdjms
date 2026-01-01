package ltdjms.discord.aiagent.services.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

/**
 * 列出 Discord 頻道工具（LangChain4J 版本）。
 *
 * <p>使用 LangChain4J 的 @Tool 註解，通過 ToolExecutionContext 獲取執行上下文。
 */
public final class LangChain4jListChannelsTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jListChannelsTool.class);

  /** 支援的頻道類型映射 */
  private static final Map<String, ChannelType> CHANNEL_TYPE_MAP =
      Map.of(
          "text",
          ChannelType.TEXT,
          "voice",
          ChannelType.VOICE,
          "category",
          ChannelType.CATEGORY,
          "forum",
          ChannelType.FORUM,
          "media",
          ChannelType.MEDIA,
          "stage",
          ChannelType.STAGE);

  @Inject
  public LangChain4jListChannelsTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 獲取 Discord 伺服器中的所有頻道資訊。
   *
   * @param type 頻道類型篩選（可選值：text, voice, category, forum, media, stage）
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 頻道列表 JSON 字串
   */
  @Tool(
      """
      獲取 Discord 伺服器中的頻道列表資訊。

      使用場景：
      - 當用戶詢問有哪些頻道時使用
      - 需要查看伺服器頻道結構時使用
      - 檢查特定頻道是否存在時使用

      返回資訊：
      - 頻道數量
      - 每個頻道的 ID、名稱和類型

      支援的頻道類型：
      - text：文字頻道
      - voice：語音頻道
      - category：類別
      - forum：論壇頻道
      - media：媒體頻道
      - stage：舞台頻道

      重要：
      - 所有頻道 ID 以「字串」形式返回，避免 JSON 數字溢位造成精度損失
      """)
  public String listChannels(
      @P(
              value =
                  """
                  頻道類型篩選條件，用於只獲取特定類型的頻道。

                  可選值（不區分大小寫）：
                  - "text"：只列出文字頻道
                  - "voice"：只列出語音頻道
                  - "category"：只列出類別
                  - "forum"：只列出論壇頻道
                  - "media"：只列出媒體頻道
                  - "stage"：只列出舞台頻道

                  如不提供此參數，將返回所有類型的頻道。

                  範例：
                  - "text"：只獲取文字頻道
                  - "voice"：只獲取語音頻道
                  """,
              required = false)
          String type,
      InvocationParameters parameters) {

    // 1. 驗證類型參數（如果提供）
    if (type != null && !type.isBlank()) {
      String lowerType = type.toLowerCase();
      if (!CHANNEL_TYPE_MAP.containsKey(lowerType)) {
        String errorMsg = String.format("無效的頻道類型: %s。支援的類型: %s", type, CHANNEL_TYPE_MAP.keySet());
        LOGGER.warn("LangChain4jListChannelsTool: {}", errorMsg);
        return buildErrorResponse(errorMsg);
      }
    }

    // 2. 從 InvocationParameters 獲取執行上下文
    Long guildId = parameters.get("guildId");
    if (guildId == null) {
      LOGGER.error("LangChain4jListChannelsTool: guildId 未設置");
      return buildErrorResponse("guildId 未設置");
    }

    // 3. 獲取 Guild
    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      String errorMsg = String.format("找不到指定的伺服器: %d", guildId);
      LOGGER.warn("LangChain4jListChannelsTool: {}", errorMsg);
      return buildErrorResponse("找不到伺服器");
    }

    try {
      // 4. 收集頻道資訊
      List<GuildChannel> channels = guild.getChannels();
      List<Map<String, Object>> channelInfos = new ArrayList<>();

      String typeFilter = (type != null && !type.isBlank()) ? type.toLowerCase() : null;

      for (GuildChannel channel : channels) {
        // 應用類型篩選
        if (typeFilter != null && !matchesType(channel, typeFilter)) {
          continue;
        }

        // 構建頻道資訊
        channelInfos.add(buildChannelInfo(channel));
      }

      // 5. 返回 JSON 格式結果
      String jsonResult = buildJsonResult(channelInfos);
      LOGGER.info("LangChain4jListChannelsTool: 找到 {} 個頻道", channelInfos.size());
      return jsonResult;

    } catch (Exception e) {
      String errorMsg = String.format("獲取頻道列表失敗: %s", e.getMessage());
      LOGGER.error("LangChain4jListChannelsTool: {}", errorMsg, e);
      return buildErrorResponse(errorMsg);
    }
  }

  /**
   * 檢查頻道類型是否匹配篩選條件。
   *
   * @param channel 頻道
   * @param typeFilter 類型篩選條件
   * @return 是否匹配
   */
  private boolean matchesType(GuildChannel channel, String typeFilter) {
    ChannelType channelType = channel.getType();
    ChannelType targetType = CHANNEL_TYPE_MAP.get(typeFilter);
    return channelType == targetType;
  }

  /**
   * 構建頻道資訊 Map。
   *
   * @param channel 頻道
   * @return 頻道資訊 Map
   */
  private Map<String, Object> buildChannelInfo(GuildChannel channel) {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("id", String.valueOf(channel.getIdLong()));
    info.put("name", channel.getName());
    info.put("type", getReadableType(channel.getType()));
    return info;
  }

  /**
   * 將 ChannelType 轉換為可讀字串。
   *
   * @param type 頻道類型
   * @return 可讀字串
   */
  private String getReadableType(ChannelType type) {
    return switch (type) {
      case TEXT -> "text";
      case VOICE -> "voice";
      case CATEGORY -> "category";
      case FORUM -> "forum";
      case MEDIA -> "media";
      case STAGE -> "stage";
      default -> type.name();
    };
  }

  /**
   * 構建 JSON 格式結果。
   *
   * @param channelInfos 頻道資訊列表
   * @return JSON 字串
   */
  private String buildJsonResult(List<Map<String, Object>> channelInfos) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"count\": ").append(channelInfos.size()).append(",\n");
    json.append("  \"channels\": [\n");

    for (int i = 0; i < channelInfos.size(); i++) {
      if (i > 0) {
        json.append(",\n");
      }
      Map<String, Object> info = channelInfos.get(i);
      json.append("    {\n");
      json.append("      \"id\": \"").append(info.get("id")).append("\",\n");
      json.append("      \"name\": \"").append(info.get("name")).append("\",\n");
      json.append("      \"type\": \"").append(info.get("type")).append("\"");
      json.append("\n    }");
    }

    json.append("\n  ]\n");
    json.append("}");
    return json.toString();
  }

  /**
   * 構建錯誤回應。
   *
   * @param error 錯誤訊息
   * @return JSON 格式的錯誤回應
   */
  private String buildErrorResponse(String error) {
    return """
    {
      "success": false,
      "error": "%s"
    }
    """
        .formatted(error);
  }
}
