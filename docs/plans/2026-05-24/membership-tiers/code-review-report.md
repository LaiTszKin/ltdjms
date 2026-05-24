# Code Review Report

- **Spec**: membership-tiers (batch: core, join-tracking, spend-ledger, settlement, payment-discount, benefits-ui)
- **Date**: 2026-05-24
- **Reviewer**: QA Agent
- **Result**: **NOT PASS**

---

## 業務需求符合度總結

本次實作已建立完整的 membership 模組骨架（V029–V031 migration、domain、repository、service、shop/panel 整合、`make verify` 全綠），**核心常數與 tier 純函式符合定稿財務模型**，且**無幻覺代碼**（所有跨模組引用均可解析）。

然而，存在 **2 項 P0 資料/權益正確性缺陷** 與 **4 項 P1 功能缺口**，導致部分 spec 場景在 production 下無法正確運作：

| 關鍵需求 | 判定 | 證據 | 缺口 |
|---------|------|------|------|
| 六等門檻/折扣/贈幣常數 | ✅ 符合 | `MembershipTier.java` L7–13 | — |
| M = catalog 原價（非折後） | ✅ 符合 | `MembershipSpendService.resolveListPriceM()` L189–204 | — |
| 法幣 escort 付款寫入 spend ledger | ✅ 符合 | `FiatOrderPostPaymentWorker.java` L183 | — |
| 結算日重算 tier + 發幣 | ⚠️ 部分 | `MembershipSettlementService.settle()` L50–106 | 週期邊界遺失 spend（P0-1）；grant 失敗無 retry（P1-4） |
| 青銅 qualifying 後永久保底 | ❌ 不符合 | `MembershipSpendService.markQualifyingBronzeOrder()` L212–228 | 折扣/UI 讀 `current_tier` 而非 effective tier（P0-2） |
| 付款前套用折扣寫入 ECPay | ⚠️ 部分 | `FiatOrderService.createFiatOnlyOrder()` | `escortOptionCode`-only 商品無折扣（P1-2） |
| 結算日發放贈幣（tier 不變也發） | ⚠️ 部分 | `MembershipSettlementService.java` L96 | idempotency 非原子（P1-3） |
| `/user-panel` 展示等級與進度 | ⚠️ 部分 | `UserPanelView.formatMembershipField()` | NONE 文案、週期 M 與 settlement 不一致（P2/P3） |
| GUILD_MEMBERS intent | ✅ 符合 | `DiscordCurrencyBot.java` L97–98 | — |

**剩餘不確定性**：青銅「取得」是否必須**即時**生效（付款當下）vs 僅在結算日寫入 `current_tier`——coordination 文案「完成 qualifying 單後取得」與 payment-discount「依當前等級折扣」強烈暗示即時生效；若產品意圖為「次月結算才升青銅」，需更新 spec 並調整 UI 文案。

---

## 六維度審查摘要

