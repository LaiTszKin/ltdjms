# Spec: Issue 74 設定 Schema 正規化

- Date: 2026-04-09
- Feature: Issue 74 設定 Schema 正規化
- Owner: Codex

## Goal
建立單一 canonical configuration schema，讓 `EnvironmentConfig`、packaged defaults、測試與文件都描述同一套 key namespace 與 fallback 規則，避免維運人員修改了系統根本不會讀取的設定鍵。

## Scope

### In Scope
- 以 `EnvironmentConfig` 為 runtime canonical schema owner，收斂 config path、env 映射與預設值來源。
- 決定 `application.properties` / `application.conf` 的單一權責，消除平行 schema。
- 對齊 `docs/development/configuration.md` 與實際 runtime fallback chain。
- 補齊測試，明確驗證 packaged defaults 與 canonical key namespace。

### Out of Scope
- 變更現有環境變數名稱（例如 `DISCORD_BOT_TOKEN`, `DB_URL`）。
- 新增動態 reload、遠端 config service、secret manager integration。
- 修改 ECPay / AI / DB 的業務行為，只處理設定鍵與文件一致性。
- 變更 `.env` 優先序。

## Functional Behaviors (BDD)

### Requirement 1: Runtime 必須只有一套 canonical key namespace
**GIVEN** `EnvironmentConfig` 是系統所有 runtime 設定的讀取入口  
**AND** packaged defaults 與文件都是為了描述相同的 runtime contract  
**WHEN** operator 查閱預設檔或文件、或系統從 packaged defaults 取 fallback 值  
**THEN** 所有來源都必須描述同一套 canonical key namespace  
**AND** 不可再出現 `discord.bot-token`、`database.*`、`db.*` 並存但只有部分會被 runtime 讀取的情況

**Requirements**:
- [x] R1.1 `EnvironmentConfig` 明確定義 canonical config path，並成為唯一真相來源。
- [x] R1.2 packaged defaults 只能描述 canonical key namespace。
- [x] R1.3 若保留 legacy resource，必須明確證明它不再承載獨立 schema。

### Requirement 2: Packaged defaults 與文件必須描述相同 fallback chain
**GIVEN** 系統宣稱優先序為 `system env > .env > packaged defaults > built-in defaults`  
**WHEN** 維運人員依文件設定本機或測試環境  
**THEN** 文件、resource 檔與測試都必須描述同一條 fallback chain  
**AND** 不可再把 `application.conf` 說成 canonical defaults，卻實際只由 `application.properties` 被驗證

**Requirements**:
- [x] R2.1 `docs/development/configuration.md` 必須精確描述實際的 packaged defaults 檔與 key namespace。
- [x] R2.2 若 `application.conf` 被移除或降為 compatibility shim，文件需同步反映。
- [x] R2.3 runtime fallback 順序與文件描述不可互相矛盾。

### Requirement 3: 自動化測試必須涵蓋 canonical schema 與 packaged defaults
**GIVEN** config drift 容易在未來改動中再次出現  
**WHEN** 開發者調整 config path、defaults 或 env mapping  
**THEN** 自動化測試必須能偵測 schema 漂移  
**AND** 對外環境變數 contract 不得被無意破壞

**Requirements**:
- [x] R3.1 測試需覆蓋 canonical packaged defaults 檔案與主要 config key。
- [x] R3.2 測試需驗證 `.env` / packaged defaults / built-in defaults 之間的實際優先序。
- [x] R3.3 文件範例與實際 resource 檔至少要有一個同步檢查點或可回歸比對的測試證據。

## Error and Edge Cases
- [x] `DISCORD_BOT_TOKEN` 這類 required key 缺失時，文件與 runtime 錯誤訊息需一致反映其來源要求。
- [x] `DB_URL` 缺省時由 `DATABASE_*` 組合產生的邏輯，不可因 schema 正規化而被破壞。
- [x] 若 `application.conf` 保留為 compatibility shim，不可再偷偷承載另一套 live defaults。
- [x] 無 `.env`、空 `.env`、 malformed `.env` 的 fallback 行為需維持。
- [x] 任何文件中的範例 key 若與 runtime schema 不同，必須視為 defect 而非可接受差異。

## Clarification Questions
None

## References
- Official docs:
  - `https://github.com/lightbend/config#standard-behavior`
  - `https://github.com/lightbend/config/blob/main/HOCON.md`
- Related code files:
  - `src/main/java/ltdjms/discord/shared/EnvironmentConfig.java`
  - `src/main/resources/application.properties`
  - `src/main/resources/application.conf`
  - `src/test/java/ltdjms/discord/shared/EnvironmentConfigDotEnvIntegrationTest.java`
  - `docs/development/configuration.md`
  - GitHub issue `#74`
