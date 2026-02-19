package ltdjms.discord.aiagent.services.tools;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/** 刪除 Discord 資源（頻道、類別、身分組）工具。 */
public final class LangChain4jDeleteDiscordResourceTool {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LangChain4jDeleteDiscordResourceTool.class);

  @Inject
  public LangChain4jDeleteDiscordResourceTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 刪除指定 Discord 資源。
   *
   * @param resourceType 資源類型：channel/category/role
   * @param resourceId 資源 ID
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 執行結果 JSON 字串
   */
  @Tool(
      """
      刪除 Discord 伺服器資源。

      支援資源類型：
      - channel：刪除一般頻道（文字/語音/論壇等）
      - category：刪除類別（必須先清空類別內頻道）
      - role：刪除身分組

      參數說明：
      - resourceType：資源類型（channel/category/role）
      - resourceId：要刪除的資源 ID（字串）

      注意事項：
      - category 若仍包含頻道，會拒絕刪除並提示先清空
      - @everyone 與受管理身分組（managed role）不可刪除
      """)
  public String deleteDiscordResource(
      @P(value = "資源類型：channel/category/role", required = true) String resourceType,
      @P(value = "資源 ID（字串）", required = true) String resourceId,
      InvocationParameters parameters) {

    if (resourceType == null || resourceType.isBlank()) {
      return ToolJsonResponses.error("resourceType 不能為空");
    }
    if (resourceId == null || resourceId.isBlank()) {
      return ToolJsonResponses.error("resourceId 不能為空");
    }

    ResourceType parsedResourceType = parseResourceType(resourceType);
    if (parsedResourceType == null) {
      return ToolJsonResponses.error("resourceType 必須是 channel、category 或 role");
    }

    Long parsedResourceId = parseSnowflakeId(resourceId);
    if (parsedResourceId == null) {
      return ToolJsonResponses.error("無效的 resourceId 格式");
    }

    Long guildId = parameters.get("guildId");
    if (guildId == null) {
      LOGGER.error("LangChain4jDeleteDiscordResourceTool: guildId 未設置");
      return ToolJsonResponses.error("guildId 未設置");
    }

    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      LOGGER.warn("LangChain4jDeleteDiscordResourceTool: 找不到指定伺服器: {}", guildId);
      return ToolJsonResponses.error("找不到伺服器");
    }

    String authorizationError =
        ToolCallerAuthorizationGuard.validateAdministrator(
            parameters, guild, LOGGER, "LangChain4jDeleteDiscordResourceTool");
    if (authorizationError != null) {
      return ToolJsonResponses.error(authorizationError);
    }

    try {
      return switch (parsedResourceType) {
        case CHANNEL -> deleteChannel(guild, parsedResourceId);
        case CATEGORY -> deleteCategory(guild, parsedResourceId);
        case ROLE -> deleteRole(guild, parsedResourceId);
      };
    } catch (InsufficientPermissionException e) {
      LOGGER.warn("LangChain4jDeleteDiscordResourceTool: 權限不足: {}", e.getMessage());
      return ToolJsonResponses.error("機器人沒有足夠的權限刪除該資源");
    } catch (Exception e) {
      LOGGER.error("LangChain4jDeleteDiscordResourceTool: 刪除資源失敗", e);
      return ToolJsonResponses.error("刪除資源失敗: " + extractErrorMessage(e));
    }
  }

  private String deleteChannel(Guild guild, long channelId) {
    GuildChannel channel = guild.getGuildChannelById(channelId);
    if (channel == null) {
      return ToolJsonResponses.error("找不到指定頻道");
    }

    if (channel instanceof Category) {
      return ToolJsonResponses.error("此 ID 對應到類別，請使用 resourceType=category");
    }

    String channelName = channel.getName();
    channel.delete().complete();

    LOGGER.info(
        "LangChain4jDeleteDiscordResourceTool: 成功刪除頻道 {}, guildId={}",
        channelId,
        guild.getIdLong());

    return ToolJsonResponses.customSuccess(
        builder ->
            builder
                .message("頻道刪除成功")
                .put("resourceType", ResourceType.CHANNEL.value)
                .put("resourceId", channelId)
                .put("resourceName", channelName));
  }

  private String deleteCategory(Guild guild, long categoryId) {
    Category category = guild.getCategoryById(categoryId);
    if (category == null) {
      return ToolJsonResponses.error("找不到指定類別");
    }

    int childChannelCount = category.getChannels().size();
    if (childChannelCount > 0) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("categoryId", categoryId);
      details.put("categoryName", category.getName());
      details.put("childChannelCount", childChannelCount);
      return ToolJsonResponses.error("類別內仍有頻道，請先移動或刪除子頻道", details);
    }

    String categoryName = category.getName();
    category.delete().complete();

    LOGGER.info(
        "LangChain4jDeleteDiscordResourceTool: 成功刪除類別 {}, guildId={}",
        categoryId,
        guild.getIdLong());

    return ToolJsonResponses.customSuccess(
        builder ->
            builder
                .message("類別刪除成功")
                .put("resourceType", ResourceType.CATEGORY.value)
                .put("resourceId", categoryId)
                .put("resourceName", categoryName));
  }

  private String deleteRole(Guild guild, long roleId) {
    Role role = guild.getRoleById(roleId);
    if (role == null) {
      return ToolJsonResponses.error("找不到指定身分組");
    }

    if (role.isPublicRole()) {
      return ToolJsonResponses.error("@everyone 身分組不可刪除");
    }
    if (role.isManaged()) {
      return ToolJsonResponses.error("受管理的身分組不可刪除");
    }

    String roleName = role.getName();
    role.delete().complete();

    LOGGER.info(
        "LangChain4jDeleteDiscordResourceTool: 成功刪除身分組 {}, guildId={}", roleId, guild.getIdLong());

    return ToolJsonResponses.customSuccess(
        builder ->
            builder
                .message("身分組刪除成功")
                .put("resourceType", ResourceType.ROLE.value)
                .put("resourceId", roleId)
                .put("resourceName", roleName));
  }

  private ResourceType parseResourceType(String resourceType) {
    if (resourceType == null || resourceType.isBlank()) {
      return null;
    }

    String normalized = resourceType.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "channel", "頻道" -> ResourceType.CHANNEL;
      case "category", "類別" -> ResourceType.CATEGORY;
      case "role", "身分組", "角色" -> ResourceType.ROLE;
      default -> null;
    };
  }

  private Long parseSnowflakeId(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    String value = raw.trim();

    if (value.startsWith("<@&") && value.endsWith(">")) {
      value = value.substring(3, value.length() - 1);
    } else if (value.startsWith("<#") && value.endsWith(">")) {
      value = value.substring(2, value.length() - 1);
    } else if (value.startsWith("<") && value.endsWith(">")) {
      value = value.substring(1, value.length() - 1);
    }

    int slashIndex = value.lastIndexOf('/');
    if (slashIndex >= 0 && slashIndex < value.length() - 1) {
      value = value.substring(slashIndex + 1);
    }

    try {
      return Long.parseUnsignedLong(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String extractErrorMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }

    String message = current.getMessage();
    if (message == null || message.isBlank()) {
      return current.getClass().getSimpleName();
    }
    return current.getClass().getSimpleName() + ": " + message;
  }

  private enum ResourceType {
    CHANNEL("channel"),
    CATEGORY("category"),
    ROLE("role");

    private final String value;

    ResourceType(String value) {
      this.value = value;
    }
  }
}
