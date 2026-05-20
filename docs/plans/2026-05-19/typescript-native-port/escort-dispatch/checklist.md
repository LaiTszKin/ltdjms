# Checklist: Escort Dispatch

- Date: 2026-05-20
- Feature: Escort Dispatch

## Usage Notes

- Add/remove items based on actual scope; keep only applicable items.
- Use `$test-case-strategy` for test level selection, oracle design, and drift-check planning.
- Property-based coverage required for business-logic changes unless `N/A` with reason.
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded (or `N/A` if none).
- [ ] Affected plans updated after clarification (or `N/A` + reason).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

### 狀態機轉換 (State Machine Transitions) — 最高優先級

- [ ] CL-01: EscortDispatchOrder 7 狀態機所有合法轉換路徑 — R1–R9 → UT-Dispatch-01 — Result: `NOT RUN`
  - PENDING_CONFIRMATION → CONFIRMED（護航者確認）
  - CONFIRMED → PENDING_CUSTOMER_CONFIRMATION（護航者完單請求）
  - PENDING_CUSTOMER_CONFIRMATION → COMPLETED（客戶確認完成 / 24h 超時自動完成）
  - PENDING_CUSTOMER_CONFIRMATION → AFTER_SALES_REQUESTED（客戶申請售後）
  - COMPLETED → AFTER_SALES_REQUESTED（客戶從已完成狀態申請售後）
  - AFTER_SALES_REQUESTED → AFTER_SALES_IN_PROGRESS（售後人員接手）
  - AFTER_SALES_IN_PROGRESS → AFTER_SALES_CLOSED（售後人員結案）

- [ ] CL-02: 所有非法狀態轉換被正確拒絕 — R1–R9 → UT-Dispatch-02 — Result: `NOT RUN`
  - 已確認的訂單不可重複確認
  - 非護航者不可確認/完單
  - 非客戶不可確認完成/申請售後
  - 已在售後流程中的訂單不可重複申請售後
  - 非 AFTER_SALES_REQUESTED 不可接手
  - 非 AFTER_SALES_IN_PROGRESS 不可結案
  - 非接手人不可結案
  - 已結案不可再結案

- [ ] CL-03: EscortDispatchOrder 建構驗證（22 欄位、escort ≠ customer、source type 對應 snapshot 規則）— R1.3–R1.4 → UT-Dispatch-03 — Result: `NOT RUN`
  - MANUAL 時 source snapshot 欄位必須全部為 null
  - CURRENCY_PURCHASE / FIAT_PAYMENT 時必須有 sourceReference、sourceProductId、sourceProductName、sourceEscortOptionCode
  - escortUserId === customerUserId 時拋出例外
  - orderNumber 非空、≤32 字元
  - 各 status 對應的必要時間戳驗證

### 冪等性與並發控制 (Idempotency & Concurrency)

- [ ] CL-04: Handoff from currency purchase 的 idempotency（findBySourceIdentity）— R11.1–R11.7 → UT-Handoff-01 — Result: `NOT RUN`
  - 同一 sourceType + sourceReference 只建立一筆訂單
  - 第二次呼叫直接回傳既有訂單

- [ ] CL-05: Handoff from fiat payment 的 idempotency + snapshot survival — R11.2–R11.7 → UT-Handoff-02 — Result: `NOT RUN`
  - product snapshot 欄位完整保留（productId、productName、currencyPrice、fiatPriceTwd、escortOptionCode）
  - Product 被刪除後 dispatch order 仍保留 snapshot 資料

- [ ] CL-06: Handoff exception fallback（create 失敗後的 findBySourceIdentity 重試）— R11.7 → UT-Handoff-03 — Result: `NOT RUN`
  - save 失敗 → catch block 內再次查詢 → 若有則回傳既有訂單，無則回傳 persistenceFailure

- [ ] CL-07: assignEscort 並發控制 — R3.2 → IT-Repo-01 — Result: `NOT RUN`
  - 兩個 assignEscort 同時執行，只有一個成功（WHERE escort_user_id=0 RETURNING *）
  - 第二個回傳 null

- [ ] CL-08: claimAfterSales 並發控制 — R8.2 → IT-Repo-02 — Result: `NOT RUN`
  - 兩個 claimAfterSales 同時執行，只有一個成功（WHERE after_sales_assignee_user_id IS NULL RETURNING *）
  - 第二個回傳 null → service 層解讀為「已被其他售後人員接手」

- [ ] CL-09: closeAfterSales 並發控制 — R9.2 → IT-Repo-03 — Result: `NOT RUN`
  - 非接手人的 closeAfterSales 回傳 null

- [ ] CL-10: 訂單編號唯一性重試機制 — R1.2 → UT-Number-01 — Result: `NOT RUN`
  - 最多重試 20 次，超過拋出例外

### 超時自動完成 (Timeout Auto-Completion)

- [ ] CL-11: 24h 客戶確認超時自動完成 — R10.1–R10.3 → UT-Timeout-01 — Result: `NOT RUN`
  - completionRequestedAt + 24h < now → 自動 COMPLETED
  - 自動完成僅在查詢時觸發（lazy），非背景排程
  - 自動完成失敗（DB error）時回傳原始訂單，不拋出例外

