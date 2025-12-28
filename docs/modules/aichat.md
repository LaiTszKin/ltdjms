# AI Chat Module

## 概述

AI Chat 模組提供 Discord 機器人的 AI 聊天功能。當使用者在 Discord 頻道中提及機器人時，機器人會使用 AI 服務生成並發送回應訊息。

## 架構

### 分層設計

```
ltdjms.discord.aichat/
├── domain/           # 領域模型
│   ├── AIServiceConfig.java
│   ├── AIChatRequest.java
│   ├── AIChatResponse.java
│   ├── PromptSection.java        # 提示詞區間
│   ├── SystemPrompt.java         # 完整系統提示詞
│   └── PromptLoadError.java      # 載入錯誤類型
├── services/         # 服務層
│   ├── AIChatService.java (interface)
│   ├── DefaultAIChatService.java
│   ├── AIClient.java
│   ├── PromptLoader.java         # 提示詞載入器介面
│   ├── DefaultPromptLoader.java  # 檔案系統實作
│   ├── MessageChunkAccumulator.java
│   ├── MessageSplitter.java
│   └── StreamingResponseHandler.java
└── commands/         # JDA 事件處理
    └── AIChatMentionListener.java
```

### 組件說明

#### AIServiceConfig

AI 服務配置，包含連線資訊與參數：

- `baseUrl`: AI 服務 Base URL（預設: `https://api.openai.com/v1`）
- `apiKey`: API 金鑰（必填）
- `model`: 模型名稱（預設: `gpt-3.5-turbo`）
- `temperature`: 溫度 0.0-2.0（預設: 0.7）
- `maxTokens`: 最大 Token 數 1-4096（預設: 500）
- `timeoutSeconds`: 連線逾時秒數 1-120（預設: 30，不限制推理時間）

#### AIChatRequest / AIChatResponse

符合 OpenAI Chat Completions API 標準的請求與回應模型。

#### AIClient

使用 Java 17 內建 HttpClient 與 AI 服務通訊的 HTTP 客戶端。

#### DefaultAIChatService

處理 AI 請求的主要服務，包括：
- 建立請求
- 呼叫 AI 服務
- 分割長訊息（Discord 2000 字元限制）
- 發布事件

#### AIChatMentionListener

JDA 事件監聽器，監聽使用者的機器人提及並觸發 AI 回應。

#### PromptLoader（V015 新增）

從外部檔案系統載入系統提示詞的服務介面。

**實作類別**：`DefaultPromptLoader`

**功能**：
- 掃描指定目錄中的 `.md` 檔案
- 將檔案內容解析為 `PromptSection`
- 組裝成完整的 `SystemPrompt`
- 驗證檔案大小與格式

**預設目錄結構**：
```
prompts/
├── personality.md    # 機器人人格定義
├── rules.md          # 使用規則
└── custom.md         # 自訂提示詞（可選）
```

#### PromptSection

提示詞區間模型，包含：
- `title`: 標題（檔案名稱，不含副檔名）
- `content`: 內容（檔案完整內容）

#### SystemPrompt

完整系統提示詞模型，包含：
- `sections`: `PromptSection` 列表
- `toFormattedString()`: 格式化為單一字串

#### PromptLoadError

提示詞載入錯誤類型：
- `DIRECTORY_NOT_FOUND`: 目錄不存在
- `FILE_TOO_LARGE`: 檔案超過大小限制
- `READ_FAILED`: 讀取失敗
- `EMPTY_DIRECTORY`: 目錄為空

## 配置

在 `.env` 檔案中配置 AI 服務：

```bash
# AI 服務配置
AI_SERVICE_BASE_URL=https://api.openai.com/v1
AI_SERVICE_API_KEY=your_api_key_here
AI_SERVICE_MODEL=gpt-3.5-turbo
AI_SERVICE_TEMPERATURE=0.7
AI_SERVICE_MAX_TOKENS=500
AI_SERVICE_TIMEOUT_SECONDS=30

# 提示詞載入器配置（V015 新增）
PROMPTS_DIR_PATH=./prompts
PROMPT_MAX_SIZE_BYTES=1048576
```

### AI 服務供應商範例

#### OpenAI
```bash
AI_SERVICE_BASE_URL=https://api.openai.com/v1
AI_SERVICE_API_KEY=sk-...
AI_SERVICE_MODEL=gpt-3.5-turbo
```

#### Azure OpenAI
```bash
AI_SERVICE_BASE_URL=https://your-resource.openai.azure.com/openai/deployments/your-deployment
AI_SERVICE_API_KEY=your-azure-api-key
AI_SERVICE_MODEL=gpt-35-turbo
```

#### 本地模型 (Ollama)
```bash
AI_SERVICE_BASE_URL=http://localhost:11434/v1
AI_SERVICE_API_KEY=ollama
AI_SERVICE_MODEL=llama2
```

