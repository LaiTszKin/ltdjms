# Prompt 分層載入功能實作計劃

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 將現有的單一 prompt 載入機制重構為雙層 prompt 系統，支援基礎系統提示詞與 Agent 系統提示詞的分離載入。

**Architecture:** 修改 `PromptLoader` 介面接受 `agentEnabled` 參數，`DefaultPromptLoader` 實作雙資料夾載入邏輯（`prompts/system/` 永遠注入，`prompts/agent/` 條件注入），`LangChain4jAIChatService` 根據 `AIAgentChannelConfigService.isAgentEnabled()` 決定載入模式。

**Tech Stack:** Java 17, Dagger 2 (DI), JUnit 5, Mockito, SLF4J/Logback

---

## 實作概觀

此計劃將：
1. 修改 `PromptLoader` 介面，新增 `boolean agentEnabled` 參數
2. 更新 `DefaultPromptLoader` 實作雙資料夾載入邏輯
3. 修改 `LangChain4jAIChatService` 整合新的載入邏輯
4. 遷移現有 prompt 檔案到新資料夾結構
5. 更新所有測試以支援新行為

---

## Task 1: 修改 PromptLoader 介面

**Files:**
- Modify: `src/main/java/ltdjms/discord/aichat/services/PromptLoader.java`

**Step 1: 讀取現有介面定義**

確認現有的 `loadPrompts()` 方法簽名。

**Step 2: 修改介面，新增 agentEnabled 參數**

```java
package ltdjms.discord.aichat.services;

import ltdjms.discord.aichat.domain.SystemPrompt;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** 提示詞載入器介面，用於從外部檔案系統載入系統提示詞。 */
public interface PromptLoader {
  /**
   * 從 prompts 資料夾載入所有 markdown 檔案並合併為系統提示詞。
   *
   * @param agentEnabled 是否啟用 Agent 功能（決定是否載入 agent/ 資料夾）
   * @return 成功時回傳合併後的 {@link SystemPrompt}，失敗時回傳 {@link DomainError}
   */
  Result<SystemPrompt, DomainError> loadPrompts(boolean agentEnabled);
}
```

**Step 3: 執行測試預期編譯失敗**

Run: `mvn clean compile -DskipTests`
Expected: COMPILE ERROR - DefaultPromptLoader 未實作新介面

**Step 4: 提交變更**

```bash
git add src/main/java/ltdjms/discord/aichat/services/PromptLoader.java
git commit -m "feat(prompt): modify PromptLoader interface to accept agentEnabled parameter"
```

---

## Task 2: 實作 DefaultPromptLoader 雙資料夾載入邏輯

**Files:**
- Modify: `src/main/java/ltdjms/discord/aichat/services/DefaultPromptLoader.java`
- Test: `src/test/java/ltdjms/discord/aichat/unit/PromptLoaderTest.java`

