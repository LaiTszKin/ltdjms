# Code Review Report

- **Spec**: membership-ui (user-panel-membership-info · shop-member-discount-display · admin-membership-management)
- **Date**: 2026-05-25
- **Reviewer**: QA (fresh review post `/fix`)
- **Verdict**: Needs Attention

---

## 判決說明

**Verdict**: Needs Attention

- **Ready to Merge** — 所有測試通過，無 P0/P1 問題，建議項目可選
- **Needs Attention** — 有 P2 問題或重要建議值得處理
- **Needs Work** — 有 P0/P1 問題或未滿足的需求，必須先修復

本次審查在 `/fix` 之後重新執行，**未參考上一份 code review report**。核心業務需求（個人面板三項資訊、商店會員價展示、管理員調整 M/等級）均已落地；`make verify` 全綠（2078 tests, 0 failures）。尚餘 P2 級架構/即時刷新/安全加固與 P3 級可維護性建議，建議合併前或下一迭代處理。

---

## 業務需求驗收

### user-panel-membership-info

| 需求 | 狀態 | 證據 | 缺口 / 不確定性 |
|------|------|------|----------------|
| R1.1 加入日期 `<t:epoch:D>` | ✅ 滿足 | `UserPanelView.appendJoinDate` L105–112 | — |
| R1.2 無 join →「尚未記錄」 | ✅ 滿足 | `UserPanelView` L107–110；`MembershipQueryService.noneSummary` L90 | — |
| R2.1 remainingM 公式 + clamp | ✅ 滿足 | `MembershipPanelSummary.computeRemaining` L37–41；`clampDisplaySpend` L32–34 | 顯示用 clamp；ledger 仍可為負（admin Q2 預設） |
| R2.2 NONE → 白銀門檻 | ✅ 滿足 | `MembershipQueryService.noneSummary` L83–92 | — |
| R2.3 最高等級文案 | ✅ 滿足 | `UserPanelView.appendRemainingToNextTier` L138–139 | — |
| R2.4 保留進度行 | ✅ 滿足 | `UserPanelView.appendNextTierProgress` L144–157 | — |
| R3.1–R3.3 權益區塊 | ✅ 滿足 | `UserPanelView.appendCurrentBenefits` L115–121；NONE hint L78–87 | — |
| tier/spend 變更刷新 open panel | ✅ 滿足 | `UserPanelUpdateListener` L52–56；`MembershipPeriodSpendChangedEvent` | spend 刷新為 `/fix` 新增，超出原 spec 但符合 UX |
| 查詢失敗 → NONE hint 不 crash | ⚠️ 部分 | `UserPanelView` L69–70 支援 null summary | `MemberInfoFacade.getMembershipSummary` 永不回 null；repository 拋錯時走 `Result.err` 而非 hint（P3） |

### shop-member-discount-display

| 需求 | 狀態 | 證據 | 缺口 / 不確定性 |
|------|------|------|----------------|
| R1.1–R1.4 列表 embed 劃線價 | ✅ 滿足 | `ShopView.formatFiatListLine` L334–338；batch quote in `ShopCommandHandler`/`ShopButtonHandler` | — |
| R2.1–R2.2 select 精簡價 | ✅ 滿足 | `EscortPriceQuote.formatSelectDescription` L49–88 | 合併為單一方法，符合 R2 語意 |
| R3.1–R3.2 確認頁格式 + charged 不變 | ✅ 滿足 | `ShopView.buildPurchaseConfirmEmbed` 等；`CurrencyPurchaseService`/`FiatOrderService` 結帳重 quote | — |
| pricing null → fallback 原價 | ✅ 滿足 | `ShopService.quoteEscortPrices` L63–64 | 單品 `quoteEscortPrice` 仍 throw（P2-3） |
| open shop 在 tier 變更後即時刷新 | ⚠️ 部分 | 下次 paginate/reopen 會重 quote | 已開啟的 shop embed 不訂閱 `MembershipTierChangedEvent`（P2-2） |

### admin-membership-management

