# Design: Admin Panel PBT

- Date: 2026-05-22
- Feature: Admin Panel PBT
- Change Name: admin-pbt

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1.1–R1.4, R2.1–R2.3, R3.1–R3.3, R4.1–R4.4, R5.1–R5.4, R6.1–R6.3, R7.1–R7.3, R8.1–R8.3 |
| In-scope modules (≤3)       | `packages/admin/src/facades/`, `packages/admin/src/panel/admin/handlers/`  |
| External systems touched    | None                                                                         |
| Batch coordination          | `../coordination.md`                                                        |

## Target vs baseline

|                       | Baseline (today) | Target (after this change) |
| --------------------- | ---------------- | --------------------------- |
| Structure / ownership | 少量 admin 單元測試（admin-panel-update-listener、balance-management-handler） | 7 個 Integration PBT，覆蓋全部 admin 管理功能的 facade→service→DB 管線 |

## Boundaries

- Entry surface(s): vitest → Admin Facades (CurrencyManagementFacade, ProductManagementFacade, GameConfigManagementFacade, GameTokenManagementFacade, DispatchManagementFacade, AIConfigManagementFacade)
- Trust boundary crossed: `None`
- Outside → inside (one line): `PBT test` → `createTestContainer()` + seed → `CurrencyManagementFacade.updateCurrencyConfig()` / etc → real PostgreSQL

## Modules (nouns only)

| Module key | Responsibility (one sentence) | Owned artifacts |
| ---------- | ---------------------------- | ---------------------------------------- |
| `currencyConfigPbt` | 驗證貨幣參數變更即時生效 | `currency-config.pbt.test.ts` |
| `productMgmtPbt` | 驗證商品 CRUD 操作正確性 | `product-management.pbt.test.ts` |
| `redeemCodeGenPbt` | 驗證兌換碼批量生成唯一性 | `redemption-code-gen.pbt.test.ts` |
| `gameConfigPbt` | 驗證 Dice 參數變更生效 | `game-config.pbt.test.ts` |
| `tokenMgmtPbt` | 驗證代幣批量調整 | `game-token-management.pbt.test.ts` |
| `dispatchConfigPbt` | 驗證護航選項目錄 CRUD | `dispatch-config.pbt.test.ts` |
| `aiChannelPbt` | 驗證 AI 白名單頻道設定 | `ai-channel-config.pbt.test.ts` |

---

## Interaction anchors (`INT-###`)

| ID        | Intent | Caller → Callee | Coupling kind | Information crossing | Failure expectation |
| --------- | ------ | --------------- | ------------- | -------------------- | ------------------- |
| `INT-001` | 所有 PBT 依賴 test-infra + admin module DI | `PBT test` → `test-infra` | sync import | DI container with admin facades | test-infra 不可用時 skip |
| `INT-002` | Currency config PBT 測試 facade → service → DB | `PBT test` → `CurrencyManagementFacade` → `CurrencyConfigService` → DB | sync call | config 參數 → DB currency_config row | DomainError 時驗證 |
| `INT-003` | Product management PBT 測試 facade → service → DB | `PBT test` → `ProductManagementFacade` → `ProductService` → DB | sync call | product 參數 → DB product row | 下架商品購買失敗 |

**Ordering / concurrency (design-level):** 七個 PBT 檔案完全獨立，可同時開發。各自 `beforeAll` reset DB → seed guild + 相關資料。

## Data & persistence (design-level)

| Resource                      | Typical readers/writers | Consistency expectation |
| ----------------------------- | ----------------------- | ----------------------- |
| `currency_config` table       | CurrencyManagementFacade (write), CurrencyConfigService (read) | Guild-level singleton |
| `product` table               | ProductManagementFacade (write), ShopService (read) | Status transition (active/inactive) |
| `redemption_code` table       | RedemptionService (write) | Code uniqueness |
| `dice_config` table           | GameConfigManagementFacade (write) | Guild-level singleton |
| `token_account` table         | GameTokenManagementFacade (write) | Token ≥ 0 |
| `escort_option_catalog` table | DispatchManagementFacade (write) | Option ordering |
| `ai_channel_restriction` table | AIConfigManagementFacade (write) | Channel ID uniqueness |

## Invariants (system-level)

| Invariant | What breaks it architecturally | Symptoms if violated |
| --------- | ------------------------ | -------------------- |
| 管理設定變更不影響既有用戶餘額 | Admin facade 誤呼叫 balance adjustment | 用戶餘額意外變更 |
| 商品下架不影響已存在 pending 訂單 | ProductService 未區分新訂單與既有訂單 | 既有訂單無法完成 |

## Batch-only

Admin PBT 依賴 test-infra 的 `createTestContainer` 和 seed factory，以及 economy/shop/dispatch/ai 各 module 的 DI 配置。
