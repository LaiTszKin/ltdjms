# Design: message-alignment

- Date: 2026-05-22
- Feature: message-alignment
- Change Name: message-alignment

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1.1-R1.6, R2.1-R2.8, R3.1-R3.6                                             |
| In-scope modules            | `packages/shared/src/localization/dice-game-messages.ts`, `packages/economy/src/commands/dice-game-1-handler.ts`, `packages/economy/src/commands/dice-game-2-handler.ts` |
| External systems touched    | None                                                                         |
| Batch coordination          | `../coordination.md`                                                         |

## Target vs baseline

|                       | Baseline (today)                                    | Target (after this change)                                   |
| --------------------- | --------------------------------------------------- | ------------------------------------------------------------ |
| Structure / ownership | DiceGameMessages 在 shared；handler 在 economy       | 相同位置，僅內容變更                                          |
| Message format        | TS 自訂格式（較詳細、含加總/餘額變動/描述 footer）     | 1:1 匹配 Java DiceGameMessages.formatXxxZhTw() 輸出           |

## Boundaries

- Entry surface(s): Discord slash command → DiceGame1Handler / DiceGame2Handler → DiceGameMessages 模板
- Trust boundary crossed: None
- Outside → inside: Discord user → `/dice-game-1` or `/dice-game-2` → handler → DiceGameMessages 格式化 → reply

## Modules

| Module key | Responsibility | Owned artifacts |
| ---------- | -------------- | --------------- |
| `dice-game-messages` | 提供與 Java 一致的訊息模板字串（zh-TW） | `DiceGameMessages` const object |
| `dice-game-1-handler` | 組裝 DiceGame1 結果和錯誤訊息 | `DiceGame1Handler` class |
| `dice-game-2-handler` | 組裝 DiceGame2 結果和錯誤訊息 | `DiceGame2Handler` class |

---

## Interaction anchors (`INT-###`)

| ID        | Intent | Caller → Callee | Coupling kind | Information crossing | Failure propagation |
| --------- | ------ | --------------- | ------------- | -------------------- | ------------------- |
| `INT-001` | 遊戲結果顯示 | Handler → DiceGameMessages | 模板字串 + replace | 骰子值、獎勵、餘額、currency icon/name | 模板缺少參數時顯示原始 placeholder |

**Ordering / concurrency:** 無並行相依性。所有變更為同步字串替換。

## Requirement linkage

### 訊息模板更新 (R1.1-R1.3, R2.1-R2.2, R3.5)
- Anchor order: 先更新 DiceGameMessages 常數 → 再更新 handler 引用
- 先改模板，確保 handler 能引用正確的新 key；避免 handler 引用不存在的常數

### Handler 訊息組裝 (R1.4-R1.6, R2.4-R2.8, R3.6)
- Anchor order: 兩個 handler 可並行修改（互不依賴）
- 每個 handler 的變更順序：先改成功訊息 → 再改錯誤訊息

## Data & persistence

| Resource | Typical readers/writers | Consistency expectation |
| -------- | ----------------------- | ----------------------- |
| DiceGameMessages constants | Handler (read-only) | 常數定義與 Java 原始碼一致 |

## Invariants

| Invariant | What breaks it | Symptoms if violated |
| --------- | -------------- | -------------------- |
| 遊戲邏輯不變 | 修改 dice services 的計算邏輯 | 獎勵金額與 Java 不一致 |
| 代幣扣款行為不變 | 修改 tryDeductTokens 呼叫 | 代幣扣除時機或數量變化 |
| 千分位格式化 | 忘記對數字呼叫 toLocaleString() | 數字無逗號分隔，與 Java String.format("%,d") 不一致 |

## Tradeoffs

| Decision | Rejected alternative | Locks in |
| -------- | -------------------- | -------- |
| 統一使用 zh-TW 格式，不支援英文 locale | 保留 Java 的 locale 切換邏輯 | 簡化實作，因為 Discord 不支援 zh-TW locale，TS 端無從判斷 |
| 使用模板字串 + replace 而非函數調用 | 每個格式寫成獨立函數 | 與現有 DiceGameMessages 風格一致 |

## Batch-only

本 spec 僅處理訊息格式對齊。package 拆分參見 `../package-extraction/`。