| 維度 | 結論 |
|------|------|
| 無幻覺代碼 | ✅ 通過 — 所有 Java 引用、SQL 欄位、Dagger 綁定均可解析 |
| 無冗余代碼 | ⚠️ `MembershipSpendService.noop()` 含 ~90 行 inline stub（P2） |
| 無 spec 偏移 | ❌ 2×P0、2×P1 行為偏移（見下表） |
| 無 spec 遺漏 | ⚠️ Atlas 未合入主資源、部分 IT/並發測試缺失（P2） |
| 無架構瑕疵 | ❌ 事件未消費、多 transaction 副作用、join anchor 缺口（P1–P2） |
| 無性能隱患 | ⚠️ 結算 scheduler 無 batch limit、N+1、單執行緒（P1 at scale） |

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 結算後 `last_settlement_at` 設為 scheduler 執行時間 `now`，而非週期邊界 `periodEnd` | `[periodEnd, now)` 區間的 spend 永遠不計入任何結算週期，tier 評級遺失 M | `MembershipSettlementService.java` | L77–84 |
| 2 | 青銅 qualifying 僅設 `has_qualifying_bronze_order`，未更新 `current_tier`；定價/UI 只讀 `current_tier` | 用戶完成 M≥500 qualifying 單後至下次結算前仍付原價、面板顯示 NONE | `MembershipPricingService.java` / `MembershipSpendService.java` / `MemberInfoFacade.java` | L44–48 / L212–228 / L129–134 |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 3 | Token grant idempotency 為 check→credit→insert，無 `ON CONFLICT DO NOTHING` claim-first | 部分失敗或並發重試可能雙倍發幣 | `MembershipTokenGrantService.java` / `JdbcMembershipTokenGrantRepository.java` | L62–102 / L55–66 |
| 4 | `tryAdjustTokens` 失敗後無 next-tick retry；settlement 已推進 `next_settlement_at` | 該期贈幣永久遺失 | `MembershipTokenGrantService.java` / `MembershipSettlementScheduler.java` | L82–90 / L54–62 |
| 5 | `escortOptionCode` 非空但 `shouldAutoCreateEscortOrder=false` 的商品：spend 計入但折扣不套用 | 同商品 M 累加與實付不一致，違反 payment-discount R1.2 | `MembershipPricingService.java` vs `MembershipSpendService.java` | L64–66 vs L207–209 |
| 6 | 無 join anchor（`next_settlement_at` null）的用戶永不進入結算管線 | 歷史成員/未觸發 join 事件者 spend 與 bronze flag 累積但 tier/grant 停滯 | `JdbcMembershipRepository.java` / `MembershipJoinService.java` | L72–75 / L42–73 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 7 | 並發雙 guild join 為 read-modify-write，無 transactional min() | 可能寫入較晚 join 日，結算錨點錯誤 | `MembershipJoinService.java` / `JdbcMembershipRepository.java` | L43–73 / L136–186 |
| 8 | spend insert 與 bronze flag 更新非同一 transaction | qualifying 單 spend 已寫入但 flag 可能未設 | `MembershipSpendService.java` | L161–177 |
| 9 | `MembershipTierChangedEvent` 已發布但無 listener 消費 | tier 變更不自動刷新 user-panel | `MembershipSettlementService.java` / `UserPanelUpdateListener.java` | L90–94 |
| 10 | Panel 週期 M 用 `[periodStart, now)`，settlement 用 `[periodStart, periodEnd)` | 結算日前後面板數字與實際評級不一致 | `MemberInfoFacade.java` / `MembershipSettlementService.java` | L123–124 / L64–66 |
| 11 | `schema.sql` 未同步 V030/V031 與 `fiat_order` 新欄位 | 維運參考 schema 與 production 脫節 | `src/main/resources/db/schema.sql` | — |
| 12 | 缺少 settle→grant→balance 整合測試、並發 settlement/join IT | 端到端 grant 與 race 行為未驗證 | `MembershipSettlementIntegrationTest.java` | — |
| 13 | Architecture Atlas 僅在 plan 目錄，未更新 `resources/project-architecture/` | batch shared outcome 未完成 | `docs/plans/.../architecture_diff/` | — |
| 14 | `MembershipSpendService.noop()` 在 production service 內嵌 ~90 行 stub | 層級邊界模糊；deprecated worker ctor 可靜默跳過 spend | `MembershipSpendService.java` | L38–131 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 15 | NONE tier 面板顯示購買提示而非 spec 要求的「尚未達標」 | UX 文案偏移 | `UserPanelView.java` | L30–31, L66–67 |
| 16 | `membership_tier_at_order` 可選 audit 欄位未實作 | 無 per-order tier 稽核軌跡 | V030 migration | — |
| 17 | 結算 scheduler 無 batch limit、單執行緒、per-user N+1 | 大規模 backlog 時延遲與 connection pool 競爭 | `MembershipSettlementScheduler.java` / `JdbcMembershipRepository.java` | L19, L54–63 / L72–86 |
| 18 | V031 冗餘 index 與 UNIQUE 重複 | 寫入放大 | `V031__create_membership_token_grant_log.sql` | L10, L13 |
| 19 | `MembershipModule` 提供 unqualified `Clock`（Asia/Taipei）影響全域 DI 消費者 | 時區語意隱式擴散 | `MembershipModule.java` | L51–55 |

---

## 解決方案

### P0 修復

#### P0-1: 結算週期邊界遺失 spend

- **涉及檔案**：`MembershipSettlementService.java` > `settle()`（L77–84）
- **根因**：`settledAt = now` 寫入 `last_settlement_at`，下一週期起點為 scheduler 執行時間；`[periodEnd, now)` 的 `paid_at` 既不屬已結週期（sum 上界為 `periodEnd`），也不屬新週期（下界為 `now`）。
- **修復方案**：將 `last_settlement_at` 設為 `periodEnd`（合約週期邊界），而非 `clock.instant()`。`saveSettlementResult` 第四參數改傳 `periodEnd`。可選：scheduler tick 時若 `now >> periodEnd`，仍用 `periodEnd` 作為已結週期上界。
- **驗證方式**：新增 integration test：在 `periodEnd` 與 settle 執行之間插入 spend entry，assert 該 M 計入剛結束的週期 avgM。