**Step 1: 先撰寫測試案例 - agentEnabled=false 時只載入 system/**

```java
// src/test/java/ltdjms/discord/aichat/unit/PromptLoaderTest.java

@Test
@DisplayName("agentEnabled=false 時應只載入 system prompt")
void loadPrompts_withAgentDisabled_shouldLoadSystemOnly() throws IOException {
  // 準備測試資料夾結構
  Path testDir = tempDir.resolve("prompts");
  Path systemDir = testDir.resolve("system");
  Files.createDirectories(systemDir);

  // 建立 system/ 測試檔案
  Files.writeString(systemDir.resolve("intro.md"), "# 龍騰電競介紹");
  Files.writeString(systemDir.resolve("rules.md"), "# 基礎規則");

  // 建立但清空 agent/ 資料夾（驗證不會被讀取）
  Path agentDir = testDir.resolve("agent");
  Files.createDirectories(agentDir);
  Files.writeString(agentDir.resolve("agent.md"), "# Agent 說明（不應該被載入）");

  // 建立 config mock
  EnvironmentConfig mockConfig = mock(EnvironmentConfig.class);
  when(mockConfig.getPromptsDirPath()).thenReturn(testDir.toString());
  when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1024 * 1024L);

  PromptLoader loader = new DefaultPromptLoader(mockConfig);

  // 執行
  Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

  // 驗證
  assertThat(result.isOk()).isTrue();
  SystemPrompt prompt = result.getValue();
  assertThat(prompt.sections()).hasSize(2);
  assertThat(prompt.sections().get(0).title()).isEqualTo("龍騰電競介紹");
  assertThat(prompt.sections().get(1).title()).isEqualTo("基礎規則");
}
```

**Step 2: 執行測試預期失敗**

Run: `mvn test -Dtest=PromptLoaderTest#loadPrompts_withAgentDisabled_shouldLoadSystemOnly`
Expected: FAIL - 方法尚未實作新邏輯

**Step 3: 實作 DefaultPromptLoader 新邏輯**

```java
// src/main/java/ltdjms/discord/aichat/services/DefaultPromptLoader.java

package ltdjms.discord.aichat.services;

import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.newBufferedReader;
import static ltdjms.discord.shared.DomainError.unexpectedFailure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import ltdjms.discord.aichat.domain.PromptSection;
import ltdjms.discord.aichat.domain.SystemPrompt;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** 預設的提示詞載入器實作，從本地檔案系統載入 markdown 檔案。 */
public final class DefaultPromptLoader implements PromptLoader {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultPromptLoader.class);
  private static final String MD_EXTENSION = ".md";

  private final EnvironmentConfig config;

  public DefaultPromptLoader(EnvironmentConfig config) {
    this.config = config;
  }

  @Override
  public Result<SystemPrompt, DomainError> loadPrompts(boolean agentEnabled) {
    // 1. 載入 system/ 資料夾（必備）
    Result<SystemPrompt, DomainError> systemResult = loadFromDirectory("system");
    if (systemResult.isErr()) {
      return systemResult;
    }

    // 2. 如果啟用 Agent，載入 agent/ 資料夾（可選）
    SystemPrompt finalPrompt = systemResult.getValue();
    if (agentEnabled) {
      Result<SystemPrompt, DomainError> agentResult = loadFromDirectory("agent");
      if (agentResult.isOk()) {
        finalPrompt = combinePrompts(finalPrompt, agentResult.getValue());
      } else {
        // agent/ 不存在時記錄警告，但不影響運作
        LOG.warn("Agent prompts directory not found, using base prompt only");
      }
    }

    return Result.ok(finalPrompt);
  }

  /**
   * 從指定子資料夾載入提示詞。
   *
   * @param subDir 子資料夾名稱（如 "system" 或 "agent"）
   * @return 載入結果
   */
  private Result<SystemPrompt, DomainError> loadFromDirectory(String subDir) {
    Path promptsDir = Paths.get(config.getPromptsDirPath());
    Path targetDir = promptsDir.resolve(subDir);

    if (!exists(targetDir) || !isDirectory(targetDir)) {
      return Result.err(unexpectedFailure(
          "Required prompts directory not found: " + subDir, null));
    }

    List<PromptSection> sections = new ArrayList<>();

    try (Stream<Path> stream = Files.list(targetDir)) {
      stream.filter(this::isValidPromptFile)
          .sorted()
          .forEach(file -> {
            try {
              PromptSection section = loadPromptSection(file);
              sections.add(section);
            } catch (IOException e) {
              LOG.error("Failed to load prompt file: {}", file, e);
            }
          });
    } catch (IOException e) {
      return Result.err(unexpectedFailure("Failed to list prompts directory: " + subDir, e));
    }

    // 記錄載入統計
    MDC.put("directory", subDir);
    MDC.put("fileCount", String.valueOf(sections.size()));
    LOG.debug("Loaded {} prompt sections from {}/ directory", sections.size(), subDir);
    MDC.clear();

    return Result.ok(new SystemPrompt(sections));
  }

  /**
   * 合併多個提示詞。
   */
  private SystemPrompt combinePrompts(SystemPrompt base, SystemPrompt additional) {
    List<PromptSection> sections = new ArrayList<>(base.sections());
    sections.addAll(additional.sections());
    return new SystemPrompt(sections);
  }

  private boolean isValidPromptFile(Path file) {
    String fileName = file.getFileName().toString();
    return Files.isRegularFile(file)
        && fileName.endsWith(MD_EXTENSION)
        && !fileName.startsWith(".");
  }

  private PromptSection loadPromptSection(Path file) throws IOException {
    String fileName = file.getFileName().toString();
    String title = normalizeTitle(fileName);

    StringBuilder content = new StringBuilder();
    try (var reader = newBufferedReader(file)) {
      String line;
      while ((line = reader.readLine()) != null) {
        content.append(line).append("\n");
      }
    }

    return new PromptSection(title, content.toString());
  }

  private String normalizeTitle(String fileName) {
    return fileName
        .replace(MD_EXTENSION, "")
        .replace("-", " ")
        .replace("_", " ");
  }
}
```

**Step 4: 執行測試驗證通過**

Run: `mvn test -Dtest=PromptLoaderTest#loadPrompts_withAgentDisabled_shouldLoadSystemOnly`
Expected: PASS