- [ ] CL-12: 超時邊界條件 — R6.3, R10.2 → UT-Timeout-02 — Result: `NOT RUN`
  - 恰好 24h 時視為已超時（非嚴格小於）
  - 未達 24h 不觸發自動完成

- [ ] CL-13: 客戶確認完成時的 idempotent（已 COMPLETED 狀態）— R6.2 → UT-Timeout-03 — Result: `NOT RUN`
  - 若 ensureTimeoutCompletion 已自動完成，customerConfirmCompletion 直接回傳 ok

### 通知流程 (Notification Flow)

- [ ] CL-14: DM 通知完整鏈路 — R3.4–R9.3 → UT-Notify-01 — Result: `NOT RUN`
  - 派發 → DM 護航者（Confirm 按鈕）
  - 確認 → DM 護航者更新（Complete 按鈕）+ DM 客戶通知
  - 完單請求 → DM 護航者更新（按鈕移除）+ DM 客戶（雙按鈕：確認完成、申請售後）
  - 客戶確認 → DM 客戶更新 + DM 護航者（完成通知）
  - 客戶申請售後 → DM 客戶更新 + DM 售後人員（Claim 按鈕）
  - 售後接手 → DM 售後人員更新（Close 按鈕）+ DM 客戶通知
  - 售後結案 → DM 售後人員更新（按鈕移除）+ DM 客戶通知

- [ ] CL-15: DM 發送失敗不阻斷業務流程 — R3.4 → UT-Notify-02 — Result: `NOT RUN`
  - 用戶關閉私訊 → 記錄 warn log → 管理員操作仍視為成功
  - 用戶不存在（retrieveUser 失敗）→ 記錄 warn log → 不拋出例外

- [ ] CL-16: 售後通知在線優先策略 — R7.4 → UT-Notify-03 — Result: `NOT RUN`
  - 有在線售後人員 → 僅通知在線者
  - 無在線售後人員 → 通知全部設定人員
  - 無設定售後人員 → 回傳提示訊息

### 面板互動 (Panel Interaction)

- [ ] CL-17: 派單面板完整互動流程 — R14 → IT-Panel-01 — Result: `NOT RUN`
  - /dispatch-panel → mode select → create mode（select customer + option → create）
  - /dispatch-panel → mode select → assign mode（select pending order + escort → assign）

- [ ] CL-18: 管理員權限檢查 — R14.1 → UT-Panel-01 — Result: `NOT RUN`
  - 非管理員執行 /dispatch-panel → reject
  - 非管理員點擊面板按鈕 → reject
  - Owner 可以執行（無 ADMINISTRATOR flag 時）

- [ ] CL-19: DM-only 操作在 guild 中被拒絕 — R4.3, R5.3 → UT-Panel-02 — Result: `NOT RUN`
  - 確認接單在 guild 頻道中點擊 → reject "請在機器人私訊中操作"
  - 完成訂單在 guild 頻道中點擊 → reject
  - 客戶確認完成在 guild 頻道中點擊 → reject
  - 售後接手在 guild 頻道中點擊 → reject

- [ ] CL-20: Session state 生命週期 — R14.2–R14.4 → UT-Panel-03 — Result: `NOT RUN`
  - 每個 guild:userId 獨立 session
  - Mode switch 重置 session（清除已選值）
  - Back 按鈕清除 session 回到模式選擇

- [ ] CL-21: 歷史記錄查詢 — R15 → UT-Panel-04 — Result: `NOT RUN`
  - findRecentOrders(guildId, 10) → 顯示最近 10 筆，每筆經過 ensureTimeoutCompletion

### 定價與售後管理 (Pricing & Staff Management)

- [ ] CL-22: Guild 層級護航定價覆寫 — R12 → UT-Pricing-01 — Result: `NOT RUN`
  - updateOptionPrice: upsert 寫入 override
  - getEffectivePrice: override > default
  - resetOptionPrice: 刪除後回到 default
  - listOptionPrices: 合併 catalog + override，正確標記 overridden

- [ ] CL-23: 售後人員管理 — R13 → UT-Staff-01 — Result: `NOT RUN`
  - addStaff idempotent（ON CONFLICT DO NOTHING）
  - removeStaff 僅移除存在的記錄
  - isAfterSalesStaff 例外時回傳 false（安全預設）

### 訂單編號 (Order Number)

- [ ] CL-24: 訂單編號格式 ESC-YYYYMMDD-XXXXXX — R1.1 → UT-Number-02 — Result: `NOT RUN`
  - 日期部分正確（YYYYMMDD）
  - 尾碼 6 位，僅含允許字元（排除 I、O、0、1）
  - 1000 次產生無重複

### 其他 Edge Cases

- [ ] CL-25: 護航品類驗證 — R2.1 → UT-Service-01 — Result: `NOT RUN`
  - 有效的 option code（如 CONF_DAM_300W）→ pass
  - 無效的 option code → DomainError("護航品類無效，可用代碼：...")

