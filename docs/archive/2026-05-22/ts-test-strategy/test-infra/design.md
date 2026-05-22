# Design: Integration PBT Test Infrastructure

- Date: 2026-05-22
- Feature: Integration PBT Test Infrastructure
- Change Name: test-infra

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1.1–R1.4, R2.1–R2.3, R3.1–R3.6, R4.1–R4.6, R5.1–R5.4, R6.1–R6.3          |
| In-scope modules (≤3)       | `packages/shared/src/infra/database/`, `packages/shared/src/__tests__/`      |
| External systems touched    | Docker daemon (via testcontainers)                                           |
| Batch coordination          | `../coordination.md`                                                        |

## Target vs baseline

|                       | Baseline (today) | Target (after this change) |
| --------------------- | ---------------- | --------------------------- |
| Structure / ownership | 各 package 獨立單元測試，使用 mock DB / mock DI | 共享的 Integration PBT 基礎設施：testcontainers + seed factory + arbitrary + test DI helper，所有 package 統一使用 |

## Boundaries

- Entry surface(s): vitest globalSetup / `createTestContainer()` / seed factory functions / arbitrary generators
- Trust boundary crossed: `None`（所有組件為內部測試基礎設施，無外部輸入）
- Outside → inside (one line): `vitest runner` → `globalSetup` → `testcontainers PostgreSQL`

## Modules (nouns only)

| Module key | Responsibility (one sentence) | Owned artifacts (types, tables, queues) |
| ---------- | ---------------------------- | ---------------------------------------- |
| `testcontainer` | 管理 PostgreSQL 容器的啟動、migration、停止生命週期 | 環境變數 `DB_CONNECTION_URL`, template DB |
| `seeder` | 提供可組合的 seed data factory functions | `SeedGuildOptions`, `SeedUserOptions` 等型別 |
| `arbitrary` | 封裝 fast-check arbitrary 產生器 | `guildId()`, `userId()`, `positiveAmount()` 等 |
| `testContainer` | 建立測試用 DI container，註冊真實 DB + mock 依賴 | `createTestContainer()` helper |
| `assertion` | 提供業務不變量驗證 helper | `assertBalanceConserved()`, `measureResponseTime()` 等 |

---

## Interaction anchors (`INT-###`)

| ID        | Intent (when this coupling matters) | Caller → Callee | Coupling kind | Information / state crossing (summary) | Failure / propagation expectation (summary) |
| --------- | ------------------------------------ | --------------- | ---------------------------------------------------------------------------------------- | -------------------------------------- | ------------------------------------------- |
| `INT-001` | vitest globalSetup 啟動 container 後，migration runner 需要 DB connection | `globalSetup` → `runMigrations` | sync call | DB pool → migration SQL files | 若 migration 失敗，globalSetup 拋錯、測試全部 skip |
| `INT-002` | 每個 test file 的 `beforeAll` 呼叫 `resetDatabase()` 確保隔離 | `test file` → `test-db-reset` | sync call | Pool → `DROP/CREATE DATABASE` SQL | 若 reset 失敗，該 file 全部測試 skip |
| `INT-003` | PBT 測試使用 seed factory 建立測試資料，使用 arbitrary 產生隨機輸入 | `PBT test` → `seeder` + `arbitrary` | sync call | Seed options → DB rows | Seed 失敗時測試失敗 |
| `INT-004` | 測試使用 `createTestContainer()` 建立 DI 容器後再呼叫 module configure | `PBT test` → `testContainer` → `configure*Module` | sync call | DB pool + config → DI container | DI 配置失敗時測試失敗 |

**Ordering / concurrency (design-level):** globalSetup → (每個 test file 內: resetDatabase → seed → createTestContainer → run PBT) → globalTeardown。多個 test file 可並行但需各自使用獨立 DB name 或透過 mutex 序列化 reset。

## Requirement linkage (coarse ordering)

### R1 (Testcontainer) → R2 (Template reset) → R3-6 (Factory/Arbitrary/DI/Assertion) → R7 (Smoke)

- Anchor order hint: `INT-001` → `INT-002` → `INT-003` + `INT-004`
- Narrative glue:
  - 容器必須先啟動才能建立 template DB
  - Template DB 就緒後才能開發 seed factory（需要真實 DB 驗證）
  - Seed factory + arbitrary + DI helper + assertion helper 可並行開發
  - 全部完成後以 smoke test 驗證整合

## Data & persistence (design-level)

| Resource                      | Typical readers/writers (module keys) | Consistency expectation (ordering, idempotency) |
| ----------------------------- | ------------------------------------- | ------------------------------------------------ |
| Testcontainer PostgreSQL      | globalSetup (write), all tests (read/write), globalTeardown (delete) | Template DB 不可被寫入（read-only） |
| Template DB (`template_clean`) | `resetDatabase` (read) | 僅建立一次（globalSetup），之後唯讀 |
| Test DB (per test file)       | seed factory (write), PBT tests (read/write) | 每 run 前 reset，隔離保證 |

## Invariants (system-level)

| Invariant | What breaks it architecturally | Symptoms if violated |
| --------- | ------------------------ | -------------------- |
| Test isolation: 每個 PBT run 的 DB 狀態完全獨立 | 多個 test file 共享同一 DB 且未正確 reset | 測試互相影響、flaky tests |
| Template DB 不可變更：template_clean 僅含 schema | seed factory 或 migration 誤寫入 template DB | 所有新 test DB 包含殘留資料 |

## Tradeoffs inherited by implementation

| Decision | Rejected alternative | Locks in (for **`tasks.md`**) |
| -------- | -------------------- | ---------------------------- |
| 使用 template DB 快速 reset | truncate all tables（慢，50+ tables 需多次 roundtrip） | `resetDatabase()` 需 DROP/CREATE DATABASE 權限 |
| 每個 test file 獨立 DB | 共享單一 DB + transaction rollback（Drizzle 不支援 savepoint） | 需要動態 DB name 避免衝突 |
| globalSetup 級別啟動 container | per-test-file 啟動（啟動成本 30s/次，太慢） | vitest globalSetup/globalTeardown 配置 |

## Batch-only

test-infra 為 batch 中的前置 spec。所有 factory function、arbitrary、assertion helper 的簽名在此凍結，後續 economy-pbt、shop-pbt、ecpay-e2e、admin-pbt 僅 import 使用，不可修改。
