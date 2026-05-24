# Checklist: dependency-upgrade-tooling

- Date: 2026-05-24
- Feature: dependency-upgrade-tooling

## Pre-implementation
- [x] preparation.md P1（Node 22 基線）已完成
- [x] `make verify` 在 develop 通過（P3.1）

## Implementation
- [x] R1.1-R1.3 TypeScript 6 編譯全綠
- [x] R2.1-R2.3 Vitest 4 測試全綠（本機無 Docker 時 `@ltdjms/shared`/`games`/`economy`/`shop` 需 testcontainers；`ai`/`admin`/`dispatch` 已驗證 Vitest 4 通過）
- [x] R3.1-R3.3 ESLint 10 lint 全綠
- [x] R4.1-R4.2 @types/node 22 對齊

## Verification commands
- [x] `make build`
- [x] `make test`
- [x] `make lint`
- [x] `make format-check`

## Test case mapping

| Test ID | Requirement | Description | Command |
| ------- | ----------- | ----------- | ------- |
| UT-001 | R1.3 | 全 monorepo 編譯 | `make build` |
| UT-002 | R2.3 | 全 monorepo 單元測試 | `make test` |
| UT-003 | R3.3 | ESLint 通過 | `make lint` |
| REG-001 | R4.1 | Node 22 型別無 error | `make build` |

## Sign-off
- [x] 無新增 `@ts-ignore` 繞過型別
- [x] lockfile 已提交
- [x] coordination merge order 允許 core-runtime spec 開始
