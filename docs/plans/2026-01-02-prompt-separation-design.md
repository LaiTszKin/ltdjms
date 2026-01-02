# Prompt 分層載入功能設計

**日期**: 2026-01-02
**狀態**: 設計已完成
**關聯規格**: prompt-separation

---

## 概述

將現有的單一 prompt 載入機制重構為**雙層 prompt 系統**，支援基礎系統提示詞與 Agent 系統提示詞的分離載入。

### 核心需求

1. **雙層結構**：`prompts/system/`（永遠注入）與 `prompts/agent/`（條件注入）
2. **條件注入**：僅在 Agent 功能啟用時注入 agent prompt
3. **覆蓋模式**：使用與現有相同的檢查邏輯（頻道優先 → 類別 → 預設關閉）
4. **保守遷移**：檔案遷移納入實作計劃，保持向後相容性

---

## 資料夾結構

### 新的 Prompt 組織方式

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

### 檔案遷移對應

| 原檔案 | 內容性質 | 目標位置 |
|--------|----------|----------|
| `intro.md` | 龍騰電競企業介紹 | `system/intro.md` |
| `personality.md` | AI 個性設定 | `system/personality.md` |
| `rules.md` | 基礎規則 | `system/rules.md` |
| `agent.md` | Agent 身分與工具說明 | `agent/agent.md` |
| `commands.md` | 指令說明 | `agent/commands.md` |

---

## PromptLoader 介面變更

### 介面定義

```java
public interface PromptLoader {
  /**
   * 載入系統提示詞，根據 agentEnabled 決定是否包含 agent prompt。
   *
   * @param agentEnabled 是否啟用 Agent 功能
   * @return 成功時回傳合併後的 SystemPrompt，失敗時回傳 DomainError
   */
  Result<SystemPrompt, DomainError> loadPrompts(boolean agentEnabled);
}
```

### DefaultPromptLoader 實作

```java
public class DefaultPromptLoader implements PromptLoader {
  private final EnvironmentConfig config;

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
        LOGGER.warn("Agent prompts directory not found, using base prompt only");
      }
    }

    return Result.ok(finalPrompt);
  }

  private Result<SystemPrompt, DomainError> loadFromDirectory(String subDir) {
    Path dir = Paths.get(config.getPromptsDirPath(), subDir);

    if (!Files.exists(dir) || !Files.isDirectory(dir)) {
      return Result.err(DomainError.unexpectedFailure(
          "Required prompts directory not found: " + subDir, null));
    }

    // 載入邏輯與現有類似...
  }

  private SystemPrompt combinePrompts(SystemPrompt base, SystemPrompt additional) {
    List<PromptSection> sections = new ArrayList<>(base.sections());
    sections.addAll(additional.sections());
    return new SystemPrompt(sections);
  }
}
```

### 行為特點

| 情境 | 行為 |
|------|------|
| `system/` 不存在 | 返回錯誤（配置異常） |
| `agent/` 不存在且 `agentEnabled=true` | 記錄警告，繼續使用 system prompt |
| `agentEnabled=false` | 完全不讀取 `agent/` 資料夾 |

---

## AIChatService 整合

### LangChain4jAIChatService 修改

```java
public final class LangChain4jAIChatService implements AIChatService {
  private final PromptLoader promptLoader;
  private final AIAgentChannelConfigService agentChannelConfigService;

  @Override
  public void generateStreamingResponse(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      long messageId,
      StreamingResponseHandler handler) {

    long channelIdLong = parseChannelId(channelId);
    boolean agentEnabled = isAgentEnabled(guildId, channelIdLong);

    // 【修改】使用 agentEnabled 載入對應的 prompt
    SystemPrompt systemPrompt = loadSystemPromptOrEmpty(agentEnabled);

    // 建構會話 ID
    String conversationId = buildConversationId(guildId, channelId, userId);

    // 創建 Agent 服務（已有邏輯，不需修改）
    LangChain4jAgentService agentService = agentServiceFactory.create(agentEnabled);

    // 開始串流對話...
  }

  /**
   * 【修改】載入系統提示詞，根據 agentEnabled 決定是否包含 agent prompt。
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

  /**
   * 檢查當前頻道是否允許 Agent 工具。
   * 使用現有的 AIAgentChannelConfigService，維持一致的檢查邏輯。
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
}
```

### 整合流程

```
用戶訊息
    ↓
檢查 Agent 是否啟用 (AIAgentChannelConfigService)
    ↓
載入 SystemPrompt (PromptLoader.loadPrompts(agentEnabled))
    ↓ ├─ true:  system/ + agent/
    └─ false: system/ only
    ↓
創建 AgentService (根據 agentEnabled 決定是否註冊工具)
    ↓
執行 AI 對話
```

---

## 與類別層級配置的整合

### 檢查邏輯一致性

使用與現有「AI 類別層級配置」功能相同的檢查邏輯：

