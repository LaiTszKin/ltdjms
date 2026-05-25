# Code Review Report

- **Spec**: membership-tiers (batch)
- **Date**: 2026-05-24
- **Reviewer**: QA Agent (post-fix regression review)
- **Result**: NOT PASS

---

## 業務需求驗收

**結論：核心會員流程（等級判定、消費計入、結算、折扣、贈幣、面板）已落地且主路徑可用，但仍有 2 項 P0 與數項 P1 邊界缺陷，尚不足以視為完全滿足規劃中的業務要求。**

| 需求 | 狀態 | 證據 | 缺口 / 不確定性 |
|------|------|------|----------------|
| 六等門檻與折扣/贈幣常數 | ✅ 滿足 | `MembershipTier.java` L7–13；門檻 500/14k/33k/100k/120k/250k | 無 |
| M = catalog 原價計入 ledger | ✅ 滿足 | `MembershipSpendService.resolveListPriceM()` L95–110 讀 `EscortOptionCatalog.priceTwd` | 折扣基礎用 shop 標價（design 允許），與 ledger M 可能不同 |
| 青銅 qualifying M≥500 + 永久保底 | ✅ 滿足 | `JdbcMembershipSpendRepository` L121–148；`MembershipTierEvaluator` | 無 |
| 青銅折扣/面板即時生效 | ✅ 滿足 | `MembershipTierEvaluator.effectiveTier()`；`MembershipPricingService` L41–48 | 面板自動刷新僅在 settlement 事件觸發，qualifying 當下不刷新（P1-2） |
| 結算日 = 最早加入日（29–31→28） | ⚠️ 部分 | `MembershipJoinService` L70–74；`mergeEarliestGuildJoin` | 未 join 追蹤用戶以**首筆付款日** lazy anchor（P1-1） |
| 月結算重算 tier + 推進 next_settlement | ✅ 滿足 | `MembershipSettlementService.settle()` L65–98 | 大批量 due 用戶可能延遲數十小時（P0-2） |
| 結算日發放 monthlyTokenGrant（冪等） | ✅ 滿足 | `MembershipTokenGrantService.grantForSettlement()`；V031 UNIQUE | 跨模組 saga 部分失敗風險（P1-4） |
| 護航商品付款折扣（ECPay 金額一致） | ✅ 滿足 | `FiatOrderService` + `MembershipPricingService.quoteEscortPrice()` | 無 |
| 非法幣 / 非 escort 不計 M、不折扣 | ✅ 滿足 | `EscortProductRules.isEscortLinked()` | 無 |
| `/user-panel` 展示等級/進度/結算日 | ⚠️ 部分 | `MemberInfoFacade.getMembershipSummary()`；`UserPanelView` | NONE  tier 隱藏週期 M 與結算日（P2-1） |
| GUILD_MEMBERS intent | ✅ 滿足 | `DiscordCurrencyBot.java` L97–98 | 無 runtime fail-fast（P3-1） |
| Architecture Atlas 新增模塊 | ⚠️ 部分 | `resources/project-architecture/features/membership-tiers/` 存在 | 未併入 `atlas.index.yaml`（P2-5） |

---

## 六維度審查摘要

