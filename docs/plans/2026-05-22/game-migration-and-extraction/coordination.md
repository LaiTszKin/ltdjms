# Coordination: game-migration-and-extraction

- Date: 2026-05-22
- Batch: game-migration-and-extraction

## Business Goals

將 Java bot 的骰子遊戲 1:1 完整遷移至 TypeScript（確保前端輸出完全一致），並將所有遊戲相關代碼從 `@ltdjms/economy` 抽出為獨立 package `@ltdjms/games`。

- Batch members: [[message-alignment], [package-extraction]]
- Shared outcome: TypeScript 端骰子遊戲的 Discord 訊息輸出與 Java bot 完全一致，且遊戲代碼獨立為 `@ltdjms/games` package，不與貨幣邏輯混雜
- Out of scope: 新增遊戲類型、修改遊戲玩法、修改貨幣系統

## Design Principles

- Current baseline: 兩個骰子遊戲已在 TypeScript 端結構性完成遷移，存在於 `@ltdjms/economy` package 中。遊戲邏輯（隨機骰子、獎勵計算、代幣扣款）與 Java 一致，但 Discord 訊息格式有差異
- Shared invariants: 遊戲玩法邏輯不變（骰子隨機算法、獎勵計算公式、代幣扣除/不退還規則）；所有現有測試必須繼續通過
- Shared constraints: TypeScript 端不區分 Discord locale（因為 Discord 不支援非 Discord 官方 locales 的 zh-TW），統一使用 zh-TW 訊息格式
- Legacy direction: Java 端 `DiceGameMessages.java` 中的 `formatMessage()` 英文格式和 `formatXxxZhTw()` 中文格式 → TypeScript 端統一只保留 zh-TW 格式
- Compatibility window: None
- Cleanup after cutover: 移除 `@ltdjms/economy` 中被遷移走的遊戲相關檔案

## Spec Boundaries

### Ownership Map

#### Spec Set 1: message-alignment
- Primary concern: 對齊 TypeScript 骰子遊戲的 Discord 輸出訊息與 Java bot 完全一致
- Allowed touch points: `packages/shared/src/localization/dice-game-messages.ts`, `packages/economy/src/commands/dice-game-1-handler.ts`, `packages/economy/src/commands/dice-game-2-handler.ts`
- Must not change: 遊戲邏輯（dice services）、代幣服務、貨幣服務、DI 容器結構

#### Spec Set 2: package-extraction
- Primary concern: 從 `@ltdjms/economy` 抽出所有遊戲相關代碼（dice + token + commands + domain types + events），建立 `@ltdjms/games` package
- Allowed touch points: `packages/economy/src/dice/`, `packages/economy/src/token/`, `packages/economy/src/commands/`（遊戲部分）, `packages/economy/src/domain/types.ts`, `packages/economy/src/events/`, `packages/admin/src/facades/`（遊戲部分）, `packages/admin/src/panel/admin/handlers/`（遊戲部分）, 以及新建 `packages/games/`
- Must not change: 貨幣系統、其他非遊戲 admin handlers、shop/dispatch/ai packages

### Collisions & Integration

- Shared files & edit rules:
  - `packages/economy/src/domain/types.ts` — `message-alignment` 不可修改；`package-extraction` 需拆分為遊戲和貨幣兩部分
  - `packages/economy/src/di/economy-module.ts` — `message-alignment` 不可修改；`package-extraction` 需移除遊戲相關註冊
  - `packages/admin/src/di/AdminModule.ts` — `message-alignment` 不可修改；`package-extraction` 需更新 import 來源
- Shared API / schema freeze: `@ltdjms/economy` 的 currency 服務介面保持不變，`@ltdjms/games` 依賴這些介面
- Compatibility shim retention: `@ltdjms/economy` 在 `package-extraction` 完成後需保留對遊戲類型的 re-export，直到 `@ltdjms/admin` 完全切換 import 來源
- Merge order: `message-alignment` → `package-extraction`（先對齊訊息再移動檔案，避免 merge conflict）
- Integration checkpoints:
  - `make build` 通過
  - `make test` 全部通過
  - `make verify` 通過
- Re-coordination trigger: 若 `package-extraction` 過程中發現需要修改遊戲邏輯才能完成拆分，則需回報重新評估
