package ltdjms.discord.aiagent.services.tools;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.domain.ChannelPermission;
import ltdjms.discord.aiagent.domain.PermissionSetting;
import ltdjms.discord.aiagent.services.PermissionParser;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/**
 * 創建 Discord 類別工具（LangChain4J 版本）。
 *
 * <p>使用 LangChain4J 的 @Tool 註解，通過 ToolExecutionContext 獲取執行上下文。
 */
public final class LangChain4jCreateCategoryTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jCreateCategoryTool.class);

  /** 類別名稱最大長度 */
  private static final int MAX_CATEGORY_NAME_LENGTH = 100;

  /** 類別創建逾時時間（秒） */
  private static final int CREATION_TIMEOUT_SECONDS = 30;

  @Inject
  public LangChain4jCreateCategoryTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 創建 Discord 類別。
   *
   * @param name 類別名稱（不超過 100 字符）
   * @param permissions 權限設定列表，每個元素包含 roleId 和 allowSet（可選）
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 執行結果 JSON 字串
   */
  @Tool(
      """
      創建一個新的 Discord 類別（頻道分組）。

      使用場景：
      - 當用戶需要組織頻道結構時使用
      - 適用於將相關頻道分類管理（如「活動區」「公告區」等）

      參數說明：
      - name：類別名稱，不能為空且不超過 100 字符
      - permissions：可選參數，用於設置類別的訪問權限

      注意事項：
      - 類別本身不是頻道，是頻道的容器
      - 創建後可以將頻道移入此類別
      - 類別權限會被其中的頻道繼承
      """)
  public String createCategory(
      String name,
      @P(
              value =
                  """
                  權限設定列表，用於控制特定角色對此類別的訪問權限。

                  每個設定包含：
                  - roleId：Discord 角色的 ID（數字）
                  - permissionSet：權限集合名稱，可選值：
                    * "admin_only"：僅管理員可訪問
                    * "private"：私密類別（指定角色才能訪問）

                  範例：
                  - [{"roleId": 123456789, "permissionSet": "admin_only"}]

                  如不提供此參數，類別將使用默認權限（所有成員可訪問）。
                  """)
          List<PermissionSetting> permissions,
      InvocationParameters parameters) {

    // 1. 驗證參數
    String validationError = validateName(name);
    if (validationError != null) {
      return buildErrorResponse(validationError);
    }

    // 2. 從 InvocationParameters 獲取執行上下文
    Long guildId = parameters.get("guildId");
    Long channelId = parameters.get("channelId");
    Long userId = parameters.get("userId");

    if (guildId == null) {
      LOGGER.error("LangChain4jCreateCategoryTool: guildId 未設置");
      return buildErrorResponse("guildId 未設置");
    }

    // 3. 獲取 Guild
    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      String errorMsg = String.format("找不到指定的伺服器: %d", guildId);
      LOGGER.warn("LangChain4jCreateCategoryTool: {}", errorMsg);
      return buildErrorResponse("找不到伺服器");
    }

    try {
      // 4. 創建類別
      Category category = createCategoryWithTimeout(guild, name);
      if (category == null) {
        return buildErrorResponse("創建類別失敗");
      }

      // 5. 應用權限（如果有提供）
      if (permissions != null && !permissions.isEmpty()) {
        applyPermissions(category, permissions, guild);
      }

      // 6. 返回成功結果
      String resultMsg =
          String.format("成功創建類別: %s (ID: %d)", category.getName(), category.getIdLong());
      LOGGER.info("LangChain4jCreateCategoryTool: {}", resultMsg);
      return buildSuccessResponse(resultMsg, category.getIdLong(), category.getName());

    } catch (InsufficientPermissionException e) {
      String errorMsg = String.format("機器人沒有足夠的權限創建類別: %s", e.getMessage());
      LOGGER.warn("LangChain4jCreateCategoryTool: {}", errorMsg);
      return buildErrorResponse("機器人沒有足夠的權限");

    } catch (TimeoutException e) {
      String errorMsg = String.format("創建類別逾時: %s", e.getMessage());
      LOGGER.warn("LangChain4jCreateCategoryTool: {}", errorMsg);
      return buildErrorResponse("創建類別逾時");

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      String errorMsg = String.format("創建類別被中斷: %s", e.getMessage());
      LOGGER.error("LangChain4jCreateCategoryTool: {}", errorMsg);
      return buildErrorResponse("創建類別被中斷");

    } catch (Exception e) {
      String errorMsg = String.format("創建類別失敗: %s", e.getMessage());
      LOGGER.error("LangChain4jCreateCategoryTool: {}", errorMsg, e);
      return buildErrorResponse(errorMsg);
    }
  }

  /**
   * 驗證類別名稱。
   *
   * @param name 類別名稱
   * @return 錯誤訊息，如果驗證通過則返回 null
   */
  private String validateName(String name) {
    if (name == null || name.isBlank()) {
      return "類別名稱不能為空";
    }

    if (name.length() > MAX_CATEGORY_NAME_LENGTH) {
      return String.format("類別名稱不能超過 %d 字符（當前: %d）", MAX_CATEGORY_NAME_LENGTH, name.length());
    }

    return null;
  }

  /**
   * 創建類別並設定逾時。
   *
   * @param guild Discord 伺服器
   * @param categoryName 類別名稱
   * @return 創建的類別
   * @throws Exception 如果創建失敗或逾時
   */
  private Category createCategoryWithTimeout(Guild guild, String categoryName) throws Exception {
    CompletableFuture<Category> future = guild.createCategory(categoryName).submit();

    try {
      return future.get(CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    } catch (TimeoutException e) {
      future.cancel(true);
      throw e;
    }
  }

  /**
   * 應用權限設定到類別。
   *
   * @param category 類別
   * @param permissionsParam 權限參數列表
   * @param guild Discord 伺服器
   */
  private void applyPermissions(
      Category category, List<PermissionSetting> permissionsParam, Guild guild) {
    try {
      for (PermissionSetting setting : permissionsParam) {
        long roleId = setting.roleId();
        Role role = guild.getRoleById(roleId);
        if (role == null) {
          LOGGER.warn("LangChain4jCreateCategoryTool: 角色不存在 (ID: {}), 跳過權限設定", roleId);
          continue;
        }

        ChannelPermission permission = PermissionParser.parse(setting);
        applyPermissionToCategory(category, role, permission.permissionSet());
      }

    } catch (Exception e) {
      LOGGER.warn("LangChain4jCreateCategoryTool: 應用權限時發生錯誤: {}", e.getMessage());
      // 權限應用失敗不影響類別創建，僅記錄警告
    }
  }

  /**
   * 將權限集合應用到類別的角色。
   *
   * @param category 類別
   * @param role 角色
   * @param permissions 權限集合
   */
  private void applyPermissionToCategory(
      Category category, Role role, java.util.EnumSet<Permission> permissions) {

    try {
      category
          .upsertPermissionOverride(role)
          .setPermissions(permissions, java.util.EnumSet.noneOf(Permission.class))
          .complete();

      LOGGER.debug(
          "LangChain4jCreateCategoryTool: 已為角色 {} 設定權限: {}", role.getIdLong(), permissions);

    } catch (InsufficientPermissionException e) {
      LOGGER.warn(
          "LangChain4jCreateCategoryTool: 沒有足夠權限設定角色 {} 的權限: {}", role.getIdLong(), e.getMessage());

    } catch (Exception e) {
      LOGGER.error(
          "LangChain4jCreateCategoryTool: 設定角色 {} 權限時發生錯誤: {}",
          role.getIdLong(),
          e.getMessage(),
          e);
    }
  }

  /**
   * 構建成功回應。
   *
   * @param message 成功訊息
   * @param categoryId 類別 ID
   * @param categoryName 類別名稱
   * @return JSON 格式的成功回應
   */
  private String buildSuccessResponse(String message, long categoryId, String categoryName) {
    return """
    {
      "success": true,
      "message": "%s",
      "categoryId": "%d",
      "categoryName": "%s"
    }
    """
        .formatted(message, categoryId, categoryName);
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
