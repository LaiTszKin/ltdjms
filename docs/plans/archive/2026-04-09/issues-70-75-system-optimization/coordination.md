# Coordination: Issues 70-75 系統優化批次

- Date: 2026-04-09
- Batch: issues-70-75-system-optimization

## Coordination Goal
在不改變 LTDJMS 核心經濟、付款、履約與 AI 互動對外功能的前提下，將 issue 70-75 拆成 5 份可獨立審批、獨立實作、獨立驗證的優化／硬化 spec，分別處理履約傳輸安全、ECPay callback 風險、Agent 記憶體資料外洩面、設定來源正規化，以及 currency persistence 單一路徑化。

## Batch Scope
- Included spec sets: `None`
- Archived completed spec sets:
  - `issue-70-fulfillment-url-ssrf-hardening` → `docs/plans/archive/2026-04-09/issues-70-75-system-optimization/issue-70-fulfillment-url-ssrf-hardening`
  - `issues-71-72-ecpay-callback-auth-hardening` → `docs/plans/archive/2026-04-09/issues-70-75-system-optimization/issues-71-72-ecpay-callback-auth-hardening`
  - `issue-73-agent-tool-memory-isolation` → `docs/plans/archive/2026-04-09/issues-70-75-system-optimization/issue-73-agent-tool-memory-isolation`
  - `issue-74-config-schema-canonicalization` → `docs/plans/archive/2026-04-09/issues-70-75-system-optimization/issue-74-config-schema-canonicalization`
  - `issue-75-currency-persistence-source-unification` → `docs/plans/archive/2026-04-09/issues-70-75-system-optimization/issue-75-currency-persistence-source-unification`
- Shared outcome: 系統在付款／履約／AI／設定／currency persistence 五個高風險面向上都有更清楚的單一真相與回歸保護，但 slash commands、shop、AI thread、guild currency 對使用者的主流程維持相容。
- Out of scope:
  - 新增終端使用者功能
  - 修改資料庫 schema
  - 重新設計商品、付款、Agent 或 currency domain model
  - 任何需要多份 spec 先後落地才可運作的跨批次重構
- Independence rule: 每份 spec 都必須在目前 `main` 的基線上單獨完成；若另一份 spec 尚未實作，仍不得阻塞本 spec 的設計、測試、審查與合併。

## Shared Context
- Current baseline:
  - `shop/` 內的付款與履約流程已依賴 idempotent claim / mark 機制，callback 與 fulfillment 皆為同步服務流程。
  - `aichat/` 與 `aiagent/` 共用 LangChain4j 記憶體與工具執行資料流，thread 級別對話以 `guildId:threadId:userId` 為主鍵。
  - `EnvironmentConfig` 已是 runtime 設定讀取入口，但 packaged config 與文件仍有漂移。
  - `CurrencyRepositoryModule` 生產 DI 已使用 JOOQ repository，但多數 integration / performance tests 仍直接 new JDBC repository。
- Shared constraints:
  - 不改變 Discord custom ID、slash command 名稱、付款單號生成格式、商品 fulfillment payload schema、currency domain repository 介面。
  - 不引入新的外部服務。
  - 任何安全硬化都必須預設 fail closed，但 production happy path 不得被無關改動破壞。
- Shared invariants:
  - 付款 paid transition 與 fulfillment 仍需維持冪等。
  - 商品 fulfillment webhook 仍維持既有 payload 與簽章標頭格式。
  - Agent conversation 仍以 user-scoped thread memory 為邊界。
  - 設定優先序仍維持 `system env > .env > packaged defaults > built-in defaults`。
  - currency account/config repository contract 對 service 層保持不變。

## Shared Preparation

### Shared Fields / Contracts
- Shared fields to introduce or reuse:
  - 不新增跨 spec 共用資料欄位。
  - 若單一 spec 需要新增內部 config 或 metadata，必須由該 spec 自己定義、自己驗證，不能要求其他 spec 先提供基底。
- Canonical source of truth:
  - 付款與履約真相：`shop/services` 與對應 repository claim / mark 語意
  - Agent 記憶體真相：`aichat/services/LangChain4jAIChatService.java` + `aiagent/services/*`
  - 設定真相：`src/main/java/ltdjms/discord/shared/EnvironmentConfig.java`
  - currency persistence 真相：`src/main/java/ltdjms/discord/shared/di/CurrencyRepositoryModule.java`
- Required preparation before implementation: `None`

### Replacement / Legacy Direction
- Legacy behavior being replaced:
  - URL query token 式 callback 授權
  - 工具結果直接回灌 chat memory 的 raw assistant text
  - `application.conf` / `application.properties` / docs 的平行 config schema
  - currency account/config 的 JDBC 與 JOOQ 雙實作真相分裂
- Required implementation direction:
  - 以 in-place replacement 為主，不建立長期平行路徑。
  - 必要的相容層只能是短期 alias、bridge 或 test factory，且需在 spec 內明確標出清理終點。
- Compatibility window:
  - `issue-74-config-schema-canonicalization` 可保留短期文件／檔案 alias 規則，但 runtime canonical schema 只能有一套。
  - `issue-75-currency-persistence-source-unification` 可有短期 test adapter / factory，但 production 與主要 regression coverage 最終只能對齊同一路徑。
