# Design: Issue 74 設定 Schema 正規化

- Date: 2026-04-09
- Feature: Issue 74 設定 Schema 正規化
- Change Name: issue-74-config-schema-canonicalization

## Design Goal
讓 `EnvironmentConfig`、packaged defaults、測試與文件對外只說同一套 schema，並把「哪個 resource file 才是 canonical defaults」這件事明確寫死，而不是留給人猜。

## Change Summary
- Requested change: 依 issue #74 消除 `.conf`、`.properties` 與文件間的平行 config schema。
- Existing baseline: `EnvironmentConfig` 讀的是 `discord.bot.token`、`db.pool.*` 等 canonical path；`application.properties` 已部分符合；`application.conf` 與文件卻仍描述 `discord.bot-token`、`database.*` 等不同 schema。
- Proposed design delta: `EnvironmentConfig` 繼續作為 schema owner；`application.properties` 成為唯一 canonical packaged defaults；`application.conf` 被移除或降為不承載 live defaults 的 compatibility shim；文件與測試同步對齊。

## Scope Mapping
- Spec requirements covered: `R1.1-R1.3`, `R2.1-R2.3`, `R3.1-R3.3`
- Affected modules:
  - `src/main/java/ltdjms/discord/shared/EnvironmentConfig.java`
  - `src/main/resources/application.properties`
  - `src/main/resources/application.conf`
  - `src/test/java/ltdjms/discord/shared/EnvironmentConfigDotEnvIntegrationTest.java`
  - `docs/development/configuration.md`
- External contracts involved: `Lightbend Config / Typesafe Config Load Behavior`
- Coordination reference: `../coordination.md`

## Current Architecture
目前設定系統有四個描述層：
1. `EnvironmentConfig`：真正被 runtime 查詢的 canonical path。
2. `.env` / system env：由 `EnvironmentConfig` 映射到 canonical path。
3. `application.properties`：已被測試覆蓋的 packaged defaults。
4. `application.conf` 與文件：仍攜帶另一套 schema 與 fallback 敘述。

這導致維運人員可能修改了 repository 中的某個 resource key，但 runtime 根本不會去讀它。

## Proposed Architecture
- schema owner：`EnvironmentConfig` 定義 canonical path 與 env mapping。
- packaged defaults owner：`application.properties` 作為唯一 live defaults 檔。
- legacy resource handling：`application.conf` 若保留，只能作為 compatibility shim / 註解載體，不能再承載另一套 live defaults；更理想是直接移除。
- documentation owner：`docs/development/configuration.md` 只描述 canonical path 與實際 fallback chain。

## Implemented Architecture
- `EnvironmentConfig` 改為只解析 `application.properties`，不再以 `ConfigFactory.load()` 混入 `application.conf`。
- `application.properties` 已補齊 canonical key namespace 與對應預設值，與 runtime getter/fallback chain 對齊。
- `application.conf` 改為 comment-only compatibility shim，避免再次成為第二套 live schema。
- `EnvironmentConfigDotEnvIntegrationTest` 新增 canonical schema 與文件 drift regression assertions。

## Component Changes

### Component 1: `EnvironmentConfig`
- Responsibility: 定義 canonical config path、優先序、env 映射與 built-in defaults。
- Inputs: system env、`.env`、packaged defaults、built-in defaults。
- Outputs: runtime getter 行為。
- Dependencies: `ConfigFactory.load()`, `DotEnvLoader`
- Invariants:
  - 只有一套 canonical key namespace。
  - env var contract 維持相容。
  - fallback 順序保持 `system env > .env > packaged defaults > built-in defaults`。

### Component 2: Packaged Defaults Resources
- Responsibility: 提供 classpath 上的 default config 值。
- Inputs: canonical key namespace。
- Outputs: 供 `ConfigFactory.load()` 讀取的 defaults。
- Dependencies: resource 載入機制。
- Invariants:
  - 只有一個檔案承載 live defaults。
  - 不可再存在第二套 drift schema。

### Component 3: Shared Config Documentation & Tests
- Responsibility: 對外描述真實 config contract，並以測試鎖住其行為。
- Inputs: canonical schema、resource defaults、fallback chain。
- Outputs: 開發文件、drift regression tests。
- Dependencies: `EnvironmentConfigDotEnvIntegrationTest`, `docs/development/configuration.md`
- Invariants:
  - 文件與測試都以 canonical schema 為準。
  - required key / fallback 行為需有具體測試證據。

## Sequence / Control Flow
1. 啟動時 `EnvironmentConfig` 先建立 canonical defaults 與 env 映射，再經 `ConfigFactory.load()` 合成 runtime config。
2. packaged defaults 僅由 canonical defaults 檔提供；若有 legacy resource，不能承載另一套 live key。
3. 文件與測試直接對 canonical schema 與 fallback chain 做說明與回歸驗證。

## Data / State Impact
- Created or updated data: classpath resource 與 shared config 測試／文件；不影響業務資料
- Consistency rules:
  - 環境變數名稱不變
  - canonical path 與 packaged defaults 同步
  - 文件聲明與測試證據一致
- Migration / rollout needs:
  - 若移除 `application.conf`，需同步更新文件與任何依賴該檔存在的本機說明
  - 若保留 shim，需在文件中標示其非 canonical 身分

## Risk and Tradeoffs
- Key risks:
  - 若誤刪 live defaults，可能讓某些 getter 回退到 built-in defaults 或 required-key failure。
  - 若只修文件不修 resource / tests，drift 仍會回來。
- Rejected alternatives:
  - 同時維持兩個 live packaged defaults 檔並要求人工同步：已證明會漂移。
  - 只在文件上備註差異，不做 canonicalization：無法消除維運誤導。
- Operational constraints:
  - 不可破壞現有 `.env` 容錯與 `DATABASE_*` fallback 邏輯。
  - 不可偷偷改變 production 需要的 env var 名稱。

## Validation Plan
- Tests:
  - Unit / regression：canonical packaged defaults 與主要 getter fallback
  - Regression：`.env`、packaged defaults、built-in defaults 順序
  - Documentation check：文件中的範例 key 與實際 canonical schema 一致
- Contract checks: 以 Lightbend Config 官方 load behavior 驗證「多個標準 resource 會被載入」的前提，故應用層必須明確指定 canonical defaults owner。
- Rollback / fallback: 若 canonicalization 導致 getter fallback 錯誤，可暫時回退到上一版 resource 組合，但必須在回退後立即補 drift test，避免再次雙源化。

## Execution Evidence
- Verified tests: `mvn -q -Dtest=EnvironmentConfigTest,EnvironmentConfigDotEnvIntegrationTest test`
- Key evidence:
  - canonical resource regression：`shouldKeepApplicationPropertiesAsOnlyLivePackagedDefaultsSchema`
  - documentation regression：`shouldDocumentTheSameCanonicalSchemaAndFallbackChain`
  - fallback order / resilience：既有 `.env` precedence、missing key、malformed `.env` 案例全部持續通過

## Open Questions
None
