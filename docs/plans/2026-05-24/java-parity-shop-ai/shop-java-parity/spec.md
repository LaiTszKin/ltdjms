# Spec: shop-java-parity

- Date: 2026-05-24
- Feature: shop-java-parity
- Owner: laitszkin

## Goal

將 TypeScript `@ltdjms/shop` 成員端 `/shop` 商店（Discord UI + browse/purchase 互動 + ShopService 分頁）1:1 對齊 Java bot，以 Java 測試與 fixtures 為 oracle。

## Scope

### In Scope
- **ShopView parity**：embed 結構、customId、button label/style、action row 布局（含 disabled 分頁、兩列 layout）、buy menu split、search modal field id=`keyword`、confirm/cancel purchase
- **ShopService parity**：0-based page input、1-based `currentPage` output、empty catalog `totalPages=0`、`hasProducts`、`getProductCount`
- **Browse interactions**：`/shop`、prev/next、search modal、search pagination、back-to-shop；`getAllPurchasableProducts` 作為 buy menu 資料源
- **Purchase interactions**：currency-only → confirm embed；fiat-only → defer+DM+summary；dual-price → payment choice；`shop_confirm_purchase_*` / `shop_cancel_purchase`；inflight fiat dedup；currency purchase escort handoff + notifications
- **Parity tests**：port `ShopViewTest`, `ShopServiceTest`, `ShopButtonHandlerTest`, `ShopSelectMenuHandlerTest`；新增 `ShopCommandHandler` 測試

### Out of Scope
- 修改 Java bot
- Admin 商品管理面板
- ECPay callback/fulfillment 業務邏輯（已有 TS 測試，僅 supertest harness 在 external-deps spec）
- 後端 CurrencyPurchaseService/FiatOrderService 內部重寫（已有 ~90% parity）

## Functional Behaviors (BDD)

### Requirement 1: ShopView customId 與常數 1:1
**GIVEN** Java `ShopView` 定義所有 `shop_*` 常數
**WHEN** TypeScript `shop-view.ts` 匯出常數
**THEN** 所有 customId 與 Java 逐字一致
**AND** 含 `shop_confirm_purchase_{id}`、`shop_cancel_purchase`

**Requirements**:
- [ ] R1.1 Mirror Java `ShopView` public constants
- [ ] R1.2 Search modal text input customId = `keyword`（非 `shop_search_input`）
- [ ] R1.3 Parity test 比對 `fixtures/java-shop-custom-ids.json`

### Requirement 2: 商店列表 embed 與分頁 components 1:1
**GIVEN** Java `ShopViewTest` oracle
**WHEN** 渲染 browse page
**THEN** embed 欄位、footer、color 與 Java 一致
**AND** row1 = prev/next（邊界時 disabled）；row2 = buy + search（僅 `hasProducts` 時）

**Requirements**:
- [ ] R2.1 Button labels `⬅️ 上一頁` / `下一頁 ➡️`
- [ ] R2.2 空商店 embed 文案對齊 Java
- [ ] R2.3 UT snapshot 對齊 `fixtures/java-shop-view-oracle.json` browse 場景

### Requirement 3: ShopService 分頁契約 1:1
**GIVEN** Java `ShopService` 0-based input
**WHEN** handler 呼叫 `getShopPage(guildId, pageIndex)`
**THEN** 回傳 `ShopPage.currentPage` 為 1-based
**AND** 無商品時 `totalPages = 0`

**Requirements**:
- [ ] R3.1 修正 TS off-by-one（handlers 傳 `page - 1` 或等價）
- [ ] R3.2 新增 `hasProducts(guildId)`、`getProductCount(guildId)`
- [ ] R3.3 Port `ShopServiceTest.java` → vitest

### Requirement 4: Browse 互動流程 1:1
**GIVEN** Java `ShopCommandHandler` + `ShopButtonHandler`
**WHEN** 使用者瀏覽/搜尋/返回
**THEN** editMessageEmbeds 流程、錯誤訊息、search empty 文案對齊 Java

**Requirements**:
- [ ] R4.1 `/shop` 初始 page index 0
- [ ] R4.2 Guild guard：`此功能只能在伺服器中使用`
- [ ] R4.3 Search empty：`找不到符合「{keyword}」的商品`
- [ ] R4.4 Buy 按鈕使用 `productService.getAllPurchasableProducts(guildId)`
- [ ] R4.5 Port `ShopButtonHandlerTest.java`

### Requirement 5: 購買流程 1:1
**GIVEN** Java `ShopSelectMenuHandler`
**WHEN** 使用者選商品並完成購買
**THEN** 路由依 price type 分支；confirm 後才扣款；fiat defer+DM

**Requirements**:
- [ ] R5.1 Currency-only → confirm embed + `shop_confirm_purchase_*` / `shop_cancel_purchase`
- [ ] R5.2 Fiat-only → deferReply + DM payment + interaction summary
- [ ] R5.3 Dual-price → payment choice（`💰 貨幣購買` / `💳 法幣下單`，無價格在 label）
- [ ] R5.4 Inflight fiat dedup（ConcurrentHashMap 等價）
- [ ] R5.5 Currency purchase 後 escort handoff + buyer/admin notify（對齊 Java handler）
- [ ] R5.6 Port `ShopSelectMenuHandlerTest.java`

### Requirement 6: Buy menu 與 search buy select 1:1
**GIVEN** Java `ShopView.buildBuyMenu` / search result components
**WHEN** 商品 >25 需 split menus
**THEN** 共用 customId `shop_buy_select` / `shop_search_buy_select`（非 suffix `_1`）

**Requirements**:
- [ ] R6.1 Buy menu 描述格式對齊 Java（combined price description）
- [ ] R6.2 Search result component order：buy select → pagination → back
- [ ] R6.3 Search pagination 始終顯示 prev/next（disabled at bounds）

## Error and Edge Cases
- [ ] 分頁超出範圍 — clamp 至有效頁
- [ ] 餘額不足 — confirm embed 顯示錯誤或禁止 confirm（對齊 Java）
- [ ] 重複 fiat 訂單 — inflight guard ephemeral 提示
- [ ] DM 關閉 — fiat fallback 訊息對齊 Java
- [ ] 非 guild interaction — 統一錯誤文案

## Clarification Questions
None（驗收粒度：structural parity test；customId 逐字一致；衝突以 Java 為準）

## References
- Java: `ShopView.java`, `ShopCommandHandler.java`, `ShopButtonHandler.java`, `ShopSelectMenuHandler.java`, `ShopService.java`, `ProductService.getAllPurchasableProducts`
- Java tests: `ShopViewTest.java`, `ShopServiceTest.java`, `ShopButtonHandlerTest.java`, `ShopSelectMenuHandlerTest.java`
- TS: `packages/shop/src/view/shop-view.ts`, `packages/shop/src/commands/shop-handler.ts`, `packages/shop/src/services/shop.service.ts`
- Fixtures: `fixtures/java-shop-*.json`（preparation 建立）
