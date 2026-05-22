# Design: Shop Business Invariant PBT

- Date: 2026-05-22
- Feature: Shop Business Invariant PBT
- Change Name: shop-pbt

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1.1–R1.4, R2.1–R2.3, R3.1–R3.4, R4.1–R4.5, R5.1–R5.2                       |
| In-scope modules (≤3)       | `packages/shop/src/services/` (redemption, shop, fiat-order, currency-purchase) |
| External systems touched    | None (ECPay API 由 ecpay-e2e 負責)                                           |
| Batch coordination          | `../coordination.md`                                                        |

## Target vs baseline

|                       | Baseline (today) | Target (after this change) |
| --------------------- | ---------------- | --------------------------- |
| Structure / ownership | 單元測試使用 mock DB/mock repo | 4 個 Integration PBT，走真實 DB 驗證 shop 業務不變量 |

## Boundaries

- Entry surface(s): vitest → shop services (RedemptionService, ShopService, FiatOrderService, CurrencyPurchaseService)
- Trust boundary crossed: `None`
- Outside → inside (one line): `PBT test` → `createTestContainer()` + `seedProduct()` + `seedRedemptionCode()` → `RedemptionService.redeem()` / `FiatOrderService.createOrder()` / etc → real PostgreSQL

## Modules (nouns only)

| Module key | Responsibility (one sentence) | Owned artifacts (types, tables, queues) |
| ---------- | ---------------------------- | ---------------------------------------- |
| `redemptionPbt` | 驗證兌換碼冪等性與庫存管理 | `redemption.pbt.test.ts` |
| `shopPurchasePbt` | 驗證貨幣購買餘額扣減與獎勵發放 | `shop-purchase.pbt.test.ts` |
| `fiatOrderPbt` | 驗證法幣訂單建立資料完整性 | `fiat-order-creation.pbt.test.ts` |
| `currencyPurchasePbt` | 驗證付款後獎勵計算正確 | `currency-purchase.pbt.test.ts` |

---

## Interaction anchors (`INT-###`)

| ID        | Intent | Caller → Callee | Coupling kind | Information crossing | Failure expectation |
| --------- | ------ | --------------- | ------------- | -------------------- | ------------------- |
| `INT-001` | 所有 PBT 依賴 test-infra | `PBT test` → `test-infra` | sync import | `createTestContainer()`, `seedProduct()`, `seedRedemptionCode()` | test-infra 不可用時 skip |
| `INT-002` | Redemption PBT 呼叫 RedemptionService | `PBT test` → `RedemptionService` | sync call | redeem 參數 → DB code 狀態變更 + reward | DomainError 時驗證錯誤型別 |
| `INT-003` | Shop purchase PBT 透過 ShopService 完成購買流程 | `PBT test` → `ShopService` | sync call | 購買請求 → DB 餘額變更 + transaction record | DomainError 時餘額不變 |

**Ordering / concurrency (design-level):** 四個 PBT 獨立執行，各自 seed 獨立資料。`beforeAll` reset DB → seed guild/users/products。

## Data & persistence (design-level)

| Resource                      | Typical readers/writers | Consistency expectation |
| ----------------------------- | ----------------------- | ----------------------- |
| `redemption_code` table       | RedemptionService (read/write) | code 僅能被 claim 一次（冪等更新） |
| `product` table               | ShopService / FiatOrderService (read) | 唯讀（PBT 不測試 admin 上下架） |
| `fiat_order` table            | FiatOrderService (write) | orderNumber 唯一 |

## Invariants (system-level)

| Invariant | What breaks it architecturally | Symptoms if violated |
| --------- | ------------------------ | -------------------- |
| 兌換碼僅能被兌換一次 | RedemptionService 未做 claim 冪等 | 同一 code 多次兌換、庫存變負 |
| 購買後餘額 = 購買前餘額 - 價格 | CurrencyPurchaseService 計算錯誤 | 用戶多扣／少扣 |

## Batch-only

Shop PBT 依賴 test-infra 的 `createTestContainer`、`seedProduct`、`seedRedemptionCode`。
