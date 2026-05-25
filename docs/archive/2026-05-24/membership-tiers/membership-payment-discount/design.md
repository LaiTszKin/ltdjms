# Design: membership-payment-discount

## Pricing DTO

```java
record EscortPriceQuote(
  long listPriceTwd,
  long chargedPriceTwd,
  long listCurrencyPrice,      // optional
  long chargedCurrencyPrice,
  MembershipTier appliedTier,
  BigDecimal discountRate
) {}
```

## 法幣流程

```
ShopSelectMenuHandler
  → MembershipPricingService.quote(userId, product)
  → FiatOrderService.create(..., quote.chargedPriceTwd(), quote.listPriceTwd())
  → Ecpay generateCvsPaymentCode(chargedPriceTwd)
  → FiatOrder.createPending(..., chargedPriceTwd, listPriceTwd snapshot)
```

**禁止**在 callback 才折扣。

## 貨幣流程

```
CurrencyPurchaseService.purchaseProduct
  → quote.chargedCurrencyPrice()
  → tryAdjustBalance(-charged)
```

## 折扣基礎

- listPriceTwd：product.fiatPriceTwd（shop 標價）
- 與 spend ledger 的 catalog M 可能不同（admin 定價 vs catalog）；**折扣以 shop 商品標價為準**，ledger 仍用 catalog M（coordination 已區分）

## 檔案

| 檔案 | 變更 |
|------|------|
| `membership/services/MembershipPricingService.java` | 新增 |
| `FiatOrderService.java` | 接受 quote |
| `FiatOrder.java` | list/charged 欄位 |
| `CurrencyPurchaseService.java` | quote |
| `ShopView.java` | 會員價展示 |

## 四捨五入

`charged = round(list * (1 - d))` 使用 `Math.round` 對 TWD long。
