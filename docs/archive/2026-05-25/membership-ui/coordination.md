# Coordination: membership-ui

- Date: 2026-05-25
- Batch: membership-ui

## Business Goals

在既有會員等級後端（join tracking、spend ledger、settlement、payment discount、token grant）之上，補齊三塊使用者可見能力：

1. **個人面板**：加入日期、距下一等級剩餘消費、完整會員權益
2. **管理面板**：管理員調整用戶本週期消費 M 與會員等級
3. **商店列表**：會員瀏覽商店時，護航商品以劃線原價 + 折扣價 + 折扣率呈現

- Batch members: [user-panel-membership-info, shop-member-discount-display, admin-membership-management]
- Shared outcome: 三份 spec 各自可驗收；`make verify` 全綠；Architecture Atlas overlay 通過 validate
- Out of scope: TypeScript bot parity、推播 tier 變更通知、會員等級 ladder 比較表、永久 tier override flag（除非 clarification 後另開 spec）

## Design Principles

- Current baseline: Java bot 已有 `MembershipQueryService`、`UserPanelView`（等級/進度/結算日/護航折扣）、`MembershipPricingService`（結帳流程折扣）、`AdminPanelService`（貨幣/代幣/遊戲/AI/護航設定，**無**會員管理）
- Shared invariants:
  - 會員身份鍵為 **Discord User ID（全域）**
  - 加入日期來源為 `global_member_membership.earliest_guild_join_at`（結算錨點）
  - 「距下一等級消費」= `max(0, nextTierThresholdM - periodSpendListPriceM)`
  - 權益展示僅列出**當前等級**享有項目（護航折扣 + 每月贈幣）；不展示全 ladder
  - 商店劃線價格僅在 **embed description** 使用 Discord `~~` markdown；Select Menu description 為純文字 compact 格式
  - 管理員調整消費透過 `membership_spend_entry` 新增 `ADMIN_ADJUST` 列（可稽核）；調整等級直接更新 `current_tier` 並發布 `MembershipTierChangedEvent`
  - 管理員手動設等級在**下次結算**時可能被 ledger 重算覆寫（見 admin spec clarification）
- Shared constraints:
  - 不新增 Flyway migration（除非 admin spec 需 audit 表；預設用既有 ledger unique key）
  - `MembershipQueryService` 維持 read-only；寫入走 `MembershipAdminService`
  - Discord embed 總字元 ≤ 6000；Select option description ≤ 100 字元
- Legacy direction: 2026-05-24 `membership-benefits-ui` 已交付核心 panel；本 batch 為增量 UI + admin write path
- Compatibility window: 無資料遷移；上線後既有 panel/shop 行為向後相容（僅增加欄位/格式）

## Spec Boundaries

### Ownership Map

#### Spec Set 1: user-panel-membership-info
- Primary concern: 擴充 `MembershipPanelSummary` 與 `UserPanelView` 顯示加入日、剩餘 M、權益
- Allowed touch points: `membership/services/MembershipQueryService.java`、`MembershipPanelSummary.java`、`panel/services/UserPanelView.java`、`UserPanelEmbedBuilderTest.java`
- Must not change: settlement、payment discount、admin write path、shop embed

#### Spec Set 2: shop-member-discount-display
- Primary concern: 商店列表/搜尋/購買選單/確認頁統一會員價格式（劃線 + 折扣率）
- Allowed touch points: `EscortPriceQuote.java`、`ShopView.java`、`ShopService.java`、`ShopCommandHandler.java`、`ShopButtonHandler.java`、`ShopSelectMenuHandler.java`
- Must not change: `MembershipPricingService` 折扣演算法、ledger、settlement、admin panel

#### Spec Set 3: admin-membership-management
- Primary concern: 管理員調整 period spend M 與 current tier
- Allowed touch points: `MembershipAdminService.java`（新）、`MembershipManagementFacade.java`（新）、`AdminPanelService.java`、`AdminPanelButtonHandler.java`、`AdminPanelViewFactory.java`、`MembershipSpendRepository` admin insert
- Must not change: shop 列表渲染、user panel 文案（可讀 tier 結果）、settlement scheduler 核心邏輯

### Collisions & Integration

- Shared files & edit rules:
  - `MembershipPanelSummary` — **user-panel-membership-info** 擴充欄位；admin/shop 只讀
  - `EscortPriceQuote` formatters — **shop-member-discount-display** 擁有；user-panel 不碰
  - `MembershipTierChangedEvent` — admin set tier 與既有 settlement/bronze 共用；`UserPanelUpdateListener` 已訂閱
  - `CommandHandlerModule` — 各 spec 追加 `@Provides`（admin facade、shop handler 注入不衝突）
- Shared API freeze: `MembershipTier` 常數 additive-only
- Compatibility shim: 無 join 記錄 → 加入日期顯示「尚未記錄」
- Merge order: `user-panel-membership-info` ∥ `shop-member-discount-display` → `admin-membership-management`
- Integration checkpoints:
  - user-panel 完成：panel embed 含三項新資訊
  - shop 完成：列表 embed 劃線價；確認頁格式一致
  - admin 完成：可調 M 與 tier；tier 變更刷新 open user panel
  - batch 完成：`make verify` + 三份 spec `apltk architecture validate`
- Re-coordination trigger: 需永久 tier override、負數 spend ledger、或 admin 調整立即觸發 settlement

## Clarification Gate（Batch 級）

以下問題在 **admin-membership-management** spec 中記錄；user-panel 與 shop spec 不依答案阻塞：

1. 管理員設等級是否應在下次結算前固定（需 `tier_override_until` 欄位）？
2. 管理員調整消費是否允許負數 ledger 列（deduct 使 period sum 下降）？
3. Admin spend 調整的 `guild_id` 是否固定為操作者所在 guild？

**預設設計路徑（未答覆前採用）：** 設等級立即生效、下次結算可被 ledger 覆寫；允許 deduct（負 M 列）；guild_id = 操作者 guild。
