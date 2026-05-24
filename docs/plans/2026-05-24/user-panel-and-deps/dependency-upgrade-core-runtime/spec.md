# Spec: dependency-upgrade-core-runtime

- Date: 2026-05-24
- Feature: dependency-upgrade-core-runtime
- Owner: laitszkin

## Goal

將 monorepo 核心 runtime 外部依賴升級至最新穩定版本，重點處理 zod 4、drizzle-orm 0.45、pino 10、discord.js 14.26 等 major/minor breaking changes。

## Scope

### In Scope
- 升級所有 package 的 runtime dependencies：
  - `zod@^4.4.3`、`drizzle-orm@^0.45.2`、`drizzle-kit@^0.31.10`
  - `pg@^8.21.0`、`pino@^10.3.1`、`discord.js@^14.26.4`
  - `ioredis@^5.10.1`、`tsyringe@^4.10.0`、`reflect-metadata@^0.2.2`
  - `undici@^8.3.0`（已最新，對齊版本號）
- 修復 zod 4 schema API 變更（`.strict()` → `.strict()` 行為、error map、coerce 等）
- 修復 pino 10 logger 初始化/API 變更
- 統一 `pino` 版本（admin/ai/bot/shared/shop 不再混用 ^9.0.0 與 ^9.6.0）
- 統一 `pg` 版本（admin devDep ^8.21.0 與其他 ^8.13.0 對齊）
- 統一 `reflect-metadata`：dispatch 改為 runtime dependency（與其他 package 一致）

### Out of Scope
- `@langchain/*`（langchain spec）
- `express`（express spec）
- TypeScript/Vitest/ESLint（tooling spec）
- user-panel 功能

## Functional Behaviors (BDD)

### Requirement 1: Zod 4 遷移
**GIVEN** 各 package 使用 zod 3 schema 驗證
**WHEN** 升級至 zod 4.4.3
**THEN** 所有 schema 解析行為與升級前一致
**AND** `make test` 通過

**Requirements**:
- [ ] R1.1 所有 package `zod` dependency 更新至 `^4.4.3`
- [ ] R1.2 修復 zod 4 breaking API（參考 [Zod 4 migration](https://zod.dev/v4/changelog)）
- [ ] R1.3 既有 zod schema 單元測試全綠

### Requirement 2: Drizzle ORM 升級
**GIVEN** drizzle-orm 0.42 + drizzle-kit 0.30
**WHEN** 升級至 0.45.2 / 0.31.10
**THEN** schema 定義與 query 行為不變
**AND** migration 工具仍可正常執行

**Requirements**:
- [ ] R2.1 `drizzle-orm@^0.45.2` 所有 package 對齊
- [ ] R2.2 `drizzle-kit@^0.31.10` shared package 更新
- [ ] R2.3 整合測試中 DB query 全綠

### Requirement 3: Pino 10 升級
**GIVEN** logger 使用 pino 9
**WHEN** 升級至 pino 10.3.1
**THEN** log 輸出格式與 level 行為不變
**AND** bot 啟動無 logger 初始化錯誤

**Requirements**:
- [ ] R3.1 所有 package `pino` 統一為 `^10.3.1`
- [ ] R3.2 修復 pino 10 transport/API breaking changes
- [ ] R3.3 bot 啟動 smoke test 日誌正常

### Requirement 4: Discord.js 與基礎設施 patch 升級
**GIVEN** discord.js 14.18
**WHEN** 升級至 14.26.4
**THEN** slash command、button、modal interaction 行為不變

**Requirements**:
- [ ] R4.1 `discord.js@^14.26.4` 所有 package 對齊
- [ ] R4.2 `ioredis@^5.10.1`、`pg@^8.21.0`、`tsyringe@^4.10.0` 對齊
- [ ] R4.3 `make verify` 全綠

## Error and Edge Cases
- [ ] zod 4 預設 error message 格式變更 — 更新測試 oracle 而非降級 zod
- [ ] drizzle query builder API 微調 — 僅修 compile error
- [ ] pino 10 移除 deprecated API — 更新 logger factory

## Clarification Questions
None

## References
- Official docs:
  - [Zod 4 Changelog](https://zod.dev/v4/changelog)
  - [Drizzle ORM Releases](https://github.com/drizzle-team/drizzle-orm/releases)
  - [Pino Documentation](https://getpino.io/)
  - [discord.js Guide](https://discordjs.guide/)
- Related code files:
  - `packages/shared/src/`（zod schemas、logger）
  - `packages/*/package.json`
