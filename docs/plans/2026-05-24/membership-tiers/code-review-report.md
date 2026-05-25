# Code Review Report

- **Spec**: membership-tiers (batch: membership-core, membership-join-tracking, membership-spend-ledger, membership-settlement, membership-payment-discount, membership-benefits-ui)
- **Date**: 2026-05-24
- **Reviewer**: QA Agent
- **Result**: NOT PASS

---

## 業務需求驗收

| 需求 | 狀態 | 證據 | 缺口 / 不確定性 |
|------|------|------|----------------|
| 六等常數與 coordination 定稿表一致 | **PASS** | `MembershipTier.java` L8–13；`MembershipTierConfigTest` | — |
| 全域 User ID、跨 guild 累加 M | **PASS** | `membership_spend_entry.discord_user_id`；`JdbcMembershipSpendRepository.sumListPriceInPeriod` | — |
| 僅法幣 TWD + escort-linked 計入 M | **PASS** | `MembershipSpendService.recordFiatEscortPayment` L54–58；`EscortProductRules.isEscortLinked` | — |
| M 為 catalog 原價，非折後實付 | **PARTIAL** | 主路徑：`resolveListPriceM` → `EscortOptionCatalog.priceTwd` | catalog 缺失時 fallback 使用 `toFulfillmentProduct().fiatPriceTwd()`（= `amountTwd` 折後價） |
| 最早加入日為結算錨點（29–31 → 28） | **PASS** | `MembershipJoinService.clampDayOfMonth`；`mergeEarliestGuildJoin` | — |
| 青銅 ≥500 M 單次 qualifying，永久保底 | **PASS** | `JdbcMembershipSpendCoordinator`；`MembershipTierEvaluator.resolveTier` | — |
| 白銀～黑金每結算日重算，可升可降 | **PASS** | `MembershipSettlementService.settle` L66–72, L93–95 | — |
| 結算日發放贈幣（可累積、冪等） | **PASS** | `MembershipTokenGrantService.grantForSettlement`；V031/V033 grant log | grant 失敗後 tier 已更新、tokens 延遲（符合 EC1）；audit retry 有重複風險 |
| 付款折扣凍結於訂單建立、ECPay 一致 | **PASS** | `FiatOrderService.createFiatOnlyOrder` L66–93；callback 驗 `amountTwd` | — |
| `/user-panel` 展示等級、進度、結算日 | **PASS** | `UserPanelView.formatMembershipField` L63–136 | — |
| GUILD_MEMBERS intent + join listener | **PASS** | `DiscordCurrencyBot.java` L105–108；`GuildMemberJoinListener` | — |
| Architecture Atlas membership 模塊 | **PARTIAL** | `atlas.index.yaml` L25 已列 feature；7 子模塊 HTML 存在 | 5 條跨模塊 edges 未合併至主 atlas |
| `make verify` 全綠 | **PASS** | 2034 tests, 0 failures（2026-05-25 執行） | — |
| spend 失敗不阻斷 fulfillment | **FAIL** | spec EC + design L53 | worker 現 throw，與 spec 相反 |

