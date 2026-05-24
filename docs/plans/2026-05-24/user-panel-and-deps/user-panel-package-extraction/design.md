# Design: user-panel-package-extraction

- Date: 2026-05-24
- Feature: user-panel-package-extraction

## Traceability

| | |
| --- | --- |
| Requirement IDs | R1.1-R4.2 |
| In-scope modules | 新建 `packages/user-panel/`、`packages/admin/`、`apps/bot/` |
| Prerequisites | deps upgrade specs 完成（建議） |
| Batch coordination | `../coordination.md` |

## Target vs baseline

| | Baseline | Target |
| --- | --- | --- |
| 程式碼位置 | `@ltdjms/admin/src/panel/user/` | `@ltdjms/user-panel/` |
| Facade | admin/MemberInfoFacade | user-panel/MemberInfoFacade |
| DI | AdminModule 直接 new | configureUserPanelContainer() |
| Package count | 7 | 8 (+user-panel) |

## Boundaries

- Entry: Discord `/user-panel` → SlashCommandListener → UserPanelCommand
- Trust boundary: Discord ephemeral interaction ↔ bot backend
- Data sources: economy (balance/tx), games (tokens/tx), shop (redemption/tx)

## Modules

| Module key | Responsibility | Artifacts |
| ---------- | -------------- | --------- |
| `user-panel/commands` | Slash command handlers | UserPanelCommand, RedeemCodeCommandHandler |
| `user-panel/handlers` | Button/modal handlers | TransactionHistoryHandler, RedemptionCodeHandler |
| `user-panel/services` | Thin service layer（可選 UserPanelService） | UserPanelService（parity spec 可引入） |
| `user-panel/facades` | 資料聚合 | MemberInfoFacade |
| `user-panel/session` | 15min session | PanelSessionManager |
| `user-panel/listeners` | Domain event push | UserPanelUpdateListener |
| `user-panel/di` | DI wiring | user-panel-module.ts |
| `user-panel/i18n` | zh-TW strings | zh-TW.ts |

## Interaction anchors

| ID | Caller → Callee | Purpose |
| --- | --- | --- |
| INT-101 | UserPanelCommand → MemberInfoFacade | getUserPanelView |
| INT-102 | TransactionHistoryHandler → MemberInfoFacade | get*TransactionPage |
| INT-103 | RedemptionCodeHandler → MemberInfoFacade | redeemCode |
| INT-104 | UserPanelUpdateListener → PanelSessionManager | push embed update |
| INT-105 | configureUserPanelContainer → economy/games/shop DI | resolve services |
| INT-106 | AdminModule → user-panel module | register handlers to SlashCommandListener |

## Dependency chain

```
shared → economy → games → shop → user-panel → admin → bot
```

## Test strategy

| Layer | Cases |
| ----- | ----- |
| Unit | MemberInfoFacade（遷移既有測試） |
| Unit | PanelSessionManager TTL |
| Integration | DI container resolve all USER_PANEL_TOKENS |
| Smoke | `/user-panel` 仍可開啟（manual or mock interaction） |
