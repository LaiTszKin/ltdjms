 # Code Review Report

- **Spec**: membership-ui batch (`user-panel-membership-info`, `shop-member-discount-display`, `admin-membership-management`)
- **Date**: 2026-05-25
- **Reviewer**: QA Agent
- **Verdict**: Needs Attention

---

## 判決說明

**Verdict**: Needs Attention

三份 spec 的核心業務行為已落地，`make verify` 全綠（2062 tests, 0 failures）。個人面板新欄位、商店劃線價、管理員調整消費/等級的主流程均可運作，且無幻覺代碼。

尚未達 Ready to Merge 的原因：

1. **P1 性能**：商店 batch quote 對同一用戶重複查 membership row，購買選單路徑還會對全量商品 quote，大 catalog 下有可感知延遲風險。
2. **P2 功能/一致性**：admin DEDUCT 後 period sum 顯示與「距下一等級」計算未在 read model 統一 clamp（違反 batch 預設 Q2）；admin 調整消費後 open user panel 不會即時刷新（Goal 部分未達）。
3. **P2 驗收缺口**：`AdminPanelButtonHandler` 會員管理互動流程缺少 handler 級測試；architecture atlas overlay 未合併至 `resources/project-architecture/`。

---

## 業務需求追溯

### user-panel-membership-info

| 需求 | 狀態 | 證據 | 缺口 / 不確定性 |
|------|------|------|----------------|
| R1 加入日期 | ✅ 滿足 | `UserPanelView.appendJoinDate()` L105–112；`<t:epoch:D>` / 「尚未記錄」 | — |
| R2 距下一等級剩餘 M | ⚠️ 部分 | `MembershipQueryService.getPanelSummary()` L54–56；`UserPanelView.appendRemainingToNextTier()` L131–141 | admin DEDUCT 使 ledger sum 為負時，user panel 可顯示負 M，remaining 亦未以 clamped spend 計算（見 P2-1） |
| R3 目前權益 | ✅ 滿足 | `appendCurrentBenefits()` L115–121；NONE 提示 L32–34 | — |
| Edge: tier 變更刷新 | ✅ 滿足 | `UserPanelUpdateListener` L51–53 訂閱 `MembershipTierChangedEvent` | spend 調整不會觸發刷新（admin batch Goal） |

### shop-member-discount-display

| 需求 | 狀態 | 證據 | 缺口 / 不確定性 |
|------|------|------|----------------|
| R1 列表 embed 劃線價 | ✅ 滿足 | `EscortPriceQuote.formatFiatEmbedLine()`；`ShopView.formatFiatListLine()` L351–355；handlers batch quote | — |
| R2 購買選單精簡價 | ⚠️ 部分 | `ShopView.buildPriceDescription()` L358–389 產生 compact 文字、≤100 字元 | 未委派 `EscortPriceQuote.format*SelectDescription()`（contract 偏移）；有餘裕時未含原價（P3-1） |
| R3 確認頁格式統一 | ✅ 滿足 | confirm embeds 使用 embed formatters；`FiatOrderServiceTest` 已對齊劃線格式 | — |
| Edge: pricing 不可用 fallback | ⚠️ 部分 | `ShopService.quoteEscortPrices()` L63–64 回傳空 map；`ShopView.resolveQuote()` fallback list price | 缺少 escort 商品 + 空 quotes 的 strikethrough 負向測試（P3-5） |

### admin-membership-management

| 需求 | 狀態 | 證據 | 缺口 / 不確定性 |
|------|------|------|----------------|
| R1 查看會員詳情 | ✅ 滿足 | `MembershipAdminService.getDetail()`；`AdminPanelViewFactory.formatMembershipDetailBody()` L576–600 | 文案與 user panel 不完全一致（P3-2） |
| R2 調整本週期 M | ✅ 滿足 | `adjustPeriodSpend()` ADD/DEDUCT/SET L78–85；`insertAdminAdjust` + unique reference | clamp 顯示僅在 admin view 局部處理（P2-1）；open user panel 不刷新（P2-2） |
| R3 設定等級 | ✅ 滿足 | `setTier()` 更新 tier + bronze flag + event L117–163 | 下次 settlement 覆寫行為已於 footer 提示 |
| R4 Admin UI | ⚠️ 部分 | `AdminPanelButtonHandler` L2536–2803 完整流程；`AdminPanelCommandHandlerTest` 僅驗主選單按鈕 | handler 互動流程無 unit test（P2-3） |
| Goal: 即時刷新 open panel | ⚠️ 部分 | tier 變更經 event 刷新 user panel | spend 調整無 domain event；shop 依 quote-on-interaction，open shop embed 不主動刷新（可接受，見 P3-9） |

