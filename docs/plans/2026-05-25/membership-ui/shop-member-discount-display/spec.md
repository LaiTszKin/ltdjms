# Spec: shop-member-discount-display

- Date: 2026-05-25
- Feature: shop-member-discount-display
- Owner: laitszkin

## Goal

會員用戶瀏覽商店時，對已接入護航且有折扣的商品，在列表與結帳流程中以「~~原價~~ 折扣價（折扣率）」格式展示，提升折扣感知並與實際扣款一致。

## Scope

### In Scope
- `EscortPriceQuote` 新增統一格式化方法（embed 劃線版 + select menu 精簡版）
- `ShopView.buildShopEmbed` 接受 per-product quotes，護航商品顯示會員價
- `ShopCommandHandler` / `ShopButtonHandler` 在渲染前 batch quote
- 可選 `ShopService.quoteEscortPrices(userId, products, guildId)` helper
- 更新確認頁（payment choice / currency confirm / fiat confirm）使用新格式
- `buildPriceDescription` / buy menu 使用精簡文字（無 markdown）
- Unit tests：`EscortPriceQuoteTest`、`ShopViewTest`

### Out of Scope
- 修改 `MembershipPricingService` 折扣演算法
- 非 escort 商品折扣
- TypeScript shop parity
- 商店以外 UI（dispatch panel 定價）

## Functional Behaviors (BDD)

### Requirement 1: 商店列表 Embed 劃線價
**GIVEN** 白銀會員（9 折）開啟 `/shop`，頁面含 escort-linked 商品 catalog M=3500 TWD
**WHEN** 商店 embed 渲染
**THEN** 該商品法幣行顯示 `~~NT$3,500~~ NT$3,150（9 折）`（或等價貨幣行）

**Uncertainty Level**: Known

**Requirements**:
- [x] R1.1 僅 `EscortProductRules.isEscortLinked(product)` 且 quote 有折扣時使用劃線格式
- [x] R1.2 無折扣（NONE tier 或非 escort）維持原價格式，無 `~~`
- [x] R1.3 同時有貨幣與法幣價時，各自獨立格式化
- [x] R1.4 分頁、搜尋結果 embed 與主列表行為一致

### Requirement 2: 購買選單精簡價格
**GIVEN** 會員開啟「🛒 購買」選單
**WHEN** escort 商品有折扣
**THEN** option description 顯示 compact 文字，例如 `3150 TWD (9折)` 或 `90幣 (原100,9折)`，長度 ≤ 100 字元

**Uncertainty Level**: Known（Discord Select 不支援 markdown）

**Requirements**:
- [x] R2.1 不使用 `~~`；超長時優先保留折扣價 + 折扣率，省略原價
- [x] R2.2 無折扣時與現行 `buildPriceDescription` 相同

### Requirement 3: 確認頁格式統一
**GIVEN** 會員進入支付方式選擇或確認購買 embed
**WHEN** 有 escort 折扣
**THEN** 價格行使用與列表相同的 embed 劃線格式（`formatEmbedPriceLine`）

**Uncertainty Level**: Known

**Requirements**:
- [x] R3.1 `buildPaymentMethodChoiceEmbed`、`buildPurchaseConfirmEmbed`、`buildFiatPurchaseConfirmEmbed` 改用新 formatter
- [x] R3.2 扣款金額仍來自 `EscortPriceQuote.charged*`，僅改 display

## Error and Edge Cases
- [x] `MembershipPricingService` 為 null（舊 wiring）→ 列表 fallback 原價
- [x] quote 失敗 → log + 顯示 list price（不 block 瀏覽）
- [x] embed 字元總量 ≤ 6000（5 商品/頁）
- [x] 四捨五入與既有 pricing service 一致

## Clarification Questions

None（技術可行性已確認：embed 支援 `~~`；select menu 用 compact 文字）

## References
- Official docs:
  - [Discord Markdown — Strikethrough](https://support.discord.com/hc/en-us/articles/210298617-Markdown-Text-101-Chat-Formatting-Bold-Italic-Underline)
  - [Discord Component Reference — Select Option description 100 chars](https://docs.discord.com/developers/components/reference)
- Related code files:
  - `src/main/java/ltdjms/discord/shop/services/ShopView.java`
  - `src/main/java/ltdjms/discord/membership/services/EscortPriceQuote.java`
  - `src/main/java/ltdjms/discord/membership/services/MembershipPricingService.java`
