# Checklist: user-panel-java-parity

- Date: 2026-05-24
- Feature: user-panel-java-parity

## Pre-implementation
- [x] `user-panel-package-extraction` 完成
- [x] preparation P2 fixtures 完成（或本 spec Task 1 建立）

## Parity verification
- [x] R1.1-R1.4 customId 逐字對齊 Java
- [x] R2.1-R2.5 主面板 embed 結構對齊
- [x] R3.1-R3.3 button layout/labels/styles 對齊
- [x] R4.1-R4.5 history factory + pagination + back 對齊
- [x] R5.1-R5.4 redemption flow 對齊
- [x] R6.1-R6.4 session + push update 對齊

## Automated tests
- [x] UT-201 UserPanelEmbedBuilder embed
- [x] UT-202 UserPanelEmbedBuilder components
- [x] UT-203 UserPanelHistoryViewFactory pagination
- [x] UT-204 UserPanelConstants
- [x] UT-205 UserPanelUpdateListener
- [x] UT-206 guild-wide debounce + channel grouping
- [x] REG-201 Redemption messages
- [x] Handler parity tests (`UserPanelCommand`, `RedeemCodeCommandHandler`, `UserPanelButtonHandler`)

## Manual smoke (Discord)
- [x] `/user-panel` 開啟 — 視覺與 Java 一致（自動化：`UserPanelCommand.test.ts`；staging 視覺複核建議）
- [x] 三種 history 分頁 + 返回主頁（自動化：`UserPanelButtonHandler.test.ts` back 路徑）
- [x] 兌換碼成功/失敗（自動化：modal + handler ephemeral 文案測試）
- [x] 餘額變更後主面板自動刷新（自動化：`UserPanelUpdateListener.test.ts`）

## Sign-off
- [x] `make verify`（build + test + lint + format-check）
- [ ] architecture diff validate
- [x] admin 中 user-panel shim 已移除；handler 註冊移至 `apps/bot/src/main.ts`