#### P0-2: 青銅 qualifying 後折扣/UI 未即時生效

- **涉及檔案**：`MembershipPricingService.java` > `quoteEscortPrice()`（L44–48）；`MemberInfoFacade.java` > `getMembershipSummary()`（L129–134）；`MembershipSpendService.java` > `markQualifyingBronzeOrder()`（L212–228）
- **根因**：qualifying 僅更新 `has_qualifying_bronze_order`；定價與面板讀 `current_tier`，在下次 settlement 前仍為 `NONE`。
- **修復方案**（擇一或組合）：
  1. 新增 domain helper `MembershipTierEvaluator.effectiveTier(currentTier, hasQualifyingBronzeOrder)`，定價/UI/settlement 統一使用；
  2. 或在 `markQualifyingBronzeOrder` 時若 `current_tier == NONE` 則 eagerly 設 `current_tier = BRONZE`。
- **驗證方式**：unit test — qualifying spend 後 `quoteEscortPrice` 回傳 BRONZE 95 折；panel 顯示青銅等級。

### P1 修復

#### P1-1: Token grant 非原子 idempotency

- **涉及檔案**：`MembershipTokenGrantService.java` > `grantForSettlement()`（L62–102）；`JdbcMembershipTokenGrantRepository.java` > `insertGrantLog()`（L55–66）
- **根因**：check-then-act 序列；token 已 credit 後 insert 失敗可導致重試雙發。
- **修復方案**：改為 claim-first：`INSERT INTO membership_token_grant_log ... ON CONFLICT DO NOTHING RETURNING id`；僅在 claim 成功後呼叫 `tryAdjustTokens`。失敗時不 credit。
- **驗證方式**：unit test 模擬 insert 失敗後重試，assert token balance 只增加一次。

#### P1-2: Grant 失敗無 retry

- **涉及檔案**：`MembershipTokenGrantService.java`（L82–90）；`MembershipSettlementScheduler.java`（L54–62）
- **根因**：grant 內嵌於 settlement 尾端；失敗僅 log；`next_settlement_at` 已推進，無機制重試同一 `settlement_period_end`。
- **修復方案**：新增 `findPendingGrants()` 查詢已結算但無 grant log 的 period；scheduler 每 tick 先處理 pending grants。或 settlement 不推進 anchor 直到 grant claim 成功（需評估 trade-off）。
- **驗證方式**：mock `tryAdjustTokens` 第一次失敗、第二次成功；assert 第二次 tick 補發。

#### P1-3: escortOptionCode-only 商品無折扣

- **涉及檔案**：`MembershipPricingService.java` > `isEscortLinked()`（L64–66）
- **根因**：定價 escort 判定僅檢查 `shouldAutoCreateEscortOrder()`，與 spend ledger 判定不一致。
- **修復方案**：提取共用 `EscortProductRules.isEscortLinked(product)`（或 reuse `MembershipSpendService.isEscortLinked` 至 domain），pricing/spend/shop 共用。
- **驗證方式**：`MembershipPricingServiceTest` — product 僅有 `escortOptionCode` 時 assert 折扣生效。

#### P1-4: 無 join anchor 用戶無法結算

- **涉及檔案**：`MembershipSpendService.java` > `recordFiatEscortPayment()`；`MembershipJoinService.java`
- **根因**：`next_settlement_at` 僅由 join event 初始化；spend 路徑 `findOrCreate` 不設 anchor。
- **修復方案**：首次 qualifying spend 或首次 spend 時 lazy-init：`settlement_day_of_month` 取自 `paid_at`（clamp 29–31→28），`next_settlement_at` 計算下一錨點。或 bot startup backfill（spec follow-up）。
- **驗證方式**：IT — 無 join 記錄、有 spend 的 user 出現在 `findDueForSettlement` 並可 settle。

### P2 修復

#### P2-1: 並發 join earliest 競態

- **涉及檔案**：`MembershipJoinService.java`（L43–73）；`JdbcMembershipRepository.java`
- **根因**：非原子 read-modify-write。
- **修復方案**：SQL `UPDATE ... SET earliest_guild_join_at = LEAST(COALESCE(earliest_guild_join_at, ?), ?) WHERE discord_user_id = ? AND (earliest_guild_join_at IS NULL OR ? < earliest_guild_join_at)`，或 `SELECT FOR UPDATE`。
- **驗證方式**：concurrent IT 雙 thread join，assert earliest = min。

#### P2-2: spend 與 bronze flag 非原子

