# Preparation: membership-tiers

- Date: 2026-05-24
- Batch: membership-tiers

## **Task P1: 啟用 GUILD_MEMBERS Intent**

Purpose: 記錄成員加入伺服器時間，作為個人結算日錨點
Requirements: membership-join-tracking R1.1
Scope: `DiscordCurrencyBot.java`、bot 部署設定
Out of scope: 會員 schema、結算邏輯

- P1.1 [x] **JDA 啟用 `GatewayIntent.GUILD_MEMBERS`** — 在 `DiscordCurrencyBot` 建立 JDA 時加入 intent；更新 `docs/configuration.md` 說明需在 Discord Developer Portal 啟用 Server Members Intent
  - Verify: `grep -n GUILD_MEMBERS src/main/java/ltdjms/discord/currency/bot/DiscordCurrencyBot.java`

- P1.2 [x] **Bot 可正常啟動** — `make build` 通過；本機或 staging 啟動 bot 無 intent 相關 crash
  - Verify: `make build`

## **Task P2: 確認 Flyway 遷移序號**

Purpose: 避免 batch 各 spec  migration 版本衝突
Scope: `src/main/resources/db/migration/`
Out of scope: 業務邏輯

- P2.1 [x] **記錄下一可用版本號** — 檢查最新 `V*.sql`，在 `coordination.md` 註明起始版本（目前基線 `V028` 之後 → **V029**）
  - Verify: `ls src/main/resources/db/migration/V*.sql | tail -3`

## **Task P3: 測試基線**

Purpose: 新增 membership 測試前確認現有驗證綠燈
Scope: 根 Makefile
Out of scope: membership 測試本身

- P3.1 [x] **執行 `make verify`** — 記錄通過狀態作為 batch 起點
  - Verify: `make verify` exit 0
