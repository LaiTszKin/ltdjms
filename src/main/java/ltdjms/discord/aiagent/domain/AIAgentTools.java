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
                  "權限設定列表，每個元素包含 roleId 和 permissionSet",
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

  /**
   * 獲取所有已註冊的工具。
   *
   * @return 工具定義列表
   */
  public static List<ToolDefinition> all() {
    return List.of(CREATE_CHANNEL, CREATE_CATEGORY);
  }
}
