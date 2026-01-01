# Java 介面契約

**Feature**: LangChain4J AI 功能整合
**Branch**: `007-langchain4j-ai`
**Date**: 2025-12-31

## 概述

本功能重構內部實作，但保持所有公開 Java 介面不變。以下列出所有相關的公開介面契約。

---

## 1. AIChatService 介面

**位置**: `ltdjms.discord.aichat.services.AIChatService`

**說明**: AI 聊天服務介面，負責處理 AI 請求並產生回應內容。

```java
package ltdjms.discord.aichat.services;

import java.util.List;
import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** AI 聊天服務介面，負責處理 AI 請求並產生回應內容。 */
public interface AIChatService {

  /**
   * 生成 AI 回應內容（可能分段）。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID
   * @param userId 使用者 ID
   * @param userMessage 使用者訊息
   * @return 分割後的回應訊息或錯誤
   */
  Result<List<String>, DomainError> generateResponse(
      long guildId, String channelId, String userId, String userMessage);

  /**
   * 生成流式 AI 回應內容。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID
   * @param userId 使用者 ID
   * @param userMessage 使用者訊息
   * @param handler 流式回應處理器
   */
  void generateStreamingResponse(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      StreamingResponseHandler handler);

  /**
   * 生成流式 AI 回應內容（帶訊息 ID）。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID
   * @param userId 使用者 ID
   * @param userMessage 使用者訊息
   * @param messageId 觸發訊息的 ID
   * @param handler 流式回應處理器
   */
  void generateStreamingResponse(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      long messageId,
      StreamingResponseHandler handler);

  /**
   * 使用對話歷史生成 AI 回應（用於多輪工具調用）。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID
   * @param userId 使用者 ID
   * @param history 對話歷史
   * @param handler 流式回應處理器
   */
  void generateWithHistory(
      long guildId,
      String channelId,
      String userId,
      List<ConversationMessage> history,
      StreamingResponseHandler handler);
}
```

**契約保證**：
- 所有方法必須由 `LangChain4jAIChatService` 實作
- 行為與現有 `DefaultAIChatService` 完全一致
- 錯誤處理使用 `DomainError` 分類

---

## 2. StreamingResponseHandler 介面

**位置**: `ltdjms.discord.aichat.services.StreamingResponseHandler`

**說明**: 流式回應處理器接口，用於接收 AI 模型的增量回應。

```java
package ltdjms.discord.aichat.services;

import ltdjms.discord.shared.DomainError;

/**
 * 流式回應處理器接口，用於接收 AI 模型的增量回應。
 *
 * <p>實作此接口以處理流式輸出的每個片段。
 *
 * <p>為了支持向後兼容，此接口提供兩種調用方式：
 *
 * <ul>
 *   <li>舊版三參數方法：{@code void onChunk(String, boolean, DomainError)}
 *   <li>新版四參數方法：{@code void onChunk(String, boolean, DomainError, ChunkType)}
 * </ul>
 *
 * <p>當調用四參數方法時，默認實現會調用三參數方法，實現向後兼容。
 */
public interface StreamingResponseHandler {

  /** 流式回應片段類型。 */
  enum ChunkType {
    /** 推理內容（reasoning_content）。 */
    REASONING,
    /** 實際回應內容（content）。 */
    CONTENT
  }

  /**
   * 處理流式回應片段（帶類型區分）。
   *
   * @param chunk 文本片段
   * @param isComplete 是否為最後一個片段
   * @param error 錯誤（如果發生）
   * @param type 片段類型（REASONING 或 CONTENT）
   */
  void onChunk(String chunk, boolean isComplete, DomainError error, ChunkType type);

  /**
   * 處理流式回應片段（向後兼容版本）。
   *
   * <p>此方法為向後兼容而保留，默認調用四參數版本並傳入 {@link ChunkType#CONTENT}。
   *
   * @param chunk 文本片段（空字串表示無新片段，僅表示狀態變化）
   * @param isComplete 是否為最後一個片段（流結束）
   * @param error 錯誤（如果發生），當 error 不為 null 時，chunk 和 isComplete 應被忽略
   */
  default void onChunk(String chunk, boolean isComplete, DomainError error) {
    onChunk(chunk, isComplete, error, ChunkType.CONTENT);
  }
}
```

