# Contract: membership-spend-ledger

## `MembershipSpendService.recordFiatEscortPayment(FiatOrder order, Product product)`

| Precondition | Action |
|--------------|--------|
| order.status == PAID | INSERT spend entry |
| product escort-linked | 否則 return |
| duplicate source_reference | skip |

## `MembershipSpendRepository.sumListPriceInPeriod(userId, from, to)`

Returns: `long` total M（供 settlement 使用）

## Dependencies

- membership-core
- Does NOT require join-tracking（但無 join 日仍可累計 spend）