| 需求 | 狀態 | 證據 | 缺口 / 不確定性 |
|------|------|------|----------------|
| R1.1–R1.3 詳情 + 權限 | ✅ 滿足 | `AdminPanelButtonHandler` membership 路由；`MembershipQueryService.getAdminDetail` | 權限同既有 admin（Administrator/owner），非 spec BDD 的 Manage Server |
| R2.1–R2.6 ADMIN_ADJUST ledger | ✅ 滿足 | `MembershipAdminService.adjustPeriodSpend` L46–98；`JdbcMembershipSpendCoordinator.insertAdminAdjust` | SET 模式 read-sum → write 非原子（P2-1） |
| R3.1–R3.3 設 tier + event | ✅ 滿足 | `MembershipAdminService.setTier` L101–154 | Q1 永久 override 未實作（spec Out of Scope，預設 settlement 可覆寫） |
| R4.1–R4.2 UI 流程 + session | ✅ 滿足 | `AdminPanelViewFactory` membership embeds；`MembershipSessionState` | session 未掛入 `AdminPanelSessionManager` TTL（P2-4） |
| spend 調整刷新 user panel | ✅ 滿足 | `MembershipPeriodSpendChangedEvent` → `UserPanelUpdateListener` | — |
| Clarification Q1–Q3 | ⏳ 待確認 | coordination 預設已採用 | Q1/Q3 未答覆不阻塞；Q2 clamp 顯示已實作 |

### Batch 級驗收

| 項目 | 狀態 | 證據 |
|------|------|------|
| `make verify` | ✅ PASS | 2078 tests, 0 failures |
| Architecture Atlas validate | ❌ FAIL | `apltk architecture validate resources/project-architecture` — edges 引用 unknown feature slug（repo 級問題，非本 batch 獨有） |
| docs/features 同步 | ✅ | `membership-tiers.md` L60–70；`administration.md` L68–90；`shop-and-payment.md` L42–57 |

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| — | 無 | — | — | — |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| — | 無 | — | — | — |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | SET 模式調整消費：先讀 period sum 再 insert delta，read/write 非同一交易 | 併發 fiat 入帳或另一 admin 調整時，「設為 X M」可能偏離預期 | `MembershipAdminService.java` | L62–68, L156–165 |
| 2 | 已開啟的 shop embed 不訂閱 `MembershipTierChangedEvent` | admin/settlement 升級後，已開 shop 仍顯示舊折扣價直到用戶翻頁或重開；與 admin spec Goal「即時刷新商店折扣」語意部分不符 | `ShopButtonHandler.java` 等 shop commands | — |
| 3 | `EscortPriceQuote` 含 Discord UI 格式化邏輯，置於 membership service 層 | 分層邊界模糊；shop 變更展示需改 membership 模組 | `EscortPriceQuote.java` | L29–149 |
| 4 | `membershipSessionStates` 獨立於 `AdminPanelSessionManager` 生命週期 | panel session 過期後 wizard state 可能殘留；長期 admin 使用有記憶體累積風險 | `AdminPanelButtonHandler.java` | L178–179, L2539 |
| 5 | Admin 會員詳情 embed 未訂閱 membership domain events | admin A 查看成員 B 時，B 的 tier/spend 被其他流程改變，詳情不會自動刷新 | `AdminPanelUpdateListener.java` | L32–42 |
| 6 | `ShopService.quoteEscortPrices` catch-all 回傳空 map | DB 暫障時靜默降級為原價，會員可能以為無折扣 | `ShopService.java` | L73–82 |
| 7 | Architecture Atlas validate 未通過 batch checkpoint | coordination 要求 validate 通過；目前 repo 級 slug 解析失敗 | `resources/project-architecture/atlas/atlas.index.yaml` | — |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Modal submit 從 modalId 解析 `userId`，未與 session `selectedUserId` 比對 | 惡意 admin 可篡改 modal ID 調整他人 spend（與 balance modal 同模式） | `AdminPanelButtonHandler.java` | L2682–2713 |
| 2 | `UserPanelUpdateListener` 事件處理 async 不一致 | `BalanceChangedEvent` 同步、`MembershipTierChangedEvent` 走 executor，event bus 延遲行為不一致 | `UserPanelUpdateListener.java` | L46–56 |
| 3 | Buy menu 載入全 guild 可購商品 + batch quote | 大型 catalog 每次點「購買」O(n) 查詢；已有 ≥50 warning 但無分頁 | `ShopButtonHandler.java` | L174–192 |
| 4 | Admin membership wizard 每步重查 `getAdminDetail` | 每次 mode/tier select 約 2 次 DB round-trip | `AdminPanelButtonHandler.java` | L2610+ |
| 5 | `formatFiatPriceLine`/`formatCurrencyPriceLine` 為 embed 方法別名 | 輕微 API 冗余 | `EscortPriceQuote.java` | L90–96 |
| 6 | `AdminPanelViewFactory.buildMembershipSpendAdjustEmbed` 重複 `Math.max(0, …)` | summary 已 clamp，冗余防禦 | `AdminPanelViewFactory.java` | L537 |
| 7 | 缺少 confirm tier 成功路徑的 handler 測試 | 回歸覆蓋不完整（仅有 no-session 測試） | `AdminPanelButtonHandlerMembershipTest.java` | — |
| 8 | membership 查詢失敗未 fallback 至 NONE hint | spec edge case 防禦路徑未完整實作 | `MemberInfoFacade.java` | L71–72 |

