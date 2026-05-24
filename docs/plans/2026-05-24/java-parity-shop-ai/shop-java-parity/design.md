# Design: shop-java-parity

- Date: 2026-05-24
- Feature: shop-java-parity
- Change Name: shop-java-parity

## Traceability

| | |
| --- | --- |
| Requirement IDs | R1.x–R6.x |
| In-scope modules | `packages/shop` view, commands, shop.service, product-service |
| External systems | Discord interactions, ECPay (existing, not changed) |
| Batch coordination | `../coordination.md` |

## Target vs baseline

| | Baseline | Target |
| --- | --- | --- |
| Handler structure | Single `shop-handler.ts` | Behavior matches Java 3 handlers (optional file split) |
| Pagination | Off-by-one, totalPages≥1 always | Java 0-based in / 1-based out |
| Purchase | Immediate currency buy, sync fiat | Confirm step, defer+DM fiat, escort handoff |

## Boundaries

- Entry surface(s): `/shop` slash, `shop_*` customId interactions
- Trust boundary: Discord member in guild
- Outside → inside: Member → shop-handler → ShopView / ShopService → economy/fiat services

## Modules

| Module key | Responsibility | Owned artifacts |
| --- | --- | --- |
| `shop-view` | Pure Discord UI builders | shop-view.ts |
| `shop-service` | Pagination/search queries | shop.service.ts |
| `shop-handler` | Interaction routing | shop-handler.ts |

## Interaction anchors

| ID | Intent | Caller → Callee | Kind | Crossing | Failure |
| --- | --- | --- | --- | --- | --- |
| `INT-001` | Browse page | handler → ShopService | call | guildId, pageIndex | ephemeral error |
| `INT-002` | Buy menu | handler → ProductService | call | purchasable products | empty menu message |
| `INT-003` | Currency purchase | handler → CurrencyPurchaseService | tx | productId, userId | confirm blocked / refund |
| `INT-004` | Fiat order | handler → FiatOrderService | network | productId, userId | inflight dedup |
| `INT-005` | Escort handoff | handler → dispatch notify | event | order context | log + user message |

## Requirement linkage

### R3 → R4 → R5
- ShopService contract must land before handler pagination fixes
- View parity before handler snapshot tests
- Purchase flow last (depends on view confirm components)

## Data & persistence

| Resource | Readers/writers | Consistency |
| --- | --- | --- |
| ProductRepository | ShopService, ProductService | read-only in browse |
| inflightFiatOrders | handler instance map | dedup per user+product |

## Invariants

| Invariant | Breaks if | Symptoms |
| --- | --- | --- |
| customId exact match | Renaming constants | Java parity tests fail |
| 0-based service input | Handler passes 1-based page | Wrong page content |

## Tradeoffs

| Decision | Rejected | Locks in |
| --- | --- | --- |
| Keep merged handler initially | Mandatory 3-file split | Optional T7 refactor |
| Java oracle over archived TS port spec | Re-interpret requirements | ShopViewTest as source |

## Batch-only

Test harness deps from external-deps-adoption; escort/dispatch services assumed existing.