```java
// AIAgentChannelConfigService 已實作的邏輯
public boolean isAgentEnabled(long guildId, long channelId) {
    // 1. 優先檢查頻道層級配置
    Optional<AIAgentChannelConfig> channelConfig = findByChannel(guildId, channelId);
    if (channelConfig.isPresent()) {
        return channelConfig.get().agentEnabled();
    }

    // 2. 動態檢查類別層級
    // (由呼叫者提供 categoryId)

    // 3. 預設關閉
    return false;
}
```

### LangChain4jAIChatService 中的應用

```java
// 獲取頻道的類別資訊
Guild guild = JDAProvider.getJda().getGuildById(guildId);
GuildChannel channel = guild.getGuildChannelById(channelIdLong);
long categoryId = channel != null ? channel.getParentCategoryLong() : 0;

// 使用統一的檢查方法（支援頻道和類別層級）
boolean agentEnabled = agentChannelConfigService.isAgentEnabled(
    guildId, channelIdLong, categoryId);
```

---

## 測試策略

### 新增測試

**PromptLoaderTest 擴展**

```java
class PromptLoaderTest {
  @Test
  @DisplayName("agentEnabled=true 時應載入 system + agent prompt")
  void loadPrompts_withAgentEnabled_shouldLoadBoth() {
    // 準備 system/ 和 agent/ 測試資料夾
    // 呼叫 loadPrompts(true)
    // 驗證包含兩個資料夾的檔案
  }

  @Test
  @DisplayName("agentEnabled=false 時應只載入 system prompt")
  void loadPrompts_withAgentDisabled_shouldLoadSystemOnly() {
    // 準備 system/ 測試資料夾
    // 呼叫 loadPrompts(false)
    // 驗證只包含 system/ 檔案
  }

  @Test
  @DisplayName("agent/ 不存在時應記錄警告並繼續")
  void loadPrompts_agentDirMissing_shouldContinue() {
    // 只準備 system/ 資料夾
    // 呼叫 loadPrompts(true)
    // 驗證返回 system prompt + 記錄警告日誌
  }

  @Test
  @DisplayName("system/ 不存在時應返回錯誤")
  void loadPrompts_systemDirMissing_shouldReturnError() {
    // 不準備任何資料夾
    // 呼叫 loadPrompts(true)
    // 驗證返回 DomainError
  }
}
```

### 需要更新的現有測試

| 測試類別 | 更新內容 |
|----------|----------|
| `LangChain4jAIChatServiceTest` | Mock `PromptLoader.loadPrompts(boolean)` 的行為 |
| `PromptLoaderTest` | 更新現有測試以呼叫新簽名 |

### 整合測試場景

1. **Agent 啟用頻道**
   - 預期：system + agent prompt + 工具可用

2. **Agent 未啟用頻道**
   - 預期：system prompt only，無工具

3. **類別層級啟用**
   - 預期：繼承類別設定，正確載入 prompt

---

## 實作清單

### 需要修改的檔案

**Domain 層**
- 無需修改（`SystemPrompt`、`PromptSection` 保持不變）

**Persistence 層**
- 無需修改（使用現有的 `AIAgentChannelConfigService`）

**Services 層**
- `PromptLoader.java` - 修改介面，新增 `boolean agentEnabled` 參數
- `DefaultPromptLoader.java` - 實作雙資料夾載入邏輯
- `AIChatService.java` - 介面可能需要調整（視決定而定）

**Commands 層**
- `LangChain4jAIChatService.java` - 使用 `agentEnabled` 呼叫 `loadPrompts()`

### 檔案遷移

```bash
# 建立新資料夾結構
mkdir -p prompts/system
mkdir -p prompts/agent

# 移動檔案
mv prompts/intro.md prompts/system/intro.md
mv prompts/personality.md prompts/system/personality.md
mv prompts/rules.md prompts/system/rules.md
mv prompts/agent.md prompts/agent/agent.md
mv prompts/commands.md prompts/agent/commands.md
```

### 測試檔案

- `PromptLoaderTest.java` - 擴展測試覆蓋新邏輯
- `LangChain4jAIChatServiceTest.java` - 更新 mock 行為

### 文件更新

- `docs/modules/aichat.md` - 說明新的 prompt 結構
- 新增遷移指南（如需要）

---

## 成功標準

- [ ] prompts/system/ 和 prompts/agent/ 資料夾建立完成
- [ ] PromptLoader 介面變更完成，支援 `loadPrompts(boolean agentEnabled)`
- [ ] DefaultPromptLoader 實作雙資料夾載入邏輯
- [ ] LangChain4jAIChatService 整合新的 PromptLoader 呼叫
- [ ] Agent 啟用時載入 system + agent prompt
- [ ] Agent 未啟用時只載入 system prompt
- [ ] agent/ 資料夾不存在時正常運作（記錄警告）
- [ ] 與現有的 AIAgentChannelConfigService 正確整合
- [ ] 所有測試通過，覆蓋率 ≥ 80%

---

## 未來擴展

- 支援更多 prompt 類型（如特定遊戲的專用 prompt）
- 支援 prompt 版本管理與 A/B 測試
- 支援動態 prompt 組合（基於用戶角色、時間等因素）