- **涉及檔案**：`MembershipSpendService.java`（L161–177）
- **根因**：兩次獨立 JDBC 呼叫。
- **修復方案**：repository 層提供 `recordSpendAndMarkBronze(...)` 單 transaction；或 outbox 重試 flag 更新。
- **驗證方式**：mock flag update 失敗，assert 可重試且最終 flag=true。

#### P2-3: MembershipTierChangedEvent 無 listener

- **涉及檔案**：`UserPanelUpdateListener.java` 或新建 listener
- **根因**：event 發布但未註冊消費者。
- **修復方案**：擴充 `UserPanelUpdateListener` 處理 `MembershipTierChangedEvent`，刷新該 user 所有 open panel session。
- **驗證方式**：unit test publish event → verify panel refresh invoked。

#### P2-4: Panel 週期 M 與 settlement 不一致

- **涉及檔案**：`MemberInfoFacade.java`（L123–124）；`MembershipSettlementService.java`（L108–116）
- **根因**：重複且分歧的 period 邊界邏輯。
- **修復方案**：提取 `MembershipPeriodBounds.resolve(membership, clock)` 共用；panel 使用 `[periodStart, min(now, nextSettlementAt))`。
- **驗證方式**：unit test 在 overdue settlement 場景 assert panel M == settlement sum。

#### P2-5: schema.sql 與 Atlas 同步

- **涉及檔案**：`src/main/resources/db/schema.sql`；`resources/project-architecture/`
- **根因**：實作完成後未執行 docs/atlas 同步步驟。
- **修復方案**：依 `docs/operations/deployment-and-maintenance.md` 更新 schema.sql；執行 `update-project-html` 或 merge plan atlas 至主資源。
- **驗證方式**：`apltk architecture validate`；schema.sql 含 V031 表與 fiat_order 欄位。

#### P2-6: 補充整合/並發測試

- **涉及檔案**：`MembershipSettlementIntegrationTest.java`；新建 concurrent IT
- **根因**：checklist 要求但未覆蓋 grant balance、concurrent settle/join。
- **修復方案**：IT assert grant log + token balance + MEMBERSHIP_GRANT txn；parallel `settle()` assert 單次 tier 更新。
- **驗證方式**：`make test` 新增案例全綠。

#### P2-7: 移除 noop stub 至 test 包

- **涉及檔案**：`MembershipSpendService.java`（L38–131）；`FiatOrderPostPaymentWorker.java` deprecated ctors
- **根因**：production service 承載 test double。
- **修復方案**：移至 `src/test` 的 test fixture；production worker 強制注入真實 `MembershipSpendService`。
- **驗證方式**：編譯通過；現有 worker test 改用 test double。

### P3 改善

#### P3-1: NONE 面板文案

- **涉及檔案**：`UserPanelView.java`（L66–67）
- **根因**：使用購買 CTA 而非「尚未達標」。
- **修復方案**：NONE 時顯示「尚未達標（需完成 M≥500 護航法幣單）」並保留 CTA 為次要提示。
- **驗證方式**：更新 `UserPanelEmbedBuilderTest` assert 文案。

#### P3-2: 結算 scheduler 可擴展性

- **涉及檔案**：`MembershipSettlementScheduler.java`；`JdbcMembershipRepository.findDueForSettlement`
- **根因**：unbounded fetch + single-thread + per-user N+1。
- **修復方案**：`findDueForSettlement(before, limit)` 分頁；可配置 worker pool；batch prefetch spend sums。
- **驗證方式**：load test 或 benchmark 10k due users tick 時間在 SLO 內。

#### P3-3: 其他

- **membership_tier_at_order**：若需 audit，V032 migration + FiatOrder 欄位。
- **V031 冗餘 index**：drop `idx_mtgl_user_period`。
- **Clock DI**：改 `@Named("settlement") Clock` 限定 membership 模組。

---

## 審查結論

| 項目 | 狀態 |
|------|------|
| 編譯與 unit test | ✅ `make verify` 通過（2028 tests） |
| 幻覺代碼 | ✅ 無 |
| 核心常數/domain | ✅ 符合 coordination 定稿表 |
| 端到端業務正確性 | ❌ P0-1、P0-2 需修復後方可上線 |
| 生產可靠性 | ⚠️ P1-3、P1-4 建議與 P0 同批修復 |

**建議修復順序**：P0-1 → P0-2 → P1-3 → P1-2 → P1-4 → P1-1 → P2 批次 → P3。

修復完成後重新執行 `/qa` 或 `/fix` 驗收。
