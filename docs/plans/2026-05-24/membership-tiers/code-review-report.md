# Code Review Report

- **Spec**: membership-tiers (batch: membership-core, membership-join-tracking, membership-spend-ledger, membership-settlement, membership-payment-discount, membership-benefits-ui)
- **Date**: 2026-05-24
- **Reviewer**: QA Agent
- **Result**: **NOT PASS**

---

## 業務需求驗收摘要

六份子 spec 的核心流程已端到端串接：`make verify` 全綠（2037 tests）、Flyway V029–V034、Dagger  wiring、Atlas 主要邊已補齊。以下為關鍵需求追溯：

| 子 spec | 關鍵需求 | 狀態 | 證據 | 缺口 / 不確定性 |
|---------|---------|------|------|----------------|
| membership-core | 六等常數、tier 純函式、主表 schema | **MET** | `MembershipTier.java`, `MembershipTierEvaluator.java`, `V029` | — |
| membership-join-tracking | 最早 join 日、結算日初始化、GUILD_MEMBERS | **MET** | `GuildMemberJoinListener.java`, `JdbcMembershipRepository.mergeEarliestGuildJoin` | 既有成員無 backfill（spec 已知 out of scope） |
| membership-spend-ledger | escort 法幣 M 入帳、idempotent、best-effort | **MET** | `MembershipSpendService`, `JdbcMembershipSpendCoordinator`, `FiatOrderPostPaymentWorker` | spend 路徑會從 `paidAt` 初始化 anchor（見 P1-2） |
| membership-settlement | 結算日重算 tier、週期 `[L,N)`、事件 | **MET** | `JdbcMembershipSettlementCoordinator`, `MembershipSettlementService` | 與 spend 非同一 txn 可能漏計 M（見 P1-1） |
| membership-payment-discount | 折後價寫入訂單、ECPay 驗證、貨幣折扣 | **MET** | `MembershipPricingService`, `FiatOrderService`, `CurrencyPurchaseService` | — |
| membership-payment-discount | Shop 確認頁顯示會員價 | **PARTIAL** | 貨幣確認頁 `ShopView` L227–231；雙價 payment-method embed L131–140 | **法幣-only 商品跳過確認頁**（見 P2-1） |
| membership-benefits-ui | 每次結算發幣、idempotent grant、user-panel | **MET** | `MembershipTokenGrantService`, `MemberInfoFacade`, `UserPanelView` | grant 與 tier commit 非原子（見 P2-2） |

