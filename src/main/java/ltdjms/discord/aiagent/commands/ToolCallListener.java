package ltdjms.discord.aiagent.commands;

import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aiagent.services.ToolCallRequest;
import ltdjms.discord.aiagent.services.ToolCallRequestParser;
import ltdjms.discord.aiagent.services.ToolExecutor;
import ltdjms.discord.shared.di.JDAProvider;
import ltdjms.discord.shared.events.AIMessageEvent;
import ltdjms.discord.shared.events.DomainEvent;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * AI 工具調用監聽器。
 *
 * <p>監聽 AI 回應事件，當檢測到工具調用請求時執行對應的工具。
 *
 * <p>支援的工具調用格式：
 *
 * <ul>
 *   <li>JSON 格式：{@code {"tool": "工具名稱", "parameters": {...}}}
 *   <li>簡化格式：{@code @tool(工具名稱) 参数1=值1 参数2=值2}
 * </ul>
 *
 * <p>工具執行結果會記錄在日誌中，實際的 Discord 訊息發送由工具內部處理。
 */
public final class ToolCallListener implements Consumer<DomainEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallListener.class);

  private final AIAgentChannelConfigService configService;
  private final ToolExecutor toolExecutor;

  /**
   * 建立監聽器。
   *
   * @param configService AI Agent 配置服務
   * @param toolExecutor 工具執行器
   */
  @Inject
  public ToolCallListener(AIAgentChannelConfigService configService, ToolExecutor toolExecutor) {
    this.configService = configService;
    this.toolExecutor = toolExecutor;
  }

  @Override
  public void accept(DomainEvent event) {
    if (!(event instanceof AIMessageEvent aiMessageEvent)) {
      return;
    }

    handleAIMessage(aiMessageEvent);
  }

  /**
   * 處理 AI 訊息事件。
   *
   * @param event AI 訊息事件
   */
  private void handleAIMessage(AIMessageEvent event) {
    try {
      long guildId = event.guildId();
      long channelId = Long.parseLong(event.channelId());
      long userId = Long.parseLong(event.userId());

      // 1. 檢查頻道是否啟用 Agent 模式
      if (!configService.isAgentEnabled(guildId, channelId)) {
        LOGGER.debug("頻道 {} 未啟用 AI Agent 模式，忽略工具調用檢查", channelId);
        return;
      }

      String aiResponse = event.aiResponse();

      // 2. 解析工具調用請求
      Optional<ToolCallRequest> request = parseToolCall(aiResponse, guildId, channelId, userId);

      if (request.isEmpty()) {
        LOGGER.debug("AI 回應中未檢測到工具調用請求");
        return;
      }

      ToolCallRequest toolCallRequest = request.get();
      LOGGER.info(
          "檢測到工具調用：工具={}, 伺服器={}, 頻道={}, 用戶={}",
          toolCallRequest.toolName(),
          guildId,
          channelId,
          userId);

      // 3. 執行工具並處理結果
      executeToolAndLogResult(toolCallRequest);

    } catch (NumberFormatException e) {
      LOGGER.error("解析頻道或使用者 ID 失敗", e);
    } catch (Exception e) {
      LOGGER.error("處理 AI 訊息事件時發生錯誤", e);
    }
  }

  /**
   * 解析 AI 回應中的工具調用請求。
   *
   * @param aiResponse AI 回應內容
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param userId 使用者 ID
   * @return 工具調用請求，如果未檢測到則返回空
   */
  private Optional<ToolCallRequest> parseToolCall(
      String aiResponse, long guildId, long channelId, long userId) {
    return ToolCallRequestParser.parse(aiResponse, guildId, channelId, userId);
  }

  /**
   * 執行工具並記錄結果。
   *
   * <p>工具執行結果會記錄在日誌中。實際的 Discord 訊息發送由工具內部處理。
   *
   * @param request 工具調用請求
   */
  private void executeToolAndLogResult(ToolCallRequest request) {
    toolExecutor
        .submit(request)
        .thenAccept(
            result -> {
              if (result.success()) {
                LOGGER.info(
                    "工具執行成功：工具={}, 頻道={}, 結果={}",
                    request.toolName(),
                    request.channelId(),
                    result.result().orElse("無"));
                sendToolResultMessage(request, "✅ ", result.result().orElse("已完成"));
              } else {
                LOGGER.error(
                    "工具執行失敗：工具={}, 頻道={}, 錯誤={}",
                    request.toolName(),
                    request.channelId(),
                    result.error().orElse("未知錯誤"));
                sendToolResultMessage(request, "❌ ", result.error().orElse("未知錯誤"));
              }
            })
        .exceptionally(
            ex -> {
              LOGGER.error("工具執行發生異常：工具={}, 頻道={}", request.toolName(), request.channelId(), ex);
              return null;
            });
  }

  private void sendToolResultMessage(ToolCallRequest request, String prefix, String message) {
    try {
      TextChannel channel = JDAProvider.getJda().getTextChannelById(request.channelId());
      if (channel == null) {
        LOGGER.warn("無法取得頻道以發送工具結果訊息: {}", request.channelId());
        return;
      }

      String content = prefix + message;
      channel.sendMessage(content).queue();
    } catch (Exception e) {
      LOGGER.warn("發送工具結果訊息失敗: {}", e.getMessage());
    }
  }
}