### Batch 級 outcome

| 項目 | 狀態 | 證據 | 缺口 |
|------|------|------|------|
| `make verify` | ✅ | BUILD SUCCESS, 2062 tests | — |
| Atlas overlay validate | ✅ | spec 內 `architecture_diff/` 存在 | 未 promote 至 `resources/project-architecture/`（P2-4） |
| Clarification Q1–Q3 | ⏸ 待確認 | 實作採 batch 預設路徑 | 用戶尚未答覆 admin spec Q1–Q3 |

---

## 發現的問題

### P0 — 嚴重缺陷

（無）

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 商店 batch quote 對每個 escort 商品重複 `findByUserId` | 購買選單載入全量 catalog 時 DB 查詢 = escort 商品數；大 guild 下延遲與 DB 壓力線性增長 | `MembershipPricingService.java` | L41–48 |
| 1（續） | 同上，由 `ShopService.quoteEscortPrices` 迴圈觸發 | 同上 | `ShopService.java` | L67–73 |
| 1（續） | `showBuyMenu` 對全量 purchasable products quote | 放大 P1-1；catalog 越大越慢 | `ShopButtonHandler.java` | L173–184 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Period spend 未在 read model 統一 clamp | admin DEDUCT 後 admin embed 顯示 M=0 但「距下一等級」仍用 raw sum 計算；user panel 可顯示負 M 與錯誤 remaining | `MembershipQueryService.java` | L47–56 |
| 1（續） | Admin view 僅局部 clamp 顯示 | admin / user panel 數字不一致 | `AdminPanelViewFactory.java` | L583–589 |
| 1（續） | User panel 顯示 raw spend | 同上 | `UserPanelView.java` | L124–128 |
| 2 | Admin 調整消費後不刷新 open user panel | Goal「即時刷新用戶 open panel」在 spend 路徑未達；用戶需重開 `/user-panel` | `MembershipAdminService.java` | L63–115 |
| 2（續） | Listener 未訂閱 spend 變更 | 同上 | `UserPanelUpdateListener.java` | L44–54 |
| 3 | AdminPanelButtonHandler 會員管理流程缺少測試 | tasks T4.3/T4.4 標記完成但無 handler 級 mock 測試；回歸風險 | `AdminPanelButtonHandler.java` | L2536–2803 |
| 4 | Architecture atlas 未合併至 canonical 資源 | batch outcome 要求 promote；`resources/project-architecture/` 無 `admin-membership` / `quoteEscortPrices` | `resources/project-architecture/` | — |
| 5 | `EscortPriceQuote` select formatters 未被 shop UI 使用 | contract 定義的方法僅在 unit test 出現；`ShopView` 重複實作 compact 邏輯 | `EscortPriceQuote.java` | L47–63 |
| 5（續） | 同上 | 維護時兩處邏輯易漂移 | `ShopView.java` | L358–417 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Select menu 有空間時未顯示原價 | 與 R2 範例 `90幣 (原100,9折)` 不完全一致；不影響扣款 | `ShopView.buildPriceDescription()` | L358–389 |
| 2 | Admin「距下一等級」文案缺「還需」 | 與 user panel 用語不一致 | `AdminPanelViewFactory.formatMembershipDetailBody()` | L587–589 |
| 3 | `getDetail` 重複查 membership row | 每次 admin 詳情 +1 冗餘 DB round-trip | `MembershipAdminService.getDetail()` | L53–58 |
| 4 | 缺少 DEDUCT / bronze flag 正向 / tier 未變不發 event 測試 | checklist 覆蓋不完整 | `MembershipAdminServiceTest.java` | — |
| 5 | Shop pricing 不可用 + escort 商品 fallback 測試不足 | edge case 僅測空 product list | `ShopServiceTest.java` / `ShopViewTest.java` | — |
| 6 | `MembershipManagementFacade` 為薄 pass-through | 設計要求對齊 `CurrencyManagementFacade` 的 read/write 組合未落實 | `MembershipManagementFacade.java` | L12–38 |
| 7 | `buildBuyMenu` / `buildSearchBuyMenu` 重複 | 僅 customId 不同 | `ShopView.java` | L131–142, L234–245 |
| 8 | Admin handler 六處重複 `getMembershipDetail` 樣板 | 可讀性與維護成本 | `AdminPanelButtonHandler.java` | 多處 |
| 9 | Open shop embed 在 tier 變更後不主動刷新 | 下次互動 quote 正確；僅 UX 延遲 | — | — |

