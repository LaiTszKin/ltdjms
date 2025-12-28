# AI Chat 流程架構

本文件詳細說明 LTDJMS Discord Bot 的 AI Chat 功能流程，包括訊息處理、AI 服務整合、錯誤處理與事件發布機制。

---

## 1. 高階流程圖

```mermaid
flowchart TD
    Start([使用者發送訊息 @Bot]) --> CheckMention{訊息包含<br/>機器人提及?}
    CheckMention -->|否| Ignore([忽略訊息])
    CheckMention -->|是| ExtractMessage[提取訊息內容<br/>移除機器人提及]

    ExtractMessage --> IsEmpty{訊息為空?}
    IsEmpty -->|是| UseDefault[使用預設問候語<br/>「你好」]
    IsEmpty -->|否| UseMessage[使用使用者訊息]
    UseDefault --> BuildRequest
    UseMessage --> BuildRequest[建立 AI 請求<br/>AIChatRequest]

    BuildRequest --> CallService[呼叫 AIChatService]
    CallService --> ValidateConfig{配置有效?}
    ValidateConfig -->|否| ConfigError[回傳配置錯誤]
    ValidateConfig -->|是| CallClient[呼叫 AIClient]

    CallClient --> HTTPRequest[HTTP POST<br/>/chat/completions]
    HTTPRequest --> AIService{AI 服務回應}

    AIService -->|200 OK| ParseResponse[解析 JSON 回應]
    AIService -->|401| AuthError[認證失敗]
    AIService -->|429| RateLimitError[速率限制]
    AIService -->|連線逾時/連線失敗| TimeoutError[連線逾時錯誤]
    AIService -->|5xx| ServerError[伺服器錯誤]

    ParseResponse --> IsResponseEmpty{回應內容為空?}
    IsResponseEmpty -->|是| EmptyError[空回應錯誤]
    IsResponseEmpty -->|否| SplitMessage[分割長訊息<br/>MessageSplitter]

    SplitMessage --> SendDiscord[發送 Discord 訊息]
    AuthError --> SendError1[發送錯誤訊息]
    RateLimitError --> SendError2[發送錯誤訊息]
    TimeoutError --> SendError3[發送錯誤訊息]
    ServerError --> SendError4[發送錯誤訊息]
    ConfigError --> SendError5[發送錯誤訊息]
    EmptyError --> SendError6[發送錯誤訊息]

    SendDiscord --> PublishEvent[發布 AIMessageEvent]
    PublishEvent --> LogSuccess[記錄成功日誌]
    SendError1 --> LogError1[記錄錯誤日誌]
    SendError2 --> LogError2[記錄錯誤日誌]
    SendError3 --> LogError3[記錄錯誤日誌]
    SendError4 --> LogError4[記錄錯誤日誌]
    SendError5 --> LogError5[記錄錯誤日誌]
    SendError6 --> LogError6[記錄錯誤日誌]

    LogSuccess --> End([流程結束])
    LogError1 --> End
    LogError2 --> End
    LogError3 --> End
    LogError4 --> End
    LogError5 --> End
    LogError6 --> End
    Ignore --> End

    style Start fill:#e1f5e1
    style End fill:#ffe1e1
    style AIService fill:#fff4e1
    style SendDiscord fill:#e1f0ff
    style PublishEvent fill:#e1f0ff
```

---

## 2. 元件互動圖

```mermaid
flowchart LR
    subgraph Discord_Layer ["Discord 層"]
        User[Discord 使用者]
        JDA[JDA Gateway]
    end

    subgraph Event_Layer ["事件層"]
        Monitor[GenericEventMonitor]
        Listener[AIChatMentionListener]
    end

    subgraph Service_Layer ["服務層"]
        ChatSvc[AIChatService]
        HTTPClient[HttpClient]
    end

    subgraph External_Layer ["外部服務"]
        AI[AI 服務 API<br/>OpenAI 相容]
    end

    subgraph Infrastructure ["基礎設施"]
        Config[EnvironmentConfig]
        EventBus[DomainEventPublisher]
        Logger[SLF4J + Logback]
    end

    User -->|提及訊息| JDA
    JDA -->|MessageReceivedEvent| Monitor
    Monitor -->|過濾提及事件| Listener
    Listener -->|讀取配置| Config
    Listener -->|處理請求| ChatSvc
    ChatSvc -->|HTTP 請求| HTTPClient
    HTTPClient -->|POST /chat/completions| AI
    AI -->|JSON 回應| HTTPClient
    HTTPClient -->|Result| ChatSvc
    ChatSvc -->|Discord 訊息| JDA
    JDA -->|顯示回應| User
    ChatSvc -->|AIMessageEvent| EventBus
    ChatSvc -->|結構化日誌| Logger

    style User fill:#e1f5e1
    style AI fill:#ffe1e1
    style ChatSvc fill:#e1f0ff
    style EventBus fill:#fff4e1
```

