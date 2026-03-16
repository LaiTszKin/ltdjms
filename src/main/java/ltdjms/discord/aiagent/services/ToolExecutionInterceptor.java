package ltdjms.discord.aiagent.services;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.aiagent.domain.ToolExecutionLog;
import ltdjms.discord.aiagent.persistence.ToolExecutionLogRepository;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.LangChain4jToolExecutedEvent;
import ltdjms.discord.shared.events.LangChain4jToolExecutionStartedEvent;

/**
 * 工具執行審計攔截器。
 *
 * <p>記錄所有 LangChain4J 工具調用到 tool_execution_log 表，並發布事件通知。 審計紀錄只保存去敏摘要，不保存原始參數、回傳內容或錯誤內容。
 *
 * <p>使用 ThreadLocal 存儲當前執行上下文，當工具執行完成時記錄審計日誌。
 */
public final class ToolExecutionInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutionInterceptor.class);

  private final ToolExecutionLogRepository logRepository;
  private final ObjectMapper objectMapper;
  private final DomainEventPublisher eventPublisher;
  private final ThreadLocal<ExecutionContext> context = new ThreadLocal<>();

  /**
   * 建立工具執行審計攔截器。
   *
   * @param logRepository 工具執行日誌 Repository
   * @param objectMapper JSON 序列化器
   * @param eventPublisher 領域事件發布器
   */
  @Inject
  public ToolExecutionInterceptor(
      ToolExecutionLogRepository logRepository,
      ObjectMapper objectMapper,
      DomainEventPublisher eventPublisher) {
    this.logRepository = logRepository;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
  }

  /**
   * 記錄工具執行開始。
   *
   * @param toolName 工具名稱
   * @param parameters 工具參數
   */
  public void onToolExecutionStarted(String toolName, Map<String, Object> parameters) {
    try {
      ToolExecutionContext.Context ctx = ToolExecutionContext.getContext();
      context.set(
          new ExecutionContext(
              ctx.guildId(),
              ctx.channelId(),
              ctx.userId(),
              toolName,
              parameters,
              System.currentTimeMillis()));

      LangChain4jToolExecutionStartedEvent event =
          new LangChain4jToolExecutionStartedEvent(
              ctx.guildId(), ctx.channelId(), ctx.userId(), toolName, Instant.now());
      eventPublisher.publish(event);
    } catch (IllegalStateException e) {
      LOGGER.warn("無法獲取工具執行上下文，跳過審計記錄");
    }
  }

  /**
   * 記錄工具執行成功並發布事件。
   *
   * @param result 執行結果
   * @return 工具執行通知訊息
   */
  public String onToolExecutionCompleted(String result) {
    ExecutionContext ctx = context.get();
    if (ctx == null) {
      LOGGER.debug("無工具執行上下文，跳過成功記錄");
      return result;
    }

    try {
      String parametersJson = summarizeParameters(ctx.parameters());
      String resultSummary = summarizeTextPayload(result);
      ToolExecutionLog log =
          ToolExecutionLog.success(
              ctx.guildId(),
              ctx.channelId(),
              ctx.userId(),
              ctx.toolName(),
              parametersJson,
              resultSummary);
      logRepository.save(log);

      // 發布工具執行事件
      LangChain4jToolExecutedEvent event =
          new LangChain4jToolExecutedEvent(
              ctx.guildId(),
              ctx.channelId(),
              ctx.userId(),
              ctx.toolName(),
              result,
              true,
              Instant.now());
      eventPublisher.publish(event);

      LOGGER.debug("工具執行審計已記錄: tool={}, success", ctx.toolName());

      // 返回工具執行通知訊息
      return "✅ 工具「" + getToolDisplayName(ctx.toolName()) + "」執行成功";

    } catch (Exception e) {
      LOGGER.error("記錄工具執行成功日誌失敗: tool={}", ctx.toolName(), e);
      return result;
    } finally {
      context.remove();
    }
  }

  /**
   * 記錄工具執行失敗並發布事件。
   *
   * @param error 錯誤訊息
   * @return 工具執行通知訊息
   */
  public String onToolExecutionFailed(String error) {
    ExecutionContext ctx = context.get();
    if (ctx == null) {
      LOGGER.debug("無工具執行上下文，跳過失敗記錄");
      return "❌ 工具執行失敗：" + error;
    }

    try {
      String parametersJson = summarizeParameters(ctx.parameters());
      String errorSummary = summarizeTextPayload(error);
      ToolExecutionLog log =
          ToolExecutionLog.failure(
              ctx.guildId(),
              ctx.channelId(),
              ctx.userId(),
              ctx.toolName(),
              parametersJson,
              errorSummary);
      logRepository.save(log);

      // 發布工具執行失敗事件
      LangChain4jToolExecutedEvent event =
          new LangChain4jToolExecutedEvent(
              ctx.guildId(),
              ctx.channelId(),
              ctx.userId(),
              ctx.toolName(),
              error,
              false,
              Instant.now());
      eventPublisher.publish(event);

      LOGGER.debug("工具執行審計已記錄: tool={}, failed", ctx.toolName());

      // 返回工具執行失敗通知訊息
      return "❌ 工具「" + getToolDisplayName(ctx.toolName()) + "」執行失敗：" + error;

    } catch (Exception e) {
      LOGGER.error("記錄工具執行失敗日誌失敗: tool={}", ctx.toolName(), e);
      return "❌ 工具執行失敗：" + error;
    } finally {
      context.remove();
    }
  }

  /**
   * 獲取工具的顯示名稱。
   *
   * @param toolName 工具名稱
   * @return 顯示名稱
   */
  private String getToolDisplayName(String toolName) {
    return switch (toolName) {
      case "createChannel" -> "創建頻道";
      case "createCategory" -> "創建類別";
      case "createRole" -> "創建角色";
      case "listChannels" -> "列出頻道";
      case "listCategories" -> "列出類別";
      case "listRoles" -> "列出角色";
      case "getChannelPermissions" -> "獲取頻道權限";
      case "getRolePermissions" -> "獲取角色權限";
      case "modifyChannelSettings", "modifyChannelPermissions" -> "修改頻道設定";
      case "modifyCategorySettings", "modifyCategoryPermissions" -> "修改類別設定";
      case "modifyRoleSettings", "modifyRolePermissions" -> "修改角色設定";
      default -> toolName;
    };
  }

  private String summarizeParameters(Map<String, Object> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return "{\"redacted\":true,\"entryCount\":0,\"keys\":[]}";
    }

    Map<String, Object> summary = new LinkedHashMap<>();
    Map<String, Object> valueSummaries = new LinkedHashMap<>();
    List<String> keys = parameters.keySet().stream().sorted().toList();

    summary.put("redacted", true);
    summary.put("entryCount", parameters.size());
    summary.put("keys", keys);

    for (String key : keys) {
      valueSummaries.put(key, summarizeValue(parameters.get(key)));
    }
    summary.put("values", valueSummaries);

    return toJson(summary, "{\"redacted\":true,\"entryCount\":0,\"keys\":[]}");
  }

  private Map<String, Object> summarizeValue(Object value) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("redacted", true);

    if (value == null) {
      summary.put("type", "null");
      return summary;
    }

    summary.put("type", value.getClass().getSimpleName());

    if (value instanceof CharSequence text) {
      summary.put("length", text.length());
      summary.put("blank", text.toString().isBlank());
    } else if (value instanceof Collection<?> collection) {
      summary.put("size", collection.size());
    } else if (value instanceof Map<?, ?> map) {
      summary.put("size", map.size());
    } else if (value.getClass().isArray()) {
      summary.put("size", Array.getLength(value));
    }

    summary.put("sha256", sha256Hex(safeValueFingerprint(value)));
    return summary;
  }

  private String summarizeTextPayload(String payload) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("redacted", true);
    summary.put("type", "text");
    summary.put("length", payload == null ? 0 : payload.length());
    summary.put("blank", payload == null || payload.isBlank());
    summary.put("sha256", sha256Hex(payload == null ? "" : payload));
    return toJson(summary, "{\"redacted\":true,\"type\":\"text\"}");
  }

  private String safeValueFingerprint(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      LOGGER.warn("無法序列化工具參數摘要來源，改用字串雜湊", e);
      return String.valueOf(value);
    }
  }

  private String toJson(Object value, String fallback) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      LOGGER.warn("轉換工具審計摘要為 JSON 失敗", e);
      return fallback;
    }
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * 工具執行上下文數據。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param userId 用戶 ID
   * @param toolName 工具名稱
   * @param parameters 工具參數
   * @param startTime 開始時間
   */
  private record ExecutionContext(
      long guildId,
      long channelId,
      long userId,
      String toolName,
      Map<String, Object> parameters,
      long startTime) {}
}
