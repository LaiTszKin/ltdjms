package ltdjms.discord.aiagent.services.tools;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/** 在 Discord 類別之間移動頻道的工具。 */
public final class LangChain4jMoveChannelTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jMoveChannelTool.class);

  @Inject
  public LangChain4jMoveChannelTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 將指定頻道移動到目標類別。
   *
   * @param channelId 要移動的頻道 ID
   * @param targetCategoryId 目標類別 ID
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 執行結果 JSON 字串
   */
  @Tool(
      """
      將指定頻道移動到另一個類別。

      使用場景：
      - 重整伺服器頻道結構
      - 將既有頻道改放到其他分類底下

      參數說明：
      - channelId：要移動的頻道 ID（字串）
      - targetCategoryId：目標類別 ID（字串）

      注意事項：
      - 只有可分類的頻道可移動（如文字、語音、論壇等）
      - 類別本身不可作為被移動對象
      """)
  public String moveChannel(
      @P(value = "要移動的頻道 ID（字串）", required = true) String channelId,
      @P(value = "目標類別 ID（字串）", required = true) String targetCategoryId,
      InvocationParameters parameters) {

    if (channelId == null || channelId.isBlank()) {
      return ToolJsonResponses.error("channelId 不能為空");
    }
    if (targetCategoryId == null || targetCategoryId.isBlank()) {
      return ToolJsonResponses.error("targetCategoryId 不能為空");
    }

    Long parsedChannelId = parseSnowflakeId(channelId);
    if (parsedChannelId == null) {
      return ToolJsonResponses.error("無效的 channelId 格式");
    }

    Long parsedCategoryId = parseSnowflakeId(targetCategoryId);
    if (parsedCategoryId == null) {
      return ToolJsonResponses.error("無效的 targetCategoryId 格式");
    }

    Long guildId = parameters.get("guildId");
    if (guildId == null) {
      LOGGER.error("LangChain4jMoveChannelTool: guildId 未設置");
      return ToolJsonResponses.error("guildId 未設置");
    }

    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      LOGGER.warn("LangChain4jMoveChannelTool: 找不到指定伺服器: {}", guildId);
      return ToolJsonResponses.error("找不到伺服器");
    }

    String authorizationError =
        ToolCallerAuthorizationGuard.validateAdministrator(
            parameters, guild, LOGGER, "LangChain4jMoveChannelTool");
    if (authorizationError != null) {
      return ToolJsonResponses.error(authorizationError);
    }

    GuildChannel guildChannel = guild.getGuildChannelById(parsedChannelId);
    if (guildChannel == null) {
      return ToolJsonResponses.error("找不到指定頻道");
    }

    if (!(guildChannel instanceof ICategorizableChannel categorizableChannel)) {
      return ToolJsonResponses.error("該頻道類型不支援移動到類別");
    }

    Category targetCategory = guild.getCategoryById(parsedCategoryId);
    if (targetCategory == null) {
      return ToolJsonResponses.error("找不到指定類別");
    }

    Category currentParent = categorizableChannel.getParentCategory();
    if (currentParent != null && currentParent.getIdLong() == parsedCategoryId) {
      return ToolJsonResponses.customSuccess(
          builder ->
              builder
                  .message("頻道已經在目標類別中")
                  .put("channelId", parsedChannelId)
                  .put("channelName", guildChannel.getName())
                  .put("targetCategoryId", parsedCategoryId)
                  .put("targetCategoryName", targetCategory.getName()));
    }

    try {
      categorizableChannel.getManager().setParent(targetCategory).complete();

      LOGGER.info(
          "LangChain4jMoveChannelTool: 成功移動頻道 {}, targetCategory={}",
          parsedChannelId,
          parsedCategoryId);

      return ToolJsonResponses.customSuccess(
          builder ->
              builder
                  .message("頻道移動成功")
                  .put("channelId", parsedChannelId)
                  .put("channelName", guildChannel.getName())
                  .put("targetCategoryId", parsedCategoryId)
                  .put("targetCategoryName", targetCategory.getName()));
    } catch (InsufficientPermissionException e) {
      LOGGER.warn("LangChain4jMoveChannelTool: 權限不足，無法移動頻道: {}", e.getMessage());
      return ToolJsonResponses.error("機器人沒有足夠的權限移動頻道");
    } catch (Exception e) {
      LOGGER.error("LangChain4jMoveChannelTool: 移動頻道失敗", e);
      return ToolJsonResponses.error("移動頻道失敗: " + extractErrorMessage(e));
    }
  }

  private Long parseSnowflakeId(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    String value = raw.trim();

    if (value.startsWith("<#") && value.endsWith(">")) {
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
}
