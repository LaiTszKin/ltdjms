package ltdjms.discord.aiagent.services;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aiagent.domain.ToolDefinition;
import ltdjms.discord.aiagent.domain.ToolExecutionLog;
import ltdjms.discord.aiagent.domain.ToolExecutionResult;
import ltdjms.discord.aiagent.persistence.ToolExecutionLogRepository;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.di.JDAProvider;

/**
 * 預設實作的工具執行器。
 *
 * <p>使用 FIFO 佇列序列化執行工具調用，確保同時間只有一個工具在執行。 並記錄所有工具執行日誌到資料庫以供審計。
 */
public class DefaultToolExecutor implements ToolExecutor {

  private static final Logger logger = LoggerFactory.getLogger(DefaultToolExecutor.class);

  private final ToolRegistry registry;
  private final ToolExecutionLogRepository logRepository;
  private final BlockingQueue<QueueItem> queue;
  private final ExecutorService executor;
  private final AtomicBoolean running;

  /**
   * 建立工具執行器。
   *
   * <p>JDA 實例將在工具執行時從 {@link JDAProvider} 延遲獲取。
   *
   * @param registry 工具註冊中心
   * @param logRepository 工具執行日誌 Repository
   */
  @Inject
  public DefaultToolExecutor(ToolRegistry registry, ToolExecutionLogRepository logRepository) {
    this.registry = registry;
    this.logRepository = logRepository;
    this.queue = new LinkedBlockingQueue<>();
    this.executor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread thread = new Thread(r, "tool-executor-consumer");
              thread.setDaemon(false);
              return thread;
            });
    this.running = new AtomicBoolean(true);
    startConsumer();
  }

  @Override
  public CompletableFuture<ToolExecutionResult> submit(ToolCallRequest request) {
    CompletableFuture<ToolExecutionResult> future = new CompletableFuture<>();
    QueueItem item = new QueueItem(request, future);

    try {
      queue.put(item);
      logger.debug("工具調用請求已加入佇列：工具={}, 佇列大小={}", request.toolName(), queue.size());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.complete(ToolExecutionResult.failure("工具調用請求加入佇列時被中斷"));
    }

    return future;
  }

  @Override
  public ToolExecutionResult executeSync(ToolCallRequest request) {
    return execute(request);
  }

  @Override
  public int getQueueSize() {
    return queue.size();
  }

  /** 啟動消費者執行緒。 */
  private void startConsumer() {
    executor.submit(
        () -> {
          logger.info("工具執行器消費者執行緒已啟動");
          while (running.get()) {
            try {
              QueueItem item = queue.poll(1, TimeUnit.SECONDS);
              if (item != null) {
                ToolExecutionResult result = execute(item.request());
                item.future().complete(result);
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              if (running.get()) {
                logger.warn("消費者執行緒被中斷", e);
              }
              break;
            } catch (Exception e) {
              logger.error("消費者執行緒發生未預期的錯誤", e);
            }
          }
          logger.info("工具執行器消費者執行緒已停止");
        });
  }

  /**
   * 執行工具調用。
   *
   * @param request 工具調用請求
   * @return 執行結果
   */
  private ToolExecutionResult execute(ToolCallRequest request) {
    String toolName = request.toolName();

    // 檢查工具是否已註冊
    if (!registry.isRegistered(toolName)) {
      logger.warn("工具未註冊：{}", toolName);
      ToolExecutionLog log =
          ToolExecutionLog.failure(
              request.guildId(),
              request.channelId(),
              request.userId(),
              toolName,
              "{}",
              String.format("工具 '%s' 未註冊", toolName));
      logRepository.save(log);
      return ToolExecutionResult.failure(String.format("工具 '%s' 未註冊", toolName));
    }

    // 獲取工具定義
    Result<ToolDefinition, ltdjms.discord.shared.DomainError> getResult =
        registry.getTool(toolName);

    if (getResult.isErr()) {
      logger.error("獲取工具定義失敗：{}, 錯誤：{}", toolName, getResult.getError());
      ToolExecutionLog log =
          ToolExecutionLog.failure(
              request.guildId(),
              request.channelId(),
              request.userId(),
              toolName,
              "{}",
              String.format("獲取工具 '%s' 定義失敗", toolName));
      logRepository.save(log);
      return ToolExecutionResult.failure(String.format("獲取工具 '%s' 定義失敗", toolName));
    }

    ToolDefinition definition = getResult.getValue();

    // 獲取工具實例
    Tool toolInstance = registry.getToolInstance(toolName);
    if (toolInstance == null) {
      logger.error("獲取工具實例失敗：{}", toolName);
      ToolExecutionLog log =
          ToolExecutionLog.failure(
              request.guildId(),
              request.channelId(),
              request.userId(),
              toolName,
              "{}",
              String.format("獲取工具 '%s' 實例失敗", toolName));
      logRepository.save(log);
      return ToolExecutionResult.failure(String.format("獲取工具 '%s' 實例失敗", toolName));
    }

    // 將參數轉換為 JSON 字符串
    String parametersJson;
    try {
      parametersJson = convertParametersToJson(request.parameters());
    } catch (Exception e) {
      logger.warn("轉換參數為 JSON 失敗：{}", toolName, e);
      parametersJson = "{}";
    }

    // 執行工具
    try {
      logger.info(
          "開始執行工具：工具={}, 伺服器={}, 頻道={}, 用戶={}",
          toolName,
          request.guildId(),
          request.channelId(),
          request.userId());

      // 從 JDAProvider 延遲獲取 JDA 實例
      net.dv8tion.jda.api.JDA jda = JDAProvider.getJda();
      ToolContext context =
          new ToolContext(request.guildId(), request.channelId(), request.userId(), jda);

      ToolExecutionResult result = toolInstance.execute(request.parameters(), context);

      // 記錄執行日誌
      if (result.success()) {
        logger.info("工具執行成功：工具={}, 結果={}", toolName, result.result().orElse("無"));
        ToolExecutionLog log =
            ToolExecutionLog.success(
                request.guildId(),
                request.channelId(),
                request.userId(),
                toolName,
                parametersJson,
                result.result().orElse("無"));
        logRepository.save(log);
      } else {
        logger.error("工具執行失敗：工具={}, 錯誤={}", toolName, result.error().orElse("未知錯誤"));
        ToolExecutionLog log =
            ToolExecutionLog.failure(
                request.guildId(),
                request.channelId(),
                request.userId(),
                toolName,
                parametersJson,
                result.error().orElse("未知錯誤"));
        logRepository.save(log);
      }

      return result;

    } catch (Exception e) {
      logger.error("工具執行發生異常：工具=" + toolName, e);
      String errorMsg = String.format("工具 '%s' 執行失敗：%s", toolName, e.getMessage());
      ToolExecutionLog log =
          ToolExecutionLog.failure(
              request.guildId(),
              request.channelId(),
              request.userId(),
              toolName,
              parametersJson,
              errorMsg);
      logRepository.save(log);
      return ToolExecutionResult.failure(errorMsg);
    }
  }

  /**
   * 將參數 Map 轉換為 JSON 字符串。
   *
   * @param parameters 參數 Map
   * @return JSON 字符串
   */
  private String convertParametersToJson(java.util.Map<String, Object> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return "{}";
    }
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      return mapper.writeValueAsString(parameters);
    } catch (Exception e) {
      logger.warn("轉換參數為 JSON 失敗", e);
      return "{}";
    }
  }

  /**
   * 優雅關閉執行器。
   *
   * <p>停止接受新請求，等待佇列中的請求完成後關閉。
   */
  public void shutdown() {
    logger.info("開始關閉工具執行器...");
    running.set(false);

    // 關閉執行緒池
    executor.shutdown();

    try {
      // 等待佇列中的請求完成（最多 30 秒）
      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        logger.warn("工具執行器未能在指定時間內完成，強制關閉");
        executor.shutdownNow();
      }

      // 等待強制關閉完成（最多 10 秒）
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        logger.error("工具執行器無法完成關閉");
      }

      logger.info("工具執行器已關閉，剩餘佇列大小：{}", queue.size());

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("關閉工具執行器時被中斷", e);
      executor.shutdownNow();
    }
  }

  /**
   * 佇列項目。
   *
   * @param request 工具調用請求
   * @param future CompletableFuture
   */
  private record QueueItem(
      ToolCallRequest request, CompletableFuture<ToolExecutionResult> future) {}
}