**Step 5: 撰寫測試 - agentEnabled=true 時載入 system + agent/**

```java
@Test
@DisplayName("agentEnabled=true 時應載入 system + agent prompt")
void loadPrompts_withAgentEnabled_shouldLoadBoth() throws IOException {
  Path testDir = tempDir.resolve("prompts");
  Path systemDir = testDir.resolve("system");
  Path agentDir = testDir.resolve("agent");
  Files.createDirectories(systemDir);
  Files.createDirectories(agentDir);

  Files.writeString(systemDir.resolve("intro.md"), "# 龍騰電競介紹");
  Files.writeString(agentDir.resolve("agent.md"), "# Agent 說明");

  EnvironmentConfig mockConfig = mock(EnvironmentConfig.class);
  when(mockConfig.getPromptsDirPath()).thenReturn(testDir.toString());
  when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1024 * 1024L);

  PromptLoader loader = new DefaultPromptLoader(mockConfig);

  Result<SystemPrompt, DomainError> result = loader.loadPrompts(true);

  assertThat(result.isOk()).isTrue();
  SystemPrompt prompt = result.getValue();
  assertThat(prompt.sections()).hasSize(2);
  assertThat(prompt.sections().get(0).title()).isEqualTo("龍騰電競介紹");
  assertThat(prompt.sections().get(1).title()).isEqualTo("Agent 說明");
}
```

**Step 6: 執行測試驗證通過**

Run: `mvn test -Dtest=PromptLoaderTest#loadPrompts_withAgentEnabled_shouldLoadBoth`
Expected: PASS

**Step 7: 撰寫測試 - agent/ 不存在時記錄警告並繼續**

```java
@Test
@DisplayName("agent/ 不存在且 agentEnabled=true 時應記錄警告並繼續")
void loadPrompts_agentDirMissing_shouldContinueWithWarning() throws IOException {
  Path testDir = tempDir.resolve("prompts");
  Path systemDir = testDir.resolve("system");
  Files.createDirectories(systemDir);

  Files.writeString(systemDir.resolve("intro.md"), "# 龍騰電競介紹");

  EnvironmentConfig mockConfig = mock(EnvironmentConfig.class);
  when(mockConfig.getPromptsDirPath()).thenReturn(testDir.toString());
  when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1024 * 1024L);

  PromptLoader loader = new DefaultPromptLoader(mockConfig);

  // 擷取警告日誌
  Logger loaderLogger = LoggerFactory.getLogger(DefaultPromptLoader.class);
  ListAppender<ILoggingEvent> appender = new ListAppender<>();
  appender.start();
  ((ch.qos.logback.classic.Logger) loaderLogger).addAppender(appender);

  Result<SystemPrompt, DomainError> result = loader.loadPrompts(true);

  assertThat(result.isOk()).isTrue();
  assertThat(result.getValue().sections()).hasSize(1);

  // 驗證警告日誌
  List<ILoggingEvent> logs = appender.list;
  assertThat(logs.stream()
      .anyMatch(e -> e.getLevel() == Level.WARN
          && e.getMessage().contains("Agent prompts directory not found")))
      .isTrue();
}
```

**Step 8: 執行測試驗證通過**

Run: `mvn test -Dtest=PromptLoaderTest#loadPrompts_agentDirMissing_shouldContinueWithWarning`
Expected: PASS

**Step 9: 撰寫測試 - system/ 不存在時應返回錯誤**

```java
@Test
@DisplayName("system/ 不存在時應返回錯誤")
void loadPrompts_systemDirMissing_shouldReturnError() throws IOException {
  Path testDir = tempDir.resolve("prompts");
  Files.createDirectories(testDir);

  EnvironmentConfig mockConfig = mock(EnvironmentConfig.class);
  when(mockConfig.getPromptsDirPath()).thenReturn(testDir.toString());
  when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1024 * 1024L);

  PromptLoader loader = new DefaultPromptLoader(mockConfig);

  Result<SystemPrompt, DomainError> result = loader.loadPrompts(true);

  assertThat(result.isErr()).isTrue();
  assertThat(result.getError().category()).isEqualTo(DomainError.Category.UNEXPECTED_FAILURE);
  assertThat(result.getError().message()).contains("system");
}
```

**Step 10: 執行測試驗證通過**

Run: `mvn test -Dtest=PromptLoaderTest#loadPrompts_systemDirMissing_shouldReturnError`
Expected: PASS

**Step 11: 執行所有 PromptLoaderTest 驗證**

Run: `mvn test -Dtest=PromptLoaderTest`
Expected: ALL PASS

**Step 12: 提交變更**

```bash
git add src/main/java/ltdjms/discord/aichat/services/DefaultPromptLoader.java
git add src/test/java/ltdjms/discord/aichat/unit/PromptLoaderTest.java
git commit -m "feat(prompt): implement dual-folder loading logic for system and agent prompts"
```

---

## Task 3: 修改 LangChain4jAIChatService 整合新的 PromptLoader

**Files:**
- Modify: `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`
- Modify: `src/test/java/ltdjms/discord/aichat/unit/LangChain4jAIChatServiceTest.java`

**Step 1: 讀取現有 LangChain4jAIChatService 實作**

確認 `loadSystemPromptOrEmpty()` 方法和 `generateStreamingResponse()` 方法。

**Step 2: 修改 loadSystemPromptOrEmpty 方法接受 agentEnabled 參數**

```java
// src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java

/**
 * 載入系統提示詞，根據 agentEnabled 決定是否包含 agent prompt。
 *
 * @param agentEnabled 是否啟用 Agent 功能
 * @return 系統提示詞，載入失敗時返回空提示詞
 */
