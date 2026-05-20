# Design: Escort Dispatch

- Date: 2026-05-20
- Feature: Escort Dispatch
- Change Name: escort-dispatch

> **Purpose:** **High-level architectural context for `tasks.md`**—structure, coupling, sequencing intent—not a second implementation list. Requirement intent stays in `spec.md`; **documented vendor truth** stays in **`contract.md`**. **`tasks.md` owns** every runnable step (paths, edits, verifies).
>
> **Do not duplicate `tasks.md`:** no checkbox-style chores, no per-file implementation lines, no verifiers—the executable queue exists only under **`tasks.md`**. Optional **`INT-###`** labels are **coarse anchors** that task rows cite for traceability.
>
> **Audience:** Humans/agents authoring **`tasks.md`**, and implementers needing **mental model before** ticking task boxes—not a standalone execution script.

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1.1–R15.3                                                                  |
| In-scope modules (≤3)       | `packages/dispatch/`                                                          |
| External systems touched    | None (Discord API 透過 `@ltdjms/shared` 的 DiscordRuntimeGateway 抽象)        |
| Batch coordination          | [`../coordination.md`](../coordination.md)                                    |

## Target vs baseline

|                       | Baseline (today) | Target (after this change) |
| --------------------- | ---------------- | --------------------------- |
| Structure / ownership | Java: `ltdjms.discord.dispatch.*` + `ltdjms.discord.product.domain.EscortOptionCatalog*` + `ltdjms.discord.shared.di.DispatchModule` | TypeScript: `packages/dispatch/src/` 內含所有 dispatch 相關 schema、domain、services、repos、panel、notification |

## Boundaries

- Entry surface(s): Discord slash command (`/dispatch-panel`) + Discord button/select interactions (DM and guild) + internal handoff call from shop module
- Trust boundary crossed: `None` — 僅 Discord 使用者互動，無外部 API 呼叫
- Outside → inside (one line): `Discord 使用者` → `DiscordRuntimeGateway (shared)` → `DispatchPanelCommandHandler / DispatchPanelInteractionHandler` → `EscortDispatchOrderService` → `PostgreSQL (via Drizzle)`

## Modules (nouns only)

| Module key | Responsibility (one sentence) | Owned artifacts (types, tables, queues) |
| ---------- | ---------------------------- | ---------------------------------------- |
| `schema` | 定義 Drizzle ORM schema 與 TypeScript 型別（對應 PostgreSQL 三個 table） | `escortDispatchOrder` table, `guildEscortOptionPrice` table, `dispatchAfterSalesStaff` table; `EscortDispatchOrderRow`, `GuildEscortOptionPriceRow`, `DispatchAfterSalesStaffRow` types |
| `domain` | 領域模型：EscortDispatchOrder record（7 狀態機 + 工廠方法 + withXxx 轉換方法）、OrderNumber 值物件、OptionPriceView、status 與 sourceType enum | `EscortDispatchOrder`, `EscortDispatchOrderStatus` enum, `SourceType` enum, `OrderNumber`, `OptionPriceView` |
| `repo` | Repository 層：條件式 UPDATE（WHERE 護欄 + RETURNING）、idempotent INSERT、Drizzle 查詢 | `EscortDispatchOrderRepo`, `EscortOptionPriceRepo`, `DispatchAfterSalesStaffRepo` interfaces + `Drizzle*Repo` implementations |
| `service` | 核心業務邏輯：訂單生命週期、handoff 交接、定價覆寫、售後人員管理 | `EscortDispatchOrderService`, `EscortDispatchHandoffService`, `EscortDispatchOrderNumberGenerator`, `DispatchAfterSalesStaffService`, `EscortOptionPricingService` |
| `panel` | Discord 互動 UI：/dispatch-panel 指令、所有 button/select 處理、session state、embed 建構 | `DispatchPanelCommandHandler`, `DispatchPanelInteractionHandler`, `DispatchPanelView`, `DispatchPanelMessageFactory`, `SessionState` |
| `notification` | DM 通知管線：護航者接單/完單、客戶確認/售後、售後人員接手/結案、在線優先策略 | `DispatchNotificationService` |
| `di` | DI 註冊：所有 service、repository、handler 的 instantiation 與 wiring | `dispatchModule` (提供給 shared DI container) |

