# Checklist: shop-member-discount-display

- Date: 2026-05-25
- Feature: shop-member-discount-display

## Clarification & Approval Gate

- [x] Clarification responses recorded — None required
- [ ] Explicit approval obtained (date/ref: pending)

## Behavior-to-Test Checklist

- [ ] CL-01: 列表 embed escort 商品法幣劃線價 — R1.1 → UT-04 — Result: `NOT RUN`
- [ ] CL-02: NONE tier 列表無劃線 — R1.2 → UT-02 — Result: `NOT RUN`
- [ ] CL-03: 貨幣與法幣各自格式化 — R1.3 → UT-01 — Result: `NOT RUN`
- [ ] CL-04: 搜尋/分頁 embed 一致 — R1.4 → ShopButtonHandlerTest — Result: `NOT RUN`
- [ ] CL-05: Buy menu description ≤100 無 markdown — R2.1 → UT-03, UT-05 — Result: `NOT RUN`
- [ ] CL-06: 確認頁 embed 劃線格式 — R3.1 → ShopViewTest — Result: `NOT RUN`
- [ ] CL-07: 扣款仍用 charged price — R3.2 → CurrencyPurchaseServiceTest 回歸 — Result: `NOT RUN`

## Hardening Checklist

- [ ] Regression: 既有 `ShopViewTest` member price fiat confirm
- [ ] Property-based: N/A（display only）
- [ ] Adversarial: select description 超長截斷策略

## E2E / Integration Decisions

- [ ] ShopService.quoteEscortPrices: Unit with mock pricing — Reason: 無 DB 依賴
- [ ] Discord live render: N/A — embed 字串 unit 覆蓋

## Execution Summary

- [ ] Unit: `NOT RUN`
- [ ] Integration: `NOT RUN`
- [ ] E2E: `N/A`

## Completion Records

- [ ] shop-member-discount-display: pending implementation