---

## 3. 資料流程圖

```mermaid
flowchart TD
    subgraph Input ["輸入階段"]
        UserMsg[使用者訊息<br/>「@Bot 你好」]
    end

    subgraph Processing ["處理階段"]
        Extract[提取純訊息<br/>「你好」]
        Build[建構 AIChatRequest<br/>model, messages, temperature, max_tokens]
        Serialize[序列化為 JSON]
    end

    subgraph Network ["網路傳輸"]
        Request[HTTP POST Request<br/>Content-Type: application/json<br/>Authorization: Bearer {API_KEY}]
    end

    subgraph AIService ["AI 服務處理"]
        AIProcess[AI 模型推理]
        AIGenerate[生成回應]
    end

    subgraph Response ["回應階段"]
        ResponseJSON[JSON 回應<br/>choices[0].message.content]
        Deserialize[反序列化 AIChatResponse]
        Validate[驗證回應內容]
    end

    subgraph Output ["輸出階段"]
        Split[智慧分割<br/>每則 ≤ 2000 字元]
        Send[發送 Discord 訊息]
        Display[顯示給使用者]
    end

    subgraph Monitoring ["監控階段"]
        Event[發布 AIMessageEvent]
        Log[記錄日誌<br/>MDC: channel_id, user_id, model]
    end

    UserMsg --> Extract
    Extract --> Build
    Build --> Serialize
    Serialize --> Request
    Request --> AIProcess
    AIProcess --> AIGenerate
    AIGenerate --> ResponseJSON
    ResponseJSON --> Deserialize
    Deserialize --> Validate
    Validate --> Split
    Split --> Send
    Send --> Display
    Validate --> Event
    Event --> Log

    style UserMsg fill:#e1f5e1
    style Display fill:#e1f5e1
    style AIProcess fill:#ffe1e1
    style Log fill:#fff4e1
```

---

## 4. 錯誤處理流程

```mermaid
flowchart TD
    ErrorStart[錯誤發生] --> ErrorClassify{錯誤分類}

    ErrorClassify -->|HTTP 401| AuthError[認證失敗]
    ErrorClassify -->|HTTP 429| RateLimitError[速率限制]
    ErrorClassify -->|逾時/連線| TimeoutError[連線逾時]
    ErrorClassify -->|HTTP 5xx| ServerError[伺服器錯誤]
    ErrorClassify -->|空回應| EmptyError[空回應]
    ErrorClassify -->|JSON 解析失敗| FormatError[格式錯誤]

    AuthError --> UserMsg1[「AI 服務認證失敗，<br/>請聯絡管理員」]
    RateLimitError --> UserMsg2[「AI 服務暫時忙碌，<br/>請稍後再試」]
    TimeoutError --> UserMsg3[「AI 服務暫時無法使用」]
    ServerError --> UserMsg4[「AI 回應格式錯誤」]
    EmptyError --> UserMsg5[「AI 沒有產生回應」]
    FormatError --> UserMsg4

    UserMsg1 --> Log1[ERROR 級日誌]
    UserMsg2 --> Log2[WARN 級日誌]
    UserMsg3 --> Log3[WARN 級日誌]
    UserMsg4 --> Log4[ERROR 級日誌]
    UserMsg5 --> Log5[WARN 級日誌]

    Log1 --> End[結束]
    Log2 --> End
    Log3 --> End
    Log4 --> End
    Log5 --> End

    style ErrorStart fill:#ffe1e1
    style End fill:#ffe1e1
    style AuthError fill:#ffcccc
    style Log1 fill:#ffcccc
```

**錯誤類型與日誌等級對應**：

| 錯誤類型 | DomainError.Category | 日誌等級 | 使用者看到訊息 |
|---------|---------------------|---------|---------------|
| HTTP 401 | `AUTHENTICATION_FAILED` | ERROR | `:x: AI 服務認證失敗，請聯絡管理員` |
| HTTP 429 | `RATE_LIMITED` | WARN | `:timer: AI 服務暫時忙碌，請稍後再試` |
| 連線逾時 | `TIMEOUT` | WARN | `:hourglass: AI 服務連線逾時，請稍後再試` |
| HTTP 5xx | `SERVICE_ERROR` | ERROR | `:warning: AI 回應格式錯誤` |
| 空回應 | `EMPTY_RESPONSE` | WARN | `:question: AI 沒有產生回應` |
| JSON 解析失敗 | `PARSE_ERROR` | ERROR | `:warning: AI 回應格式錯誤` |