---

## 審查維度摘要

- **幻覺代碼**: 無發現 — 所有引用類別/方法（`insertAdminAdjust`、`MembershipPeriodSpendChangedEvent`、`formatSelectDescription` 等）均存在且測試可編譯
- **冗余代碼**: 2 個 finding — `EscortPriceQuote` 別名方法；admin embed 重複 clamp
- **實作偏移**: 1 個 finding — shop open embed 不即時刷新 tier 折扣（P2-2）
- **實作遺漏**: 2 個 finding — atlas validate checkpoint（P2-7）；membership 查詢失敗 fallback（P3-8）
- **架構瑕疵**: 3 個 finding — EscortPriceQuote 分層（P2-3）；admin session 生命週期（P2-4）；modal session 綁定（P3-1）
- **性能隱患**: 3 個 finding — SET TOCTOU（P2-1）；buy menu 全 catalog（P3-3）；admin 重複查詢（P3-4）

---

## 解決方案

### P0 修復

無 P0 問題。

### P1 修復

無 P1 問題。

### P2 修復

#### P2-1: SET 模式 period spend 併發安全

- **涉及檔案**：`MembershipAdminService.java` > `adjustPeriodSpend`（L62–68）；`JdbcMembershipSpendCoordinator.java` > `insertAdminAdjust`（L128–169）
- **根因**：SET 在 service 層讀 sum，在 repository 層僅 insert 單筆 ledger，中間無 row lock。
- **修復方案**：新增 `insertAdminAdjustSetTarget(userId, guildId, targetM, …)` coordinator 方法：在單一 transaction 內 `SELECT sum … FOR UPDATE`（或鎖 membership row）→ 計算 delta → insert。ADD/DEDUCT 可維持 append-only。
- **驗證方式**：新增 concurrency integration test：thread A 插入 fiat spend，thread B 同時 SET target，assert 最終 sum 符合 SET 語意或明確拒絕。

#### P2-2: Shop embed 不隨 tier 變更刷新

- **涉及檔案**：shop commands（新建 listener 或擴展既有 session 機制）
- **根因**：僅 user panel 訂閱 `MembershipTierChangedEvent`；shop 為 snapshot-at-render。
- **修復方案**：（A）新增 `ShopUpdateListener` + shop session registry（對齊 `UserPanelUpdateListener`）；（B）或在 spec/docs 明確定義「下次互動才更新折扣」並降級 Goal 文案。建議 (A) 若產品要求 live refresh。
- **驗證方式**：unit test mock session + event → assert `editMessageEmbeds` 含新折扣價。

#### P2-3: EscortPriceQuote 格式化邏輯分層

- **涉及檔案**：`EscortPriceQuote.java`（L29–149）；`ShopView.java`
- **根因**：membership service record 承載 Discord 展示規則（`~~`、100 字元 truncate）。
- **修復方案**：`EscortPriceQuote` 保留 list/charged/tier/rate 純資料；將 `format*EmbedLine`、`formatSelectDescription` 移至 `shop/services/ShopPriceFormatter` 或 `ShopView` private helpers。
- **驗證方式**：搬移後既有 `EscortPriceQuoteTest`/`ShopViewTest` 全綠。

#### P2-4: Admin membership session 生命週期

- **涉及檔案**：`AdminPanelButtonHandler.java` > `membershipSessionStates`（L178–179）
- **根因**：state 存 handler 本地 map，僅進入子面板時 clear，未隨 panel session TTL 回收。
- **修復方案**：將 `MembershipSessionState` 併入 `AdminPanelSessionManager` session payload，或在 `clearSession`/TTL callback 移除 entry。
- **驗證方式**：session 過期後 assert wizard state 為空；長時間使用 memory 不線性增長。

#### P2-5: Admin 詳情 embed 訂閱 membership events

- **涉及檔案**：`AdminPanelUpdateListener.java`（L32–42）；`AdminPanelButtonHandler.java` > `refreshMembershipDetailPanel`
- **根因**：僅在 admin 自身 write 成功後 inline refresh。
- **修復方案**：listener 訂閱 `MembershipTierChangedEvent` / `MembershipPeriodSpendChangedEvent`；若 open admin session 的 `selectedUserId` 匹配 event userId，呼叫 refresh。
- **驗證方式**：mock admin session + publish event → verify embed refresh。

