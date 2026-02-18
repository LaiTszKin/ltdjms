package ltdjms.discord.aiagent.domain;

import java.util.List;

/**
 * AI Agent 工具定義。
 *
 * <p>定義所有可被 AI 調用的系統工具。
 */
public final class AIAgentTools {

  private AIAgentTools() {
    // 工具類，不允許實例化
  }

  /** 新增頻道工具。 */
  public static final ToolDefinition CREATE_CHANNEL =
      new ToolDefinition(
          "create_channel",
          "創建一個新的 Discord 文字頻道，並指定頻道名稱和權限設定",
          List.of(
              new ToolParameter(
                  "name", ToolParameter.ParamType.STRING, "頻道名稱（不超過 100 字符）", true, null),
              new ToolParameter(
                  "permissions",
                  ToolParameter.ParamType.ARRAY,
                  "權限設定列表，每個元素包含 roleId 與 allowSet/denySet（舊版可用 permissionSet）",
                  false,
                  null)));

  /** 新增類別工具。 */
  public static final ToolDefinition CREATE_CATEGORY =
      new ToolDefinition(
          "create_category",
          "創建一個新的 Discord 類別，並指定類別名稱和權限設定",
          List.of(
              new ToolParameter(
                  "name", ToolParameter.ParamType.STRING, "類別名稱（不超過 100 字符）", true, null),
              new ToolParameter(
                  "permissions", ToolParameter.ParamType.ARRAY, "權限設定列表", false, null)));

  /** 列出頻道工具。 */
  public static final ToolDefinition LIST_CHANNELS =
      new ToolDefinition(
          "list_channels",
          "獲取 Discord 伺服器中的所有頻道資訊，包括頻道名稱、ID 和類型。支援按頻道類型篩選。",
          List.of(
              new ToolParameter(
                  "type",
                  ToolParameter.ParamType.STRING,
                  "頻道類型篩選（可選值：text, voice, category, forum, media, stage）。不提供則返回所有類型。",
                  false,
                  null)));

  /** 發送訊息工具。 */
  public static final ToolDefinition SEND_MESSAGES =
      new ToolDefinition(
          "send_messages",
          "向指定的一個或多個 Discord 頻道發送單則或多則訊息",
          List.of(
              new ToolParameter(
                  "channelIds",
                  ToolParameter.ParamType.ARRAY,
                  "目標頻道 ID 列表（可選，未提供時使用當前頻道）",
                  false,
                  null),
              new ToolParameter(
                  "message", ToolParameter.ParamType.STRING, "單則訊息內容（可選）", false, null),
              new ToolParameter(
                  "messages", ToolParameter.ParamType.ARRAY, "多則訊息內容列表（可選）", false, null)));

  /** 搜尋訊息工具。 */
  public static final ToolDefinition SEARCH_MESSAGES =
      new ToolDefinition(
          "search_messages",
          "在指定頻道中搜尋包含關鍵字的歷史訊息（支援多頻道）",
          List.of(
              new ToolParameter(
                  "keywords", ToolParameter.ParamType.STRING, "搜尋關鍵字（可含多個詞）", true, null),
              new ToolParameter(
                  "channelIds", ToolParameter.ParamType.ARRAY, "要搜尋的頻道 ID 列表", false, null),
              new ToolParameter(
                  "maxResultsPerChannel",
                  ToolParameter.ParamType.NUMBER,
                  "每個頻道最多返回幾筆匹配結果",
                  false,
                  null),
              new ToolParameter(
                  "maxMessagesToScan",
                  ToolParameter.ParamType.NUMBER,
                  "每個頻道最多掃描幾筆歷史訊息",
                  false,
                  null)));

  /** 訊息管理工具。 */
  public static final ToolDefinition MANAGE_MESSAGE =
      new ToolDefinition(
          "manage_message",
          "管理指定訊息狀態（pin、delete、edit）",
          List.of(
              new ToolParameter("messageId", ToolParameter.ParamType.STRING, "目標訊息 ID", true, null),
              new ToolParameter(
                  "action", ToolParameter.ParamType.STRING, "操作類型：pin、delete、edit", true, null),
              new ToolParameter(
                  "channelId",
                  ToolParameter.ParamType.STRING,
                  "目標頻道 ID（可選，未提供時使用當前頻道）",
                  false,
                  null),
              new ToolParameter(
                  "newContent",
                  ToolParameter.ParamType.STRING,
                  "新的訊息內容（僅 action=edit 時需要）",
                  false,
                  null)));

  /**
   * 獲取所有已註冊的工具。
   *
   * @return 工具定義列表
   */
  public static List<ToolDefinition> all() {
    return List.of(
        CREATE_CHANNEL,
        CREATE_CATEGORY,
        LIST_CHANNELS,
        SEND_MESSAGES,
        SEARCH_MESSAGES,
        MANAGE_MESSAGE);
  }
}
