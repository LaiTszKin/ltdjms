# Spec: dependency-upgrade-tooling

- Date: 2026-05-24
- Feature: dependency-upgrade-tooling
- Owner: laitszkin

## Goal

將 monorepo 開發工具鏈（TypeScript、Vitest、ESLint、Prettier、typescript-eslint）升級至最新穩定版本，為後續 runtime major 升級提供一致的編譯與測試基礎。

## Scope

### In Scope
- 根 `package.json` devDependencies 升級：`typescript@^6.0.3`、`vitest@^4.1.7`、`eslint@^10.4.0`、`typescript-eslint@^8.59.4`、`prettier@^3.8.3`、`tsx@^4.22.3`
- 各 package `devDependencies` 同步 `typescript`、`vitest`、`@types/node@^22`（對齊 Node 22 基線）
- 修復 TypeScript 6 / Vitest 4 / ESLint 10 breaking changes 導致的編譯與 lint 錯誤
- 更新 `eslint.config.mjs`、`vitest.config.ts`、各 package vitest config（若 API 變更）
- CI workflow 適配新工具版本

### Out of Scope
- Runtime dependencies（zod、drizzle、discord.js、pino、langchain、express）
- 業務邏輯修改
- user-panel 功能

## Functional Behaviors (BDD)

### Requirement 1: TypeScript 6 升級
**GIVEN** monorepo 目前使用 TypeScript 5.5
**WHEN** 升級至 TypeScript 6.0.3 並執行 `make build`
**THEN** 所有 package 編譯成功無 error
**AND** 無新增 `@ts-ignore` 或 `@ts-expect-error` 以繞過型別錯誤

**Requirements**:
- [x] R1.1 根與各 package `typescript` devDependency 更新至 `^6.0.3`
- [x] R1.2 根 `tsconfig.json` 與各 package tsconfig 適配 TS 6（含 `moduleResolution`、project references）
- [x] R1.3 `pnpm -r exec tsc --noEmit` 或 `make build` 全綠

### Requirement 2: Vitest 4 升級
**GIVEN** 測試目前使用 Vitest 3
**WHEN** 升級至 Vitest 4.1.7
**THEN** `make test` 全部通過
**AND** 測試覆蓋率與測試數量不減少

**Requirements**:
- [x] R2.1 根與各 package `vitest` devDependency 更新至 `^4.1.7`
- [x] R2.2 `vitest.config.ts` 適配 Vitest 4 API（pool、projects、reporters 若有 breaking change）
- [ ] R2.3 `make test` exit code 0（本機需 Docker testcontainers；Vitest 4 非 DB 專案已驗證）

### Requirement 3: ESLint 10 + typescript-eslint 升級
**GIVEN** ESLint 9 flat config 已存在
**WHEN** 升級 ESLint 10 與 typescript-eslint 8.59.4
**THEN** `make lint` 通過
**AND** 無停用核心規則以通過 lint

**Requirements**:
- [x] R3.1 根 `eslint`、`typescript-eslint` devDependency 更新
- [x] R3.2 `eslint.config.mjs` 適配 ESLint 10 flat config API
- [ ] R3.3 `make lint` exit code 0（124 項既有 typed-lint violation 待修）

### Requirement 4: Node 22 型別對齊
**GIVEN** preparation spec 確立 Node 22 基線
**WHEN** 更新 `@types/node` 至 `^22.0.0`（各 package devDependency）
**THEN** 無 Node 型別相關編譯錯誤

**Requirements**:
- [x] R4.1 所有 package `@types/node` 統一為 `^22.0.0`
- [x] R4.2 移除對 Node 20 特定型別的依賴假設

## Error and Edge Cases
- [x] Vitest 4 變更導致 PBT 測試 timeout — 調整 `testTimeout` 而非跳過測試（現有 config 已含 30s timeout，無需變更）
- [x] TypeScript 6 stricter checks 暴露既有 bug — 修復型別而非降級（已修 `apps/bot/tsconfig.json` references 與 Makefile bot build）
- [ ] ESLint 10 新規則誤報 — 僅對明確 false positive 使用 inline disable 並註明原因（124 項 violation 為 develop 既有 debt，非 ESLint 10 新規則）

## Clarification Questions
None — Node 22 基線由 preparation spec 定義。

## References
- Official docs:
  - [TypeScript 6.0 Release Notes](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-6-0.html)
  - [Vitest 4 Migration Guide](https://vitest.dev/guide/migration)
  - [ESLint 10 Migration](https://eslint.org/docs/latest/use/migrate-to-10.0.0)
- Related code files:
  - `package.json`
  - `eslint.config.mjs`
  - `vitest.config.ts`
  - `tsconfig.json`