---

## 審查維度摘要

- **幻覺代碼**: 無發現 — 所有引用 API、JDA 元件、domain event 均存在且可編譯
- **冗余代碼**: 4 個 finding（P2-5 select formatters 死碼；P3-6–8 重複樣板/方法）
- **實作偏移**: 3 個 finding（P2-2 Goal 刷新；P2-5 contract wiring；P3-1 R2 範例）
- **實作遺漏**: 2 個 P2（handler tests、atlas promote）+ 4 個 P3 測試缺口
- **架構瑕疵**: 2 個 P2（read model clamp；facade 邊界薄弱）
- **性能隱患**: 1 個 P1（N× membership lookup + 全量 buy menu quote）

---

## 解決方案

### P1 修復

#### P1-1: 商店 batch quote 重複 membership 查詢

- **涉及檔案**：`MembershipPricingService.java` > `quoteEscortPrice()`（L41–48）；`ShopService.java` > `quoteEscortPrices()`（L61–83）
- **根因**：tier 為 user-scoped，但每個 product 獨立呼叫 `findByUserId`。
- **修復方案**：新增 `quoteEscortPrices(long userId, List<Product> products, long guildId)` 於 `MembershipPricingService`，先 resolve tier 一次，再 in-memory 計算各 product quote；`ShopService` 改呼叫 batch API。
- **驗證方式**：新增 test：3 個 escort products → mock repository `findByUserId` 僅被呼叫 1 次；`make verify`。

#### P1-1b: 購買選單全量 catalog quote

- **涉及檔案**：`ShopButtonHandler.java` > `showBuyMenu()`（L173–184）
- **根因**：`getAllPurchasableProducts` 無分頁即 quote 全列表。
- **修復方案**：短期：配合 P1-1 batch tier 將 DB 降為 O(1)。中期：buy menu 分頁或延遲 quote（選中後再 quote）。至少加 catalog size 上限或 warning log。
- **驗證方式**：integration/manual：50+ escort SKU guild 量測 buy menu 延遲；unit test 驗證 quote 次數上界。

### P2 修復

#### P2-1: Period spend 顯示與 remaining 計算不一致

- **涉及檔案**：`MembershipQueryService.java` > `getPanelSummary()`（L47–66）；`MembershipPanelSummary.java`（L24–28, L32–37）；`AdminPanelViewFactory.java`（L583–589）；`UserPanelView.java`（L124–128）
- **根因**：raw ledger sum 直接進 summary；admin view 僅 display 層 `Math.max(0, …)`，remaining/progress 仍用 raw 值。
- **修復方案**：在 `MembershipQueryService` 引入 `displayPeriodSpendM = Math.max(0, periodSpendM)`，summary 的 `periodSpendListPriceM` 存 display 值（或新增欄位並統一 formatter）；`computeRemaining` / `nextTierProgressRatio` 基於 display spend。移除 view 層 ad hoc clamp。
- **驗證方式**：test：ledger sum = -5000, threshold = 14000 → user/admin 均顯示 M=0、remaining=14000、progress=0%。

#### P2-2: Admin spend 調整不刷新 open user panel

- **涉及檔案**：`MembershipAdminService.java` > `adjustPeriodSpend()`（L105 後）；`UserPanelUpdateListener.java`（L44–54）
- **根因**：僅 tier 變更有 `MembershipTierChangedEvent`；spend 調整無 event。
- **修復方案**：新增 `MembershipPeriodSpendChangedEvent(userId)`，adjust 成功後 publish；`UserPanelUpdateListener` 訂閱並呼叫 `updatePanelsForUser(userId)`（與 tier 路徑相同）。
- **驗證方式**：unit test：adjust spend → verify event published → listener refreshes panel mock。