| 維度 | 結論 |
|------|------|
| 無幻覺代碼 | ✅ 通過 — 所有引用類別/方法/表/Dagger 綁定均可解析，`make build` 成功 |
| 無冗余代碼 | ⚠️ 有改善空間 — 未使用的 repository API、重複 INSERT SQL（P3） |
| 無 spec 偏移 | ⚠️ 有偏移 — 結算錨點 lazy fallback、面板 NONE 展示、fulfillment 前置 spend（P1/P2） |
| 無 spec 遺漏 | ⚠️ 有遺漏 — `membership_tier_at_order` 未寫入、Atlas index 未合併（P2） |
| 無架構瑕疵 | ⚠️ 有瑕疵 — spend/fulfill 邊界、事件覆蓋、aggregate 跨 repo 寫入（P0/P1/P2） |
| 無性能隱患 | ⚠️ 有隱患 — 單批結算無 drain、同步事件 fanout、pending grant 缺索引（P0/P2） |

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | spend 記錄失敗被吞掉後仍 mark fulfilled | 已付款且 fulfillment 成功的訂單可能永久缺少 M 與青銅 qualifying，tier 計算失真 | `FiatOrderPostPaymentWorker.java` | L141–142 |
| 2 | 結算排程每 tick 僅處理 100 筆 due 用戶、無 drain loop | 結算日集中時 backlog 以 ~100 人/小時消化，tier 變更與贈幣嚴重延遲 | `MembershipSettlementScheduler.java` | L58–69 |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | lazy settlement anchor 以首筆付款日初始化錨點 | 違反「最早加入日結算」；miss join 的舊成員結算日偏移且難修正 | `MembershipSpendService.java` | L113–116 |
| 2 | 青銅 qualifying 升級不發 `MembershipTierChangedEvent` | 已開啟的 user-panel 不會即時刷新折扣/等級 | `JdbcMembershipSpendRepository.java` | L121–148 |
| 3 | spend 僅在 fulfillment 全成功後才寫入 | handoff/reward 失敗時 PAID 訂單不計 M，與 spend-ledger spec BDD 不一致 | `FiatOrderPostPaymentWorker.java` | L95–141 |
| 4 | token grant 跨 gametoken 模組無完整 saga 補償 | `tryAdjustTokens` 成功但 `recordTransaction` 失敗時，claim log 已佔位可能阻斷重試一致性 | `MembershipTokenGrantService.java` | L102–123 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | NONE tier 面板隱藏週期 M 與下次結算日 | 未達青銅但已 join/有消費的用戶看不到進度與結算日 | `UserPanelView.java` | L63–66 |
| 2 | `membership_tier_at_order` 欄位從未寫入 | V032 migration 與 spec audit 欄位未完成，折扣爭議難追溯 | `FiatOrderService.java` | L66–93 |
| 3 | 贈幣 guild 取自最近 spend guild | 跨 guild 消費時 token 落入非預期 guild（spec 未定義） | `MembershipTokenGrantService.java` | L91–101 |
| 4 | spend repository 直接更新 `global_member_membership` | 違反 coordination ownership；aggregate 邊界模糊 | `JdbcMembershipSpendRepository.java` | L121–148 |
| 5 | Architecture Atlas 未併入 main index | batch 交付物 `apltk architecture validate` 不完整 | `resources/project-architecture/atlas/atlas.index.yaml` | — |
| 6 | settlement 同步發布 tier 事件觸發全量 panel 重建 | 單 tick 100 人 tier 變更時 settlement 執行緒被 Discord/DB 阻塞 | `MembershipSettlementService.java` | L93–96 |
| 7 | `findPendingGrants` 缺少 `last_settlement_at` 索引 | membership 表增長後每 tick 全表掃描 | `JdbcMembershipTokenGrantRepository.java` | L107–119 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 無 GUILD_MEMBERS intent runtime fail-fast | misconfigured 部署靜默失去 join 追蹤 | `DiscordCurrencyBot.java` | L97–98 |
| 2 | 未使用的 repository/service API | 維護成本、誤用 footgun | `MembershipSpendRepository.java` 等 | — |
| 3 | settlement 每用戶 5–8 次獨立 DB round-trip | 中等規模 guild 結算 tick 偏慢 | `MembershipSettlementService.java` | L50–98 |
| 4 | `MembershipTierConfig` 純 passthrough | 僅 test/單點使用，可 inline 至 enum | `MembershipTierConfig.java` | — |
| 5 | tier 變更時 per-guild 重複查 membership | 多 guild 開 panel 時 N 倍重複 SUM | `UserPanelUpdateListener.java` | L45–67 |

---

## 解決方案

### P0 修復

#### P0-1: spend 失敗後仍 mark fulfilled