**整體判定：NOT PASS** — P1-1～P1-4（結算錨點與 spend 競態）及 P2-1（法幣確認頁 UI）需在合併前修復或明確接受風險並更新 spec。

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| — | 無 | — | — | — |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Settlement 與 spend 寫入分屬不同 transaction；settlement 僅鎖 membership 列後 SUM spend，期間內晚到的 spend（含 retry）可能永久落在已關閉週期外 | 月平均 M 被低估、tier 永久偏低、panel 週期累計錯誤 | `JdbcMembershipSettlementCoordinator.java`, `JdbcMembershipSpendCoordinator.java` | L41–87, L133–137 / L64–83 |
| 2 | Spend 路徑以 **`paidAt`** 初始化 `settlement_day_of_month` / `next_settlement_at`，違反 coordination「結算日 = 最早 join 日」 | 無 join 事件的既有成員、或 payment-before-join 用戶結算日錯誤 | `JdbcMembershipSpendCoordinator.java` | L111–127 |
| 3 | `mergeEarliestGuildJoin` 僅在 `next_settlement_at IS NULL` 時寫入；payment 已初始化 anchor 後，更早的 join 無法修正 `next_settlement_at` | `settlement_day_of_month` 與 `next_settlement_at` 不一致，首次結算日期錯誤 | `JdbcMembershipRepository.java` | L111–114 |
| 4 | `MembershipPeriodBounds.resolvePeriodStart` 在無 `last_settlement_at` 且無 `earliest_guild_join_at` 時回傳 **EPOCH**；配合 P1-2 首次結算可能加總自 epoch 以來全部 spend | 首次結算 tier 可能被高估 | `MembershipPeriodBounds.java`, `JdbcMembershipSettlementCoordinator.java` | L29–37 / L58–59 |
| 5 | 結算排程硬上限 **1000 用戶/小時**（100×10 batch × 3600s interval），積壓僅 log 不立即 drain | 結算日高峰 tier 重算與贈幣延遲數小時至數天 | `MembershipSettlementScheduler.java` | L17–19, L73–91 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 法幣-only escort 商品從選單直接 `deferReply` 建單，無 Shop 確認 embed；雙價法幣路徑亦跳過確認 | 不符合 payment-discount R3 / checklist C4「確認頁顯示會員價」 | `ShopSelectMenuHandler.java` | L112–125, L234–256 |
| 2 | Tier 持久化在 coordinator txn 內 commit，token grant 在 `settle()` 尾端另開 saga；`MembershipTierChangedEvent` 先於 grant 完成 | UI 可先顯示新 tier 但贈幣尚未到帳；失敗依賴 scheduler retry | `MembershipSettlementService.java`, `MembershipTokenGrantService.java` | L48–63 / L79–202 |
| 3 | Advisory lock 涵蓋整個 tick（grant retry + spend retry + 最多 1000 settle + grant retry），持鎖期間其他實例完全跳過 | 多實例部署下 tick 過長時 failover 延遲；佔用一條 DB connection | `JdbcMembershipSettlementTickGuard.java`, `MembershipSettlementScheduler.java` | L22–34 / L67–94 |
| 4 | Spend / grant retry 每 tick 各處理固定 batch（50 / 100），無 drain loop | 故障恢復後積壓需數小時才能清完 | `MembershipSpendRetryService.java`, `MembershipTokenGrantService.java` | L17, L45–62 / L29, L55–66 |
| 5 | Catalog 缺失時 fallback 優先 `order.listPriceTwd`，spec R1.2 寫 **`product.fiatPriceTwd`** | 商品改價後 retry 的 M 可能與 spec 字面不一致 | `MembershipSpendService.java` | L124–135 |
| 6 | Spend 寫入失敗僅 LOG，design 要求 **LOG + metric** | 維運無法告警 spend 落帳失敗率 | `MembershipSpendService.java`, `FiatOrderPostPaymentWorker.java` | L100–105 |
| 7 | `MembershipRepository.saveSettlementResult` / `ensureSettlementAnchor` 零 call site，live path 在 coordinator | 誤改 repository 路徑導致行為漂移 | `MembershipRepository.java`, `JdbcMembershipRepository.java` | L44–56, L140–205 |
| 8 | `MembershipSettlementService` 直接依賴 concrete `JdbcMembershipSettlementCoordinator`；coordinator 反向 import `MembershipJoinService` | 分層反轉、測試/mock 困難 | `MembershipSettlementService.java`, `JdbcMembershipSettlementCoordinator.java` | L9, L19 / L20 |
| 9 | `MemberInfoFacade` 直接注入 membership repository 並重複 period/tier 邏輯 | 跨模組應走 service facade；語意變更需多處同步 | `MemberInfoFacade.java` | L151–174 |
| 10 | Panel 更新：單執行緒 + 無界 queue；`updatePanelsByUser` 掃描全部 session | 結算日大量 `GameTokenChangedEvent` 時 panel 更新排隊延遲 | `UserPanelUpdateListener.java`, `PanelSessionManager.java` | L27–33, L45–50 / L140–164 |
| 11 | Spend retry 無 claim/lease pattern（`findPending` + `recordAttempt`） | 若未來多 executor 重試可能重複處理 | `MembershipSpendRetryService.java`, `JdbcMembershipSpendRetryRepository.java` | L45–62 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `resolveEscortProductPrice` 為 spec alias，零 caller | 冗余 API | `MembershipPricingService.java` | L63–66 |
| 2 | `schema.sql` 未含 `fiat_order` membership 欄位（僅註解） | `PostgresIntegrationTestBase` 路徑與 Flyway 漂移 | `src/main/resources/db/schema.sql` | L254–257 |
| 3 | `PostgresIntegrationTestBase.cleanDatabase` 未 TRUNCATE `membership_token_grant_log` | 整合測試可能交叉污染 | `PostgresIntegrationTestBase.java` | L69–72 |
| 4 | `UserPanelUpdateListenerTest` 未覆蓋 `MembershipTierChangedEvent` | 回歸風險 | `UserPanelUpdateListenerTest.java` | — |
| 5 | Atlas 缺 event-bus、spend-retry→shop 等邊 | 架構圖不完整 | `resources/project-architecture/atlas/atlas.index.yaml` | — |
| 6 | Grant status `SKIPPED_NO_GUILD` 用於程式但未寫入 V033 COMMENT | 文件漂移 | `JdbcMembershipTokenGrantRepository.java` | — |
| 7 | `findDueForSettlement(Instant)` 無 limit  overload 預設 `Integer.MAX_VALUE` | API footgun | `JdbcMembershipRepository.java` | L72–74 |

---

## 解決方案

### P0 修復

無 P0 問題。

### P1 修復

#### P1-1: Settlement 與 spend 競態導致 M 漏計

