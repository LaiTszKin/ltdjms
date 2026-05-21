# Design: Administration

- Date: 2026-05-20
- Feature: Administration
- Change Name: administration

> **Purpose:** **High-level architectural context for `tasks.md`**—structure, coupling, sequencing intent—not a second implementation list. Requirement intent stays in `spec.md`; **documented vendor truth** stays in **`contract.md`**. **`tasks.md` owns** every runnable step (paths, edits, verifies).
>
> **Do not duplicate `tasks.md`:** no checkbox-style chores, no per-file implementation lines, no verifiers—the executable queue exists only under **`tasks.md`**. Optional **`INT-###`** labels are **coarse anchors** that task rows cite for traceability.
>
> **Audience:** Humans/agents authoring **`tasks.md`**, and implementers needing **mental model before** ticking task boxes—not a standalone execution script.

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1.1–R14.6                                                                   |
| In-scope modules (≤3)       | `packages/admin/`（新建，所有 administration 原始碼）                          |
| External systems touched    | Discord API（透過 `@ltdjms/shared` DiscordRuntimeGateway）；所有依賴 package（economy、shop、dispatch、ai）的 Facade 介面 |
| Batch coordination          | [`../coordination.md`](../coordination.md)                                    |

## Target vs baseline

|                       | Baseline (today)                                                                   | Target (after this change)                                                                                                             |
| --------------------- | ---------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Structure / ownership | Java monolith：`ltdjms.discord.panel.*` 套件，與所有 domain service 直接耦合在單一 JVM 程序 | `packages/admin/` — 獨立 TypeScript workspace package，僅依賴其他 package 的 Facade/service 介面，零直接 database 或 Redis 存取 |

## Boundaries

- Entry surface(s): Discord Interaction（slash command、button、select menu、modal submit），全部經由 discord.js `interactionCreate` 事件進入 `SlashCommandListener`
- Trust boundary crossed: Discord guild permission（ADMINISTRATOR / guild owner）—由 Discord API 的 `default_member_permissions` 做第一層過濾，handler 層做第二層檢查
- Outside → inside (one line): `Discord 用戶互動事件` → `SlashCommandListener` → `PanelCommandHandler`（按 commandName 分發）→ `Facade` → `Domain Services（其他 package）`

## Modules (nouns only)

| Module key              | Responsibility (one sentence)                                                                   | Owned artifacts (types, tables, queues)                                                                     |
| ----------------------- | ----------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `admin-panel`           | 管理面板的 slash command、按鈕處理器、Modal 處理器、session 管理、即時更新監聽器                        | `AdminPanelCommand`、各 feature handler（9 組）、`AdminPanelSessionManager`、`AdminPanelUpdateListener`       |
| `user-panel`            | 用戶面板的 slash command、按鈕處理器、Modal 處理器、session 管理、即時更新監聽器                        | `UserPanelCommand`、user panel handlers、`PanelSessionManager`、`UserPanelUpdateListener`                    |
| `facades`               | 5 個聚合 Facade，封裝對其他 package domain service 的呼叫，提供管理面板與用戶面板所需的統一 API            | `CurrencyManagementFacade`、`GameTokenManagementFacade`、`GameConfigManagementFacade`、`AIConfigManagementFacade`、`MemberInfoFacade` |
| `command-infra`         | 集中式 slash command 分發、metrics 收集、錯誤處理                                                    | `SlashCommandListener`、`SlashCommandMetrics`、`BotErrorHandler`                                             |
| `slash-registration`    | 在 Discord API 註冊所有 slash command（含 zh-TW 在地化）                                             | Command registration script / `SlashCommandRegistrar`                                                       |

---

## Interaction anchors (`INT-###`)

**Grain:** **Above `tasks.md`**. One anchor ≈ a **meaningful handshake** between module keys—not one checkbox. Several task lines may realize a single `INT-###`.