- Cleanup required after cutover:
  - 移除過時測試建構方式、平行 repository 實作、漂移文件範例與不再允許的 callback 授權方式。

## Spec Ownership Map

### Spec Set 1: `issue-70-fulfillment-url-ssrf-hardening`
- Primary concern: 將商品 backend fulfillment target 驗證與實際 outbound 連線綁成單一路徑，補齊 DNS rebinding / non-public target regression 保護。
- Allowed touch points:
  - `src/main/java/ltdjms/discord/shop/services/ProductFulfillmentApiService.java`
  - `src/test/java/ltdjms/discord/shop/services/ProductFulfillmentApiServiceTest.java`
- Must not change:
  - ECPay callback 流程
  - `EnvironmentConfig`
  - 商品 payload schema
- Depends on shared preparation: `None`
- Cross-spec implementation dependency: `None`

### Spec Set 2: `issues-71-72-ecpay-callback-auth-hardening`
- Primary concern: 取消 query-token 授權與 stage/public callback 的危險預設，保留 production paid callback 主流程。
- Allowed touch points:
  - `src/main/java/ltdjms/discord/shop/services/EcpayCvsPaymentService.java`
  - `src/main/java/ltdjms/discord/shop/services/EcpayCallbackHttpServer.java`
  - `src/main/java/ltdjms/discord/shop/services/FiatPaymentCallbackService.java`
  - `src/test/java/ltdjms/discord/shop/services/*`
- Must not change:
  - `EnvironmentConfig` 的 canonical schema（由 issue 74 擁有）
  - fulfillment transport（由 issue 70 擁有）
- Depends on shared preparation: `None`
- Cross-spec implementation dependency: `None`

### Spec Set 3: `issue-73-agent-tool-memory-isolation`
- Primary concern: 將工具執行紀錄與 chat memory context 解耦，保留審計能力但不持久化 raw tool results。
- Allowed touch points:
  - `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`
  - `src/main/java/ltdjms/discord/aiagent/services/InMemoryToolCallHistory.java`
  - `src/main/java/ltdjms/discord/aiagent/services/SimplifiedChatMemoryProvider.java`
  - `src/test/java/ltdjms/discord/aiagent/unit/services/*`
- Must not change:
  - Discord thread history provider 取數邏輯
  - 工具本身的 Discord 權限模型
- Depends on shared preparation: `None`
- Cross-spec implementation dependency: `None`

### Spec Set 4: `issue-74-config-schema-canonicalization`
- Primary concern: 建立單一 canonical config schema，對齊 `EnvironmentConfig`、packaged defaults、測試與文件。
- Allowed touch points:
  - `src/main/java/ltdjms/discord/shared/EnvironmentConfig.java`
  - `src/main/resources/application.properties`
  - `src/main/resources/application.conf`
  - `src/test/java/ltdjms/discord/shared/*`
  - `docs/development/configuration.md`
- Must not change:
  - shop callback 安全決策
  - currency repository 選型
- Depends on shared preparation: `None`
- Cross-spec implementation dependency: `None`

### Spec Set 5: `issue-75-currency-persistence-source-unification`
- Primary concern: 讓 production wiring 與主要 integration/performance coverage 對齊同一組 repository implementation，並為 config repository 補足 JOOQ coverage。
- Allowed touch points:
  - `src/main/java/ltdjms/discord/shared/di/CurrencyRepositoryModule.java`
  - `src/main/java/ltdjms/discord/currency/persistence/*`
  - `src/test/java/ltdjms/discord/currency/integration/*`
  - `src/test/java/ltdjms/discord/currency/performance/*`
- Must not change:
  - currency domain service 介面
  - 獨立的 transaction repository 路徑（本批次不處理）
- Depends on shared preparation: `None`
- Cross-spec implementation dependency: `None`

## Conflict Boundaries
- Shared files requiring coordination:
  - `None`；本批次已刻意避免讓兩份 spec 同時擁有同一個核心檔案。
- Merge order / landing order:
  - 功能上完全獨立。
  - 建議的審查順序僅為操作便利：`issue-74` → `issue-70` → `issues-71-72` → `issue-73` → `issue-75`。
- Worktree notes:
  - 可各自使用獨立 worktree。
  - 每份 spec 合併前都應以自身 spec 列出的測試集為最低驗證門檻。

## Integration Checkpoints
- Combined behaviors to verify after merge:
  - 商品 fulfillment 仍能成功呼叫 public HTTPS backend，且不接受非 public target。
  - ECPay callback 在 production path 可正常處理 paid callback；stage/public 危險部署不再默默放行。
  - Agent thread 仍可正確取得 thread history，但舊回合的 raw tool result 不再成為後續 prompt context。
  - 設定文件、packaged defaults 與 runtime schema 對齊。
  - currency integration/performance 測試改為驗證 production repository path。
- Required final test scope:
  - 目標 unit / integration / adversarial regression tests
  - `make test`
  - 視 `issue-75` 內容補跑對應 integration/performance suite
- Rollback notes:
  - 每份 spec 都應可單獨回退，且不得要求其他 spec 的 cleanup 先存在。
  - 若某 spec 上線後出現風險，回退時也必須保留既有 paid/fulfillment/account data 不被破壞。

## Open Questions
None
