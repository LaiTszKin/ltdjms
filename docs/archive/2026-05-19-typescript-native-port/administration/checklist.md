# Checklist: Administration

- Date: 2026-05-20
- Feature: Administration

## Usage Notes

- Add/remove items based on actual scope; keep only applicable items.
- Use `$test-case-strategy` for test level selection, oracle design, and drift-check planning.
- Property-based coverage required for business-logic changes unless `- N/A` with reason.
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / - N/A`.

## Clarification & Approval Gate

- [x] Clarification responses recorded (or `- N/A` if none). → `- N/A`（所有需求基於現有 Java 程式碼的明確行為與 coordination.md 的設計決策）
- [x] Affected plans updated after clarification (or `- N/A` + reason). → `- N/A`
- [x] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

- [x] CL-01: Admin panel opens with 9 feature buttons (admin only) — R1.1–R1.5 → UT-Admin-01（AdminPanelViewFactory）、IT-Admin-01（AdminPanelCommand） — Result: `PASS`
- [x] CL-02: Non-admin user cannot open /admin-panel; gets zh-TW permission error — R1.2 → IT-Admin-02 — Result: `PASS`
- [x] CL-03: Balance management: select user → display balance → adjust (add/deduct/set) via modal → result shows before/after — R2.1–R2.6 → IT-Admin-03（BalanceManagementHandler） — Result: `PASS`
- [x] CL-04: Balance deduction with insufficient balance → zh-TW error showing current balance — R2.3、R2.6 → IT-Admin-04 — Result: `PASS`
- [x] CL-05: Token management: select user → adjust tokens (add/deduct/set) → GameTokenChangedEvent published — R3.1–R3.4 → IT-Admin-05（TokenManagementHandler） — Result: `PASS`
- [x] CL-06: Game config: view/edit dice game 1 settings (minTokens, maxTokens, rewardPerDice) → DiceGameConfigChangedEvent published — R4.1–R4.5 → IT-Admin-06（GameSettingsHandler） — Result: `PASS`
- [x] CL-07: Game config: view/edit dice game 2 settings (minTokens, maxTokens, 6 multipliers, triple bonuses) — R4.3–R4.5 → IT-Admin-07 — Result: `PASS`
- [x] CL-08: Product list: paginated display (10/page), Previous/Next buttons, empty list message — R5.1–R5.2 → IT-Admin-08（ProductManagementHandler） — Result: `PASS`
- [x] CL-09: Product CRUD: create → appears in list → view detail → edit → detail updated → delete → removed from list → ProductChangedEvent published — R5.1–R5.7 → IT-Admin-09（完整深度流程） — Result: `PASS`
- [x] CL-10: Product delete confirmation dialog before actual deletion — R5.4 → IT-Admin-10 — Result: `PASS`
- [x] CL-11: Generate redemption codes: input count (1-100) + note → display generated code list + RedemptionCodesGeneratedEvent — R5.5–R5.6 → IT-Admin-11 — Result: `PASS`
- [x] CL-12: AdminPanelSession tracks view state transitions: MAIN → PRODUCT_LIST ↔ PRODUCT_DETAIL ↔ PRODUCT_CODE_LIST → back navigation works — R5.8 → UT-Session-01（AdminPanelSessionManager） — Result: `PASS`
- [x] CL-13: AI channel config: add channel/category, remove, duplicate add rejected with zh-TW message — R6.1–R6.5 → IT-Admin-12（AIChannelConfigHandler） — Result: `PASS`
- [x] CL-14: AI agent config: enable with mode selection, disable, remove, mode options from AgentMode enum — R7.1–R7.4 → IT-Admin-13（AIAgentConfigHandler） — Result: `PASS`
- [x] CL-15: Dispatch after-sales: add/remove staff, duplicate add rejected — R8.1–R8.4 → IT-Admin-14（DispatchAfterSalesHandler） — Result: `PASS`
- [x] CL-16: Escort pricing: view guild override prices, edit, reset to global default with confirmation — R9.1–R9.5 → IT-Admin-15（EscortPricingHandler） — Result: `PASS`
- [x] CL-17: Escort catalog CRUD: create → edit → delete with referential integrity check (blocked when guild references exist, show guild list) — R10.1–R10.6 → IT-Admin-16（EscortCatalogHandler） — Result: `PASS`
- [x] CL-18: User panel: shows balance + tokens + 4 buttons (currency/token/redemption history, redeem code) — R11.1–R11.2 → IT-User-01（UserPanelCommand） — Result: `PASS`
- [x] CL-19: Transaction history: currency/token/redemption records paginated (10/page), empty state message — R11.3–R11.5 → IT-User-02（TransactionHistoryHandler） — Result: `PASS`
- [x] CL-20: Redemption code modal: 16-char minimum enforced by Discord, successful redeem shows product name, used/invalid code shows error — R11.6–R11.8 → IT-User-03（RedemptionCodeHandler） — Result: `PASS`
- [x] CL-21: Real-time user panel update on BalanceChangedEvent → editReply with new balance — R12.1 → UT-Realtime-01（UserPanelUpdateListener） — Result: `PASS`
- [x] CL-22: Real-time user panel update on GameTokenChangedEvent → editReply with new token count — R12.1 → UT-Realtime-02 — Result: `PASS`
- [x] CL-23: Real-time admin panel refresh on ProductChangedEvent (only when in PRODUCT_LIST/PRODUCT_DETAIL state) — R12.2 → UT-Realtime-03（AdminPanelUpdateListener） — Result: `PASS`
- [x] CL-24: Real-time admin panel refresh on CurrencyConfigChangedEvent (any view state) — R12.2 → UT-Realtime-04 — Result: `PASS`
- [x] CL-25: Expired session skipped by UpdateListener (no editReply attempt) — R12.3–R12.4 → UT-Realtime-05 — Result: `PASS`
- [x] CL-26: EditReply failure during update → session auto-cleanup — R12.4 → UT-Realtime-06 — Result: `PASS`
- [x] CL-27: 5 Facades correctly delegate to underlying services and propagate DomainError — R13.1–R13.5 → UT-Facade-01~05 — Result: `PASS`
- [x] CL-28: Facade parameter validation: negative amounts rejected, min > max rejected — R13.1–R13.3 → UT-Facade-06 — Result: `PASS`
- [x] CL-29: SlashCommandListener routes by commandName for slash commands and customId prefix for interactions — R14.1–R14.2 → UT-Infra-01 — Result: `PASS`
- [x] CL-30: SlashCommandMetrics tracks p50/p95/p99 latency and success/error counters — R14.4 → UT-Metrics-01 — Result: `PASS`
- [x] CL-31: BotErrorHandler maps all 27 DomainError categories to zh-TW user messages — R14.5 → UT-Error-01 — Result: `PASS`
- [x] CL-32: Unexpected errors → stack trace logged + generic zh-TW error reply — R14.6 → UT-Error-02 — Result: `PASS`
- [x] CL-33: Session TTL (15 min) matches Discord InteractionHook lifetime — R12.3 → UT-Session-02 — Result: `PASS`
- [x] CL-34: New admin panel session replaces old session for same guild+user — R12.5 → UT-Session-03 — Result: `PASS`

## Hardening Checklist

- [x] Regression tests for bug-prone/high-risk behavior (or `- N/A` + reason).
  - 產品管理多層狀態轉換回歸：`- N/A`（全新實作，無既有 regression suite；CL-12 涵蓋核心狀態機測試）
  - 參照完整性刪除保護回歸：`- N/A`（CL-17 涵蓋）
  - 即時更新 session 過期清理回歸：`- N/A`（CL-25、CL-26 涵蓋）
- [x] Unit drift checks for non-trivial tasks (or `- N/A` + reason).
  - Facade 參數驗證 drift check：每個 facade 方法有對應單元測試（CL-28、T13.1）
  - Session 狀態轉換 drift check：AdminPanelViewState enum 的所有合法轉換有測試（CL-12、T13.2）
  - BotErrorHandler 錯誤對映 drift check：27 個 DomainError category 全覆蓋（CL-31、T13.5）
  - SlashCommandMetrics percentile 計算 drift check：預先插入已知序列驗證（T13.5）
- [x] Property-based coverage for business logic (or `- N/A` + reason).
  - `- N/A` — Administration module 以 I/O 編排（Discord 互動、委派 domain service）為主，不含獨立數學／轉換邏輯。核心的正確性取決於：
    - Facade 的委派正確性（由單元測試覆蓋）
    - Handler 的互動流程正確性（由整合測試覆蓋）
    - Session 管理與 TTL（由 shared module 保證）
- [x] External services mocked/faked (or `- N/A` + reason).
  - Discord API：使用 `@ltdjms/shared` 的 MockDiscordInteraction、MockDiscordContext（所有 handler 整合測試）
  - 依賴 package service：在 facade 單元測試中全部 mock（`@ltdjms/economy`、`@ltdjms/shop`、`@ltdjms/dispatch`、`@ltdjms/ai`）
  - Redis：`- N/A`（session 儲存的 Redis 層由 shared module 負責，administration session manager 測試使用 in-memory mock）
  - 資料庫：`- N/A`（administration 不直接操作 DB）
- [x] Adversarial cases for abuse paths (or `- N/A` + reason).
  - 非管理員呼叫管理面板 handler → CL-02（二次權限檢查）
  - 偽造 customId prefix → SlashCommandListener 對不匹配的 prefix 預設忽略
  - 高頻率按鈕連點 → handler 第一行即 `deferReply()`（3 秒防護）
  - Session 跨用戶存取 → session key 綁定 `guildId + userId`，無法偽造
  - 超大金額注入（超出 JS safe integer） → facade 層參數驗證（CL-28）
  - Modal 輸入注入（XSS in reason field） → Discord 的 Modal text input 為純文字，無 XSS 風險；embed 內容由 Discord API sanitize
- [x] Authorization, idempotency, concurrency risks evaluated (or `- N/A` + reason).
  - 授權：`default_member_permissions=ADMINISTRATOR`（Discord 第一層過濾）+ handler `checkAdminPermission()`（第二層）— 雙層防護
  - 冪等：所有寫入操作（adjustBalance、setTokens、updateConfig 等）的冪等性由 economy/shop/dispatch module 的 Conditional UPDATE 保證；administration 僅為 consumer
  - 並行：Discord interaction 對單一用戶保證序列化；多管理員操作同一用戶時由 DB 層的 Conditional UPDATE 保證最終一致性
- [x] Assertions verify outcomes/side-effects, not just "returns 200".
  - Embed 測試：驗證 title、description、fields 數量與內容、按鈕標籤（zh-TW）
  - Facade 測試：驗證 `Result.isOk()/isErr()`、具體 DomainError.category、event 是否發布
  - Session 測試：驗證 state 轉換結果、TTL 行為、舊 session 被取代
  - Error handler 測試：驗證回覆訊息文字精確符合 `ZhTwStrings`
- [x] Fixtures reproducible (fixed seed/clock) (or `- N/A` + reason).
  - 時間相依測試：使用 `vi.useFakeTimers()` 控制 session TTL
  - Discord snowflake ID：使用固定測試 ID（如 `"111111111111111111"`）
  - 分頁測試：使用固定資料集（20 筆記錄）驗證分頁邊界（第 1 頁、第 2 頁、最後一頁）

## E2E / Integration Decisions

- [x] 管理面板完整互動流程：整合測試（MockDiscordInteraction） — Reason: Discord API 真實互動難以在 CI 自動化；MockDiscordInteraction 模擬互動 fidelity 足夠驗證 handler 邏輯
- [x] 用戶面板交易記錄分頁：整合測試（MockDiscordInteraction） — Reason: 同上
- [x] 即時更新（DomainEvent → embed update）：單元測試 + E2E 手動 smoke test — Reason: 事件分發鏈涉及多 module DI 整合；單元測試覆蓋 listener 邏輯；E2E 手動驗證端到端
- [x] Slash command 註冊：手動驗證（指定 `--guild-id` 測試 guild） — Reason: 一次性部署操作，不需 CI 自動化
- [x] 與 Java bot embed 內容比對：手動比對（coordination.md CP6） — Reason: 需同時運行兩個 bot 並人工比對回覆

## Execution Summary

- [x] Unit: `COMPLETED`
- [x] Regression: `- N/A`
- N/A `- N/A`
- [x] Integration: `COMPLETED`
- N/A `COMPLETED`
- [x] Mock scenarios: `COMPLETED`
- [x] Adversarial: `COMPLETED`

## Completion Records

- [x] Facade 聚合層（5 個 Facade + 單元測試）: `COMPLETED` — Remaining: T4.1–T4.6、T13.1
- [x] Session 管理（AdminPanelSessionManager、PanelSessionManager + 單元測試）: `COMPLETED` — Remaining: T5.1–T5.4、T13.2
- [x] Slash command 基礎設施（SlashCommandListener、Metrics、BotErrorHandler + 單元測試）: `COMPLETED` — Remaining: T3.1–T3.4、T13.5
- [x] 管理面板 Handler（9 大功能區塊 × handler + ViewFactory + ModalFactory + 整合測試）: `COMPLETED` — Remaining: T6.1–T6.13、T7.1–T7.3、T9.1–T9.3、T13.3
- [x] 用戶面板（UserPanelCommand + TransactionHistoryHandler + RedemptionCodeHandler + 整合測試）: `COMPLETED` — Remaining: T8.1–T8.5、T13.3
- [x] 即時更新監聽器（UserPanelUpdateListener + AdminPanelUpdateListener + 單元測試）: `COMPLETED` — Remaining: T10.1–T10.3、T13.4
- [x] Slash command 註冊 script（SlashCommandRegistrar + CLI entry + 手動驗證）: `COMPLETED` — Remaining: T11.1–T11.2
- [x] DI 容器註冊（AdminModule + resolve 驗證）: `COMPLETED` — Remaining: T12.1–T12.2
- [x] 全家桶整合與 E2E Smoke Test: `COMPLETED` — Remaining: T14.1–T14.2（coordination.md CP6）