| ID        | Intent (when this coupling matters)                                                       | Caller → Callee                   | Coupling kind (route pattern · RPC · event · sync call—**name/pattern**, not file path)                                                    | Information / state crossing (summary)                                                                                 | Failure / propagation expectation (summary)                                                        |
| --------- | ----------------------------------------------------------------------------------------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `INT-001` | 管理面板操作貨幣：調整餘額、設定餘額                                                            | `admin-panel` → `facades`         | sync call：`CurrencyManagementFacade.adjustBalance()` / `.setBalance()` / `.getBalance()`                                                  | guildId、userId、amount、reason、actorId → Result<Balance, DomainError>                                                | Facade 將底層 DomainError 向上傳播；handler 透過 BotErrorHandler 轉換為用戶訊息                         |
| `INT-002` | 管理面板操作代幣：調整代幣、設定代幣                                                            | `admin-panel` → `facades`         | sync call：`GameTokenManagementFacade.adjustTokens()` / `.setTokens()` / `.getTokens()`                                                   | guildId、userId、amount、reason、actorId → Result<GameToken, DomainError>                                             | 同上                                                                                                |
| `INT-003` | 管理面板操作遊戲設定                                                                         | `admin-panel` → `facades`         | sync call：`GameConfigManagementFacade.getDiceGame1Config()` / `.updateDiceGame1Config()` / ditto for game 2                              | guildId、config 物件 → Result<Config, DomainError>；更新成功後 Facade 發布 DiceGameConfigChangedEvent                    | Facade 負責參數驗證 + event 發布；handler 僅處理 UI 層的 Modal 驗證                                    |
| `INT-004` | 管理面板操作產品與兌換碼（跨模組）                                                              | `admin-panel` → `@ltdjms/shop`    | sync call：`ProductService` + `ProductRepository` + `RedemptionCodeRepository` 的直接介面呼叫                                              | 產品 CRUD 參數、兌換碼生成參數 → Result<Product, DomainError> / Result<RedemptionCode[], DomainError>                   | Shop module 負責資料持久化與 event 發布（ProductChangedEvent、RedemptionCodesGeneratedEvent）         |
| `INT-005` | 管理面板操作 AI 頻道與 Agent 設定                                                              | `admin-panel` → `facades`         | sync call：`AIConfigManagementFacade` 的所有方法                                                                                         | guildId、channelId/categoryId、agentMode → Result<void/Config, DomainError>                                           | Facade 將底層 AI module 錯誤統一轉換；變更後發布對應 event                                              |
| `INT-006` | 管理面板操作護航售後、定價、目錄                                                                | `admin-panel` → `@ltdjms/dispatch`| sync call：`DispatchAfterSalesStaffService` / `EscortOptionPricingService` / `EscortOptionCatalogRepository` 的介面呼叫                    | guildId、userId/staffId、optionId、price、目錄項目詳情 → Result<...>                                                    | Dispatch module 負責資料持久化與 event 發布；目錄刪除時需檢查參照完整性                                |
| `INT-007` | 用戶面板查詢餘額／代幣／交易記錄                                                                | `user-panel` → `facades`          | sync call：`MemberInfoFacade.getMemberSummary()` / `.getCurrencyTransactions()` / `.getTokenTransactions()` / `.getRedemptionHistory()`   | guildId、userId、分頁參數 → Result<MemberSummary/Page<Transaction>, DomainError>                                      | Facade 聚合多個 service 的查詢結果；分頁由 repository 層支援                                          |
| `INT-008` | 用戶面板兌換碼兌換                                                                            | `user-panel` → `@ltdjms/shop`     | sync call：`RedemptionService.redeemCode()`                                                                                             | guildId、userId、code → Result<RedemptionResult, DomainError>                                                         | Shop module 負責兌換冪等邏輯與 event 發布                                                             |
| `INT-009` | Session 管理：建立、查詢、TTL 過期清理                                                          | `admin-panel` / `user-panel`（自包含於 admin 套件） | in-memory Map 為主儲存，選擇性 CacheService (Redis) 支援分散式 session                                                                   | session key（guildId+userId）、session state、InteractionHook reference → session 物件                                  | Session 自包含於 admin 套件；無共享 DiscordSessionManager；TTL 過期後自動清理                       |
| `INT-010` | 即時更新：DomainEvent → UpdateListener → 更新 panel embed                                      | `facades/services` → `admin-panel` / `user-panel` | event：DomainEventPublisher.publish(event) → AdminPanelUpdateListener / UserPanelUpdateListener.onEvent(event)                            | Event payload（guildId、userId、變更詳情）→ 更新仍有效的 PanelSession embed                                             | Event listener 例外被捕獲並 logged，不影響事件分發鏈；更新失敗時清理過期 session                        |
| `INT-011` | Slash command 分發與錯誤處理                                                                  | `SlashCommandListener` → handlers / `BotErrorHandler` | sync dispatch：interactionCreate 事件 → commandName 匹配 → handler.execute()；例外 → BotErrorHandler.handle(error, interaction)         | Interaction 物件（commandName、options、guildId、userId）→ handler 回覆；DomainError → zh-TW 用戶訊息                   | BotErrorHandler 保證所有例外都轉換為用戶可見回覆（deferReply + editReply 或 reply）                      |
| `INT-012` | Slash command 延遲 metrics                                                                   | `SlashCommandListener` → `SlashCommandMetrics` | sync call：handler 執行前 recordStart()、執行後 recordEnd(commandName, success)                                                           | commandName、elapsedMs、success boolean → metrics accumulator（p50/p95/p99 percentile、counters）                       | Metrics 收集不影響指令執行；僅在開發環境 log 輸出                                                       |