- **涉及檔案**：`JdbcMembershipSettlementCoordinator.java` > `applyIfDue`（L41–87）；`JdbcMembershipSpendCoordinator.java` > `insertSpendAndQualifyBronzeIfThreshold`（L64–83）
- **根因**：Settlement SUM 與 spend INSERT 不在同一 transaction；settlement commit 後 advance `next_settlement_at`，晚到且 `paid_at` 落在已關週期的 spend 永遠不會被任何週期加總。
- **修復方案**：
  1. **首選**：Settlement txn 內對 `membership_spend_entry` 加 `paid_at` range 的 share lock 或 re-SUM 後再 advance anchor（與 spend txn 序列化同一 user）。
  2. **替代**：Spend 插入時若 `paid_at < next_settlement_at` 且 settlement 正在進行，block 在 membership `FOR UPDATE` 直到 settlement 完成。
  3. **補償**：Settlement 完成後掃描 `paid_at ∈ [periodStart, periodEnd)` 且 `created_at > settledAt` 的 orphan spend，觸發 **re-settlement** 或 merge 至下一週期（需 spec 確認）。
- **驗證方式**：整合測試 — 模擬 settlement 進行中 commit spend（paid_at 在週期內），assert 該 M 計入該週期 avgM；retry 路徑同測。

#### P1-2: Spend 路徑以 payment 日初始化結算錨點

- **涉及檔案**：`JdbcMembershipSpendCoordinator.java` > `ensureSettlementAnchor`（L111–127）
- **根因**：Spend coordinator 在 `next_settlement_at IS NULL` 時以 `paidAt` 推導 settlement day，繞過 join-tracking 權威來源。
- **修復方案**：
  1. **移除** spend 路徑的 `ensureSettlementAnchor`；僅 join listener / `MembershipJoinService` 初始化 anchor。
  2. 若需支援「先付款後 join」：保留 spend 寫入但 **不** 設 anchor；`next_settlement_at null` 的用戶 skip settlement（spec 已有 EC）。
  3. 可選 admin backfill script 為既有成員補 `earliest_guild_join_at`（follow-up）。
- **驗證方式**：測試 — 無 join 記錄時付款不應設 `next_settlement_at`；有 join 後 anchor 來自 join 日。

#### P1-3: 更早 join 無法覆寫 payment 初始化的 `next_settlement_at`

- **涉及檔案**：`JdbcMembershipRepository.java` > `mergeEarliestGuildJoin`（L103–137）
- **根因**：`next_settlement_at` 只在 NULL 時更新；payment-first 場景下 join 更新 earliest 但不重算 next。
- **修復方案**：當新 join 日 **嚴格早於** 目前 anchor 推導基準日時，一併重算 `settlement_day_of_month` 與 `next_settlement_at`（僅限 `last_settlement_at IS NULL` 的未結算用戶）。邏輯可抽取至 `MembershipJoinService` / domain。
- **驗證方式**：整合測試 — payment 設 anchor → 更早 join event → assert `next_settlement_at` 對齊 join 日。

#### P1-4: 首次結算 periodStart 回退 EPOCH

- **涉及檔案**：`MembershipPeriodBounds.java` > `resolvePeriodStart`（L29–37）
- **根因**：無 join 時 periodStart=EPOCH，配合 payment-only anchor 會 over-count。
- **修復方案**：與 P1-2/P1-3 連動 — 無 `earliest_guild_join_at` 且無 `last_settlement_at` 時 **skip settlement** 或 periodStart = `next_settlement_at` 減一個月（需與 design 對齊）。不應 silently 使用 EPOCH。
- **驗證方式**：seed 僅有 payment anchor、無 join → settle 應 skip 或 period  bounded；不應 SILVER+ 因 epoch 累計。

#### P1-5: 結算吞吐硬上限 1000/小時

- **涉及檔案**：`MembershipSettlementScheduler.java`（L17–19, L73–91）
- **根因**：`MAX_BATCHES_PER_TICK=10` × `SETTLEMENT_BATCH_LIMIT=100` 後停止；`scheduleWithFixedDelay(3600s)` 無積壓追趕。
- **修復方案**：
  1. Tick 內 **while** drain 直到 batch 空或合理時間上限（如 55 分鐘）。
  2. 或縮短 interval / 動態 backoff；記錄 backlog metric。
  3. Grant 可 async 化以縮短 per-user settle 時間。
- **驗證方式**：單元測試 mock 1500 due users → 同一 tick 或連續 tick 內全部 settle；staging 結算日壓測。

### P2 修復

#### P2-1: 法幣-only 商品缺少會員價確認頁

