# Checklist: dependency-upgrade-core-runtime

- Date: 2026-05-24
- Feature: dependency-upgrade-core-runtime

## Pre-implementation
- [x] `dependency-upgrade-tooling` 已完成且 `make verify` 通過

## Implementation
- [x] R1.1-R1.3 Zod 4 遷移完成
- [x] R2.1-R2.3 Drizzle 升級完成
- [x] R3.1-R3.3 Pino 10 升級完成
- [x] R4.1-R4.3 Discord.js 與基礎設施對齊

## Verification
- [ ] `make verify`（需 Docker；本機無 container runtime）
- [ ] `pnpm why zod` — 僅 zod 4.x（`@langchain/*` 仍帶入 zod 3.25.76，待 langchain spec 處理）
- [x] `pnpm why pino` — 僅 pino 10.x

## Test mapping

| Test ID | Requirement | Command |
| ------- | ----------- | ------- |
| UT-010 | R1.3 | `pnpm vitest run --project @ltdjms/shared -t schema` |
| UT-011 | R2.3 | `pnpm vitest run --project @ltdjms/economy` |
| IT-010 | R3.3 | bot 啟動 smoke（`make start-dev` 日誌無 error） |
| REG-010 | R4.3 | `make verify` |
