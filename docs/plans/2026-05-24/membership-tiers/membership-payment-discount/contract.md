# Contract: membership-payment-discount

## `MembershipPricingService.quote(long userId, Product product)`

Returns: `EscortPriceQuote`

| product escort-linked | Behavior |
|-----------------------|----------|
| false | charged = list，tier=NONE |
| true | charged = list * (1 - tier.discountRate) |

## Fiat order fields

| Column | Meaning |
|--------|---------|
| `amount_twd` | charged（ECPay 金額，保持向後兼容） |
| `list_price_twd` | 折前標價 |
| `charged_amount_twd` | 冗余 audit = amount_twd |

## Dependencies

- membership-core（讀 current_tier）
- fiat_order columns from spend-ledger migration