- **涉及檔案**：`ShopSelectMenuHandler.java` > buy select handler（L112–125）；`handlePayWithFiat`（L234–256）
- **根因**：僅 currency-only 走 `showPurchaseConfirmOnEdit`；fiat-only / dual-price fiat 直接 defer 建單。
- **修復方案**：法幣路徑先 `quoteEscortPrice`，對 escort-linked 商品展示與 currency 相同格式的確認 embed（含 `EscortPriceQuote.formatFiatPriceLine()`），確認後再 `processDeferredFiatOrder`。
- **驗證方式**：`ShopSelectMenuHandlerTest` / `ShopViewTest` — fiat-only escort 商品 assert embed 含「會員價 NT$…（原價 NT$…）」。

#### P2-2: Settlement 與 token grant 非原子 saga

- **涉及檔案**：`MembershipSettlementService.java`（L48–63）；`MembershipTokenGrantService.java`
- **根因**：Tier commit 與 grant 分離；event 先於 grant。
- **修復方案**：
  1. 接受 eventual consistency 但 panel 監聽 `GameTokenChangedEvent` 刷新餘額（已有）。
  2. 或 grant 完成後再 publish tier event（trade-off：panel tier 延遲）。
  3. 確保 grant retry 在 tick 首尾執行（已有）並加 backlog metric。
- **驗證方式**：整合測試 grant 失敗 → retry 成功 → 餘額正確；tier event 與 token event 順序文件化。

#### P2-3: Advisory lock 持鎖過長

- **涉及檔案**：`JdbcMembershipSettlementTickGuard.java`；`MembershipSettlementScheduler.java`
- **根因**：整 tick 持鎖。
- **修復方案**：縮小 critical section — 僅 settlement batch loop 持鎖；retry 移出鎖外或使用 per-phase lock。或 lease + heartbeat。
- **驗證方式**：多實例測試 — 長 tick 時 secondary 可在 retry phase 執行（若設計允許）。

#### P2-4: Retry queue 無 drain loop

- **涉及檔案**：`MembershipSpendRetryService.java`；`MembershipTokenGrantService.java`
- **根因**：每 tick 固定 LIMIT 一次。
- **修復方案**：仿 settlement `do-while` drain until empty or cap；spend retry 加 max attempts / dead letter。
- **驗證方式**：enqueue 120 pending spends → 單 tick 清完或明確 backlog log。

#### P2-5: M fallback 優先 order list price

- **涉及檔案**：`MembershipSpendService.java` > `fallbackListPrice`（L124–135）
- **根因**：實作選擇 frozen order 價優於 live product 價。
- **修復方案**：依 spec 改為 `product.fiatPriceTwd()` 優先；或更新 spec/design 明確「retry 以 order 快照為準」並保留現行邏輯。
- **驗證方式**：調整 `MembershipSpendServiceTest.shouldPreferOrderListPriceWhenCatalogMissing` 與 spec 對齊。

#### P2-6: 缺少 spend 失敗 metric

- **涉及檔案**：`MembershipSpendService.java`；`FiatOrderPostPaymentWorker.java`
- **根因**：僅 LOG.error。
- **修復方案**：加入 counter（如 `membership_spend_record_failure_total`）於 return false 與 enqueue 路徑。
- **驗證方式**：單元測試 mock meter；或 log 結構化欄位供 Loki 告警。

#### P2-7: 死掉的 repository settlement API

- **涉及檔案**：`MembershipRepository.java`；`JdbcMembershipRepository.java`
- **根因**：邏輯遷移至 coordinator 後未清理 port。
- **修復方案**：刪除 `saveSettlementResult` / `ensureSettlementAnchor` 或標 `@Deprecated` 並導向 coordinator；確保單一 write path。
- **驗證方式**：grep 零 reference；`make verify`。

#### P2-8: 分層反轉（service↔coordinator）

- **涉及檔案**：`MembershipSettlementService.java`；`JdbcMembershipSettlementCoordinator.java`
- **根因**：未抽象 settlement port；日曆邏輯在 service 包。
- **修復方案**：引入 `MembershipSettlementCoordinator` interface；將 `clampDayOfMonth` / `advanceNextSettlementAt` 移至 `domain` 或 `MembershipPeriodBounds`。
- **驗證方式**：coordinator 不再 import `ltdjms.discord.membership.services.*`。

#### P2-9: Panel 跨模組直接讀 repository

- **涉及檔案**：`MemberInfoFacade.java`
- **根因**：未提供 membership read service。
- **修復方案**：新增 `MembershipQueryService.getPanelSummary(userId)` 封裝 period sum + tier；panel 只依賴該 service。
- **驗證方式**：`MemberInfoFacade` 不再 import membership persistence。

