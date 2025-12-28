# Research: External Prompts Loader for AI Chat System

**Feature**: External Prompts Loader
**Branch**: `004-external-prompts-loader`
**Date**: 2025-12-28

## Overview

本文檔記錄了外部提示詞載入功能的技術研究決策。研究涵蓋 Java NIO 檔案操作、檔案名稱標準化、編碼處理、OpenAI API 限制以及現有模組整合等關鍵領域。

---

## Decision 1: Java NIO 檔案讀取方法

### Decision
使用 `Files.walk()` 配合 `Stream.filter()` 和 `Files.readString()` 進行檔案讀取。

### Rationale

**選擇 `Files.walk()` 而非 `File.listFiles()`**：
- `Files.walk()` 返回 `Stream<Path>`，更符合 Java 8+ 函數式程式設計風格
- 內建自動資源管理（try-with-resources）
- 更好的錯誤處理機制（`IOException` 檢查異常）
- 支援 `maxDepth` 參數，可限制遍歷深度

**選擇 `Files.readString()` 而非 `BufferedReader`**：
- Java 11+ 引入的簡化 API，程式碼更簡潔
- 自動處理資源關閉
- 內建 UTF-8 編碼支援
- 對於小檔案（< 1MB），效能差異可忽略

### Alternatives Considered

| Alternative | Pros | Cons | Not Chosen Because |
|-------------|------|------|-------------------|
| `File.listFiles()` + `BufferedReader` | 熟悉的 API | 需要手動資源管理，不支援 Stream | 程式碼較冗長，不符合現代 Java 風格 |
| `Files.lines()` | 串流讀取，適合大檔案 | 需要逐行處理，UTF-8 問題複雜 | 過度設計，提示詞檔案通常較小 |
| 第三方庫（Apache Commons IO） | 豐富的工具方法 | 引入外部依賴 | 違反「無新增依賴」原則 |

### Implementation Notes

```java
// 推薦實作模式
try (var paths = Files.walk(promptsDir, 1)) {
    paths.filter(Files::isRegularFile)
         .filter(path -> path.toString().endsWith(".md"))
         .sorted() // 確保字母順序
         .forEach(this::processFile);
} catch (IOException e) {
    LOGGER.error("Failed to read prompts directory", e);
}
```

**執行緒安全性**：
- `Files.walk()` 不保證執行緒安全，但 `DefaultPromptLoader` 設計為無狀態單例
- 每次呼叫 `loadPrompts()` 都是獨立操作，無共享狀態
- 多個 AI 請求同時到達時，可能讀取到不同版本的檔案（可接受的行為）

---

## Decision 2: 檔案名稱標準化策略

### Decision
使用以下規則將檔案名稱轉換為區間標題：

1. 移除 `.md` 副檔名
2. 替換連字符（`-`）和底線（`_`）為空格
3. 轉換為大寫
4. 保留非 ASCII 字元（如中文）
5. 空檔名生成預設標題（`SECTION N`）

### Rationale

**為何選擇此策略**：
- 保持標題的可讀性（連字符→空格）
- 統一格式（全大寫）使分隔線更明顯
- 保留原始檔名語義（非 ASCII 字元）
- 與功能規格中的範例一致

### Alternatives Considered

| Alternative | Pros | Cons | Not Chosen Because |
|-------------|------|------|-------------------|
| 保持原檔名（不轉換） | 簡單直接 | 標題不統一，影響可讀性 | 規格要求大寫 + 空格分隔 |
| 移除所有非字母數字 | 簡化標題 | 丟失語義（中文檔名變成空白） | 不符合國際化需求 |
| 使用 Snake_CASE | 一致的命名風格 | 不夠醒目，不像標題 | 規格要求全大寫 |

### Implementation Notes

```java
private String normalizeTitle(Path path) {
    String fileName = path.getFileName().toString();

    // 移除 .md 副檔名
    if (fileName.endsWith(".md")) {
        fileName = fileName.substring(0, fileName.length() - 3);
    }

    // 空檔名處理
    if (fileName.isBlank()) {
        return "UNTITLED";
    }

    // 替換連字符和底線為空格
    fileName = fileName.replace("-", " ").replace("_", " ");

    // 轉換為大寫
    return fileName.toUpperCase();
}
```

**範例轉換**：
- `bot-personality.md` → `BOT PERSONALITY`
- `chat_rules_v2.md` → `CHAT RULES V2`
- `系統設定.md` → `系統設定`
- `.md` → `UNTITLED`

---

## Decision 3: 編碼處理策略

### Decision
**僅支援 UTF-8 編碼**，失敗時記錄錯誤並跳過檔案。

### Rationale

