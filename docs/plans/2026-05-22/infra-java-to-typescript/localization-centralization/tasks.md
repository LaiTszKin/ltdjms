# Tasks: Localization Centralization

- Date: 2026-05-22
- Feature: localization-centralization

## **Task 1: 建立 CommandLocalizations 模組**

Purpose: 在 `packages/shared/src/localization/` 建立集中式指令在地化，1:1 對齊 Java `CommandLocalizations`
Requirements: R1.1-R1.5
Scope: `packages/shared/src/localization/command-localizations.ts` (新建)
Out of scope: 不修改在地化字串內容

- T1.1 [ ] **`packages/shared/src/localization/command-localizations.ts`** — 建立 `CommandLocalizations` 物件/類別：
  - 使用 `Record<DiscordLocale, string>` 結構儲存 zh-TW 翻譯
  - 包含 `COMMAND_NAME_LOCALIZATIONS`：12 個指令名稱
  - 包含 `COMMAND_DESCRIPTION_LOCALIZATIONS`：12 個指令描述
  - 包含 `OPTION_NAME_LOCALIZATIONS`：7 個選項名稱
  - 包含 `OPTION_DESCRIPTION_LOCALIZATIONS`：7 個選項描述
  - 包含 `CHOICE_LOCALIZATIONS`：3 個選項值
  - 提供 `getNameLocalizations(cmd)`, `getDescriptionLocalizations(cmd)`, `getOptionNameLocalizations(opt)`, `getOptionDescriptionLocalizations(opt)`, `getChoiceLocalizations(choice)` 存取方法
  - 完全對齊 Java `CommandLocalizations.java` 的翻譯內容
  - Verify: 比對 Java 原始碼確認翻譯一致; `make build` 通過

- T1.2 [ ] **`packages/shared/src/localization/index.ts`** — 匯出 `CommandLocalizations`
  - Verify: import 路徑正確

## **Task 2: 建立 DiceGameMessages 模組**

Purpose: 在 `packages/shared/src/localization/` 建立骰子遊戲訊息在地化，對齊 Java `DiceGameMessages`
Requirements: R2.1, R2.2
Scope: `packages/shared/src/localization/dice-game-messages.ts` (新建)
Out of scope: 不修改訊息模板內容

- T2.1 [ ] **`packages/shared/src/localization/dice-game-messages.ts`** — 建立 `DiceGameMessages` 模組：
  - 包含所有骰子遊戲結果訊息的 zh-TW 模板
  - 支援參數插值（tokenCost, reward 等）
  - 內容對齊 Java `DiceGameMessages.java`
  - 若 `packages/economy/src/localization/dice-game-messages.ts` 已存在且內容相同，則從 shared 匯出即可
  - Verify: 比對 Java 原始碼確認內容一致

## **Task 3: 遷移既有引用到 shared**

Purpose: 更新各 package 的 import，從 shared 取得在地化字串
Requirements: R3.1-R3.4
Scope: 各 package 的指令 handler 和指令定義
Out of scope: 不修改指令的業務邏輯

- T3.1 [ ] **`packages/economy/src/commands/*.ts`** — 更新所有指令 handler 的 import：
  - `balance-handler.ts`, `currency-config-handler.ts`, `dice-game-1-handler.ts`, `dice-game-2-handler.ts`, `dice-config-handlers.ts`, `game-token-adjust-handler.ts`
  - 從 `@ltdjms/shared` import `CommandLocalizations` 和 `DiceGameMessages`
  - Verify: `make build` 通過; `make test -- --project @ltdjms/economy` 通過

- T3.2 [ ] **`packages/admin/src/panel/admin/definitions/`** — 更新管理面板指令定義的在地化引用
  - Verify: `make build` 通過

- T3.3 [ ] **刪除重複的在地化檔案** — 若 economy 模組內有獨立的地化檔案，確認 shared 版本內容一致後刪除
  - Verify: `make build` 通過; 無 broken import

- T3.4 [ ] **`packages/shared/src/index.ts`** — 從 shared 公開 API 匯出 localiz ation 模組
  - Verify: `make build` 通過
