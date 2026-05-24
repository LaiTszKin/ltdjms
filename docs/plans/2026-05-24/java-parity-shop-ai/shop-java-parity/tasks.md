# Tasks: shop-java-parity

- Date: 2026-05-24
- Feature: shop-java-parity

## **Task 1: Oracle fixtures（若 preparation 未完成）**

Purpose: 可重複 parity 驗收
Requirements: R1.3, R2.3
Scope: `fixtures/`
Out of scope: Java 修改

- T1.1 [x] **建立/同步 java-shop-custom-ids.json**
  - Verify: JSON loadable in tests

- T1.2 [x] **建立/同步 java-shop-view-oracle.json**
  - Verify: 含 browse/search/payment/confirm 場景

- T1.3 [x] **建立/同步 java-shop-service-oracle.json**
  - Verify: 含 0-based/1-based 與 empty catalog

## **Task 2: ShopService 分頁契約**

Purpose: 修復 off-by-one 與 empty catalog
Requirements: R3.1-R3.3
Scope: `packages/shop/src/services/shop.service.ts`, `packages/shop/src/domain/`
Out of scope: handler UI

- T2.1 [x] **修正 page input 為 0-based** — mirror Java `ShopService.getShopPage(guildId, pageIndex)`
  - Verify: UT-301 pagination tests 綠

- T2.2 [x] **empty catalog totalPages=0**
  - Verify: UT-301 empty shop case

- T2.3 [x] **新增 hasProducts, getProductCount, ShopPage boundary helpers**
  - Verify: UT-301 全綠

## **Task 3: ShopView parity**

Purpose: UI 結構 1:1 對齊 Java
Requirements: R1.1-R1.2, R2.1-R2.3, R6.1-R6.3
Scope: `packages/shop/src/view/shop-view.ts`
Out of scope: handler routing

- T3.1 [x] **Mirror ShopView constants + buildShopEmbed**
  - Verify: UT-302 embed snapshot

- T3.2 [x] **兩列 pagination layout + disabled buttons at bounds**
  - Verify: UT-303 components snapshot

- T3.3 [x] **buildBuyMenu + buildSearchResultComponents + buildPurchaseConfirmEmbed**
  - Verify: UT-304 buy/search/confirm snapshots

- T3.4 [x] **Search modal field id=`keyword`**
  - Verify: UT-302 modal structure

## **Task 4: ProductService.getAllPurchasableProducts**

Purpose: Buy menu 資料源對齊 Java
Requirements: R4.4
Scope: `packages/shop/src/services/product-service.ts`
Out of scope: admin CRUD

- T4.1 [x] **實作 getAllPurchasableProducts(guildId)** — merge currency-priced + fiat-only
  - Verify: UT-305 product list oracle

## **Task 5: Browse handler parity**

Purpose: 瀏覽/搜尋/分頁對齊 Java ButtonHandler + CommandHandler
Requirements: R4.1-R4.5
Scope: `packages/shop/src/commands/shop-handler.ts`（browse 部分）
Out of scope: purchase 分支

- T5.1 [x] **/shop 使用 page index 0**
  - Verify: UT-306 ShopCommandHandler

- T5.2 [x] **Pagination editMessageEmbeds + page-1 conversion**
  - Verify: UT-307 ShopButtonHandler pagination

- T5.3 [x] **Search modal submit + search pagination + back**
  - Verify: UT-307 search cases

- T5.4 [x] **Guild guard + search empty 文案**
  - Verify: UT-307 error messages

## **Task 6: Purchase handler parity**

Purpose: 購買流程對齊 Java SelectMenuHandler
Requirements: R5.1-R5.6
Scope: `packages/shop/src/commands/shop-handler.ts`（purchase 部分）
Out of scope: CurrencyPurchaseService 內部

- T6.1 [x] **Select routing by price type** — currency-only / fiat-only / dual
  - Verify: UT-308 routing matrix

- T6.2 [x] **Confirm/cancel purchase flow + balance-aware confirm embed**
  - Verify: UT-308 confirm/cancel

- T6.3 [x] **Fiat defer + DM + summary + inflight dedup**
  - Verify: UT-308 fiat flow

- T6.4 [x] **Currency purchase escort handoff + notifications**
  - Verify: UT-308 post-purchase side effects

## **Task 7: 可選 handler 拆分**

Purpose: 長期 parity diff 可維護性
Requirements: —
Scope: `packages/shop/src/commands/` 新檔
Out of scope: 行為變更

- T7.1 [ ] **（可選）拆分 shop-command/button/select handlers** — mirror Java 三檔結構
  - Verify: 所有 UT-306–308 仍綠

## **Task 8: 回歸與驗收**

Requirements: 全部
Scope: shop package

- T8.1 [x] **既有 shop PBT/backend tests 仍通過**
  - Verify: `pnpm vitest run --project @ltdjms/shop`

- T8.2 [x] **make verify**
  - Verify: exit code 0