#### P2-10: Panel 更新效能

- **涉及檔案**：`UserPanelUpdateListener.java`；`PanelSessionManager.java`
- **根因**：單執行緒 queue；tier 更新掃全表 session。
- **修復方案**：per-user 合併更新（debounce）；`updatePanelsByUser` 用 index key `guildId:userId` 而非 suffix scan。
- **驗證方式**：模擬 100 concurrent panel + 50 grant events → 更新延遲 < 閾值。

#### P2-11: Spend retry 無 claim pattern

- **涉及檔案**：`MembershipSpendRetryService.java`
- **根因**：未使用 `FOR UPDATE SKIP LOCKED` / processing_at。
- **修復方案**：對齊 fiat fulfillment claim/release；或文件限定僅 advisory-lock tick 內呼叫。
- **驗證方式**：雙執行緒 retry 測試無 double insert。

### P3 改善

#### P3-1: 移除或採用 `resolveEscortProductPrice`

- **涉及檔案**：`MembershipPricingService.java`（L63–66）
- **根因**：Spec alias 無 caller。
- **修復方案**：刪除 alias，或將 `FiatOrderService` 等 caller 改為 spec 命名。
- **驗證方式**：grep 一致命名。

#### P3-2: 同步 `schema.sql` 與 Flyway

- **涉及檔案**：`src/main/resources/db/schema.sql`
- **根因**：`fiat_order` membership 欄位僅註解。
- **修復方案**：補齊 `list_price_twd`, `charged_amount_twd`, `membership_tier_at_order` 定義。
- **驗證方式**：schema-only 整合測試可跑 shop membership 欄位。

#### P3-3: 整合測試 TRUNCATE 補齊

- **涉及檔案**：`PostgresIntegrationTestBase.java`
- **根因**：缺 `membership_token_grant_log`。
- **修復方案**：`TRUNCATE membership_token_grant_log CASCADE`。
- **驗證方式**：`MembershipSettlementIntegrationTest` 連跑無泄漏。

#### P3-4: 補 `MembershipTierChangedEvent` panel 測試

- **涉及檔案**：`UserPanelUpdateListenerTest.java`
- **根因**：僅測 game token / balance。
- **修復方案**：新增 async await + verify `updatePanelsForUser`。
- **驗證方式**：單元測試綠。

#### P3-5: Atlas 邊補全

- **涉及檔案**：`resources/project-architecture/atlas/atlas.index.yaml`
- **根因**：缺 event-bus、spend-retry 等邊。
- **修復方案**：依 runtime 依賴補 edge；`apltk architecture validate`。
- **驗證方式**：validate pass。

#### P3-6: 文件化 `SKIPPED_NO_GUILD`

- **涉及檔案**：migration comment 或 `membership-benefits-ui/design.md`
- **根因**：V033 COMMENT 未列舉。
- **修復方案**：追加 COMMENT 或 design 一節。
- **驗證方式**：文件 review。

#### P3-7: 限制 `findDueForSettlement(Instant)` 

- **涉及檔案**：`JdbcMembershipRepository.java`
- **根因**：預設無 limit。
- **修復方案**：移除 overload 或強制 max limit；tests 改用 2-arg。
- **驗證方式**：編譯 + 測試。

---

## 六維度審查結論

| 維度 | 結論 |
|------|------|
| 無幻覺代碼 | **PASS** — 類別、SQL、DI、事件均存在且對齊 V029–V034 |
| 無冗余代碼 | **PARTIAL** — 死掉的 repository settlement API、spec alias、重複 SUM SQL |
| 無 spec 偏移 | **FAIL** — 結算錨點來源、法幣確認頁、M fallback 字面 |
| 無 spec 遺漏 | **PARTIAL** — 核心流程完整；payment-discount R3 法幣確認頁未完全落地 |
| 無架構瑕疵 | **FAIL** — settlement/spend txn 邊界、分層反轉、panel repo bypass |
| 無性能隱患 | **PARTIAL** — 1000/h 結算上限、長 advisory lock、panel 單執行緒；現階段單實例可接受，成長後需處理 |

---

## 建議修復順序

1. **P1-2 → P1-3 → P1-4**（結算錨點一致性，阻斷錯誤週期）
2. **P1-1**（M 漏計，資料正確性）
3. **P2-1**（UI spec 合規）
4. **P1-5 / P2-3 / P2-4**（規模與恢復能力）
5. **P2-7～P2-9**（架構清理）
6. **P3**（測試、文件、Atlas）

完成 P1 與 P2-1 後重新執行 `/qa` 驗收。
