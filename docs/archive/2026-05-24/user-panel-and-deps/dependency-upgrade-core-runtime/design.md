# Design: dependency-upgrade-core-runtime

- Date: 2026-05-24
- Feature: dependency-upgrade-core-runtime

## Traceability

| | |
| --- | --- |
| Requirement IDs | R1.1-R4.3 |
| In-scope modules | 所有 `packages/*`、`apps/bot` |
| Prerequisites | `dependency-upgrade-tooling` 完成 |
| Batch coordination | `../coordination.md` |

## Target vs baseline

| Package | Baseline | Target |
| ------- | -------- | ------ |
| zod | ^3.24.0 | ^4.4.3 |
| drizzle-orm | ^0.42.0 | ^0.45.2 |
| pino | ^9.x | ^10.3.1 |
| discord.js | ^14.18.0 | ^14.26.4 |
| pg | ^8.13.0 | ^8.21.0 |

## Upgrade strategy

1. **Bump all versions** in one commit per package group (shared first, then dependents)
2. **Fix zod schemas** package-by-package: shared → economy → shop → admin → ai → bot
3. **Fix pino** in shared logger module first, propagate to consumers
4. **Run tests** after each package group

## Test strategy summary

| Layer | Scope | Key cases |
| ----- | ----- | --------- |
| Unit | zod schema validators | 既有 schema 測試 |
| Unit | drizzle repos | repository 單元/整合測試 |
| Integration | bot startup | DI + logger init |
| Gate | full | `make verify` |
