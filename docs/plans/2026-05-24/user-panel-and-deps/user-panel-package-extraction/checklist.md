# Checklist: user-panel-package-extraction

- Date: 2026-05-24
- Feature: user-panel-package-extraction

## Pre-implementation
- [x] deps upgrade specs 完成（建議）
- [x] preparation P2 Java oracle fixtures 已建立

## Implementation
- [x] R1.1-R1.4 package 骨架
- [x] R2.1-R2.5 程式碼遷移
- [x] R3.1-R3.4 DI 集成
- [x] R4.1-R4.2 public API

## Verification
- [x] `make build`
- [x] `make test`
- [x] `packages/admin/src/panel/user/` 已移除或僅剩 shim
- [x] `grep -r "MemberInfoFacade" packages/admin/src` 無直接業務引用（shim 除外）

## Test mapping

| Test ID | Requirement | Command |
| ------- | ----------- | ------- |
| UT-101 | R2.3 | `pnpm vitest run --project @ltdjms/user-panel -t MemberInfoFacade` |
| UT-102 | R2.4 | `pnpm vitest run --project @ltdjms/user-panel -t PanelSessionManager` |
| IT-101 | R3.1 | user-panel DI integration test（resolve all tokens） |
| SM-101 | R3.4 | 手動 `/user-panel` smoke |

## Sign-off
- [x] architecture diff 已 render + validate
- [x] coordination 允許 parity spec 開始