**Ordering / concurrency (design-level):**

- `INT-001` 到 `INT-008` 之間無並行限制（各 handler 獨立執行，Discord interaction 已保證單一用戶的互動為序列化）
- `INT-009`（session 管理）為 `INT-001` 到 `INT-008` 的前置依賴——所有 handler 在執行前需先取得／驗證 session
- `INT-010`（即時更新）在 `INT-001` 到 `INT-008` 完成後觸發，非同步執行，不阻塞主要互動流程
- `INT-011`（命令分發）為所有 handler 的進入點，必須最先實作
- `INT-012`（metrics）包裹在 `INT-011` 中，與 handler 執行平行（fire-and-forget）

## Requirement linkage (coarse ordering)

Maps **which `R` clusters** depend on **which anchor order**. **`tasks.md` decomposes** into concrete steps.

### Phase 1: 基礎設施（R14、R13）

- Anchor order hint: `INT-011` → `INT-012` → Facade 實作（`INT-001` 到 `INT-008` 的 Facade 層面）
- Narrative glue: 先建立命令分發骨架與 Facade 層，因為所有面板 handler 都依賴它們。Facade 層必須在 handler 之前完成，因為 handler 不直接呼叫 domain service。SlashCommandListener 是最外層的進入點，必須最先可用。

### Phase 2: 管理面板核心（R1–R10）

- Anchor order hint: `INT-009` → `INT-001` → `INT-002` → `INT-003` → `INT-004` → `INT-005` → `INT-006`（R6–R10 順序無嚴格依賴）
- Narrative glue: Session 管理是所有面板互動的基礎，必須先完成。貨幣／代幣管理是最簡單的面板功能，先實作作為其他面板 handler 的模式參考。產品管理（`INT-004`）涉及多層 session 狀態轉換（MAIN → PRODUCT_LIST → PRODUCT_DETAIL → PRODUCT_CODE_LIST），是面板中最複雜的互動流程。

### Phase 3: 用戶面板（R11）

- Anchor order hint: `INT-009` → `INT-007` → `INT-008`
- Narrative glue: 用戶面板相對簡單，依賴 Facade 層（已在 Phase 1 完成）和 session 管理（已在 Phase 2 完成）。可與 Phase 2 的管理面板後半部分並行。

### Phase 4: 即時更新（R12）

- Anchor order hint: `INT-010`
- Narrative glue: 即時更新依賴所有 DomainEvent 型別定義（來自 shared module）與 Session 管理。必須在所有面板 handler 完成後實作，因為需要知道每個面板 embed 的結構才能正確更新。

## Data & persistence (design-level)

| Resource                                        | Typical readers/writers (module keys)                     | Consistency expectation (ordering, idempotency)                                                                 |
| ----------------------------------------------- | --------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `AdminPanelSession`（in-memory / Redis）         | `admin-panel`（r/w）、`admin-panel` UpdateListener（r）    | 以 guildId+userId 為 key 的單一 session；TTL 15 分鐘；過期後由下次存取或定時清理移除；Redis 不可用時降級為 in-memory Map |
| `PanelSession`（in-memory / Redis）              | `user-panel`（r/w）、`user-panel` UpdateListener（r）      | 同上                                                                                                              |
| Product、RedemptionCode（經 `@ltdjms/shop`）     | `admin-panel`（r/w via facade）、shop module（r/w）        | Shop module 透過 Conditional UPDATE 保證冪等；administration 僅為 consumer                                          |
| Balance、GameToken（經 `@ltdjms/economy`）       | `admin-panel`（r/w via facade）、`user-panel`（r via facade）| Economy module 透過 Conditional UPDATE 保證冪等                                                                   |
| Dispatch config（經 `@ltdjms/dispatch`）         | `admin-panel`（r/w via facade）                            | Dispatch module 負責一致性                                                                                         |
| AI config（經 `@ltdjms/ai`）                     | `admin-panel`（r/w via facade）                            | AI module 負責一致性                                                                                              |
| `SlashCommandMetrics`（in-memory accumulator）   | `command-infra`（w）、monitoring（r）                      | 僅限單一 process 內；定期輸出至 log；不持久化                                                                       |

