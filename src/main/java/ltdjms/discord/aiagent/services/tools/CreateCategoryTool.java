package ltdjms.discord.aiagent.services.tools;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aiagent.domain.ChannelPermission;
import ltdjms.discord.aiagent.domain.ToolExecutionResult;
import ltdjms.discord.aiagent.services.PermissionParser;
import ltdjms.discord.aiagent.services.Tool;
import ltdjms.discord.aiagent.services.ToolContext;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/**
 * 創建 Discord 類別工具。
 *
 * <p>此工具用於在指定的 Discord 伺服器中創建新的類別，並可選擇性地設定權限。
 *
 * <p>支援的功能：
 *
 * <ul>
 *   <li>創建指定名稱的類別
 *   <li>設定類別權限（支援預設權限模式和自定義權限）
 *   <li>逾時處理與錯誤恢復
 * </ul>
 */
public final class CreateCategoryTool implements Tool {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateCategoryTool.class);

  /** 類別名稱最大長度 */
  private static final int MAX_CATEGORY_NAME_LENGTH = 100;

  /** 類別創建逾時時間（秒） */
  private static final int CREATION_TIMEOUT_SECONDS = 30;

  @Override
  public String name() {
    return "create_category";
  }

  @Override
  public ToolExecutionResult execute(Map<String, Object> parameters, ToolContext context) {
    // 1. 驗證參數
    Result<Unit, String> validationResult = validateParameters(parameters);
    if (validationResult.isErr()) {
      return ToolExecutionResult.failure(validationResult.getError());
    }

    String categoryName = (String) parameters.get("name");

    // 2. 獲取 Guild
    Guild guild = context.jda().getGuildById(context.guildId());
    if (guild == null) {
      String errorMsg = String.format("找不到指定的伺服器: %d", context.guildId());
      LOGGER.warn("CreateCategoryTool: {}", errorMsg);
      return ToolExecutionResult.failure("找不到伺服器");
    }

    try {
      // 3. 創建類別
      Category category = createCategoryWithTimeout(guild, categoryName);
      if (category == null) {
        return ToolExecutionResult.failure("創建類別失敗");
      }

      // 4. 應用權限（如果有提供）
      Object permissionsParam = parameters.get("permissions");
      if (permissionsParam != null) {
        applyPermissions(category, permissionsParam, guild);
      }

      // 5. 返回成功結果
      String resultMsg =
          String.format("成功創建類別: %s (ID: %d)", category.getName(), category.getIdLong());
      LOGGER.info("CreateCategoryTool: {}", resultMsg);
      return ToolExecutionResult.success(resultMsg);

    } catch (InsufficientPermissionException e) {
      String errorMsg = String.format("機器人沒有足夠的權限創建類別: %s", e.getMessage());
      LOGGER.warn("CreateCategoryTool: {}", errorMsg);
      return ToolExecutionResult.failure("機器人沒有足夠的權限");

    } catch (TimeoutException e) {
      String errorMsg = String.format("創建類別逾時: %s", e.getMessage());
      LOGGER.warn("CreateCategoryTool: {}", errorMsg);
      return ToolExecutionResult.failure("創建類別逾時");

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      String errorMsg = String.format("創建類別被中斷: %s", e.getMessage());
      LOGGER.error("CreateCategoryTool: {}", errorMsg);
      return ToolExecutionResult.failure("創建類別被中斷");

    } catch (Exception e) {
      String errorMsg = String.format("創建類別失敗: %s", e.getMessage());
      LOGGER.error("CreateCategoryTool: {}", errorMsg, e);
      return ToolExecutionResult.failure(errorMsg);
    }
  }

  /**
   * 驗證工具參數。
   *
   * @param parameters 工具參數
   * @return 驗證結果
   */
  private Result<Unit, String> validateParameters(Map<String, Object> parameters) {
    Object nameObj = parameters.get("name");

    if (nameObj == null) {
      return Result.err("類別名稱不能為空");
    }

    if (!(nameObj instanceof String)) {
      return Result.err("類別名稱必須是字串");
    }

    String categoryName = (String) nameObj;

    if (categoryName.isBlank()) {
      return Result.err("類別名稱不能為空");
    }

    if (categoryName.length() > MAX_CATEGORY_NAME_LENGTH) {
      return Result.err(
          String.format("類別名稱不能超過 %d 字符（當前: %d）", MAX_CATEGORY_NAME_LENGTH, categoryName.length()));
    }

    return Result.okVoid();
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
   * @param permissionsParam 權限參數（可以是字串或列表）
   * @param guild Discord 伺服器
   */
  @SuppressWarnings("unchecked")
  private void applyPermissions(Category category, Object permissionsParam, Guild guild) {
    try {
      if (permissionsParam instanceof String permissionDesc) {
        // 單一字串描述（適用於 @everyone）
        applyStringPermission(category, permissionDesc, guild.getPublicRole());

      } else if (permissionsParam instanceof List<?> permissionsList) {
        // 權限列表
        for (Object item : permissionsList) {
          if (item instanceof Map<?, ?> permMap) {
            applyMapPermission(category, permMap, guild);
          }
        }
      }

    } catch (Exception e) {
      LOGGER.warn("CreateCategoryTool: 應用權限時發生錯誤: {}", e.getMessage());
      // 權限應用失敗不影響類別創建，僅記錄警告
    }
  }

  /**
   * 應用字串描述的權限。
   *
   * @param category 類別
   * @param permissionDesc 權限描述
   * @param role 要應用權限的角色
   */
  private void applyStringPermission(Category category, String permissionDesc, Role role) {
    if (role == null) {
      LOGGER.warn("CreateCategoryTool: 角色不存在，跳過權限設定");
      return;
    }

    ChannelPermission permission = PermissionParser.parse(permissionDesc, role.getIdLong());
    applyPermissionToCategory(category, role, permission.permissionSet());
  }

  /**
   * 應用 Map 格式的權限。
   *
   * @param category 類別
   * @param permMap 權限 Map
   * @param guild Discord 伺服器
   */
  @SuppressWarnings("unchecked")
  private void applyMapPermission(Category category, Map<?, ?> permMap, Guild guild) {
    Object roleIdObj = permMap.get("roleId");
    if (roleIdObj == null) {
      LOGGER.warn("CreateCategoryTool: 權限設定缺少 roleId，跳過");
      return;
    }

    long roleId = ((Number) roleIdObj).longValue();
    Role role = guild.getRoleById(roleId);
    if (role == null) {
      LOGGER.warn("CreateCategoryTool: 角色不存在 (ID: {}), 跳過權限設定", roleId);
      return;
    }

    ChannelPermission permission = PermissionParser.parse(permMap, roleId);
    applyPermissionToCategory(category, role, permission.permissionSet());
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

      LOGGER.debug("CreateCategoryTool: 已為角色 {} 設定權限: {}", role.getIdLong(), permissions);

    } catch (InsufficientPermissionException e) {
      LOGGER.warn("CreateCategoryTool: 沒有足夠權限設定角色 {} 的權限: {}", role.getIdLong(), e.getMessage());

    } catch (Exception e) {
      LOGGER.error("CreateCategoryTool: 設定角色 {} 權限時發生錯誤: {}", role.getIdLong(), e.getMessage(), e);
    }
  }
}
