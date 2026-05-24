# Spec: membership-payment-discount

- Date: 2026-05-24
- Feature: membership-payment-discount
- Owner: laitszkin

## Goal

付款時對**已接入護航**的商店商品，依當前會員等級套用永久折扣；折後金額寫入訂單/ECPay，確保 callback 驗證通過；商店 UI 展示折後價。

## Scope

### In Scope
- `MembershipPricingService.resolveEscortProductPrice(guildId, userId, product)`
- 整合 `FiatOrderService.createFiatOnlyOrder` — `charged_amount_twd`、ECPay code
- 整合 `CurrencyPurchaseService.purchaseProduct` — 貨幣價等比折扣（若 escort-linked）
- `ShopView` / confirm embed 顯示原價、折扣、實付
- 非 escort 商品：不折扣

### Out of Scope
- M 計入 ledger（spend-ledger）
- Token grant
- 修改 escort dispatch 面板定價

## Functional Behaviors (BDD)

### Requirement 1: 法幣折扣
**GIVEN** 白銀會員（9折）購買 escort-linked 商品，catalog M=3500
**WHEN** 建立法幣訂單
**THEN** ECPay amount = 3150
**AND** fiat_order.list_price_twd=3500, charged_amount_twd=3150

**Requirements**:
- [x] R1.1 NONE/無 tier 折扣 → 原價
- [x] R1.2 折扣僅當 `product.shouldAutoCreateEscortOrder()` 或 escortOptionCode 非空
- [x] R1.3 callback `TradeAmt` 必須等於建立時 amount（已在 order 凍結）

### Requirement 2: 貨幣折扣
**GIVEN** 黃金會員購買 escort-linked 貨幣商品
**WHEN** currency purchase
**THEN** 扣除折後 currencyPrice

**Requirements**:
- [x] R2.1 折後價 = round(listCurrency * (1-d))

### Requirement 3: UI
**GIVEN** 會員瀏覽 shop 確認頁
**WHEN** escort 商品
**THEN** embed 顯示「會員價 NT$xxx（原價 NT$yyy）」

## Error and Edge Cases
- [x] 無 membership 記錄 → tier NONE → 無折扣
- [x] 四捨五入規則：TWD 整數、currency long 一致

## Clarification Questions
None

## References
- `FiatOrderService.java`, `FiatPaymentCallbackService.isValidPaidCallback`
- `CurrencyPurchaseService.java`, `ShopView.java`