**結論**：核心等級判定、join tracking、結算、折扣、面板與贈幣主流程已落地且測試通過；仍有 **1 項 P0 spec 衝突**（spend 錯誤策略）及數項 P1 資料一致性 / atlas 完整性問題，尚不足以判定 batch 驗收通過。

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | spend 記錄失敗時 throw，阻斷法幣 fulfillment | 與 `membership-spend-ledger` spec EC 及 design 明確衝突；membership DB 異常時 escort handoff、獎勵、markFulfilled 全部停擺，影響核心購物流程 | `FiatOrderPostPaymentWorker.java` | L94–97 |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | catalog 缺失時 fallback M 使用折後 charged amount | 違反 COORD-3 / SPEND-R1.1；`toFulfillmentProduct()` 將 `amountTwd`（折後）寫入 `Product.fiatPriceTwd`，fallback 低估 M，影響評級 | `FiatOrder.java`；`MembershipSpendService.java` | L230；L131, L143–146 |
| 2 | token grant audit retry 可能重複寫入交易紀錄 | `tokens_adjusted=true` + `status=FAILED` 重試時跳過 `tryAdjustTokens` 但再次 `recordTransaction`，可能造成 audit 重複 | `MembershipTokenGrantService.java` | L154–174 |
| 3 | spend 寫入與 settlement anchor 非同一 transaction | spend commit 後 `ensureSettlementAnchor` 失敗 → 用戶有 M 但 `next_settlement_at` 仍 null，永不進入結算 | `MembershipSpendService.java`；`JdbcMembershipSpendCoordinator.java` | L90–91；L56–73 |
| 4 | 主 Architecture Atlas 缺少 5 條跨模塊 edges | coordination checkpoint 要求 atlas 完整；plan diff 已定義 shop→payment-discount、fiat→spend-ledger 等，主 `atlas.index.yaml` 僅列 feature | `resources/project-architecture/atlas/atlas.index.yaml` | L25（缺 edges） |
| 5 | 結算 tick 內 `GameTokenChangedEvent` 同步觸發 panel 更新 | grant 路徑在 settlement scheduler 單執行緒內同步 fan-out panel DB 查詢，batch settle 時阻塞排程 | `UserPanelUpdateListener.java`；`MembershipSettlementScheduler.java` | L45–46；L59–73 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `last_settlement_at` 設為 `periodEnd` 而非 `now` | 與 settlement spec R1.1 字面略有偏差；與 design 算法一致，影響可忽略 | `MembershipSettlementService.java` | L80–87 |
| 2 | 排程間隔 3600s，spec 寫 daily | design 允許 1h 精度；結算延遲最多 1 小時 | `MembershipSettlementScheduler.java` | L19, L45–46 |
| 3 | 同 tick 失敗 grant 需等下一小時才 retry | `retryPendingGrants` 僅在 tick 開頭執行 | `MembershipSettlementScheduler.java` | L59–61 |
| 4 | settlement sum 與 tier 寫入非 serializable | 並發 spend insert 可能落在 sum 與 save 之間，該筆 M 計入下一週期 | `MembershipSettlementService.java` | L66–87 |
| 5 | 多 instance 無 scheduler leader election | 水平擴展時重複 settle / grant 嘗試與 event fan-out | `DiscordCurrencyBot.java`；`MembershipSettlementScheduler.java` | — |
| 6 | 週期無 spend 時 grant 無限 retry | `findPrimaryGuildInPeriod` 空 → release claim → pending scan 反覆 enqueue | `MembershipTokenGrantService.java` | L120–131 |
| 7 | `qualifyBronzeIfThreshold` 死碼，與 coordinator SQL 重複 | 維護 drift 風險 | `MembershipRepository.java`；`JdbcMembershipRepository.java` | L47–51；L167–184 |
| 8 | shop 定價用 `product.fiatPriceTwd`，spend 用 catalog M | payment design 刻意分離；admin 改價 ≠ catalog 時 M 與折扣基準不一致 | `MembershipPricingService.java`；`MembershipSpendService.java` | L33；L119–124 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | spec API 名 `resolveEscortProductPrice` 實作為 `quoteEscortPrice` | 命名不一致，行為存在 | `MembershipPricingService.java` | L30 |
| 2 | 青銅 promotion 事件 `periodAvgListPriceM` 為單筆 M 非週期平均 | 事件消費者可能誤解 | `MembershipSpendService.java` | L93–99 |
| 3 | `MembershipSpendService` 注入 `Clock` 未使用 | 冗餘 DI | `MembershipSpendService.java` | L32, L44 |
| 4 | `sumListPriceInPeriod` service wrapper 無 caller | 死碼 | `MembershipSpendService.java` | L110–113 |
| 5 | grant saga retry（audit 失敗、`tokens_adjusted`）缺專項測試 | 回歸風險 | `src/test/java/ltdjms/discord/membership/` | — |

---

## 解決方案

### P0 修復

#### P0-1: spend 失敗不應阻斷 fulfillment

