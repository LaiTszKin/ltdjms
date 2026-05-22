# Coordination: Java-to-TypeScript Infrastructure Porting

- Date: 2026-05-22
- Batch: infra-java-to-typescript

## Business Goals

將 Java bot `src/main/java/ltdjms/discord/shared/` 中的基建邏輯完全復刻到 TypeScript `packages/shared/src/`，確保所有功能 1:1 還原且行為正確。

- Batch members: [[cache-invalidation-listener], [localization-centralization], [infra-verification]]
- Shared outcome: TypeScript bot 的基建層與 Java bot 行為完全一致，每個基建組件都有對應的驗證測試
- Out of scope: 業務邏輯變更、新功能開發、Discord API 行為變更

## Design Principles

- Current baseline: TypeScript `packages/shared/` 已完成約 90% 基建遷移（快取、資料庫、DI、事件、設定、型別、Discord 抽象層均已存在）
- Shared invariants: 所有基建組件必須保持與 Java 端相同的語義行為；快取失效必須在資料變更時可靠觸發
- Shared constraints: 不更換現有的 TypeScript 函式庫（tsyringe, pino, drizzle-orm, pg）；不改變現有對外 API
- Legacy direction: Java shared/cache/CacheInvalidationListener.java → TypeScript event-driven cache invalidation；分散式在地化 → 集中式 shared/localization/
- Compatibility window: 無需共存期；補上缺失組件後即可驗證
- Cleanup after cutover: None

## Spec Boundaries

### Ownership Map

#### Spec Set 1: cache-invalidation-listener
- Primary concern: 在 `packages/shared/src/infra/cache/` 建立事件驅動的快取失效監聽器，對齊 Java `CacheInvalidationListener`
- Allowed touch points: `packages/shared/src/infra/cache/`, `packages/shared/src/infra/events/`, `packages/shared/src/infra/di/container.ts`
- Must not change: 各 service 內部既有的直接快取失效邏輯（保留作為內層防禦）

#### Spec Set 2: localization-centralization
- Primary concern: 將分散在 economy、shop、admin 的 Discord 指令在地化字串集中到 `packages/shared/src/localization/`
- Allowed touch points: `packages/shared/src/localization/` (新建), 各 package 的指令 handler (改為從 shared 引用)
- Must not change: 指令的業務邏輯、handler 的行為、在地化字串的實際內容

#### Spec Set 3: infra-verification
- Primary concern: 建立全面的基建驗證測試套件，確認每個 TS 基建組件行為與 Java 對應組件一致
- Allowed touch points: `packages/shared/src/__tests__/` (新建測試), 必要時可讀取 Java 原始碼作為參考
- Must not change: 任何基建組件的實作（除非發現 bug）

### Collisions & Integration

- Shared files & edit rules: `packages/shared/src/index.ts` — 各 spec 如需新增 export，採用 additive-only 規則
- Shared API / schema freeze: `packages/shared` 對外 API 不變；新增類型僅為 additive
- Compatibility shim retention: None
- Merge order: cache-invalidation-listener → localization-centralization → infra-verification (建議順序，非強制依賴)
- Integration checkpoints:
  1. `make build` 通過
  2. `make test` 通過
  3. `make verify` 通過
- Re-coordination trigger: 任一 spec 發現需要修改既有 shared API 簽名時需重新協調