---

## Interaction anchors (`INT-###`)

**Grain:** **Above `tasks.md`**. One anchor ≈ a **meaningful handshake** between module keys—not one checkbox. Several task lines may realize a single `INT-###`.

| ID        | Intent (when this coupling matters) | Caller → Callee | Coupling kind (route pattern · RPC · event · sync call—**name/pattern**, not file path) | Information / state crossing (summary) | Failure / propagation expectation (summary) |
| --------- | ------------------------------------ | --------------- | ---------------------------------------------------------------------------------------- | -------------------------------------- | ------------------------------------------- |
| `INT-001` | Discord slash command 觸發派單面板開啟 | Discord Gateway → `panel` | Discord Interaction Create event → `DispatchPanelCommandHandler.handle()` | SlashCommandInteractionEvent（guildId, userId, member permissions） | 非管理員：ephemeral 拒絕。管理員：顯示模式選擇 embed |
| `INT-002` | 管理員面板按鈕/選單互動 | Discord Gateway → `panel` | Button/StringSelect/EntitySelect Interaction Create event → `DispatchPanelInteractionHandler.onXXX()` | CustomId（dispatch_* prefix）、選取值、guildId、userId | 非管理員：reject。Session mismatch：提示重新選擇。成功：更新 embed + components |
| `INT-003` | 護航者/客戶/售後人員在 DM 中點擊按鈕 | Discord Gateway → `panel` | Button Interaction Create event → `DispatchPanelInteractionHandler.onButtonInteraction()` | CustomId prefix 解析訂單編號、userId | DM 限定（guild reject）。狀態不符：DomainError message。成功：狀態轉換 + 下游 DM 通知 |
| `INT-004` | 商店付款後自動建立護航訂單 | `shop` → `service` | 同步函數呼叫：`EscortDispatchHandoffService.handoffFromXxx()` | guildId, buyerUserId, Product snapshot, sourceReference | Idempotent（findBySourceIdentity）。Product 驗證失敗回傳 DomainError。persistence 失敗時 fallback 查詢 |
| `INT-005` | 訂單狀態轉換 → DM 通知 | `panel` → `notification` | 同步函數呼叫：`DispatchNotificationService.notifyXxx()` | EscortDispatchOrder, 觸發者 mention, JDA/Discord client instance | DM 發送失敗不阻斷主流程（warn log）。使用者不存在時 graceful skip |
| `INT-006` | Repository 原子條件式 UPDATE | `service` → `repo` | 同步函數呼叫：`repo.assignEscort()`, `repo.claimAfterSales()`, `repo.closeAfterSales()` | orderNumber + 條件護欄值 + 更新值 | 無匹配行回傳 null/空 → service 層解讀為 race condition 並回傳對應 DomainError |
| `INT-007` | 護航品類驗證（EscortOrderOptionCatalog） | `service` → `shared` (escort option catalog repository) | 同步函數呼叫：`EscortOrderOptionCatalog.isSupported()` / `allOptions()` | optionCode string | Catalog 載入失敗時 fallback 到 hardcoded 資料 |

**Ordering / concurrency (design-level):** 每個 guild:userId 對有獨立的 ConcurrentHashMap session state。條件式 UPDATE 的 WHERE 子句在 DB 層提供並發控制（assignEscort 防止重複派發、claimAfterSales 防止重複接手）。

## Requirement linkage (coarse ordering)

Maps **which `R` clusters** depend on **which anchor order**. **`tasks.md` decomposes** into concrete steps.

### Schema + Domain (R1, R2, R3 基礎)

- Anchor order hint: `INT-006` → `INT-007`
- Narrative glue: 先定義 Drizzle schema 與 TypeScript 型別，再實作 domain model（EscortDispatchOrder + 狀態機）。Repository 介面定義依賴 domain model 的型別簽名。

### 核心服務層 (R1–R11, R13, R15)

- Anchor order hint: `INT-006` → `INT-004` → `INT-002`（服務邏輯在互動之前完成）
- Narrative glue: Repository 實作完成後先寫 service 層單元測試（mock repo），再實作 service。Handoff 服務是跨模組邊界，需在完成基本 CRUD 後實作。

### 面板互動 + 通知 (R3–R9, R14)

