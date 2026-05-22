# Spec: Localization Centralization

- Date: 2026-05-22
- Feature: localization-centralization
- Owner: [To be filled]

## Goal

將分散在 economy、shop、admin 等 package 的 Discord 指令在地化字串集中到 `packages/shared/src/localization/`，對齊 Java `shared/localization/CommandLocalizations.java` 的集中式架構。

## Scope

### In Scope
- 在 `packages/shared/src/localization/` 建立集中式在地化模組
- 移植 Java `CommandLocalizations` 的所有指令名稱、描述、選項、選項值的 zh-TW 翻譯
- 移植 Java `DiceGameMessages` 的骰子遊戲訊息在地化
- 更新各 package 的指令 handler，改為從 `@ltdjms/shared` 引用在地化字串
- 確保 Discord API 註冊指令時能正確套用 `name_localizations` 和 `description_localizations`

### Out of Scope
- 新增其他語言的翻譯
- 修改在地化字串的實際內容（除非與 Java 不一致）
- 修改 Discord API 的在地化機制本身

## Functional Behaviors (BDD)

### Requirement 1: 集中式指令在地化
**GIVEN** `packages/shared/src/localization/` 存在 `CommandLocalizations` 模組
**WHEN** 任何 package 的指令 handler 需要 zh-TW 在地化字串
**THEN** 可從 `@ltdjms/shared` import `CommandLocalizations`
**AND** 取得與 Java `CommandLocalizations.java` 完全相同的翻譯內容

**Requirements**:
- [ ] R1.1 `CommandLocalizations` 包含所有指令名稱的 zh-TW 翻譯（balance, currency-config, adjust-balance, game-token-adjust, dice-game-1, dice-game-1-config, dice-game-2, dice-game-2-config, user-panel, admin-panel, shop, dispatch-panel）
- [ ] R1.2 包含所有指令描述的 zh-TW 翻譯
- [ ] R1.3 包含所有選項名稱的 zh-TW 翻譯（name, icon, mode, member, amount, token-cost, tokens）
- [ ] R1.4 包含所有選項描述的 zh-TW 翻譯
- [ ] R1.5 包含所有 choice 值的 zh-TW 翻譯（add, deduct, adjust）

### Requirement 2: 骰子遊戲訊息在地化
**GIVEN** `packages/shared/src/localization/` 存在 `DiceGameMessages` 模組
**WHEN** economy 模組的骰子遊戲 handler 需要在地化訊息
**THEN** 可從 `@ltdjms/shared` import `DiceGameMessages`
**AND** 取得與 Java `DiceGameMessages.java` 完全相同的訊息內容

**Requirements**:
- [ ] R2.1 包含骰子遊戲結果訊息的 zh-TW 模板
- [ ] R2.2 支援遊戲參數插值（如代幣消耗、獎勵金額）

### Requirement 3: 向後相容遷移
**GIVEN** 既有 package 各自定義在地化字串
**WHEN** 集中式模組上線
**THEN** 各 package 改為從 `@ltdjms/shared` import
**AND** 刪除 package 內的重複在地化定義

**Requirements**:
- [ ] R3.1 economy 模組的指令 handler 改為使用 shared 的在地化
- [ ] R3.2 admin 模組的指令定義改為使用 shared 的在地化
- [ ] R3.3 刪除各 package 內的重複在地化檔案
- [ ] R3.4 SlashCommandRegistrar 能正確讀取並套用在地化

## Error and Edge Cases
- [ ] 缺少某指令的在地化時，Discord 自動 fallback 到英文 canonical name（標準行為）
- [ ] 在地化字串為空時不應導致指令註冊失敗
- [ ] TypeScript 編譯時應能檢查在地化鍵的型別正確性

## Clarification Questions
None — 需求明確，直接對齊 Java `CommandLocalizations` 和 `DiceGameMessages` 的內容和結構。

## References
- Java 原始碼:
  - `src/main/java/ltdjms/discord/shared/localization/CommandLocalizations.java` — 指令在地化
  - `src/main/java/ltdjms/discord/shared/localization/DiceGameMessages.java` — 骰子遊戲訊息
- TypeScript 現有程式碼:
  - `packages/economy/src/localization/dice-game-messages.ts` — 現有分散式實作
  - `packages/economy/src/commands/balance-handler.ts` — 使用在地化的 handler 範例
  - `packages/economy/src/commands/dice-game-1-handler.ts` — 骰子遊戲 handler
  - `packages/admin/src/panel/admin/definitions/AdminPanelSlashCommand.ts` — 管理面板指令定義
  - `packages/admin/src/SlashCommandRegistrar.ts` — 指令註冊器
