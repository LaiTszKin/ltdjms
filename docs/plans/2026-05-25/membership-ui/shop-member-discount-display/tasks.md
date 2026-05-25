# Tasks: shop-member-discount-display

- Date: 2026-05-25
- Feature: shop-member-discount-display

## **Task 1: 價格格式化**

Purpose: 集中 embed 劃線與 select 精簡格式
Requirements: R1.x, R2.x, R3.x
Scope: `EscortPriceQuote.java`
Out of scope: Handler wiring

- T1.1 [ ] **`EscortPriceQuote.java`** — 新增 `formatFiatEmbedLine`、`formatCurrencyEmbedLine`、`formatFiatSelectDescription`、`formatCurrencySelectDescription`；折扣率用 `MembershipTierLabels.discountLabel`
  - Verify: 新增 `EscortPriceQuoteTest`（UT-01–03）

- T1.2 [ ] **既有 formatter** — `formatFiatPriceLine` / `formatCurrencyPriceLine` delegate 至 embed 版或替換所有呼叫點
  - Verify: `make test` shop + membership 相關測試綠

## **Task 2: ShopService batch quote**

Purpose: Handler 一次取得頁面所有 quotes
Requirements: R1.4
Scope: `ShopService.java`
Out of scope: Pricing 演算法

- T2.1 [ ] **`ShopService.quoteEscortPrices()`** — 對 escort products 呼叫 `quoteEscortPrice`，回傳 `Map<Long, EscortPriceQuote>`
  - Verify: `ShopServiceTest` 新增 case

## **Task 3: ShopView 渲染**

Purpose: 列表、選單、確認頁使用新格式
Requirements: R1.x, R2.x, R3.x
Scope: `ShopView.java`
Out of scope: Handler 以外模組

- T3.1 [ ] **`ShopView.buildShopEmbed`** — 新增 quotes map 參數；價格行改用 embed formatter
  - Verify: `ShopViewTest` 新增 list embed 折扣 case

- T3.2 [ ] **`buildBuyMenu` / `buildSearchBuyMenu`** — 接受 quotes；`buildPriceDescription(product, quote)` 精簡格式
  - Verify: `ShopViewTest` select description ≤ 100

- T3.3 [ ] **確認 embeds** — `buildPaymentMethodChoiceEmbed`、`buildPurchaseConfirmEmbed`、`buildFiatPurchaseConfirmEmbed` 改用 embed formatter
  - Verify: 更新既有 `ShopViewTest` member price cases

## **Task 4: Handler 串接**

Purpose: 所有 shop 瀏覽路徑帶 userId quote
Requirements: R1.4, R2.x
Scope: `ShopCommandHandler.java`, `ShopButtonHandler.java`
Out of scope: Purchase 扣款邏輯

- T4.1 [ ] **`ShopCommandHandler`** — 初始 shop 頁 quote + 傳入 buildShopEmbed
  - Verify: `ShopButtonHandlerTest` 或 manual `/shop`

- T4.2 [ ] **`ShopButtonHandler`** — pagination、search、showBuyMenu 路徑 quote
  - Verify: `ShopButtonHandlerTest` 綠

## **Task 5: 文件**

Purpose: Feature doc 同步
Requirements: R1–R3
Scope: `docs/features/shop-and-payment.md`
Out of scope: atlas merge

- T5.1 [ ] **shop-and-payment.md** — 補商店列表會員價展示
  - Verify: 對照 spec