**為何僅支援 UTF-8**：
- UTF-8 是現代文字檔案的標準編碼（2010+）
- Java 的 `Files.readString()` 預設使用 UTF-8
- Markdown 檔案幾乎都是 UTF-8 編碼
- 多編碼支援增加複雜度，違反「簡潔架構」原則（SC-007）

**為何不使用編碼偵測**：
- 編碼偵測不準確（如 Big5 與 GBK 常混淆）
- 引入額外的依賴或複雜的邏輯
- 錯誤訊息更明確（「檔案必須使用 UTF-8 編碼」）

### Alternatives Considered

| Alternative | Pros | Cons | Not Chosen Because |
|-------------|------|------|-------------------|
| 多編碼嘗試（UTF-8 → Big5 → GBK） | 相容性更好 | 程式碼複雜，偵測不準 | 違反簡潔原則 |
| 使用 juniversalchardet 庫 | 自動偵測編碼 | 引入外部依賴 | 違反「無新增依賴」原則 |
| 使用 `CharsetDecoder` | JDK 內建 | 複雜的錯誤處理 | 過度設計 |

### Implementation Notes

```java
private String readFileContent(Path path) throws IOException {
    try {
        return Files.readString(path, StandardCharsets.UTF_8);
    } catch (MalformedInputException e) {
        LOGGER.warn("Skipping file with invalid UTF-8 encoding: {}", path);
        throw e; // 由呼叫方處理
    }
}
```

**錯誤處理流程**：
1. `MalformedInputException` → 記錄 WARN 日誌，跳過檔案
2. `IOException`（權限問題）→ 記錄 ERROR 日誌，跳過檔案
3. 成功讀取 → 繼續處理

---

## Decision 4: OpenAI API System Prompt 限制

### Decision
使用**單一合併的 system 訊息**，而非多個 system 訊息。

### Rationale

**OpenAI API 對 system 訊息的處理**：
- 官方文件未明確限制 system 訊息數量
- 實務上，多個 system 訊息會被合併處理
- 單一訊息更容易控制和除錯
- 範例格式：`[system, user, assistant, user, ...]`

**為何合併而非分離**：
- 更清晰的控制欄（單一 system 區塊）
- 減少 API 呼叫的 token 開銷
- 避免多個 system 訊息的優先級問題
- 符合常見的 prompt engineering 實踐

### Token 限制考慮

**OpenAI API Token 限制**（依模型而異）：
- GPT-3.5-turbo: 4,096 / 16,385 tokens
- GPT-4: 8,192 / 32,768 tokens
- GPT-4-turbo: 128,000 tokens

**估算**：
- 1 token ≈ 4 字元（英文）或 2-3 字元（中文）
- 500KB 提示詞 ≈ 125,000 字元 ≈ 40,000 tokens（中文場景）
- **結論**：500KB 建議限制是合理的，但可能接近某些模型的 token 上限

**緩解措施**：
- 在日誌中記錄提示詞大小（字元數）
- 如果提示詞過大，記錄 WARN 級別日誌
- 由 AI API 回傳錯誤時，記錄 ERROR 日誌（由現有錯誤處理邏輯處理）

### Alternatives Considered

| Alternative | Pros | Cons | Not Chosen Because |
|-------------|------|------|-------------------|
| 多個 system 訊息（每檔案一個） | 模組化結構 | API 行為未明確定義 | 單一訊息更可靠 |
| 使用 Function Calling | 結構化 prompt | 過度設計，改變 API 呼叫模式 | 不符合需求 |
| 壓縮提示詞 | 減少 token | 損失語義，增加複雜度 | 違反簡潔原則 |

### Implementation Notes

```java
// 在 AIChatRequest 中建構 messages 列表
public static AIChatRequest createUserMessage(String content, AIServiceConfig config, SystemPrompt systemPrompt) {
    List<AIMessage> messages = new ArrayList<>();

    // 添加合併的 system prompt（如果存在）
    if (!systemPrompt.isEmpty()) {
        messages.add(new AIMessage("system", systemPrompt.toCombinedString()));
    }

    // 添加使用者訊息
    messages.add(new AIMessage("user", content));

    return new AIChatRequest(config.model(), messages, config.temperature(), false);
}
```

---

## Decision 5: 現有 AIChat 模組整合點

### Decision
在 `DefaultAIChatService` 中注入 `PromptLoader`，並修改 `AIChatRequest` 的工廠方法以接受 system prompt。

### Rationale

**整合點分析**：
- `DefaultAIChatService` 已有 `AIServiceConfig` 依賴
- 新增 `PromptLoader` 依賴不會破壞現有邏輯
- `AIChatRequest.createUserMessage()` 是建構請求的適當位置
- 保持向後相容（無 external prompts 時使用預設行為）