- **涉及檔案**：`FiatOrderPostPaymentWorker.java` > `processSingleOrder()`（L141–142）；`MembershipSpendService.java` > `recordFiatEscortPayment()`（L41–83）
- **根因**：spend 為 best-effort void + catch-all；fulfillment 成功與 spend 寫入無原子性，也無 outbox/retry。
- **修復方案**：
  1. `recordFiatEscortPayment` 改回傳 `boolean` 或 `Result`；
  2. 僅在 spend 成功（或已 idempotent 存在）後呼叫 `markFulfilledIfNeeded`；
  3. 或新增 `membership_spend_pending(order_number)` outbox，worker 失敗時保留 retryable 狀態。
- **驗證方式**：整合測試 mock spend repository 拋例外 → 訂單不應 fulfilled；修復後重跑 `MembershipSpendServiceTest` + `FiatOrderPostPaymentWorkerTest`。

#### P0-2: 結算排程無 backlog drain

- **涉及檔案**：`MembershipSettlementScheduler.java` > `runSettlement()`（L58–69）
- **根因**：每 tick 固定 `LIMIT 100` 後結束，無 inner loop；`scheduleWithFixedDelay(3600s)` 使吞吐上限 ~100 人/小時。
- **修復方案**：
  ```java
  do {
    batch = membershipRepository.findDueForSettlement(now, SETTLEMENT_BATCH_LIMIT);
    for (long userId : batch) { settlementService.settle(userId); }
  } while (batch.size() == SETTLEMENT_BATCH_LIMIT && batchesProcessed < MAX_BATCHES_PER_TICK);
  ```
  設定 `MAX_BATCHES_PER_TICK`（如 10）並記錄 backlog 深度 metric。
- **驗證方式**：`MembershipSettlementSchedulerTest` 模擬 250 due 用戶，單 tick 應處理 >100 筆（在 cap 內）。

### P1 修復

#### P1-1: 付款日 lazy anchor 偏離最早加入日

- **涉及檔案**：`MembershipSpendService.java` > `ensureSettlementAnchor()`（L113–116）；`JdbcMembershipRepository.java` > `ensureSettlementAnchor()`（L140–166）
- **根因**：P1-4 修復引入 lazy anchor，當 `next_settlement_at IS NULL` 時以 `paidAt` 寫入 `earliest_guild_join_at`；join 事件後續無法覆寫已設 anchor（join-tracking R2.1）。
- **修復方案**：
  1. lazy anchor 僅設 `next_settlement_at`，**不**寫 `earliest_guild_join_at`；
  2. 或區分 `settlement_anchor_source`（JOIN vs PAYMENT），join 永遠可覆寫 payment anchor；
  3. 可選 admin backfill 腳本補歷史 join 日（out of scope 但文件化）。
- **驗證方式**：整合測試 — 先付款後 join → anchor 應為較早 join 日。

#### P1-2: 青銅升級不發 tier 變更事件

- **涉及檔案**：`JdbcMembershipSpendRepository.java`（L121–148）；`MembershipSpendService.java`
- **根因**：`MembershipTierChangedEvent` 僅由 `MembershipSettlementService` 在 settlement tier 變更時發布；qualifying spend 的 `NONE→BRONZE` 無事件。
- **修復方案**：在 `MembershipSpendService`（或 service 層 orchestrator）於 bronze promotion 成功後 inject `DomainEventPublisher` 發布 `MembershipTierChangedEvent(NONE, BRONZE, ...)`。
- **驗證方式**：qualifying 付款後 mock listener 應收到事件；open panel 應自動刷新。

#### P1-3: fulfillment 失敗時不寫 spend

- **涉及檔案**：`FiatOrderPostPaymentWorker.java`（L95–141）
- **根因**：spend hook 在 handoff/reward 之後；handoff 失敗 throw → release processing，spend 永不執行。
- **修復方案**：
  - **方案 A（spec 對齊）**：PAID 後立即寫 spend（與 fulfillment 解耦），fulfillment 失敗不影響 M；
  - **方案 B（維持 design）**：更新 spend-ledger spec BDD 明確限定「fulfillment 成功路徑」，並在 PAID 狀態加 pending spend indicator。