---

## 5. 訊息分割演算法

當 AI 回應超過 Discord 訊息長度限制（2000 字元）時，`MessageSplitter` 會智慧分割訊息：

```mermaid
flowchart TD
    StartSplit[AI 回應內容] --> CheckLength{長度 > 2000?}
    CheckLength -->|否| SingleMsg[單則訊息]
    CheckLength -->|是| FindBreaks[尋找分割點<br/>段落、句子、空格]

    FindBreaks --> SplitStrategy{分割策略}
    SplitStrategy -->|優先| Paragraph[段落分割<br/>「\n\n」]
    SplitStrategy -->|其次| Sentence[句子分割<br/>「。！？.!?」]
    SplitStrategy -->|最後| Force[強制分割<br/>每 1900 字元]

    Paragraph --> LoopSplit[迴圈分割]
    Sentence --> LoopSplit
    Force --> LoopSplit

    LoopSplit --> ValidatePiece{片段有效?}
    ValidatePiece -->|否| TrimPiece[修剪片段]
    ValidatePiece -->|是| AddPiece[加入片段清單]
    TrimPiece --> AddPiece

    AddPiece --> HasMore{還有剩餘?}
    HasMore -->|是| LoopSplit
    HasMore -->|否| EndSplit[結束分割]

    SingleMsg --> EndMsg[傳回訊息清單]
    EndSplit --> EndMsg

    style StartSplit fill:#e1f5e1
    style EndMsg fill:#e1f5e1
    style FindBreaks fill:#fff4e1
```

**分割原則**：
1. **段落優先**：優先在雙換行符（`\n\n`）處分割
2. **句子次之**：其次在句號、驚嘆號、問號處分割
3. **避免截斷**：盡量避免在單詞或程式碼中間分割
4. **安全邊界**：每個片段最多 1900 字元（預留 100 字元緩衝）

---

## 6. 事件發布與日誌記錄

### 6.1 AIMessageEvent 結構

當 AI 訊息成功發送後，系統會發布 `AIMessageEvent`：

```java
public class AIMessageEvent extends DomainEvent {
    private final String guildId;
    private final String channelId;
    private final String userId;
    private final String userMessage;
    private final String aiResponse;
    private final Instant timestamp;
}
```

### 6.2 結構化日誌格式

日誌使用 MDC (Mapped Diagnostic Context) 記錄關鍵資訊：

```json
{
  "timestamp": "2025-12-28T12:34:56.789Z",
  "level": "INFO",
  "logger": "ltdjms.discord.aichat.services.DefaultAIChatService",
  "message": "AI chat request completed",
  "mdc": {
    "channel_id": "1234567890",
    "user_id": "0987654321",
    "model": "gpt-3.5-turbo",
    "response_time_ms": 1234,
    "response_length": 156
  }
}
```

### 6.3 日誌等級使用原則

| 等級 | 使用場景 | 範例 |
|------|---------|------|
| ERROR | AI 服務認證失敗、伺服器錯誤、格式錯誤 | 認證失敗、JSON 解析失敗 |
| WARN | 速率限制、連線逾時、空回應 | HTTP 429、連線逾時、空內容 |
| INFO | AI 請求成功、回應時間 | 請求成功（含回應時間） |

---

## 7. 配置驗證流程