private SystemPrompt loadSystemPromptOrEmpty(boolean agentEnabled) {
  Result<SystemPrompt, DomainError> result = promptLoader.loadPrompts(agentEnabled);
  if (result.isErr()) {
    LOG.warn("Failed to load system prompt, using empty prompt: {}",
             result.getError().message());
    return SystemPrompt.empty();
  }
  return result.getValue();
}
```

**Step 3: 修改 generateStreamingResponse 方法檢查 Agent 並傳遞參數**

找到 `generateStreamingResponse` 方法中呼叫 `loadSystemPromptOrEmpty()` 的位置，修改為：

```java
// 在 generateStreamingResponse 方法中
long channelIdLong = parseChannelId(channelId);
boolean agentEnabled = isAgentEnabled(guildId, channelIdLong);

// 使用 agentEnabled 載入對應的 prompt
SystemPrompt systemPrompt = loadSystemPromptOrEmpty(agentEnabled);
```

**Step 4: 實作 isAgentEnabled 方法（如果尚未存在）**

```java
/**
 * 檢查當前頻道是否允許 Agent 工具。
 * 使用 AIAgentChannelConfigService，維持一致的檢查邏輯。
 *
 * @param guildId 伺服器 ID
 * @param channelIdLong 頻道 ID（long 型別）
 * @return 是否啟用 Agent
 */