**契約保證**：
- 介面定義保持不變
- 新的 `ChunkType` 枚舉用於區分推理內容和實際內容
- 向後兼容的默認實現

---

## 3. DomainError 類別

**位置**: `ltdjms.discord.shared.DomainError`

**說明**: 表示業務層次的錯誤，用於 `Result<T, DomainError>` 類型。

**現有錯誤類別** (保持不變)：
```java
public enum Category {
    // 通用錯誤
    INVALID_INPUT,
    INSUFFICIENT_BALANCE,
    INSUFFICIENT_TOKENS,
    PERSISTENCE_FAILURE,
    UNEXPECTED_FAILURE,

    // Discord 錯誤
    DISCORD_INTERACTION_TIMEOUT,
    DISCORD_HOOK_EXPIRED,
    DISCORD_UNKNOWN_MESSAGE,
    DISCORD_RATE_LIMITED,
    DISCORD_MISSING_PERMISSIONS,
    DISCORD_INVALID_COMPONENT_ID,

    // AI 服務錯誤
    AI_SERVICE_TIMEOUT,
    AI_SERVICE_AUTH_FAILED,
    AI_SERVICE_RATE_LIMITED,
    AI_SERVICE_UNAVAILABLE,
    AI_RESPONSE_EMPTY,
    AI_RESPONSE_INVALID,

    // 提示詞錯誤
    PROMPT_DIR_NOT_FOUND,
    PROMPT_FILE_TOO_LARGE,
    PROMPT_READ_FAILED,
    PROMPT_INVALID_ENCODING,
    PROMPT_LOAD_FAILED,

    // 頻道限制錯誤
    CHANNEL_NOT_ALLOWED,
    DUPLICATE_CHANNEL,
    INSUFFICIENT_PERMISSIONS,
    CHANNEL_NOT_FOUND
}
```

**契約保證**：
- 現有錯誤類別保持不變
- LangChain4J 異常映射到現有類別
- 不新增錯誤類別

---

## 4. Result<T, E> 類別

**位置**: `ltdjms.discord.shared.Result`

**說明**: 函數式錯誤處理的結果類型。

**契約保證**：
- 保持現有 API 不變
- `isOk()`、`isErr()`、`getValue()`、`getError()` 方法行為一致

---

## 5. 內部實作類別 (新增)

以下類別是內部實作，不屬於公開 API：

### LangChain4jAIChatService
```java
package ltdjms.discord.aichat.services;

public class LangChain4jAIChatService implements AIChatService {

    public LangChain4jAIChatService(
        StreamingChatModel chatModel,
        ChatMemoryProvider chatMemoryProvider,
        SystemPrompt systemPrompt,
        MessageSplitter messageSplitter
    ) { ... }

    @Override
    public void generateStreamingResponse(
        long guildId,
        String channelId,
        String userId,
        String userMessage,
        StreamingResponseHandler handler
    ) { ... }

    // 其他方法實作...
}
```

### LangChain4jAgentService (LangChain4J 服務介面)
```java
package ltdjms.discord.aiagent.services;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.TokenStream;

public interface LangChain4jAgentService {

    @SystemMessage("""
        你是 LTDJ 管理系統的 AI 助手，負責協助管理 Discord 伺服器。
        你可以使用提供的工具來創建頻道、類別和查詢資訊。
        """)
    TokenStream chat(
        @MemoryId String conversationId,
        @UserMessage String userMessage
    );

    @SystemMessage("""
        你是 LTDJ 管理系統的 AI 助手，負責協助管理 Discord 伺服器。
        你可以使用提供的工具來創建頻道、類別和查詢資訊。
        """)
    TokenStream chatWithHistory(
        @MemoryId String conversationId,
        @UserMessage String userMessage,
        @dev.langchain4j.service.Vocabulary AiMessage[] history
    );
}
```