#### P2-6: quoteEscortPrices 靜默降級

- **涉及檔案**：`ShopService.java` > `quoteEscortPrices`（L73–82）
- **根因**：catch Exception 回傳 `Map.of()`，UI 顯示原價。
- **修復方案**：回傳 `Result<Map<…>, DomainError>` 或在 handler 層 log + embed footer 警告「會員折扣暫不可用」。
- **驗證方式**：mock pricing throw → assert 警告文案或 Result.err。

#### P2-7: Architecture Atlas validate 失敗

- **涉及檔案**：`resources/project-architecture/atlas/atlas.index.yaml` 及 features/*.yaml
- **根因**：validate 無法解析 edge 中的 feature slug（如 `shop-payment`），可能為 aplt 與 atlas schema 版本不一致。
- **修復方案**：對照 `apltk architecture validate --help` 與 feature yaml `slug` 欄位；修正 index edges 或升級/修復 validate CLI。本 batch overlay 已 promote 至 canonical path。
- **驗證方式**：`apltk architecture validate resources/project-architecture` exit 0。

### P3 改善

#### P3-1: Modal userId 與 session 綁定

- **涉及檔案**：`AdminPanelButtonHandler.java` > `handleMembershipSpendModal`（L2682–2713）
- **根因**：信任 client-supplied modalId 中的 userId。
- **修復方案**：submit 時讀 `membershipSessionStates.get(sessionKey).selectedUserId`，與 parsed userId 比對；不匹配則 ephemeral 拒絕。modalId 僅含 mode。
- **驗證方式**：unit test 篡改 modalId userId → verify service 未被呼叫。

#### P3-2: UserPanelUpdateListener async 一致性

- **涉及檔案**：`UserPanelUpdateListener.java`（L46–56）
- **根因**：balance 同步、membership async。
- **修復方案**：全部走 `panelUpdateExecutor`，或全部同步（擇一）。
- **驗證方式**：event publish 不阻塞其他 listener（integration timing test）。

#### P3-3: Buy menu 大型 catalog 性能

- **涉及檔案**：`ShopButtonHandler.java`（L174–192）
- **根因**：`getAllPurchasableProducts` 全量載入。
- **修復方案**：buy menu 分頁、關鍵字前置、或 cap 25 + 引導搜尋。
- **驗證方式**：benchmark 100+ 商品場景；assert 互動延遲可接受。

#### P3-4: Admin wizard 重複 DB 查詢

- **涉及檔案**：`AdminPanelButtonHandler.java`；`MembershipQueryService.java`
- **根因**：每次 select change 呼叫 `requireMembershipDetail`。
- **修復方案**：user select 後 cache `MembershipAdminDetail` 於 session；adjust/setTier 成功後 invalidate。
- **驗證方式**：mock verify `findByUserId` 呼叫次數下降。

#### P3-5: EscortPriceQuote 別名方法

- **涉及檔案**：`EscortPriceQuote.java`（L90–96）
- **根因**：`formatFiatPriceLine` 僅 delegate 至 `formatFiatEmbedLine`。
- **修復方案**：呼叫點統一使用 embed 方法名，刪除別名；或保留但標 `@Deprecated(forRemoval=true)`。
- **驗證方式**：編譯 + 測試通過。

#### P3-6: Admin embed 重複 clamp

- **涉及檔案**：`AdminPanelViewFactory.java`（L537）
- **根因**：`detail.summary().periodSpendListPriceM()` 已 clamp。
- **修復方案**：移除 `Math.max(0, …)`，直接使用 summary 欄位。
- **驗證方式**：既有 admin view 測試通過。

#### P3-7: Confirm tier 成功 handler 測試

- **涉及檔案**：`AdminPanelButtonHandlerMembershipTest.java`
- **根因**：僅覆蓋 no-session 拒絕路徑。
- **修復方案**：新增 test：建立 session → confirm tier → verify `setMembershipTier` + embed refresh。
- **驗證方式**：`mvn test -Dtest=AdminPanelButtonHandlerMembershipTest` 通過。

#### P3-8: Membership 查詢失敗 fallback

- **涉及檔案**：`MemberInfoFacade.java`（L71–72）；`UserPanelView.java`（L69–70）
- **根因**：`getPanelSummary` 不 catch repository 例外；null summary 路徑不可達。
- **修復方案**：`getMembershipSummary` try/catch → log + 回傳 `noneSummary` 或 null；handler 層對 null 顯示 NONE hint。
- **驗證方式**：mock repository throw → panel 仍渲染 NONE hint，不 crash。
