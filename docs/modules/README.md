# 模組深度參考（補充文件）

本目錄保留各 Java 模組的**實作細節**（類別責任、互動 ID、時序說明）。日常理解系統請優先閱讀三支柱文件：

| 模組文件 | 對應三支柱文件 |
| --- | --- |
| `currency-system.md`, `game-tokens-and-games.md` | `docs/features/guild-economy.md` |
| `panels.md` | `docs/features/administration.md` |
| `product.md`, `shop.md`, `redemption.md` | `docs/features/shop-and-payment.md` |
| `dispatch.md` | `docs/features/escort-dispatch.md` |
| `aichat.md`, `aiagent.md` | `docs/features/ai-chat-and-agent.md` |
| `shared-module.md`, `discord-api-abstraction.md`, `cache.md`, `event-system.md` | `docs/architecture/infrastructure.md`, `docs/principles/` |

## 維護規則

- 與三支柱或程式碼衝突時，**先修正三支柱**，再更新本目錄補充細節。
- 新增使用者可見能力時，先在 `docs/features/` 以 BDD 描述，再視需要在本目錄補實作說明。
- 權威 runtime 入口：`DiscordCurrencyBot.java`、`SlashCommandListener.java`、`AppComponent.java`（見 `docs/architecture/layers-and-boundaries.md`）。