- [ ] CL-26: 護航品類 select menu 自動分頁 — R2.3 → UT-View-01 — Result: `NOT RUN`
  - ≤25 個選項 → 單一 select
  - >25 個選項 → SELECT_ORDER_OPTION + SELECT_ORDER_OPTION_EXTRA

- [ ] CL-27: 待派單訂單清單為空 — R3.1 edge case → UT-View-02 — Result: `NOT RUN`
  - pendingOrders 為空 → select menu disabled，顯示「目前沒有待派單訂單」

- [ ] CL-28: 客戶已不在伺服器中 — R14 create flow edge case → UT-Panel-05 — Result: `NOT RUN`
  - retrieveMemberById 失敗 → 回傳「找不到指定客戶，請確認該成員仍在伺服器中」

## Hardening Checklist

- [ ] Regression tests for bug-prone/high-risk behavior: 狀態機轉換、條件式 UPDATE race condition、handoff idempotency
- [ ] Unit drift checks for non-trivial tasks: 所有 withXxx 方法回傳正確 status；ensureTimeoutCompletion 在臨界時間點行為正確
- [ ] Property-based coverage for business logic: EscortDispatchOrder 建構驗證（任意 22 欄位組合 → 驗證規則不變）；訂單編號字元集（任意尾碼 → 無混淆字元）
- [ ] External services mocked/faked: DiscordRuntimeGateway（通知測試）、Database（repository 單元測試）、EscortOptionCatalogRepository（定價測試）
- [ ] Adversarial cases for abuse paths:
  - 非護航者嘗試確認訂單（canBeConfirmedBy = false）
  - 非客戶嘗試確認完成（canBeConfirmedByCustomer = false）
  - 非售後人員嘗試接手案件（isAfterSalesStaff = false）
  - 同時多管理員派發同一訂單（assignEscort race condition）
  - 同時多售後人員接手同一案件（claimAfterSales race condition）
  - 重複 handoff 相同 sourceReference（idempotency 保護）
- [ ] Authorization, idempotency, concurrency risks evaluated:
  - Admin check: ADMINISTRATOR 權限或 guild owner（R14.1）
  - DM-only button guard: `!interaction.inGuild()`（R4.3）
  - assignEscort WHERE escort_user_id=0 guard（R3.2）
  - claimAfterSales WHERE after_sales_assignee_user_id IS NULL guard（R8.2）
  - handoff findBySourceIdentity idempotency（R11.1）
  - addStaff ON CONFLICT DO NOTHING（R13.1）
- [ ] Assertions verify outcomes/side-effects, not just "returns 200":
  - 狀態轉換後 status 欄位正確
  - 時間戳欄位在正確的轉換中被設定
  - DM 通知被呼叫（mock verification）
  - Embed 內容包含正確的訂單編號與提及
- [ ] Fixtures reproducible (fixed seed/clock):
  - Clock injection 用於 timeout 測試
  - 訂單編號產生器接受 Clock + Random seed injection

## E2E / Integration Decisions

- [ ] 完整派單生命週期 E2E: 對接真實 PostgreSQL + Discord bot staging → 驗證 /dispatch-panel → create → assign → confirm → complete 完整流程 — Reason: 確保 TypeScript 版本行為與 Java production 一致
- [ ] Handoff 整合測試: 使用真實 PostgreSQL → 驗證 idempotency + snapshot survival → Reason: handoff 是跨模組邊界，需要真實 DB 驗證 conditional logic
- [ ] Notification E2E: 使用 Discord staging bot → 驗證所有 6 種 DM 通知的 embed 格式與按鈕互動 → Reason: DM 格式是使用者可見行為，必須與 Java 版完全一致
- [ ] Concurrent assignEscort 整合測試: 使用真實 PostgreSQL → 兩個同時 UPDATE → 只有一個成功 → Reason: 確保 WHERE guard 在 PostgreSQL 層級正確
- [ ] Concurrent claimAfterSales 整合測試: 使用真實 PostgreSQL → 同上

## Execution Summary

- [ ] Unit: `NOT RUN`
- [ ] Regression: `NOT RUN`
- [ ] Property-based: `NOT RUN`
- [ ] Integration: `NOT RUN`
- [ ] E2E: `NOT RUN`
- [ ] Mock scenarios: `NOT RUN`
- [ ] Adversarial: `NOT RUN`

## Completion Records

- [ ] Domain Model + State Machine: `NOT RUN` — Remaining: None
- [ ] Repository (conditional UPDATE): `NOT RUN` — Remaining: None
- [ ] Core Service (lifecycle + handoff): `NOT RUN` — Remaining: None
- [ ] Notification Service (DM flows): `NOT RUN` — Remaining: None
- [ ] Panel Interaction (slash command + buttons + session): `NOT RUN` — Remaining: None
- [ ] Pricing + Staff Management: `NOT RUN` — Remaining: None
- [ ] DI Registration + Package Setup: `NOT RUN` — Remaining: None
