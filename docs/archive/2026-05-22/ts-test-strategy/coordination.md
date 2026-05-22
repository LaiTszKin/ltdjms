# Coordination: TypeScript 原生移植測試策略

- Date: 2026-05-22
- Batch: ts-test-strategy

## Business Goals

為 TypeScript 原生移植建立完整的 Integration Property-Based Testing 測試體系，確保新 bot 與 Java 版在資料庫語義與對外行為上完全一致。所有用戶／管理員透過前端可操作的功能都需要有對應的測試案例。

- Batch members: [test-infra, economy-pbt, shop-pbt, ecpay-e2e, admin-pbt]
- Shared outcome: 完整的測試體系，包含共享基礎設施、業務不變量 PBT、ECPay E2E 金流測試，總執行時間目標 3-5 分鐘
- Out of scope: Java 版新功能開發、TS 版新功能開發、Discord API 整合測試（非 handler→service→DB 管線部分）

## Design Principles

- Current baseline: 所有 TypeScript packages 已有單元測試（vitest），但缺少整合層級的 PBT 和 E2E 測試。DI 容器使用 tsyringe，資料庫使用 Drizzle ORM + PostgreSQL。
- Shared invariants: `sum(balances)` 守恆、狀態機轉移完整性、冪等性、賠率計算正確性（`input × multiplier === output`）
- Shared constraints: 使用 Testcontainers PostgreSQL 而非 mock DB；使用 `fast-check` 產生隨機輸入；每次 PBT run 前用 template DB 快速 reset（50-100ms）
- Legacy direction: 不測試 Java 版；TS 版為新基準。所有測試僅對 TS 版生效。
- Compatibility window: None
- Cleanup after cutover: None

## Spec Boundaries

### Ownership Map

#### Spec Set 1: test-infra
- Primary concern: 建立共享的 Integration PBT 測試基礎設施
- Allowed touch points: `packages/*/src/__tests__/` 下新建測試共用模組、`packages/shared/src/infra/database/`（Testcontainer 連線工廠）、新增 `vitest` 配置、新增 devDependencies（testcontainers, fast-check）
- Must not change: 任何業務邏輯、任何 DI module 配置、任何 handler/service/repository 實作

#### Spec Set 2: economy-pbt
- Primary concern: Economy 模組的業務不變量 PBT（餘額轉帳、骰子遊戲、遊戲代幣）
- Allowed touch points: `packages/economy/src/__tests__/` 新建 PBT 測試檔，可 import test-infra 提供的共享工具
- Must not change: Economy 模組核心邏輯、DI 配置、handler 行為

#### Spec Set 3: shop-pbt
- Primary concern: Shop 模組的業務不變量 PBT（兌換碼兌換、商店購買、貨幣購買）
- Allowed touch points: `packages/shop/src/__tests__/` 新建 PBT 測試檔，可 import test-infra 提供的共享工具
- Must not change: Shop 模組核心邏輯、DI 配置、handler 行為

#### Spec Set 4: ecpay-e2e
- Primary concern: ECPay 金流 E2E 測試（取號合約、回撥處理、對帳排程）
- Allowed touch points: `packages/shop/src/__tests__/` 新建 E2E 測試檔，可 import test-infra 提供的共享工具
- Must not change: ECPay crypto 實作、callback server 實作、fiat order 核心邏輯

#### Spec Set 5: admin-pbt
- Primary concern: Admin 管理面板 PBT（貨幣設定、商品管理、遊戲設定、派單規則）
- Allowed touch points: `packages/admin/src/__tests__/` 新建 PBT 測試檔，可 import test-infra 提供的共享工具
- Must not change: Admin module 核心邏輯、DI 配置、panel handler 行為

### Collisions & Integration

- Shared files & edit rules: `test-infra` spec 建立的所有基礎設施檔案，後續 spec 只能 import 使用，不可修改。若需擴充基礎設施，必須在 `test-infra` spec 範圍內進行。
- Shared API / schema freeze: Seed data factory 的 factory function 簽名由 `test-infra` 凍結定義。
- Compatibility shim retention: None
- Merge order: test-infra → economy-pbt | shop-pbt | ecpay-e2e | admin-pbt（後四者可並行）
- Integration checkpoints: 所有 spec 完成後，執行 `make test` 確認全部 PBT + E2E 通過
- Re-coordination trigger: 若 test-infra 的介面設計變更，需通知所有下游 spec 更新