- **驗證方式**：handoff 失敗 + PAID 訂單 — 依選定方案 assert spend 存在或 spec 已更新。

#### P1-4: token grant 跨模組部分失敗

- **涉及檔案**：`MembershipTokenGrantService.java` > `grantForSettlement()`（L102–123）
- **根因**：claim → adjust → recordTransaction 三步非原子；step 3 失敗時 tokens 已加但 audit 可能缺失，claim 阻斷 `findPendingGrants` 重試路徑不一致。
- **修復方案**：grant log 增加 `status`（CLAIMED/COMPLETED/FAILED）；僅 COMPLETED 視為冪等完成；adjust 失敗 release claim；recordTransaction 失敗標 FAILED 允許重試且 detect 已加 tokens。
- **驗證方式**：mock `recordTransaction` 拋例外 → retry 應成功且不 double-credit。

### P2 修復

#### P2-1: NONE tier 面板資訊不足

- **涉及檔案**：`UserPanelView.java` > `formatMembershipField()`（L63–66）
- **根因**：`tier == NONE` 時直接回傳靜態 hint，忽略 `MemberInfoFacade` 已計算的 `periodSpendM` 與 `nextSettlementAt`。
- **修復方案**：NONE 時仍顯示週期 M、距 SILVER 門檻進度、下次結算日；hint 作為補充文案而非唯一輸出。
- **驗證方式**：`UserPanelEmbedBuilderTest` — NONE + 有 spend + 有 nextSettlement 應顯示進度。

#### P2-2: `membership_tier_at_order` 未寫入

- **涉及檔案**：`FiatOrder.java`；`FiatOrderService.java`；`JdbcFiatOrderRepository.java`
- **根因**：V032 僅加 column；domain/repository 未擴充；`quoteEscortPrice` 的 `appliedTier` 未持久化。
- **修復方案**：`FiatOrder` 加 optional `membershipTierAtOrder`；createPending 時寫入 `quote.appliedTier().name()`；repository 讀寫該欄。
- **驗證方式**：建立法幣訂單後 DB 欄位非 null。

#### P2-3: 贈幣 guild 選擇未定義

- **涉及檔案**：`MembershipTokenGrantService.java`（L91–101）
- **根因**：GameToken 為 guild-scoped，grant 需 guildId；實作取最近 spend guild 無 spec 依據。
- **修復方案**：spec 補充規則（如 primary guild / 最近消費 guild / 固定 global grant guild）；或 membership 表加 `preferred_grant_guild_id`。
- **驗證方式**：跨 guild 消費用戶 grant 落入預期 guild。

#### P2-4: spend repo 寫 membership aggregate

- **涉及檔案**：`JdbcMembershipSpendRepository.java`（L121–148）
- **根因**：原子 bronze qualifying 將 UPDATE 放在 spend repo 以保證單 transaction。
- **修復方案**：抽出 `MembershipQualificationRepository.qualifyBronzeIfThreshold()` 或在 service 層用 `@Transactional` 協調兩 repo；保持單 tx 但邊界清晰。
- **驗證方式**：重構後整合測試不變。

#### P2-5: Atlas index 未合併

- **涉及檔案**：`resources/project-architecture/atlas/atlas.index.yaml`
- **根因**：batch architecture_diff 未 merge 至 main atlas。
- **修復方案**：將 `membership-tiers` 加入 features list；執行 `apltk architecture validate`。
- **驗證方式**：validate 通過；index.html 可導航至 membership 子模塊。

#### P2-6: 同步 panel fanout 阻塞 settlement

- **涉及檔案**：`MembershipSettlementService.java`；`UserPanelUpdateListener.java`
- **根因**：`DomainEventPublisher` 同步 dispatch；tier 事件觸發全量 panel rebuild + Discord API。
- **修復方案**：panel refresh 改 async executor；或 settlement tick 內 coalesce per-user refresh；tier 變更與 token 變更 dedupe。
- **驗證方式**：benchmark 100 tier changes tick 耗時顯著下降。

