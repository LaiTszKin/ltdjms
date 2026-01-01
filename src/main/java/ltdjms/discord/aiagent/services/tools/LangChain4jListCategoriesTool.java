package ltdjms.discord.aiagent.services.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

/**
 * 列出 Discord 類別工具（LangChain4J 版本）。
 *
 * <p>使用 LangChain4J 的 @Tool 註解，通過 ToolExecutionContext 獲取執行上下文。
 */
public final class LangChain4jListCategoriesTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jListCategoriesTool.class);

  @Inject
  public LangChain4jListCategoriesTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 獲取 Discord 伺服器中的所有類別資訊。
   *
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 類別列表 JSON 字串
   */
  @Tool(
      """
      獲取 Discord 伺服器中的類別列表資訊。

      使用場景：
      - 當用戶詢問有哪些類別時使用
      - 創建頻道前需要了解可用的類別 ID 時使用
      - 需要檢查特定類別是否存在時使用

      返回資訊：
      - 類別數量
      - 每個類別的 ID 和名稱

      應用提示：
      - 所有 ID 以「字串」形式返回（避免數字精度損失）
      - 類別用於組織和管理頻道
      - 創建頻道時可以指定將其放置在特定類別下
      """)
  public String listCategories(InvocationParameters parameters) {

    // 1. 從 InvocationParameters 獲取執行上下文
    Long guildId = parameters.get("guildId");
    if (guildId == null) {
      LOGGER.error("LangChain4jListCategoriesTool: guildId 未設置");
      return buildErrorResponse("guildId 未設置");
    }

    // 2. 獲取 Guild
    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      String errorMsg = String.format("找不到指定的伺服器: %d", guildId);
      LOGGER.warn("LangChain4jListCategoriesTool: {}", errorMsg);
      return buildErrorResponse("找不到伺服器");
    }

    try {
      // 3. 收集類別資訊
      List<Category> categories = new ArrayList<>(guild.getCategories());
      List<Map<String, Object>> categoryInfos = new ArrayList<>();

      for (Category category : categories) {
        categoryInfos.add(buildCategoryInfo(category));
      }

      // 4. 返回 JSON 格式結果
      String jsonResult = buildJsonResult(categoryInfos);
      LOGGER.info("LangChain4jListCategoriesTool: 找到 {} 個類別", categoryInfos.size());
      return jsonResult;

    } catch (Exception e) {
      String errorMsg = String.format("獲取類別列表失敗: %s", e.getMessage());
      LOGGER.error("LangChain4jListCategoriesTool: {}", errorMsg, e);
      return buildErrorResponse(errorMsg);
    }
  }

  /**
   * 構建類別資訊 Map。
   *
   * @param category 類別
   * @return 類別資訊 Map
   */
  private Map<String, Object> buildCategoryInfo(Category category) {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("id", String.valueOf(category.getIdLong()));
    info.put("name", category.getName());
    return info;
  }

  /**
   * 構建 JSON 格式結果。
   *
   * @param categoryInfos 類別資訊列表
   * @return JSON 字串
   */
  private String buildJsonResult(List<Map<String, Object>> categoryInfos) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"count\": ").append(categoryInfos.size()).append(",\n");
    json.append("  \"categories\": [\n");

    for (int i = 0; i < categoryInfos.size(); i++) {
      if (i > 0) {
        json.append(",\n");
      }
      Map<String, Object> info = categoryInfos.get(i);
      json.append("    {\n");
      json.append("      \"id\": \"").append(info.get("id")).append("\",\n");
      json.append("      \"name\": \"").append(info.get("name")).append("\"");
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