**Dagger DI 擴展**：
- 在 `AIChatModule` 中提供 `PromptLoader` 單例
- 修改 `DefaultAIChatService` 的 `@Provides` 方法，加入 `PromptLoader` 參數

### Alternatives Considered

| Alternative | Pros | Cons | Not Chosen Because |
|-------------|------|------|-------------------|
| 在 `AIChatMentionListener` 中載入 | 減少 service 層修改 | 違反 DDD 分層（檔案操作不在 commands 層） | 破壞架構清晰性 |
| 建立新的 `PromptService` | 職責分離 | 過度設計，增加層數 | 違反簡潔原則 |
| 使用靜態工廠方法 | 簡單直接 | 難以測試，違反 DI 原則 | 不可測試，違反 Constitution |

### Implementation Notes

**現有程式碼修改**：
```java
// AIChatRequest.java - 新增方法
public static AIChatRequest createUserMessage(
    String content,
    AIServiceConfig config,
    SystemPrompt systemPrompt) { // 新增參數
    // ...
}

// DefaultAIChatService.java - 修改建構函數
private final PromptLoader promptLoader; // 新增欄位

@Inject
public DefaultAIChatService(
    AIServiceConfig config,
    AIClient aiClient,
    DomainEventPublisher eventPublisher,
    PromptLoader promptLoader) { // 新增參數
    // ...
}

// 在 generateResponse() 中使用
AIChatRequest request = AIChatRequest.createUserMessage(
    userMessage,
    config,
    promptLoader.loadPrompts().getValueOr(SystemPrompt.empty()) // 使用載入的提示詞
);
```

**向後相容性**：
- `loadPrompts()` 回傳 `Result<SystemPrompt, DomainError>`
- 如果 prompts 資料夾不存在，回傳 `SystemPrompt.empty()`
- `SystemPrompt.toCombinedString()` 對空物件回傳空字串
- API 請求中不包含空 system 訊息

---

## Decision 6: 配置管理策略

### Decision
使用現有的 `EnvironmentConfig` 機制，透過 Typesafe Config 讀取環境變數和 `.env` 檔案。

### Rationale

**配置項**：
```properties
# prompts 資料夾路徑（相對或絕對）
PROMPTS_DIR_PATH=./prompts

# 單一檔案大小限制（位元組）
PROMPT_MAX_SIZE_BYTES=1048576
```

**為何使用 EnvironmentConfig**：
- 與現有配置機制一致
- 支援環境變數優先順序（env > .env > conf）
- 類型安全（`getPromptsDirPath()`, `getPromptMaxSizeBytes()`）
- 已有的驗證邏輯

### Alternatives Considered

| Alternative | Pros | Cons | Not Chosen Because |
|-------------|------|------|-------------------|
| 硬編碼常數 | 簡單直接 | 不靈活，違反配置彈性原則 | 違反 Constitution III |
| Spring @Value | 整合 Spring | 專案不使用 Spring | 引入不必要的依賴 |
| 獨立的 properties 檔案 | 分離配置 | 破壞現有配置架構 | 不一致 |

### Implementation Notes

```java
// EnvironmentConfig.java - 新增方法
public Path getPromptsDirPath() {
    String path = config.getString("prompts.dir.path");
    return path != null ? Paths.get(path) : Paths.get("./prompts");
}

public long getPromptMaxSizeBytes() {
    return config.getBytes("prompts.max-size");
}
```

---

## Summary of Decisions

| Decision | Choice | Impact |
|----------|--------|--------|
| 檔案讀取方法 | `Files.walk()` + `Files.readString()` | 程式碼簡潔，符合現代 Java 風格 |
| 檔名標準化 | 移除副檔名 → 替換分隔符 → 大寫 | 統一格式，可讀性強 |
| 編碼處理 | 僅支援 UTF-8 | 簡潔，明確的錯誤訊息 |
| System Prompt | 單一合併訊息 | 符合最佳實踐，易於除錯 |
| 整合點 | `DefaultAIChatService` 注入 `PromptLoader` | 符合 DDD，最小化修改 |
| 配置管理 | `EnvironmentConfig` 擴展 | 一致性，支援多來源 |

---

## Open Questions / Risks

### Resolved
無未解決問題。所有研究任務已完成。

### Monitoring Points
1. **效能監控**：如果讀取時間超過 50ms，需考慮快取策略
2. **Token 使用量**：監控 AI API 的 token 消耗，確認提示詞大小合理
3. **編碼問題**：如果使用者反頻繁遇到編碼錯誤，考慮改進錯誤訊息或提供轉換工具

---

## References

- [Java NIO Files Documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [OpenAI API Chat Completions Guide](https://platform.openai.com/docs/api-reference/chat)
- [Typesafe Config Documentation](https://github.com/lightbend/config)
- [Constitution v1.0.0](../../../.specify/memory/constitution.md)
