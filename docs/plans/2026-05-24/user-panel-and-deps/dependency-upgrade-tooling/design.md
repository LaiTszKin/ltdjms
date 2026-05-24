# Design: dependency-upgrade-tooling

- Date: 2026-05-24
- Feature: dependency-upgrade-tooling
- Change Name: dependency-upgrade-tooling

## Traceability

| | |
| --- | --- |
| Requirement IDs | R1.1-R4.2 |
| In-scope modules | 根 tooling、`packages/*/tsconfig.json`、`packages/*/vitest.config.ts` |
| External systems touched | None |
| Batch coordination | `../coordination.md`、`../preparation.md` |

## Target vs baseline

| | Baseline | Target |
| --- | --- | --- |
| TypeScript | 5.5 | 6.0.3 |
| Vitest | 3.x | 4.1.7 |
| ESLint | 9.x | 10.4.0 |
| @types/node | ^20 | ^22 |
| Node runtime | CI 20+22 | Node 22 only |

## Boundaries

- Entry surface(s): `make build`、`make test`、`make lint`
- Trust boundary crossed: None
- Outside → inside: developer CLI → tsc/vitest/eslint

## Modules

| Module key | Responsibility | Owned artifacts |
| ---------- | -------------- | --------------- |
| `root/tooling` | 集中 devDependencies 與共用 config | `package.json`、`eslint.config.mjs`、`vitest.config.ts` |
| `packages/*/tsconfig` | 各 package 編譯設定 | `packages/*/tsconfig.json` |

## Interaction anchors (`INT-###`)

| ID | Intent | Caller → Callee | Coupling kind | Information crossing | Failure propagation |
| --- | --- | --- | --- | --- | --- |
| INT-001 | 編譯 | `make build` → `tsc -b` | CLI | tsconfig project references | 編譯 error 中斷 build |
| INT-002 | 測試 | `make test` → vitest workspace | CLI | vitest projects config | 測試 fail 中斷 verify |

## Requirement linkage

| Requirement | Design element |
| ----------- | -------------- |
| R1.x | TS 6 project references 不變，僅版本 bump + 修復 break |
| R2.x | Vitest workspace 維持逐 package project 結構 |
| R3.x | flat config 維持，更新 parser/plugin 版本 |
| R4.x | @types/node 22 對齊 preparation Node 基線 |

## Test strategy summary

| Layer | Scope | Key cases |
| ----- | ----- | --------- |
| Build gate | 全 monorepo | `make build` |
| Test gate | 全 monorepo | `make test` |
| Lint gate | 全 monorepo | `make lint` |
| Format gate | 全 monorepo | `make format-check` |