#### P2-7: pending grant 查詢缺索引

- **涉及檔案**：新增 `V033__membership_pending_grant_index.sql`
- **根因**：`findPendingGrants` filter `last_settlement_at IS NOT NULL AND current_tier <> 'NONE'` 無 supporting index。
- **修復方案**：
  ```sql
  CREATE INDEX idx_gmm_pending_grant_scan
    ON global_member_membership (last_settlement_at)
    WHERE last_settlement_at IS NOT NULL AND current_tier <> 'NONE';
  ```
- **驗證方式**：EXPLAIN 顯示 index scan。

### P3 改善

#### P3-1: GUILD_MEMBERS intent fail-fast

- **涉及檔案**：`DiscordCurrencyBot.java`
- **根因**：僅 enable intent，無啟動自檢。
- **修復方案**：JDA ready 後 probe `guild.retrieveMember()` 或 document + health check endpoint。
- **驗證方式**：關閉 intent 時啟動 log ERROR 或 exit non-zero。

#### P3-2: 清理未使用 API

- **涉及檔案**：`MembershipSpendRepository.insertIfAbsent`；`MembershipTokenGrantRepository.hasGrantForPeriod` 等
- **修復方案**：移除或標 `@VisibleForTesting`；production path 只保留 `insertSpendAndQualifyBronzeIfThreshold` + `tryClaimGrantLog`。
- **驗證方式**：grep 無 dead public API。

#### P3-3: settlement N+1 查詢

- **涉及檔案**：`MembershipSettlementScheduler.java`；`MembershipSettlementService.java`
- **修復方案**：`findDueMembershipsForSettlement` 一次取 row；batch SUM spend。
- **驗證方式**：結算 100 人 DB query count ≤ 常數級。

#### P3-4: MembershipTierConfig passthrough

- **涉及檔案**：`MembershipTierConfig.java`
- **修復方案**：inline 至 `MembershipTier` 或保留作 spec contract facade 並加 javadoc。
- **驗證方式**：無行為變更。

#### P3-5: per-guild 重複 membership 查詢

- **涉及檔案**：`UserPanelUpdateListener.java` > `updatePanelsForUser()`
- **修復方案**：先 `getMembershipSummary(userId)` 一次，per-guild 只查 balance/tokens。
- **驗證方式**：tier 事件 mock 驗證 membership repo 呼叫次數 = 1。

---

## 修復優先順序建議

1. **P0-1** → **P0-2**（資料完整性 + 結算 SLA）
2. **P1-1** → **P1-2** → **P1-3** → **P1-4**（業務規則 + 事件 + 跨模組一致性）
3. **P2 batch**（UX、audit、Atlas、性能）
4. **P3**（技術債）

修復完成後重新執行 `/qa` 驗收。

---

## 已確認修復（相對上一輪 QA）

以下上一輪 P0–P3 缺陷在本次審查中**已解決**：

- ✅ P0-1 settlement `last_settlement_at` 改為 `periodEnd`
- ✅ P0-2 青銅 immediate tier（`effectiveTier` + atomic qualify）
- ✅ P1-1 token grant claim-first 冪等 + release
- ✅ P1-2 grant retry（`retryPendingGrants` + scheduler hook）
- ✅ P1-3 escort-linked 判定統一（`EscortProductRules`）
- ✅ P1-4 lazy settlement anchor 機制（但引入 P1-1 新偏移）
- ✅ P2-1 atomic earliest join merge
- ✅ P2-2 atomic spend + bronze qualify
- ✅ P2-3 settlement tier 事件觸發 panel refresh
- ✅ P2-4 共用 `MembershipPeriodBounds`
- ✅ P2-5 architecture HTML 已複製至 resources（index 仍待 P2-5）
- ✅ P2-7 移除 production noop
- ✅ P3-1 NONE tier hint 文案
- ✅ P3-2 settlement batch limit
- ✅ P3-3 `@SettlementClock` qualifier