```mermaid
flowchart TD
    StartConfig[服務啟動] --> LoadEnv[載入 .env 檔案]
    LoadEnv --> ValidateBaseURL{AI_SERVICE_BASE_URL<br/>有效?}
    ValidateBaseURL -->|否| ConfigError1[啟動失敗<br/>缺少必要配置]
    ValidateBaseURL -->|是| ValidateKey{AI_SERVICE_API_KEY<br/>存在?}

    ValidateKey -->|否| ConfigError2[啟動失敗<br/>缺少 API 金鑰]
    ValidateKey -->|是| ValidateModel{AI_SERVICE_MODEL<br/>有效?}

    ValidateModel -->|否| UseDefault[使用預設模型<br/>gpt-3.5-turbo]
    ValidateModel -->|是| ValidateTemp{AI_SERVICE_TEMPERATURE<br/>範圍 0.0-2.0?}

    UseDefault --> ValidateTemp
    ValidateTemp -->|否| ConfigError3[啟動失敗<br/>溫度超出範圍]
    ValidateTemp -->|是| ValidateTokens{AI_SERVICE_MAX_TOKENS<br/>範圍 1-4096?}

    ValidateTokens -->|否| ConfigError4[啟動失敗<br/>Token 數超出範圍]
    ValidateTokens -->|是| ValidateTimeout{AI_SERVICE_TIMEOUT_SECONDS<br/>範圍 1-120?}

    ValidateTimeout -->|否| ConfigError5[啟動失敗<br/>連線逾時超出範圍]
    ValidateTimeout -->|是| CreateConfig[建立 AIServiceConfig]

    CreateConfig --> Success[配置成功]
    ConfigError1 --> Fail1[記錄錯誤日誌]
    ConfigError2 --> Fail2[記錄錯誤日誌]
    ConfigError3 --> Fail3[記錄錯誤日誌]
    ConfigError4 --> Fail4[記錄錯誤日誌]
    ConfigError5 --> Fail5[記錄錯誤日誌]

    Fail1 --> Abort[中止啟動]
    Fail2 --> Abort
    Fail3 --> Abort
    Fail4 --> Abort
    Fail5 --> Abort
    Success --> Ready[服務就緒]

    style StartConfig fill:#e1f5e1
    style Ready fill:#e1f5e1
    style Abort fill:#ffe1e1
    style ConfigError1 fill:#ffcccc
    style ConfigError2 fill:#ffcccc
    style ConfigError3 fill:#ffcccc
    style ConfigError4 fill:#ffcccc
    style ConfigError5 fill:#ffcccc
```

**配置驗證規則**：

| 變數名稱 | 必填 | 預設值 | 驗證規則 |
|---------|:----:|--------|---------|
| `AI_SERVICE_BASE_URL` | ✅ | 無 | 必須為有效 URL |
| `AI_SERVICE_API_KEY` | ✅ | 無 | 不可為空白 |
| `AI_SERVICE_MODEL` | ❌ | `gpt-3.5-turbo` | 無 |
| `AI_SERVICE_TEMPERATURE` | ❌ | `0.7` | 0.0 ≤ 值 ≤ 2.0 |
| `AI_SERVICE_MAX_TOKENS` | ❌ | `500` | 1 ≤ 值 ≤ 4096 |
| `AI_SERVICE_TIMEOUT_SECONDS` | ❌ | `30` | 1 ≤ 值 ≤ 120 |

---

## 8. 效能考量

### 8.1 並行處理

```mermaid
flowchart TD
    subgraph Concurrent ["並行請求處理"]
        User1[使用者 A] --> Request1[請求 1]
        User2[使用者 B] --> Request2[請求 2]
        User3[使用者 C] --> Request3[請求 3]

        Request1 --> Pool[HTTP 連線池]
        Request2 --> Pool
        Request3 --> Pool

        Pool --> AI1[AI 服務呼叫 1]
        Pool --> AI2[AI 服務呼叫 2]
        Pool --> AI3[AI 服務呼叫 3]

        AI1 --> Response1[回應 1]
        AI2 --> Response2[回應 2]
        AI3 --> Response3[回應 3]

        Response1 --> User1
        Response2 --> User2
        Response3 --> User3
    end

    style User1 fill:#e1f5e1
    style User2 fill:#e1f5e1
    style User3 fill:#e1f5e1
    style Pool fill:#fff4e1
```

**並行處理特性**：
- **獨立處理**：每個請求獨立處理，互不干擾
- **連線池**：Java 17 HttpClient 自動管理連線池
- **無狀態**：不保存對話歷史，無需考慮並行衝突

### 8.2 效能指標

| 指標 | 目標值 | 測量方式 |
|------|--------|---------|
| AI 回應時間 | < 5 秒 (95th percentile) | 日誌 `response_time_ms` |
| 並行處理能力 | 100 個同時請求 | 壓力測試 |
| AI 服務成功率 | > 95% | 日誌錯誤率統計 |
| 錯誤回應時間 | < 3 秒 | 錯誤處理連線逾時設定 |

---

## 9. 提示詞載入流程（V015 新增）

### 9.1 提示詞載入時序圖