### ToolExecutionContext
```java
package ltdjms.discord.aiagent.services;

public final class ToolExecutionContext {

    public record Context(
        long guildId,
        String channelId,
        String userId
    ) {}

    public static void set(long guildId, String channelId, String userId);
    public static Context get();
    public static void clear();
}
```

---

## 6. 工具類契約

### LangChain4jCreateChannelTool
```java
package ltdjms.discord.aiagent.services.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

public class LangChain4jCreateChannelTool {

    @Tool("創建 Discord 頻道")
    public String createChannel(
        @P("頻道名稱") String name,
        @P("頻道類型：text 或 voice") String type,
        @P("父類別 ID（可選）") @P(required = false) Long categoryId,
        @P("權限設定（可選）") @P(required = false) Map<String, Boolean> permissions
    );
}
```

### LangChain4jCreateCategoryTool
```java
package ltdjms.discord.aiagent.services.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

public class LangChain4jCreateCategoryTool {

    @Tool("創建 Discord 類別")
    public String createCategory(
        @P("類別名稱") String name,
        @P("權限設定（可選）") @P(required = false) Map<String, Boolean> permissions
    );
}
```

### LangChain4jListChannelsTool
```java
package ltdjms.discord.aiagent.services.tools;

import dev.langchain4j.agent.tool.Tool;

public class LangChain4jListChannelsTool {

    @Tool("列出 Discord 頻道資訊")
    public String listChannels();
}
```

---

## 7. Dagger 模組契約

### AIAgentModule 更新

```java
package ltdjms.discord.shared.di;

@Module
public interface AIAgentModule {

    @Binds
    AIChatService bindAIChatService(LangChain4jAIChatService impl);

    @Provides
    @Singleton
    static StreamingChatModel provideStreamingChatModel(AIServiceConfig config) {
        return OpenAiStreamingChatModel.builder()
            .baseUrl(config.baseUrl())
            .apiKey(config.apiKey())
            .modelName(config.modelName())
            .timeout(Duration.ofSeconds(30))
            .maxRetries(2)
            .logRequests(config.logRequests())
            .logResponses(config.logResponses())
            .returnThinking(true)
            .build();
    }

    @Provides
    @Singleton
    static ChatMemoryProvider provideChatMemoryProvider(
        CacheService cacheService,
        ConversationMessageRepository repository,
        TokenEstimator tokenEstimator
    ) {
        return new PersistentChatMemoryProvider(
            cacheService,
            repository,
            tokenEstimator,
            4000,  // maxTokens
            50     // maxMessages
        );
    }

    @Provides
    @Singleton
    static LangChain4jCreateChannelTool provideCreateChannelTool(GuildService guildService);

    @Provides
    @Singleton
    static LangChain4jCreateCategoryTool provideCreateCategoryTool(GuildService guildService);

    @Provides
    @Singleton
    static LangChain4jListChannelsTool provideListChannelsTool(GuildService guildService);
}
```

---

## 8. 向後兼容性保證

### 公開 API 保持不變
- `AIChatService` 介面
- `StreamingResponseHandler` 介面
- `DomainError` 類別
- `Result<T, E>` 類別
- `ConversationMessage` 類別
- `MessageRole` 枚舉

### 行為保持不變
- 訊息分割邏輯 (MessageSplitter)
- 錯誤訊息格式
- 工具執行通知訊息
- 會話 ID 生成策略
- Token 限制處理

### 內部實作替換
- 移除：`AIClient`、`DefaultAIChatService`、`AgentOrchestrator`、`ToolCallRequestParser`
- 新增：`LangChain4jAIChatService`、`LangChain4jAgentService`、工具類

---

## 總結

本功能的契約設計確保了完全的向後兼容性。所有公開 API 保持不變，僅替換內部實作為 LangChain4J 框架。