#### P2-3: 補 AdminPanelButtonHandler 會員管理測試

- **涉及檔案**：新增 `AdminPanelButtonHandlerMembershipTest.java`；`AdminPanelButtonHandler.java`
- **根因**：tasks 標記 T4.3/T4.4 完成但僅有 service/facade 測試。
- **修復方案**：mock `AdminPanelService`，覆蓋：user select → detail embed；spend mode + modal submit success/error；tier select + confirm；非 admin ephemeral 拒絕。
- **驗證方式**：`mvn test -Dtest=AdminPanelButtonHandlerMembershipTest`。

#### P2-4: Promote architecture atlas

- **涉及檔案**：`docs/plans/2026-05-25/membership-ui/architecture_diff/` → `resources/project-architecture/`
- **根因**：batch 完成後未執行 atlas 合併。
- **修復方案**：依 `update-project-html` 流程將 validated overlay merge 至 canonical atlas；重跑 `apltk architecture validate --project .`。
- **驗證方式**：`grep admin-membership resources/project-architecture/` 有結果；validate OK。

#### P2-5: 統一 select menu 價格格式化

- **涉及檔案**：`EscortPriceQuote.java`（L47–63）；`ShopView.java`（L358–417）
- **根因**：shop UI 未使用 contract 方法，inline 重複邏輯。
- **修復方案**：在 `EscortPriceQuote` 新增 `formatSelectDescription(Product)` 處理 dual-price + truncation；`ShopView.buildPriceDescription` 委派；刪除或合併未使用的單價 select methods。
- **驗證方式**：現有 `EscortPriceQuoteTest` + `ShopViewTest` 綠；無 production-dead formatter。

### P3 改善

#### P3-1: Select menu 可選顯示原價

- **涉及檔案**：`ShopView.buildPriceDescription()` 或 `EscortPriceQuote` select formatter
- **根因**：實作直接省略原價，未利用 ≤100 字元餘裕。
- **修復方案**：若 `currentLength + originalPart <= 100`，append `(原{listPrice},…)`；否則維持現有 truncation。
- **驗證方式**：`ShopViewTest` 短描述 case 含 `(原100,9折)`。

#### P3-2: 統一 admin/user「距下一等級」文案

- **涉及檔案**：`AdminPanelViewFactory.formatMembershipDetailBody()` L587–589
- **修復方案**：改為 `還需 {remainingM} M`，與 `UserPanelView.appendRemainingToNextTier()` 一致。

#### P3-3: 消除 getDetail 重複查詢

- **涉及檔案**：`MembershipAdminService.getDetail()`；`MembershipPanelSummary` 或 `MembershipQueryService`
- **修復方案**：`getPanelSummary` 或新 `getAdminDetail` 一次 fetch 含 `hasQualifyingBronzeOrder`。

#### P3-4: 補 admin service 邊界測試

- **涉及檔案**：`MembershipAdminServiceTest.java`
- **修復方案**：新增 DEDUCT 負 delta、BRONZE 升級設 bronze flag、同 tier 不發 event 三個 case。

#### P3-5: Shop fallback strikethrough 負向測試

- **涉及檔案**：`ShopViewTest.java`
- **修復方案**：escort product + `Map.of()` quotes → assert 含 list price、不含 `~~`。

#### P3-6–8: 架構/可讀性改善

- Facade 將 `getDetail` read 組合移入 `MembershipManagementFacade`；extract buy menu builder helper；extract `requireMembershipDetail` handler helper。低優先，不阻塞合併。

---

## 總結

| 嚴重度 | 數量 |
|--------|------|
| P0 | 0 |
| P1 | 1（含 buy menu 放大效應） |
| P2 | 5 |
| P3 | 9 |

**結論**：本 batch **功能主幹已交付且測試全綠**，可進入人工驗收與 staging 測試。建議在正式合併前至少處理 **P1-1**（batch tier quote）與 **P2-1**（spend clamp 一致性）；**P2-2**（spend 刷新）與 **P2-3**（handler tests）視產品對 Goal 的嚴格程度決定是否同批修復。**P2-4** atlas promote 可與 `/archive` 或 docs 維護一併完成。