private boolean isAgentEnabled(long guildId, long channelIdLong) {
  if (channelIdLong <= 0) {
    return false;
  }
  if (agentChannelConfigService == null) {
    LOG.warn("AIAgentChannelConfigService 未注入，為安全起見停用 Agent 工具");
    return false;
  }
  boolean enabled = agentChannelConfigService.isAgentEnabled(guildId, channelIdLong);
  if (!enabled) {
    LOG.debug("Agent 工具在 guildId={}, channelId={} 未啟用，將以純聊天模式回應",
              guildId, channelIdLong);
  }
  return enabled;
}
```

**Step 5: 確認 AIAgentChannelConfigService 已注入**

檢查 `LangChain4jAIChatService` 的建構函數是否有 `AIAgentChannelConfigService` 參數，如果沒有則新增：

```java
private final AIAgentChannelConfigService agentChannelConfigService;

@Inject
public LangChain4jAIChatService(
    AIServiceConfig config,
    PromptLoader promptLoader,
    AIAgentChannelConfigService agentChannelConfigService,  // 新增
    DomainEventPublisher eventPublisher,
    AIServiceFactory aiServiceFactory,
    LangChain4jAgentServiceFactory agentServiceFactory,
    ConversationMemoryManager memoryManager,
    @Named("conversationTimeoutMinutes") int conversationTimeoutMinutes) {
  // ...
  this.agentChannelConfigService = agentChannelConfigService;
}
```

**Step 6: 在 Dagger 模組中綁定 AIAgentChannelConfigService**

如果尚未綁定，在 `AIChatModule` 中新增：

```java
// src/main/java/ltdjms/discord/aichat/injection/AIChatModule.java

@Provides
@Singleton
AIAgentChannelConfigService provideAIAgentChannelConfigService(
    DefaultAIAgentChannelConfigService impl) {
  return impl;
}
```

**Step 7: 執行編譯檢查**

Run: `mvn clean compile -DskipTests`
Expected: SUCCESS

**Step 8: 提交變更**

```bash
git add src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java
git add src/main/java/ltdjms/discord/aichat/injection/AIChatModule.java
git commit -m "feat(aichat): integrate agentEnabled check into prompt loading"
```

---

## Task 4: 更新 LangChain4jAIChatServiceTest

**Files:**
- Modify: `src/test/java/ltdjms/discord/aichat/unit/LangChain4jAIChatServiceTest.java`

**Step 1: 找到所有 mock PromptLoader.loadPrompts() 的測試**

搜尋測試中使用 `when(promptLoader.loadPrompts())` 的位置。

**Step 2: 更新 mock 行為以支援新簽名**

```java
// 將原本的
when(promptLoader.loadPrompts()).thenReturn(Result.ok(systemPrompt));

// 改為
when(promptLoader.loadPrompts(anyBoolean())).thenReturn(Result.ok(systemPrompt));
```

**Step 3: 針對 Agent 啟用/停用場景新增專門測試**

```java
@Test
@DisplayName("Agent 啟用時應載入包含 agent prompt 的完整提示詞")
void generateStreamingResponse_withAgentEnabled_shouldLoadAgentPrompts() {
  // 準備測試資料
  long guildId = 123L;
  String channelId = "456";
  String userId = "user1";
  String userMessage = "測試訊息";

  SystemPrompt fullPrompt = new SystemPrompt(List.of(
      new PromptSection("系統介紹", "基礎內容"),
      new PromptSection("Agent 說明", "Agent 工具內容")
  ));

  // Mock 設定
  when(agentChannelConfigService.isAgentEnabled(eq(guildId), anyLong()))
      .thenReturn(true);
  when(promptLoader.loadPrompts(true)).thenReturn(Result.ok(fullPrompt));
  when(aiServiceFactory.create(anyBoolean())).thenReturn(mockAgentService);

  // 執行
  service.generateStreamingResponse(
      guildId, channelId, userId, userMessage, 789L, mockHandler);

  // 驗證載入了完整的 agent prompt
  verify(promptLoader).loadPrompts(true);
}

