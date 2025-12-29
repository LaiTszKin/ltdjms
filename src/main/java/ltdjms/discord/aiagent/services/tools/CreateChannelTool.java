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
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/**
 * 創建 Discord 頻道工具。
 *
 * <p>此工具用於在指定的 Discord 伺服器中創建新的文字頻道，並可選擇性地設定權限。
 *
 * <p>支援的功能：
 *
 * <ul>
 *   <li>創建指定名稱的文字頻道
 *   <li>設定頻道權限（支援預設權限模式和自定義權限）
 *   <li>逾時處理與錯誤恢復
 * </ul>
 */
public final class CreateChannelTool implements Tool {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateChannelTool.class);

  /** 頻道名稱最大長度 */
  private static final int MAX_CHANNEL_NAME_LENGTH = 100;

  /** 頻道創建逾時時間（秒） */
  private static final int CREATION_TIMEOUT_SECONDS = 30;

  @Override
  public String name() {
    return "create_channel";
  }

  @Override
  public ToolExecutionResult execute(Map<String, Object> parameters, ToolContext context) {
    // 1. 驗證參數
    Result<Unit, String> validationResult = validateParameters(parameters);
    if (validationResult.isErr()) {
      return ToolExecutionResult.failure(validationResult.getError());
    }

    String channelName = (String) parameters.get("name");

    // 2. 獲取 Guild
    Guild guild = context.jda().getGuildById(context.guildId());
    if (guild == null) {
      String errorMsg = String.format("找不到指定的伺服器: %d", context.guildId());
      LOGGER.warn("CreateChannelTool: {}", errorMsg);
      return ToolExecutionResult.failure("找不到伺服器");
    }

    try {
      // 3. 創建頻道
      TextChannel channel = createChannelWithTimeout(guild, channelName);
      if (channel == null) {
        return ToolExecutionResult.failure("創建頻道失敗");
      }

      // 4. 應用權限（如果有提供）
      Object permissionsParam = parameters.get("permissions");
      if (permissionsParam != null) {
        applyPermissions(channel, permissionsParam, guild);
      }

      // 5. 返回成功結果
      String resultMsg =
          String.format("成功創建頻道: %s (ID: %d)", channel.getName(), channel.getIdLong());
      LOGGER.info("CreateChannelTool: {}", resultMsg);
      return ToolExecutionResult.success(resultMsg);

    } catch (InsufficientPermissionException e) {
      String errorMsg = String.format("機器人沒有足夠的權限創建頻道: %s", e.getMessage());
      LOGGER.warn("CreateChannelTool: {}", errorMsg);
      return ToolExecutionResult.failure("機器人沒有足夠的權限");

    } catch (TimeoutException e) {
      String errorMsg = String.format("創建頻道逾時: %s", e.getMessage());
      LOGGER.warn("CreateChannelTool: {}", errorMsg);
      return ToolExecutionResult.failure("創建頻道逾時");

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      String errorMsg = String.format("創建頻道被中斷: %s", e.getMessage());
      LOGGER.error("CreateChannelTool: {}", errorMsg);
      return ToolExecutionResult.failure("創建頻道被中斷");

    } catch (Exception e) {
      String errorMsg = String.format("創建頻道失敗: %s", e.getMessage());
      LOGGER.error("CreateChannelTool: {}", errorMsg, e);
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
      return Result.err("頻道名稱不能為空");
    }

    if (!(nameObj instanceof String)) {
      return Result.err("頻道名稱必須是字串");
    }

    String channelName = (String) nameObj;

    if (channelName.isBlank()) {
      return Result.err("頻道名稱不能為空");
    }

    if (channelName.length() > MAX_CHANNEL_NAME_LENGTH) {
      return Result.err(
          String.format("頻道名稱不能超過 %d 字符（當前: %d）", MAX_CHANNEL_NAME_LENGTH, channelName.length()));
    }

    return Result.okVoid();
  }

  /**
   * 創建頻道並設定逾時。
   *
   * @param guild Discord 伺服器
   * @param channelName 頻道名稱
   * @return 創建的頻道
   * @throws Exception 如果創建失敗或逾時
   */
  private TextChannel createChannelWithTimeout(Guild guild, String channelName) throws Exception {
    CompletableFuture<TextChannel> future = guild.createTextChannel(channelName).submit();

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
   * @param permissionsParam 權限參數（可以是字串或列表）
   * @param guild Discord 伺服器
   */
  @SuppressWarnings("unchecked")
  private void applyPermissions(TextChannel channel, Object permissionsParam, Guild guild) {
    try {
      if (permissionsParam instanceof String permissionDesc) {
        // 單一字串描述（適用於 @everyone）
        applyStringPermission(channel, permissionDesc, guild.getPublicRole());

      } else if (permissionsParam instanceof List<?> permissionsList) {
        // 權限列表
        for (Object item : permissionsList) {
          if (item instanceof Map<?, ?> permMap) {
            applyMapPermission(channel, permMap, guild);
          }
        }
      }

    } catch (Exception e) {
      LOGGER.warn("CreateChannelTool: 應用權限時發生錯誤: {}", e.getMessage());
      // 權限應用失敗不影響頻道創建，僅記錄警告
    }
  }

  /**
   * 應用字串描述的權限。
   *
   * @param channel 文字頻道
   * @param permissionDesc 權限描述
   * @param role 要應用權限的角色
   */
  private void applyStringPermission(TextChannel channel, String permissionDesc, Role role) {
    if (role == null) {
      LOGGER.warn("CreateChannelTool: 角色不存在，跳過權限設定");
      return;
    }

    ChannelPermission permission = PermissionParser.parse(permissionDesc, role.getIdLong());
    applyPermissionToChannel(channel, role, permission.permissionSet());
  }

  /**
   * 應用 Map 格式的權限。
   *
   * @param channel 文字頻道
   * @param permMap 權限 Map
   * @param guild Discord 伺服器
   */
  @SuppressWarnings("unchecked")
  private void applyMapPermission(TextChannel channel, Map<?, ?> permMap, Guild guild) {
    Object roleIdObj = permMap.get("roleId");
    if (roleIdObj == null) {
      LOGGER.warn("CreateChannelTool: 權限設定缺少 roleId，跳過");
      return;
    }

    long roleId = ((Number) roleIdObj).longValue();
    Role role = guild.getRoleById(roleId);
    if (role == null) {
      LOGGER.warn("CreateChannelTool: 角色不存在 (ID: {}), 跳過權限設定", roleId);
      return;
    }

    ChannelPermission permission = PermissionParser.parse(permMap, roleId);
    applyPermissionToChannel(channel, role, permission.permissionSet());
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

      LOGGER.debug("CreateChannelTool: 已為角色 {} 設定權限: {}", role.getIdLong(), permissions);

    } catch (InsufficientPermissionException e) {
      LOGGER.warn("CreateChannelTool: 沒有足夠權限設定角色 {} 的權限: {}", role.getIdLong(), e.getMessage());

    } catch (Exception e) {
      LOGGER.error("CreateChannelTool: 設定角色 {} 權限時發生錯誤: {}", role.getIdLong(), e.getMessage(), e);
    }
  }
}
