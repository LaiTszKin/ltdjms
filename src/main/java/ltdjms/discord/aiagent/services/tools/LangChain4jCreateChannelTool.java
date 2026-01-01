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
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/**
 * 創建 Discord 頻道工具（LangChain4J 版本）。
 *
 * <p>使用 LangChain4J 的 @Tool 註解，通過 ToolExecutionContext 獲取執行上下文。
 */
public final class LangChain4jCreateChannelTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jCreateChannelTool.class);

  /** 頻道名稱最大長度 */
  private static final int MAX_CHANNEL_NAME_LENGTH = 100;

  /** 頻道創建逾時時間（秒） */
  private static final int CREATION_TIMEOUT_SECONDS = 30;

  /** 類別查找重試次數（給 JDA 緩存更新的時間） */
  private static final int CATEGORY_LOOKUP_MAX_RETRIES = 8;

  /** 類別查找重試間隔（毫秒） */
  private static final long CATEGORY_LOOKUP_RETRY_DELAY_MS = 1000;

  @Inject
  public LangChain4jCreateChannelTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 創建 Discord 文字頻道。
   *
   * @param name 頻道名稱（不超過 100 字符）
   * @param categoryId 類別 ID（可選，用於將頻道創建在特定類別下，請以字串雪花 ID 傳入）
   * @param permissions 權限設定列表，每個元素包含 roleId 和 allowSet/denySet（可選）
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 執行結果 JSON 字串
   */
  @Tool(
      """
      創建一個新的 Discord 文字頻道。

      使用場景：
      - 當用戶要求創建新頻道時使用此工具
      - 適用於各類文字頻道需求（公告、聊天、公告等）
      - 可以指定將頻道創建在特定類別下

      參數說明：
      - name：頻道名稱，不能為空且不超過 100 字符
      - categoryId：可選參數，指定頻道所屬的類別 ID。如果提供，頻道將創建在該類別下
      - permissions：可選參數，用於設置特定角色的訪問權限

      注意事項：
      - 創建後頻道默認繼承類別權限
      - 如未指定權限，所有成員默認可訪問
      - 如果指定的 categoryId 不存在，將返回錯誤
      """)
  public String createChannel(
      String name,
      @P(
              value =
                  """
                  類別 ID，用於將頻道創建在特定類別下。

                  注意：
                  - 請務必以「字串」形式傳入完整的雪花 ID（例："123456789012345678"），避免 JSON 數字精度損失。
                  - 若不提供，頻道將建立在伺服器頂層。
                  """,
              required = false)
          String categoryId,
      @P(
              value =
                  """
                  權限設定列表，用於控制特定角色對此頻道的訪問權限。

                  每個設定包含：
                  - roleId：Discord 角色的 ID（數字）
                  - permissionSet：權限集合名稱，可選值：
                    * "admin_only"：僅管理員可發言（所有人可查看）
                    * "private"：私密頻道（指定角色才能查看和發言）

                  範例：
                  - [{"roleId": 123456789, "permissionSet": "admin_only"}]
                  - [{"roleId": 987654321, "permissionSet": "private"}]

                  如不提供此參數，頻道將使用默認權限（所有成員可查看和發言）。
                  """,
              required = false)
          List<PermissionSetting> permissions,
      InvocationParameters parameters) {

    // 1. 驗證參數
    String validationError = validateName(name);
    if (validationError != null) {
      return buildErrorResponse(validationError);
    }

    // 2. 從 InvocationParameters 獲取執行上下文
    Long guildId = parameters.get("guildId");
    if (guildId == null) {
      LOGGER.error("LangChain4jCreateChannelTool: guildId 未設置");
      return buildErrorResponse("guildId 未設置");
    }

    // 3. 獲取 Guild
    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      String errorMsg = String.format("找不到指定的伺服器: %d", guildId);
      LOGGER.warn("LangChain4jCreateChannelTool: {}", errorMsg);
      return buildErrorResponse("找不到伺服器");
    }

    try {
      // 4. 驗證類別（如果提供）
      Category category = null;
      Long parsedCategoryId = parseSnowflakeId(categoryId);
      if (categoryId != null && parsedCategoryId == null) {
        LOGGER.warn("LangChain4jCreateChannelTool: 類別 ID 格式無效: {}", categoryId);
        return buildErrorResponse("類別 ID 格式無效，請以字串提供完整的類別 ID");
      }

      if (parsedCategoryId != null) {
        category = findCategoryWithRetry(guild, parsedCategoryId);
        if (category == null) {
          String errorMsg = String.format("找不到指定的類別: %s", categoryId);
          LOGGER.warn("LangChain4jCreateChannelTool: {}", errorMsg);
          return buildErrorResponse("找不到指定的類別");
        }
      }

      // 5. 創建頻道
      TextChannel channel = createChannelWithTimeout(guild, name, category);
      if (channel == null) {
        return buildErrorResponse("創建頻道失敗");
      }

      // 6. 應用權限（如果有提供）
      if (permissions != null && !permissions.isEmpty()) {
        applyPermissions(channel, permissions, guild);
      }

      // 7. 返回成功結果
      String resultMsg =
          String.format("成功創建頻道: %s (ID: %d)", channel.getName(), channel.getIdLong());
      LOGGER.info("LangChain4jCreateChannelTool: {}", resultMsg);
      return buildSuccessResponse(resultMsg, channel.getIdLong(), channel.getName());

    } catch (InsufficientPermissionException e) {
      String errorMsg = String.format("機器人沒有足夠的權限創建頻道: %s", e.getMessage());
      LOGGER.warn("LangChain4jCreateChannelTool: {}", errorMsg);
      return buildErrorResponse("機器人沒有足夠的權限");

    } catch (TimeoutException e) {
      String errorMsg = String.format("創建頻道逾時: %s", e.getMessage());
      LOGGER.warn("LangChain4jCreateChannelTool: {}", errorMsg);
      return buildErrorResponse("創建頻道逾時");

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      String errorMsg = String.format("創建頻道被中斷: %s", e.getMessage());
      LOGGER.error("LangChain4jCreateChannelTool: {}", errorMsg);
      return buildErrorResponse("創建頻道被中斷");

    } catch (Exception e) {
      String errorMsg = String.format("創建頻道失敗: %s", e.getMessage());
      LOGGER.error("LangChain4jCreateChannelTool: {}", errorMsg, e);
      return buildErrorResponse(errorMsg);
    }
  }

  /**
   * 查找類別，帶重試機制。
   *
   * <p>由於 JDA 的緩存需要通過 Gateway 事件更新，剛創建的類別可能無法立即找到。 此方法會嘗試多次，每次間隔一段時間，給 JDA 足夠的時間更新緩存。
   *
   * @param guild Discord 伺服器
   * @param categoryId 類別 ID
   * @return 找到的類別，如果所有重試都失敗則返回 null
   */
  private Category findCategoryWithRetry(Guild guild, long categoryId) {
    for (int attempt = 0; attempt < CATEGORY_LOOKUP_MAX_RETRIES; attempt++) {
      // 嘗試從 guild 緩存獲取
      Category category = guild.getCategoryById(categoryId);
      if (category != null) {
        if (attempt > 0) {
          LOGGER.info("LangChain4jCreateChannelTool: 在第 {} 次嘗試中找到類別 {}", attempt + 1, categoryId);
        }
        return category;
      }

      // 嘗試從 JDA 實例獲取
      category = JDAProvider.getJda().getCategoryById(categoryId);
      if (category != null) {
        if (attempt > 0) {
          LOGGER.info(
              "LangChain4jCreateChannelTool: 在第 {} 次嘗試中從 JDA 實例找到類別 {}", attempt + 1, categoryId);
        }
        return category;
      }

      // 如果不是最後一次嘗試，等待後再重試
      if (attempt < CATEGORY_LOOKUP_MAX_RETRIES - 1) {
        try {
          LOGGER.debug(
              "LangChain4jCreateChannelTool: 類別 {} 暫時找不到（第 {} 次嘗試），{}ms 後重試",
              categoryId,
              attempt + 1,
              CATEGORY_LOOKUP_RETRY_DELAY_MS);
          Thread.sleep(CATEGORY_LOOKUP_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          LOGGER.warn("LangChain4jCreateChannelTool: 類別查找重試被中斷");
          return null;
        }
      }
    }

    LOGGER.warn(
        "LangChain4jCreateChannelTool: 類別 {} 在 {} 次重試後仍未找到",
        categoryId,
        CATEGORY_LOOKUP_MAX_RETRIES);
    return null;
  }

  /**
   * 驗證頻道名稱。
   *
   * @param name 頻道名稱
   * @return 錯誤訊息，如果驗證通過則返回 null
   */
  private String validateName(String name) {
    if (name == null || name.isBlank()) {
      return "頻道名稱不能為空";
    }

    if (name.length() > MAX_CHANNEL_NAME_LENGTH) {
      return String.format("頻道名稱不能超過 %d 字符（當前: %d）", MAX_CHANNEL_NAME_LENGTH, name.length());
    }

    return null;
  }

  /**
   * 解析字串形式的雪花 ID，避免因 JSON 數字精度損失造成 ID 失真。
   *
   * @param snowflakeId 字串形式的 ID
   * @return 解析後的 long 值；若輸入為 null 或空白則回傳 null；若格式錯誤則回傳 null
   */
  private Long parseSnowflakeId(String snowflakeId) {
    if (snowflakeId == null || snowflakeId.isBlank()) {
      return null;
    }

    try {
      return Long.parseUnsignedLong(snowflakeId.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /**
   * 創建頻道並設定逾時。
   *
   * @param guild Discord 伺服器
   * @param channelName 頻道名稱
   * @param category 類別（可選）
   * @return 創建的頻道
   * @throws Exception 如果創建失敗或逾時
   */
  private TextChannel createChannelWithTimeout(Guild guild, String channelName, Category category)
      throws Exception {
    var channelAction = guild.createTextChannel(channelName);

    // 如果指定了類別，設置父類別
    if (category != null) {
      channelAction.setParent(category);
    }

    CompletableFuture<TextChannel> future = channelAction.submit();

    try {
      return future.get(CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    } catch (TimeoutException e) {
      future.cancel(true);
      throw e;
    }
  }

  /**
   * 應用權限設定到頻道。
   *
   * @param channel 文字頻道
   * @param permissionsParam 權限參數列表
   * @param guild Discord 伺服器
   */
  private void applyPermissions(
      TextChannel channel, List<PermissionSetting> permissionsParam, Guild guild) {
    try {
      for (PermissionSetting setting : permissionsParam) {
        long roleId = setting.roleId();
        Role role = guild.getRoleById(roleId);
        if (role == null) {
          LOGGER.warn("LangChain4jCreateChannelTool: 角色不存在 (ID: {}), 跳過權限設定", roleId);
          continue;
        }

        ChannelPermission permission = PermissionParser.parse(setting);
        applyPermissionToChannel(channel, role, permission.permissionSet());
      }

    } catch (Exception e) {
      LOGGER.warn("LangChain4jCreateChannelTool: 應用權限時發生錯誤: {}", e.getMessage());
      // 權限應用失敗不影響頻道創建，僅記錄警告
    }
  }

  /**
   * 將權限集合應用到頻道的角色。
   *
   * @param channel 文字頻道
   * @param role 角色
   * @param permissions 權限集合
   */
  private void applyPermissionToChannel(
      TextChannel channel, Role role, java.util.EnumSet<Permission> permissions) {

    try {
      channel
          .upsertPermissionOverride(role)
          .setPermissions(permissions, java.util.EnumSet.noneOf(Permission.class))
          .complete();

      LOGGER.debug("LangChain4jCreateChannelTool: 已為角色 {} 設定權限: {}", role.getIdLong(), permissions);

    } catch (InsufficientPermissionException e) {
      LOGGER.warn(
          "LangChain4jCreateChannelTool: 沒有足夠權限設定角色 {} 的權限: {}", role.getIdLong(), e.getMessage());

    } catch (Exception e) {
      LOGGER.error(
          "LangChain4jCreateChannelTool: 設定角色 {} 權限時發生錯誤: {}", role.getIdLong(), e.getMessage(), e);
    }
  }

  /**
   * 構建成功回應。
   *
   * @param message 成功訊息
   * @param channelId 頻道 ID
   * @param channelName 頻道名稱
   * @return JSON 格式的成功回應
   */
  private String buildSuccessResponse(String message, long channelId, String channelName) {
    return """
    {
      "success": true,
      "message": "%s",
      "channelId": "%d",
      "channelName": "%s"
    }
    """
        .formatted(message, channelId, channelName);
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