## Invariants (system-level)

| Invariant                                                                   | What breaks it architecturally                                                                          | Symptoms if violated                                                       |
| --------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| 管理面板從不直接呼叫 domain service，一律透過 Facade                                | Handler 直接 import economy/shop/dispatch/ai 的 service 類別                                              | 跨模組耦合無法獨立測試；更換底層 service 實作時需修改 handler                       |
| 所有管理面板操作必須經過 ADMINISTRATOR／guild owner 權限檢查                      | Handler 省略 `checkAdminPermission()` 呼叫，或 SlashCommandListener 未設定 `default_member_permissions`    | 非管理員可執行敏感操作（修改他人餘額、變更遊戲設定等）                                |
| Interaction 回應必須在 3 秒內 defer（deferReply），實際內容透過 editReply 發送     | Handler 在執行長時間操作（如 DB 查詢）前未 deferReply                                                       | Discord 回傳 "Unknown interaction" 錯誤，用戶看不到回應                          |
| Session TTL 過期後不應嘗試更新 embed                                          | UpdateListener 未檢查 session.isExpired() 即呼叫 interaction.editReply()                                 | Discord API 回傳 404 或 "Unknown interaction"，產生大量無意義錯誤 log               |
| 單一 guild+user 在任何時刻最多只有一個 active admin panel session                | AdminPanelSessionManager 在建立新 session 時未清理舊 session                                                | 多個 panel 同時存在，即時更新僅作用於最後建立的 panel，舊 panel 顯示過時資料             |
| Facade 層不直接操作資料庫或 Redis                                                | Facade 中出現 drizzle 或 ioredis import                                                                    | Facade 成為 "god object"，失去聚合層的語義清晰度與可測試性                             |

## Tradeoffs inherited by implementation

| Decision                                                                           | Rejected alternative                                            | Locks in (for **`tasks.md`**)                                                                                    |
| ---------------------------------------------------------------------------------- | --------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| 管理面板 handler 使用 Facade 而非直接注入 domain service                                | Handler 直接注入多個 domain service（更少程式碼，但耦合度高）         | 每個 handler 只注入 1–2 個 Facade；facade 內部管理對 domain service 的呼叫順序與錯誤轉換                           |
| Admin panel button 互動使用 customId prefix 路由（`admin_balance_*`、`admin_token_*`）| 使用狀態機在 handler 內部根據 session state 路由                    | customId 攜帶足夠的操作資訊（如 userId、productId）；handler 從 customId 解析參數而非從 session state 推斷        |
| Session 自包含於 admin 套件（in-memory Map + 選擇性 CacheService Redis）| 使用共享的 `DiscordSessionManager`（但 shared 中不存在此類別）         | Session key 格式、TTL 機制在 admin 套件內部一致；Redis 不可用時自動降級；無需 shared 層的 session 抽象            |
| 產品／兌換碼管理不經 Facade，直接使用 shop module 的 service 介面（因為這是管理面板的直接操作，非跨模組聚合）| 建立 ProductManagementFacade 封裝                                   | 產品管理 handler 注入 `ProductService`、`ProductRepository`、`RedemptionCodeRepository`（來自 `@ltdjms/shop`）    |
| Slash command 註冊使用集中式 script（而非在 client ready 事件中動態註冊）              | 在 `SlashCommandListener` 中動態註冊                                | 部署前執行註冊 script；command 定義集中在 `packages/admin/src/commands/definitions/`                             |
| 在地化字串使用靜態常數物件（`zh-TW`），不支援多語言切換                                  | 使用 i18n library（i18next）                                       | 所有用戶可見字串定義在 `packages/admin/src/i18n/zh-TW.ts`；加入新字串只需在該檔案新增 key                           |

## Batch-only

- **slash command 全域註冊 script**：由於 slash command 註冊必須在 bot 啟動前完成（或每次更新），administration 負責實作一個集中式註冊 script，涵蓋所有 package 定義的 slash command（不僅限於 admin panel 的 `/admin-panel` 和 `/user-panel`）。其他 package 將 command 定義 export 出來，由 administration 的註冊 script 彙總後一次呼叫 Discord API。詳見 `coordination.md` integration checkpoints。
- **BotErrorHandler 的全域覆蓋**：雖然 BotErrorHandler 定義在 administration，但其錯誤對映表涵蓋所有 DomainError category（來自 shared module），因此全家桶的 command handler 都可受益於統一的錯誤訊息轉換。