@Test
@DisplayName("Agent 停用時應只載入基礎系統提示詞")
void generateStreamingResponse_withAgentDisabled_shouldLoadSystemPromptsOnly() {
  long guildId = 123L;
  String channelId = "456";
  String userId = "user1";
  String userMessage = "測試訊息";

  SystemPrompt basePrompt = new SystemPrompt(List.of(
      new PromptSection("系統介紹", "基礎內容")
  ));

  when(agentChannelConfigService.isAgentEnabled(eq(guildId), anyLong()))
      .thenReturn(false);
  when(promptLoader.loadPrompts(false)).thenReturn(Result.ok(basePrompt));
  when(aiServiceFactory.create(anyBoolean())).thenReturn(mockAgentService);

  service.generateStreamingResponse(
      guildId, channelId, userId, userMessage, 789L, mockHandler);

  verify(promptLoader).loadPrompts(false);
}
```

**Step 4: 執行所有 LangChain4jAIChatServiceTest**

Run: `mvn test -Dtest=LangChain4jAIChatServiceTest`
Expected: ALL PASS

**Step 5: 提交變更**

```bash
git add src/test/java/ltdjms/discord/aichat/unit/LangChain4jAIChatServiceTest.java
git commit -m "test(aichat): update tests for new prompt loading with agentEnabled parameter"
```

---

## Task 5: 遷移現有 Prompt 檔案到新資料夾結構

**Files:**
- Create: `prompts/system/intro.md`
- Create: `prompts/system/personality.md`
- Create: `prompts/system/rules.md`
- Create: `prompts/agent/agent.md`
- Create: `prompts/agent/commands.md`

**Step 1: 檢查現有 prompts 資料夾內容**

Run: `ls -la prompts/`
確認現有的 `.md` 檔案。

**Step 2: 建立新資料夾結構**

```bash
mkdir -p prompts/system
mkdir -p prompts/agent
```

**Step 3: 讀取並分類現有檔案**

根據設計文件的分類：
- `intro.md` → `prompts/system/intro.md`
- `personality.md` → `prompts/system/personality.md`
- `rules.md` → `prompts/system/rules.md`
- `agent.md` → `prompts/agent/agent.md`
- `commands.md` → `prompts/agent/commands.md`

**Step 4: 移動檔案**

```bash
# 備份現有檔案
cp -r prompts prompts.backup

# 移動檔案
mv prompts/intro.md prompts/system/intro.md
mv prompts/personality.md prompts/system/personality.md
mv prompts/rules.md prompts/system/rules.md
mv prompts/agent.md prompts/agent/agent.md
mv prompts/commands.md prompts/agent/commands.md
```

**Step 5: 驗證新結構**

Run: `tree prompts/` 或 `find prompts -type f -name "*.md" | sort`
確認檔案都在正確位置。

**Step 6: 執行測試確保檔案載入正常**

Run: `mvn test -Dtest=PromptLoaderTest`
Expected: ALL PASS

**Step 7: 提交變更**

```bash
git add prompts/
git commit -m "refactor(prompt): migrate to dual-folder structure (system/ and agent/)"
```

---

## Task 6: 更新相關文件

**Files:**
- Modify: `docs/modules/aichat.md`

**Step 1: 讀取現有 aichat.md 文件**

Run: `cat docs/modules/aichat.md`

**Step 2: 新增新的 Prompt 結構說明章節**

```markdown
## Prompt 載入機制

### 資料夾結構