- **涉及檔案**：`FiatOrderPostPaymentWorker.java` > `processSingleOrder`（L94–97）
- **根因**：上一輪 QA 修復將 spend 失敗改為 throw，與 `membership-spend-ledger/spec.md` EC 及 `design.md` L53「LOG.error + 不 throw」直接衝突。
- **修復方案**：
  1. 移除 throw；改為 `LOG.error` + 結構化 metric/alert。
  2. 新增 membership spend retry 佇列（類似 token grant retry）：以 `order_number` 為 key 記錄 `PENDING`/`FAILED` spend，由 scheduler 或獨立 worker 重試 `recordFiatEscortPayment`。
  3. 保留 fulfillment 關鍵路徑（handoff、reward、markFulfilled）不受 membership 影響。
  4. 更新 `FiatOrderPostPaymentWorkerTest.shouldNotMarkFulfilledWhenSpendRecordingFails` 為「fulfillment 成功 + spend 標記待重試」。
- **驗證方式**：
  - 模擬 spend repository throw → order 仍 markFulfilled、escort handoff 成功。
  - retry job 第二次成功 → spend entry 出現且 bronze flag 正確。
  - `make verify` 全綠。

### P1 修復

#### P1-1: fallback M 應優先使用訂單凍結 list price

- **涉及檔案**：`MembershipSpendService.java` > `recordFiatEscortPayment` / `resolveListPriceM`（L67–68, L131, L143–146）；`FiatOrder.java` > `toFulfillmentProduct`（L230）
- **根因**：fallback 讀取 `Product.fiatPriceTwd`，而 fulfillment snapshot 將 `amountTwd`（折後 charged）填入該欄位；訂單已持久化 `listPriceTwd` 卻未使用。
- **修復方案**：
  1. `recordFiatEscortPayment` 在 catalog 缺失時優先使用 `order.listPriceTwd()`。
  2. 若仍為 null，再 fallback `product.fiatPriceTwd()` 並 warn。
  3. 考慮 `toFulfillmentProduct()` 改用 `listPriceTwd ?? amountTwd` 供 fulfillment 語意一致（需評估 shop 其他 consumer）。
- **驗證方式**：integration test — 白銀折扣訂單 + mock catalog miss → spend entry `list_price_twd` = 原價 3500 而非 3150。

#### P1-2: grant audit 重試冪等

- **涉及檔案**：`MembershipTokenGrantService.java` > `grantForSettlement`（L154–174）；`MembershipTokenGrantRepository.java`
- **根因**：saga 在 tokens 已調整後，audit insert 失敗標記 `FAILED`；重試路徑無條件再次 `recordTransaction`。
- **修復方案**：
  1. audit 表或 grant log 增加 `audit_recorded` flag，或對 `(guild_id, user_id, settlement_period_end, source=MEMBERSHIP_GRANT)` 加 UNIQUE。
  2. 重試時若 `tokens_adjusted=true`，先查是否已有 audit row；有則直接 `completeGrantClaim`。
- **驗證方式**：unit test — mock `recordTransaction` 第一次 throw、第二次 成功；assert 僅一筆 audit、grant status=COMPLETED。

#### P1-3: spend + anchor 原子化

- **涉及檔案**：`JdbcMembershipSpendCoordinator.java`；`MembershipSpendService.java`（L90–91）
- **根因**：coordinator transaction 只涵蓋 spend + bronze；anchor 在 service 層另開 transaction。
- **修復方案**：將 `ensureSettlementAnchor` SQL 併入 coordinator transaction（僅 `inserted=true` 時），或新增 reconciliation：`next_settlement_at IS NULL AND EXISTS spend entry` 的修復 job。
- **驗證方式**：integration test — anchor update mock 失敗 → 同一 transaction rollback spend；或 reconciliation 測試補 anchor。

#### P1-4: 合併 Atlas 跨模塊 edges

