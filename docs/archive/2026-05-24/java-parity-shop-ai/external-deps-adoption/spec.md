# Spec: external-deps-adoption

- Date: 2026-05-24
- Feature: external-deps-adoption
- Owner: laitszkin

## Goal

引入並驗證 Java parity batch 所需的外部依賴（LangGraph checkpoint、zod-to-json-schema、@robojs/mock、supertest），建立 Vitest JSON snapshot parity 測試慣例，降低後續 shop/ai spec 的實作與測試複雜度。

## Scope

### In Scope
- 安裝並鎖定版本：
  - `@langchain/langgraph@^1.3.2`、`@langchain/langgraph-checkpoint-postgres@^1.0.1`、`@langchain/langgraph-checkpoint-redis@^1.0.5`、`zod-to-json-schema@^3.25.2` → `packages/ai`
  - `@robojs/mock@0.1.1-next.1`、`supertest@^7.2.2`、`@types/supertest@^6.0.0` → `packages/shop` devDependencies
- LangGraph Postgres checkpoint PoC（連接現有 `pg` pool）
- LangGraph Redis checkpoint PoC（Redis Stack / RedisJSON）
- zod-to-json-schema 從現有 Zod tool schema 生成 JSON Schema 的 PoC
- @robojs/mock slash/button interaction smoke test PoC
- supertest ECPay callback route smoke test PoC
- Vitest `toMatchJsonSnapshot()` / `toMatchJsonSchema()` 共用 helper 與文件

### Out of Scope
- Shop / AI 完整 parity 實作（member spec 負責）
- 用 LangGraph `createReactAgent` 完全取代手寫 agent loop（PoC 後由 ai-agent spec 決定）
- drizzle-zod（P4 可選，本 spec 不引入）
- 修改 Java bot

## Functional Behaviors (BDD)

### Requirement 1: 依賴安裝與 lockfile
**GIVEN** monorepo 使用 pnpm workspace
**WHEN** 執行依賴安裝
**THEN** 所有新套件版本符合 contract.md 鎖定範圍
**AND** `make build` 通過

**Requirements**:
- [x] R1.1 `packages/ai/package.json` 新增 LangGraph 生態 runtime deps
- [x] R1.2 `packages/shop/package.json` 新增 mock/supertest devDeps
- [x] R1.3 `pnpm install` 更新 lockfile 且無 peer dependency 衝突

### Requirement 2: Vitest JSON snapshot 慣例
**GIVEN** user-panel-java-parity 已有 oracle 模式
**WHEN** parity spec 撰寫 UI 測試
**THEN** 共用 helper 支援 embed/component JSON 比對

**Requirements**:
- [x] R2.1 建立 `packages/shared/src/__tests__/parity/json-snapshot.ts`（或等價位置）export `assertJsonParity(actual, oracle)`
- [x] R2.2 文件化慣例：Java fixture → TS `toMatchJsonSnapshot()` 流程

### Requirement 3: LangGraph checkpoint PoC
**GIVEN** 現有 Postgres 與 Redis 基礎設施
**WHEN** 執行 checkpoint PoC 測試
**THEN** Postgres checkpoint 可 write/read thread state
**AND** Redis checkpoint 可 write/read（需 RedisJSON）

**Requirements**:
- [x] R3.1 Postgres checkpoint 整合測試通過（testcontainers 或現有 test DB）
- [x] R3.2 Redis checkpoint 整合測試通過或明確記錄 fallback（僅 Postgres）
- [x] R3.3 PoC 報告：是否保留 REASONING/TOOL_INTENT 外層 streaming 控制

### Requirement 4: zod-to-json-schema PoC
**GIVEN** 現有 Zod tool parameter schema
**WHEN** 呼叫 `zodToJsonSchema`
**THEN** 輸出與 Java `@Tool` JSON schema 結構相容（name/description/parameters）

**Requirements**:
- [x] R4.1 至少 2 個 tool schema PoC 測試通過
- [x] R4.2 與 `@langchain/core` `StructuredTool` binding 相容

### Requirement 5: @robojs/mock PoC
**GIVEN** discord.js v14 interaction 模型
**WHEN** mock slash command `/shop`
**THEN** 可取得 interaction reply payload 供 snapshot

**Requirements**:
- [x] R5.1 shop package 內 1 個 smoke test 通過
- [x] R5.2 若 pre-release 不穩，document fallback hand mock 策略

### Requirement 6: supertest PoC
**GIVEN** shop callback Express server
**WHEN** supertest POST callback endpoint
**THEN** 回傳預期 HTTP status（不需完整 ECPay 簽章）

**Requirements**:
- [x] R6.1 1 個 callback route smoke test 通過
- [x] R6.2 與現有 `payment-callback.test.ts` 不衝突

## Error and Edge Cases
- [x] LangGraph peer dependency 與 `@langchain/core@1.1.48` 不相容 → 記錄並 pin 相容版本
- [x] @robojs/mock 與 discord.js 14 型別衝突 → 使用 `@ts-expect-error` 或 hand mock fallback
- [x] Redis Stack 本機不可用 → ai-agent spec 改 Postgres-only checkpoint
- [x] supertest 與 Express 5 middleware 型別 → 使用 `@types/supertest@^6`

## Clarification Questions
None（依賴版本與用途已在 batch 需求中確認）

## References
- Official docs:
  - LangGraph.js: https://langchain-ai.github.io/langgraphjs/
  - LangGraph checkpoint Postgres: https://www.npmjs.com/package/@langchain/langgraph-checkpoint-postgres
  - zod-to-json-schema: https://www.npmjs.com/package/zod-to-json-schema
  - @robojs/mock: https://www.npmjs.com/package/@robojs/mock
  - supertest: https://github.com/ladjs/supertest
  - Vitest snapshot: https://vitest.dev/guide/snapshot.html
- Related code files:
  - `packages/ai/package.json`
  - `packages/shop/package.json`
  - `docs/archive/2026-05-24/user-panel-and-deps/user-panel-java-parity/fixtures/`