- Anchor order hint: `INT-001` → `INT-002` → `INT-003` → `INT-005`
- Narrative glue: 面板 UI 依賴 service 層（orderService, afterSalesStaffService）。通知服務可獨立開發但最終由面板互動 handler 觸發。

### 定價覆寫 + 售後管理 (R12, R13)

- Anchor order hint: `INT-007`（無其他依賴，可並行開發）
- Narrative glue: 定價服務依賴 EscortOptionCatalogRepository（共享基礎設施），售後管理為獨立 CRUD。

## Data & persistence (design-level)

| Resource                      | Typical readers/writers (module keys) | Consistency expectation (ordering, idempotency) |
| ----------------------------- | ------------------------------------- | ------------------------------------------------ |
| `escort_dispatch_order` table | `repo`, `service` (write via conditional UPDATE), `panel` (read via service) | 條件式 UPDATE 的 WHERE 子句保證原子性。訂單編號唯一（UK constraint + application-level retry）。source_type + source_reference 用於 idempotent handoff |
| `guild_escort_option_price` table | `repo`, `service` | Upsert via ON CONFLICT (guild_id, option_code) DO UPDATE |
| `dispatch_after_sales_staff` table | `repo`, `service` | Insert via ON CONFLICT DO NOTHING（idempotent add） |
| Session state (in-memory) | `panel` (ConcurrentHashMap) | Per guild:userId key。Mode switch 重置。無 TTL（依賴 JVM/Node process 生命週期） |

## Invariants (system-level)

| Invariant | What breaks it architecturally           | Symptoms if violated |
| --------- | ---------------------------------------- | -------------------- |
| 同一筆商店付款只產生一筆護航訂單 | handoff 時跳過 findBySourceIdentity | 重複建立訂單，影響營運統計與護航人員工作分配 |
| PENDING_CONFIRMATION 訂單只能派發一次 | assignEscort 不使用條件式 WHERE escort_user_id=0 | 多個管理員同時派發同一訂單給不同護航者，造成衝突 |
| 售後案件只能被一位售後人員接手 | claimAfterSales 不使用條件式 WHERE after_sales_assignee_user_id IS NULL | 多個售後人員同時接手同一案件，責任不清 |
| 24 小時超時自動完成 | 查詢時未呼叫 ensureTimeoutCompletion | 已逾時訂單停留在 PENDING_CUSTOMER_CONFIRMATION，阻斷售後流程 |
| 護航者與客戶不可為同一人 | createOrder 和 assignPendingOrder 未檢查 | 自己為自己護航，喪失業務意義 |

## Tradeoffs inherited by implementation

| Decision | Rejected alternative | Locks in (for **`tasks.md`**) |
| -------- | -------------------- | ---------------------------- |
| 在查詢時做超時檢查（lazy auto-complete），而非背景排程 | 用 cron job 每分鐘掃描超時訂單 | 實作較簡單（無需排程基礎設施），但超時完成只在查詢時觸發 |
| Drizzle ORM 手寫條件式 SQL（`WHERE status = ? AND escort_user_id = 0 RETURNING *`），而非 Drizzle update API | 使用 Drizzle 的 `returning()` + `where()` 高階 API | Repository 層需要在 Drizzle 上執行 raw SQL 以維持與 PostgreSQL RETURNING 子句的精確對應 |
| 護航品類驗證透過 @ltdjms/shared 提供的 EscortOptionCatalogRepository 介面 | 在 dispatch 模組內建立獨立的 catalog | 遵循現有架構（catalog 是跨模組共享資源，屬於 product domain） |
| 售後人員在線優先通知策略 | 一律通知全部設定人員 | 減少對離線人員的干擾，但需要 guild.retrieveMemberById 的額外 API 呼叫 |
| DM 通知發送失敗不阻斷主流程 | 嚴格模式：DM 失敗則整個操作失敗 | 護航訂單狀態更新與 DM 通知解耦，避免因用戶私訊設定而阻斷業務流程 |

## Batch-only

Coordinated via `../coordination.md` — escort-dispatch 可與 ai-chat-agent 並行開發（兩者獨立，無相互依賴）。dispatch 依賴 shared-infrastructure 的型別與 DI 容器。dispatch 不依賴 economy 或 shop（dispatch 是下游 consumer，僅透過 handoff interface 被呼叫）。