## 使用方式

### 提及機器人

在 Discord 頻道中提及機器人即可觸發 AI 回應：

```
@LTDJMSBot 你好
```

如果訊息為空（僅提及），會使用預設問候語「你好」。

### 提示詞載入（V015 新增）

`DefaultPromptLoader` 會在服務啟動時自動載入 `prompts/` 目錄中的 `.md` 檔案作為系統提示詞。

**提示詞檔案範例**：

`prompts/personality.md`：
```markdown
# 機器人人格

你是一個友善且有幫助的 AI 助手，名為「龍騰電競智能助手」。

## 特點
- 禮貌且友善
- 提供準確的資訊
- 承認不知道的事情
```

`prompts/rules.md`：
```markdown
# 使用規則

1. 使用繁體中文回應
2. 簡潔明確，避免冗長
3. 不生成有害或不當內容
4. 保護使用者隱私
```

**載入流程**：
1. 掃描 `PROMPTS_DIR_PATH` 目錄
2. 讀取所有 `.md` 檔案
3. 每個檔案解析為一個 `PromptSection`
4. 按照檔案名稱排序組裝成 `SystemPrompt`
5. 格式化後作為系統訊息傳入 AI 請求

**錯誤處理**：

| 錯誤類型 | 原因 | 處理方式 |
|---------|------|----------|
| `DIRECTORY_NOT_FOUND` | 目錄不存在 | 記錄 WARN 日誌，使用空提示詞 |
| `FILE_TOO_LARGE` | 單一檔案超過大小限制 | 跳過該檔案，記錄 WARN 日誌 |
| `READ_FAILED` | 讀取失敗（權限問題等） | 跳過該檔案，記錄 ERROR 日誌 |
| `EMPTY_DIRECTORY` | 目錄為空或無 `.md` 檔案 | 記錄 WARN 日誌，使用空提示詞 |

### 錯誤處理

當 AI 服務發生錯誤時，會顯示友善的錯誤訊息：

| 錯誤類型 | 使用者看到的訊息 |
|---------|-----------------|
| 認證失敗 | `:x: AI 服務認證失敗，請聯絡管理員` |
| 速率限制 | `:timer: AI 服務暫時忙碌，請稍後再試` |
| 逾時 | `:hourglass: AI 服務連線逾時，請稍後再試` |
| 服務不可用 | `:warning: AI 服務暫時無法使用` |
| 空回應 | `:question: AI 沒有產生回應` |
| 格式錯誤 | `:warning: AI 回應格式錯誤` |

## 事件

### AIMessageEvent

當 AI 訊息發送時會發布 `AIMessageEvent`，包含：
- `guildId`: Discord 伺服器 ID
- `channelId`: Discord 頻道 ID
- `userId`: 使用者 ID
- `userMessage`: 使用者原始訊息
- `aiResponse`: AI 回應內容
- `timestamp`: 事件時間戳

## 日誌

日誌使用結構化格式，包含以下 MDC 欄位：
- `channel_id`: Discord 頻道 ID
- `user_id`: 使用者 ID
- `model`: AI 模型名稱

日誌等級：
- `ERROR`: AI 服務呼叫失敗、認證錯誤
- `WARN`: 速率限制、連線逾時、空回應
- `INFO`: AI 請求成功、回應時間

## 限制

- **無對話歷史**: 系統不保存對話歷史，每次請求都是獨立的
- **訊息長度**: 單則訊息限制 2000 字元（Discord 限制），超過會自動分割
- **連線逾時**：AI 服務連線逾時設定為 30 秒（可配置），不限制推理時間
- **並行**: 支援多個並行請求

## 測試

### 單元測試

```bash
# 執行 AI Chat 模組的所有單元測試
mvn test -Dtest='ltdjms.discord.aichat.unit.*'

# 執行特定測試類別
mvn test -Dtest=AIClientTest
mvn test -Dtest=AIChatServiceTest
mvn test -Dtest=MessageSplitterTest
```

### 整合測試

```bash
# 執行 AI Chat 整合測試
mvn test -Dtest='ltdjms.discord.aichat.integration.*'
```

## 相關文件

- [AI Chat 規格](../../specs/003-ai-chat/spec.md)
- [AI Chat 實作計畫](../../specs/003-ai-chat/plan.md)
- [AI Chat 快速入門](../../specs/003-ai-chat/quickstart.md)
- [外部提示詞載入器規格](../../specs/004-external-prompts-loader/spec.md)（V015 新增）
- [外部提示詞載入器實作計畫](../../specs/004-external-prompts-loader/plan.md)（V015 新增）
- [系統架構](../architecture/overview.md)
- [AI Chat 流程架構](../architecture/ai-chat-flow.md)
