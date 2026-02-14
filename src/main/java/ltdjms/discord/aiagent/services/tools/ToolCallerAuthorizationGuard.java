package ltdjms.discord.aiagent.services.tools;

import org.slf4j.Logger;

import dev.langchain4j.invocation.InvocationParameters;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

/** 工具呼叫者授權檢查。 */
final class ToolCallerAuthorizationGuard {

  private static final String MISSING_PARAMETERS_MESSAGE = "缺少調用參數";
  private static final String MISSING_USER_ID_MESSAGE = "userId 未設置";
  private static final String MEMBER_NOT_FOUND_MESSAGE = "找不到呼叫者成員資訊";
  private static final String ADMIN_REQUIRED_MESSAGE = "你沒有權限使用此工具";

  private ToolCallerAuthorizationGuard() {
    // 工具類，不允許實例化
  }

  /**
   * 驗證工具呼叫者是否具備管理員權限。
   *
   * @param parameters 工具調用參數
   * @param guild 伺服器
   * @param logger 日誌
   * @param toolName 工具名稱
   * @return 驗證失敗時返回錯誤訊息，成功返回 null
   */
  static String validateAdministrator(
      InvocationParameters parameters, Guild guild, Logger logger, String toolName) {

    if (parameters == null) {
      logger.warn("{}: 缺少調用參數", toolName);
      return MISSING_PARAMETERS_MESSAGE;
    }

    Long userId = parameters.get("userId");
    if (userId == null) {
      logger.warn("{}: userId 未設置", toolName);
      return MISSING_USER_ID_MESSAGE;
    }

    Member caller = resolveCaller(guild, userId);
    if (caller == null) {
      logger.warn("{}: 找不到呼叫者成員資訊, userId={}", toolName, userId);
      return MEMBER_NOT_FOUND_MESSAGE;
    }

    if (isGuildOwner(guild, userId) || caller.hasPermission(Permission.ADMINISTRATOR)) {
      return null;
    }

    logger.warn("{}: 拒絕非管理員工具調用, guildId={}, userId={}", toolName, guild.getIdLong(), userId);
    return ADMIN_REQUIRED_MESSAGE;
  }

  private static boolean isGuildOwner(Guild guild, long userId) {
    try {
      return guild.getOwnerIdLong() == userId;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static Member resolveCaller(Guild guild, long userId) {
    Member caller = guild.getMemberById(userId);
    if (caller != null) {
      return caller;
    }

    try {
      return guild.retrieveMemberById(userId).complete();
    } catch (Exception ignored) {
      return null;
    }
  }
}