專案使用雙層 prompt 系統，將基礎系統提示詞與 Agent 功能提示詞分離：

```
prompts/
├── system/              # 基礎系統提示詞（永遠注入）
│   ├── intro.md        # 龍騰電競介紹
│   ├── personality.md  # AI 個性設定
│   └── rules.md        # 基礎規則
└── agent/              # Agent 系統提示詞（條件注入）
    ├── agent.md        # Agent 身分與職責
    └── commands.md     # 工具使用說明
```

### 載入行為

| 情境 | 載入內容 |
|------|----------|
| Agent 啟用 | `system/` + `agent/` |
| Agent 停用 | 僅 `system/` |
| `system/` 不存在 | 錯誤（配置異常） |
| `agent/` 不存在 | 記錄警告，繼續使用 `system/` |
```

**Step 3: 提交文件變更**

```bash
git add docs/modules/aichat.md
git commit -m "docs(aichat): document new dual-folder prompt structure"
```

---

## Task 7: 執行完整測試套件驗證

**Step 1: 執行所有單元測試**

Run: `mvn test`
Expected: ALL PASS

**Step 2: 執行所有整合測試**

Run: `mvn test -Dtest="**/*Integration*"`
Expected: ALL PASS

**Step 3: 執行完整測試與覆蓋率檢查**

Run: `mvn verify`
Expected: ALL PASS, Coverage ≥ 80%

**Step 4: 本地建置驗證**

Run: `mvn clean package -DskipTests`
Expected: BUILD SUCCESS

**Step 5: 提交任何遺漏的修正**

```bash
git add -A
git commit -m "fix: resolve test failures from prompt separation implementation"
```

---

## Task 8: 手動驗證與整合測試

**Step 1: 啟動本地開發環境**

Run: `make start-dev`
確保 Docker 容器正常啟動。

**Step 2: 觀察應用程式啟動日誌**

Run: `make logs`
檢查是否有 prompt 載入相關的錯誤或警告。

**Step 3: 測試 Agent 啟用頻道**

1. 設定某頻道啟用 Agent 模式
2. 在該頻道發送測試訊息
3. 驗證 AI 回應包含 Agent 能力（可使用工具）

**Step 4: 測試 Agent 停用頻道**

1. 設定某頻道停用 Agent 模式
2. 在該頻道發送測試訊息
3. 驗證 AI 回應僅為基礎聊天（無工具可用）

**Step 5: 驗證類別層級配置**

1. 設定某類別啟用 Agent 模式
2. 在該類別下的頻道發送渊試訊息
3. 驗證正確繼承類別設定

**Step 6: 清理測試環境**

Run: `make stop`

**Step 7: 提交任何發現的問題修正**

```bash
git add -A
git commit -m "fix: resolve issues found during manual integration testing"
```

---

## 驗收標準確認

完成所有任務後，確認以下標準：

- [ ] `PromptLoader` 介面已修改為接受 `boolean agentEnabled` 參數
- [ ] `DefaultPromptLoader` 實作雙資料夾載入邏輯
- [ ] `LangChain4jAIChatService` 根據 `AIAgentChannelConfigService.isAgentEnabled()` 決定載入模式
- [ ] prompts/ 資料夾結構重組為 system/ 和 agent/
- [ ] 所有測試通過（單元測試 + 整合測試）
- [ ] 測試覆蓋率 ≥ 80%
- [ ] 手動驗證 Agent 啟用/停用頻道行為正確
- [ ] 文件已更新反映新結構

---

## 注意事項

1. **向後相容性**：確保現有測試更新後仍能通過
2. **錯誤處理**：`system/` 不存在是致命錯誤，`agent/` 不存在是警告
3. **日誌記錄**：使用適當的日誌層級（DEBUG/WARN/ERROR）
4. **TDD 流程**：先寫測試，再實作，確保每個小步驟都可驗證
5. **頻繁提交**：每個任務完成後立即提交，保持清晰的提交歷史
