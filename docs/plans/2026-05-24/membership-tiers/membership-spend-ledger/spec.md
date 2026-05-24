# Spec: membership-spend-ledger

- Date: 2026-05-24
- Feature: membership-spend-ledger
- Owner: laitszkin

## Goal

護航相關**法幣 TWD 付款成功**後，將 catalog **原價 M** 寫入全域 spend ledger，供結算計算月平均 M；並標記青銅 qualifying 訂單。

## Scope

### In Scope
- Flyway `V030__create_membership_spend_entry.sql`
- `MembershipSpendService.recordFiatEscortPayment(...)`
- Hook：`FiatOrderPostPaymentWorker` 在 PAID + escort-linked 商品成功後呼叫
- 擴充 `fiat_order`：`list_price_twd`（M）、`membership_tier_at_order`（可選 audit）
- Idempotency：`source_reference = order_number` UNIQUE

### Out of Scope
- 貨幣購買計入 M（非法幣）
- 未接入護航的商店商品
- 直接 dispatch 面板手動開單（無法幣付款）— out of scope unless later

## Functional Behaviors (BDD)

### Requirement 1: 計入條件
**GIVEN** 法幣訂單 PAID 且 `order.shouldAutoCreateEscortOrder()` 或 `escortOptionCode` 非空
**WHEN** post-payment worker 處理
**THEN** 寫入 spend entry，`list_price_twd = catalogDefaultPrice(optionCode)`
**AND** 若 list_price_twd ≥ 500 則 `has_qualifying_bronze_order = true`

**Requirements**:
- [ ] R1.1 M 來源：`EscortOptionCatalog.priceTwd`（**非** guild override、**非**折後價）
- [ ] R1.2 無 escortOptionCode 但 auto escort → 用 product.fiatPriceTwd 作 fallback M
- [ ] R1.3 重複處理同一 orderNumber → no-op（idempotent）

### Requirement 2: 不計入
**GIVEN** 貨幣購買或非法幣訂單
**WHEN** 購買完成
**THEN** 不寫 spend ledger

## Error and Edge Cases
- [ ] catalog code 不存在 → log warn，fallback product.fiatPriceTwd
- [ ] record 失敗不應 rollback 已完成的 fulfillment（best-effort + retry 或 dead letter log）

## Clarification Questions
None

## References
- `FiatOrderPostPaymentWorker.java`
- `EscortOptionCatalog.java`
- `Product.shouldAutoCreateEscortOrder()`
