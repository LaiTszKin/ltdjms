# Contract: Integration PBT Test Infrastructure

- Date: 2026-05-22
- Feature: Integration PBT Test Infrastructure
- Change Name: test-infra

## Scope

- **External deps in this doc:** 2
- `testcontainers` + `fast-check` — 兩個外部 npm 套件，無網路 API 依賴

## Dependencies

### testcontainers (Node.js)

#### Evidence

| Primary docs URL(s)             | Sections / anchors used |
| ------------------------------- | ----------------------- |
| https://node.testcontainers.org/ | Quick Start, PostgreSQL module, Container lifecycle |

**Version revision assumed:** `@testcontainers/postgresql` ^10.x (latest stable)，`testcontainers` ^10.x

#### Facts we rely on (must be citeable)

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| `PostgreSqlContainer` 自動 pull `postgres:16-alpine` image | node.testcontainers.org — PostgreSQL module |
| `container.start()` 回傳 started container with `getConnectionUri()` | node.testcontainers.org — Quick Start |
| `withDatabaseName()` / `withUsername()` / `withPassword()` 設定 | node.testcontainers.org — PostgreSQL module |
| Container 在 `container.stop()` 後自動移除 | node.testcontainers.org — Container lifecycle |

#### Limits & failures (coding obligations)

| Category                         | Doc fact | Meaning while executing **`tasks.md`** |
| -------- | --------- | ---------------------------------------- |
| Docker daemon required | testcontainers 需要 Docker socket 連線 | globalSetup 需檢查 Docker 可用性；不可用時 skip 而非 crash |
| Image pull 可能超時 | 首次 pull 可能需要 30-60s | 設定合理 timeout（120s） |
| Port 綁定 | 自動綁定隨機 port | 使用 `getConnectionUri()` 而非硬編 port |

#### Security & secrets (policy level)

| Concern           | Constraint |
| ----------------- | ---------- |
| Auth / scopes    | 測試用 DB 密碼為隨機生成，僅測試期間有效 |
| Secret keys (names)| 無 — 測試環境無機密 |

#### Integration anchors (`EXT-###`)

| ID        | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| --------- | ---------------------------------------------------------- | ----------------------------------------------------------- | --------------------- |
| `EXT-001` | `PostgreSqlContainer` from `@testcontainers/postgresql` | 必須 `start()` 後才能 `getConnectionUri()`；必須 `stop()` 清理 | 不可假設 port 固定 |
| `EXT-002` | `fc.assert` / `fc.property` from `fast-check` | 預設 100 次 run per property；可透過 `numRuns` 調整 | 不可假設測試順序 |

**Doc-level ordering constraint:** `EXT-001`（容器啟動）→ 其他所有操作

#### Trace hooks (no task parroting)

- Spec IDs covered: R1.1–R1.4, R2.1–R2.3
- Related **`design.md`** module keys / `INT-###`: `INT-001`, `INT-002`
- **Unknown / `TBD`:** None