- **涉及檔案**：`resources/project-architecture/atlas/atlas.index.yaml`
- **根因**：batch 實作完成後僅加入 feature 清單，未從 plan diff 合併 5 條 edges（e-6kw808, e-d78qfs, e-zsdgqz, e-e5v09v, e-btfhi7）。
- **修復方案**：自 `docs/plans/2026-05-24/membership-tiers/architecture_diff/atlas/atlas.index.yaml` L371–415 複製 edges 至主 atlas；執行 `apltk architecture validate`。
- **驗證方式**：`apltk architecture validate` OK；viewer 可見 shop→membership 等連線。

#### P1-5: grant 觸發的 panel 更新改為非同步

- **涉及檔案**：`UserPanelUpdateListener.java`（L45–46）；可選 `GameTokenService` event 過濾
- **根因**：`MembershipTierChangedEvent` 已 async，但 `GameTokenChangedEvent`（grant 路徑）仍同步 `updateUserPanel`。
- **修復方案**：將 `GameTokenChangedEvent` 中 `source=MEMBERSHIP_GRANT` 的更新路由至 `panelUpdateExecutor`，或 settlement 完成後只發一次 async panel refresh。
- **驗證方式**：scheduler integration test 或 mock 計時 — batch settle 100 用戶時 scheduler thread 不被 panel 更新阻塞超過閾值。

### P2 修復

#### P2-1: spec 與 `last_settlement_at` 語意對齊

- **涉及檔案**：`MembershipSettlementService.java`（L80）
- **根因**：spec 寫 `= now`，design 寫 `periodEnd`。
- **修復方案**：更新 spec 文案對齊 design（推薦），或改存 `clock.instant()` 並另存 `settlement_period_end`。
- **驗證方式**：doc/spec 一致；現有 settlement test 仍綠。

#### P2-2 ~ P2-6: 排程與並發強化（可分批）

- **P2-3**：tick 結尾再呼叫 `retryPendingGrants()`。
- **P2-4**：settle 單 transaction + `SELECT FOR UPDATE` on membership row。
- **P2-5**：ShedLock 或 DB advisory lock 包裹 scheduler tick。
- **P2-6**：grant 無 guild 時寫 `SKIPPED_NO_GUILD` terminal status，停止 pending scan。
- **P2-7**：刪除 `MembershipRepository.qualifyBronzeIfThreshold` 及 JDBC 實作。

### P3 改善

#### P3-1 ~ P3-5: 清理與測試補強

- 移除未使用 `Clock`、`sumListPriceInPeriod` wrapper。
- 刪除 `MembershipJoinService.computePreviousSettlementAt` 死碼。
- 為 grant saga retry、spend retry queue 補 unit/integration test。
- 考慮將 `MembershipTierChangedEvent.periodAvgListPriceM` 在 immediate bronze promotion 場景改名或加 javadoc。

---

## 修復優先順序

1. **P0-1** — 恢復 spend best-effort + retry（解除 spec 衝突、恢復 fulfillment 可用性）
2. **P1-1** — fallback M 使用 `order.listPriceTwd`
3. **P1-2** — grant audit 冪等
4. **P1-3** — spend + anchor 原子化
5. **P1-4** — Atlas edges 合併
6. **P1-5** — async panel on grant path
7. P2 / P3 依營運優先級排程

---

## 六維度審查摘要

| 維度 | 結論 |
|------|------|
| 無幻覺代碼 | **PASS** — 所有 API、Dagger 綁定、migration 與測試引用均可解析 |
| 無冗余代碼 | **PARTIAL** — `qualifyBronzeIfThreshold`、未使用 Clock、`sumListPriceInPeriod` wrapper |
| 無 spec 偏移 | **FAIL** — spend 錯誤策略、fallback M、atlas edges |
| 無 spec 遺漏 | **PARTIAL** — 核心功能齊；atlas cross-edges 未合併 |
| 無架構瑕疵 | **PARTIAL** — split saga、spend/anchor 分 transaction、sync panel on grant |
| 無性能隱患 | **PARTIAL** — scheduler 1000/tick 上限、grant 同步 panel、pending grant 子查詢 |

**整體判定：NOT PASS** — 需完成 P0-1 及 P1-1～P1-4 後重新執行 `/qa` 驗收。