```mermaid
sequenceDiagram
    participant Bot as DiscordCurrencyBot
    participant Module as AIChatModule
    participant Loader as DefaultPromptLoader
    participant FS as FileSystem
    participant AI as AIClient

    Bot->>Module: 建構 AIChatModule
    Module->>Loader: 注入 PromptLoader
    activate Loader

    Loader->>FS: 檢查目錄是否存在
    FS-->>Loader: 目錄存在/不存在

    alt 目錄存在
        Loader->>FS: 掃描 .md 檔案
        FS-->>Loader: 檔案列表

        loop 每個 .md 檔案
            Loader->>FS: 讀取檔案內容
            alt 檔案大小 > 限制
                Loader->>Loader: 記錄 WARN，跳過
            else 讀取成功
                Loader->>Loader: 建立 PromptSection
            end
        end

        alt 至少有一個 PromptSection
            Loader->>Loader: 組裝 SystemPrompt
            Loader-->>Module: Result.ok(SystemPrompt)
        else 無有效檔案
            Loader-->>Module: Result.err(EMPTY_DIRECTORY)
        end
    else 目錄不存在
        Loader-->>Module: Result.err(DIRECTORY_NOT_FOUND)
    end

    deactivate Loader

    Module->>AI: 建立 AIClient（含 SystemPrompt）
```

### 9.2 提示詞載入流程圖

```mermaid
flowchart TD
    Start([服務啟動]) --> InitPromptLoader[初始化 PromptLoader]
    InitPromptLoader --> CheckDir{prompts/<br/>目錄存在?}

    CheckDir -->|否| DirNotFound[記錄 WARN 日誌<br/>使用空提示詞]
    CheckDir -->|是| ScanFiles[掃描目錄中的 .md 檔案]

    ScanFiles --> HasFiles{有 .md 檔案?}
    HasFiles -->|否| EmptyDir[記錄 WARN 日誌<br/>使用空提示詞]

    HasFiles -->|是| ProcessFiles[處理每個檔案]
    ProcessFiles --> LoadFile[載入檔案內容]

    LoadFile --> CheckSize{檔案大小<br/>超過限制?}
    CheckSize -->|是| SkipFile[記錄 WARN 日誌<br/>跳過此檔案]
    CheckSize -->|否| ParseSection[建立 PromptSection]

    SkipFile --> NextFile{還有檔案?}
    ParseSection --> AddSection[加入 sections 清單]
    AddSection --> NextFile

    NextFile -->|是| ProcessFiles
    NextFile -->|否| HasSections{有有效<br/>PromptSection?}

    HasSections -->|否| EmptyDir
    HasSections -->|是| BuildPrompt[建立 SystemPrompt]

    BuildPrompt --> FormatPrompt[格式化為單一字串]
    FormatPrompt --> InjectClient[注入至 AIClient]

    DirNotFound --> Ready([服務就緒])
    EmptyDir --> Ready
    InjectClient --> Ready

    style Start fill:#e1f5e1
    style Ready fill:#e1f5e1
    style DirNotFound fill:#fff4e1
    style EmptyDir fill:#fff4e1
    style BuildPrompt fill:#e1f0ff
```

### 9.3 提示詞載入錯誤處理

| 錯誤類型 | 觸發條件 | 日誌等級 | 系統行為 |
|---------|---------|----------|----------|
| `DIRECTORY_NOT_FOUND` | `PROMPTS_DIR_PATH` 目錄不存在 | WARN | 使用空提示詞，服務正常啟動 |
| `FILE_TOO_LARGE` | 單一檔案超過 `PROMPT_MAX_SIZE_BYTES` | WARN | 跳過該檔案，繼續處理其他檔案 |
| `READ_FAILED` | 檔案讀取失敗（權限問題等） | ERROR | 跳過該檔案，繼續處理其他檔案 |
| `EMPTY_DIRECTORY` | 目錄為空或無 `.md` 檔案 | WARN | 使用空提示詞，服務正常啟動 |

**設計原則**：
- **寬容失敗**：提示詞載入失敗不應阻止服務啟動
- **部分載入**：部分檔案失敗不影響其他有效檔案的載入
- **日誌記錄**：所有錯誤都會記錄日誌，方便問題排查

---

## 10. 相關文件

| 文件 | 說明 |
|------|------|
| [AI Chat 模組文件](../modules/aichat.md) | 模組概述與使用方式 |
| [AI Chat 時序圖](sequence-diagrams.md#9-ai-chat-提及回應流程v010-新增) | 詳細時序圖 |
| [AI Chat 規格](../../specs/003-ai-chat/spec.md) | 功能規格與驗收標準 |
| [AI Chat 實作計畫](../../specs/003-ai-chat/plan.md) | 實作計畫與技術決策 |
| [AI Chat API 契約](../../specs/003-ai-chat/contracts/openapi.yaml) | AI 服務 API 規格 |
| [AI Chat 快速入門](../../specs/003-ai-chat/quickstart.md) | 快速開始指南 |
| [外部提示詞載入器規格](../../specs/004-external-prompts-loader/spec.md) | V015 新增功能規格 |
| [外部提示詞載入器實作計畫](../../specs/004-external-prompts-loader/plan.md) | V015 實作計畫 |
