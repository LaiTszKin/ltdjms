# Contract: Administration

- Date: 2026-05-20
- Feature: Administration
- Change Name: administration

> **Purpose:** **High-level external-dependency context for `tasks.md`**: cite-backed facts, limits, failures, security—so integrations are not hallucinated. **Not** a runnable checklist; **`tasks.md` executes** wiring (files, calls, mocks, tests). Internal coupling intent stays in **`design.md`** (`INT-###`).
>
> **Anti-duplication:** Do not enumerate per-file edits, checkbox steps, or copy task ordering. **`EXT-###`** are **constraints / anchors** that task rows may cite.
>
> **Undocumented gaps:** **`TBD`** + clarification—never invent payloads, endpoints, or semantics.

## Scope

- **External deps in this doc:** 6（discord.js API、5 個依賴 package 的 Facade/Service 合約）
- **No external deps 表示什麼**：administration 不直接與外部 HTTP API（如 ECPay、AI provider）互動；所有外部通訊皆透過其他 package 的 Facade/Service 介面進行。

## Dependencies

### discord.js v14 — Discord API Interaction

#### Evidence

| Primary docs URL(s)                                   | Sections / anchors used                                                                                  |
| ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| https://discord.js.org/docs/packages/discord.js/main  | ChatInputCommandInteraction、ButtonInteraction、StringSelectMenuInteraction、ModalSubmitInteraction、InteractionReplyOptions、EmbedBuilder、ActionRowBuilder、StringSelectMenuBuilder、ModalBuilder、TextInputBuilder、GuildMemberRoleManager、PermissionsBitField |
| https://discord.com/developers/docs/interactions/application-commands | Slash command registration、command permissions（default_member_permissions）、interaction response types（deferred vs immediate） |

**Version revision assumed:** discord.js `^14.16.0`（如 coordination.md 所指定）

#### Facts we rely on (must be citeable)

| Fact / capability needed                                                                              | Doc location                                                                                       |
| ----------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `ChatInputCommandInteraction` 提供 `commandName`、`options`（含 `getUser()`、`getString()` 等方法）    | discord.js docs: ChatInputCommandInteraction                                                      |
| `ButtonInteraction` 提供 `customId`、`deferReply()`、`editReply()`、`reply()`                          | discord.js docs: ButtonInteraction                                                                |
| `StringSelectMenuInteraction` 提供 `values`（選取的值陣列）                                             | discord.js docs: StringSelectMenuInteraction                                                      |
| `ModalSubmitInteraction` 提供 `fields.getTextInputValue(customId)`                                    | discord.js docs: ModalSubmitInteraction                                                           |
| `EmbedBuilder` 支援 `setTitle()`、`setDescription()`、`setColor()`、`addFields()`、`setFooter()`       | discord.js docs: EmbedBuilder                                                                     |
| `ActionRowBuilder` + `ButtonBuilder` / `StringSelectMenuBuilder` 建構互動元件                          | discord.js docs: ActionRowBuilder、ButtonBuilder、StringSelectMenuBuilder                          |
| `ModalBuilder` + `TextInputBuilder` 建構 Modal                                                        | discord.js docs: ModalBuilder、TextInputBuilder                                                   |
| Interaction reply 必須在 3 秒內完成（deferReply 可延長至 15 分鐘）                                      | Discord Developer Docs: Interaction Response Types                                                |
| `default_member_permissions` 可限制 slash command 對特定權限角色可見                                     | Discord Developer Docs: Application Command Permissions                                           |
| Message Component customId 最大長度 100 字元                                                            | Discord Developer Docs: Message Components                                                        |
| Select menu 最多 25 個選項（需自動分割的義務在 `@ltdjms/shared` 的 SelectMenuUtil）                      | Discord Developer Docs: Select Menu                                                               |
| Embed 限制：title 256、description 4096、fields 25、field name 256、field value 1024、footer 2048       | Discord Developer Docs: Embed Limits                                                              |

#### Limits & failures (coding obligations)

| Category                         | Doc fact                                                                                                    | Meaning while executing **`tasks.md`**                                                                                   |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Interaction 回應逾時              | 必須在 3 秒內呼叫 `deferReply()`，否則 Discord 回傳 "Unknown interaction"                                      | 所有 handler 在執行任何 DB 查詢或外部呼叫前必須 `deferReply()`；handler 的 `execute()` 方法第一行即為 `await interaction.deferReply()` |
| Interaction token 有效期          | deferReply 後 token 有效期 15 分鐘（對應 Session TTL）                                                        | Session TTL 必須 ≤ 15 分鐘；過期後不嘗試呼叫 `editReply()`                                                                   |
| Select menu 25 選項上限           | 超過 25 選項的 select menu 將無法發送                                                                         | 使用 `@ltdjms/shared` 的 `SelectMenuUtil.splitOptions()` 自動分割；過長列表使用分頁（產品列表、交易記錄）                     |
| Embed 欄位數量上限                | 單一 embed 最多 25 個 fields                                                                                  | 產品列表、交易記錄等長列表使用多重 embed 分頁（每頁 10 項），而非單一 embed 多個 fields                                         |
| Button customId 長度限制          | customId 最長 100 字元                                                                                        | customId prefix 簡短（如 `adm_bal_`、`adm_tok_`），id 參數使用短編碼（如 base36）或直接使用 Discord snowflake（≤19 位數字）    |
| Rate limit（全域）                | 每個 app 每 5 秒最多 50 個 interaction 回應                                                                   | 正常使用不會觸及；但需在 BotErrorHandler 中處理 rate limit 回應（429）並向用戶顯示「操作過於頻繁，請稍後再試」                    |

#### Security & secrets (policy level)

| Concern            | Constraint                                                                                                                          |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| 權限檢查             | 所有管理面板 handler 必須在 `deferReply()` 後立即檢查 `interaction.memberPermissions.has('Administrator')` 或 `interaction.user.id === guild.ownerId` |
| Slash command 可見性 | `/admin-panel` 的 `default_member_permissions` 設為 `ADMINISTRATOR`；Discord 負責第一層過濾                                              |
| 無 secrets 處理      | Administration module 不直接處理任何 secret key、API token；所有敏感設定由 `@ltdjms/shared` Config 管理                                    |

#### Integration anchors (`EXT-###`)

**Grain:** Boundary truth + obligations—**fewer anchors than typical task rows**. Multiple checkboxes often satisfy one anchor.

| ID        | What we integrate at this boundary *(doc-named surface)*                                   | Non‑negotiables (handling, retries, idempotency *per doc*)                                                                           | Forbidden assumptions                                                                   |
| --------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `EXT-001` | `ChatInputCommandInteraction` — slash command 進入點                                        | `commandName` 用於路由；`options` 用於參數提取；必須在 3 秒內 `deferReply()`                                                              | 不假設 interaction 永遠有效（需處理 "Unknown interaction"）                                 |
| `EXT-002` | `ButtonInteraction.customId` — 按鈕點擊路由                                                 | customId 以 prefix 匹配（如 `admin_balance_`），剩餘部分為參數（userId、productId）；不匹配時忽略                                          | 不假設 customId 字串格式永遠正確（需防禦性解析 + fallback）                                  |
| `EXT-003` | `StringSelectMenuInteraction.values` — 選單選取值                                           | 選取值為 string array，handler 取 `values[0]` 或迭代處理；空選取時回傳提示訊息                                                             | 不假設選取值陣列非空或有特定長度                                                              |
| `EXT-004` | `ModalSubmitInteraction.fields` — Modal 提交資料                                            | 每個 field 透過 `fields.getTextInputValue(customId)` 提取；值需做型別轉換與驗證（如 `parseInt()` 檢查 NaN）                               | 不假設所有欄位都已填寫或格式正確（需逐一驗證 + 欄位級錯誤提示）                                   |
| `EXT-005` | `EmbedBuilder` / `ActionRowBuilder` — Embed 與互動元件建構                                   | Embed 內容需符合 Discord 長度限制（由 `@ltdjms/shared` 的 `DiscordEmbedBuilder` 強制）；ActionRow 最多 5 個 button/select menu 元件        | 不假設 embed 內容永遠在限制內（需依賴 shared 的 builder 做自動截斷）                             |

**Doc-level ordering constraint (if any):**

1. `deferReply()` 必須是所有 handler 的第一個 Discord API 呼叫（在 `INT-011` 的 `SlashCommandListener` 中或 handler 入口處）
2. `editReply()` 或 `followUp()` 必須在 defer 之後才能呼叫
3. 權限檢查必須在 `deferReply()` 之後、業務邏輯執行之前完成（若權限不足，透過 `editReply()` 回傳錯誤訊息）

#### Trace hooks (no task parroting)

- Spec IDs covered: R1.1–R1.5、R2.1–R2.6、R3.1–R3.4、R4.1–R4.5、R5.1–R5.8、R6.1–R6.5、R7.1–R7.4、R8.1–R8.4、R9.1–R9.5、R10.1–R10.6、R11.1–R11.8、R12.1–R12.5、R14.1–R14.6（全部 requirement 均涉及 Discord API 互動）
- Related **`design.md`** module keys / `INT-###`: 全部 `INT-001` 到 `INT-012`
- **Unknown / `TBD`:** `None`

---

### @ltdjms/economy — Currency & Token & Game Facade Contracts

#### Evidence

| Primary docs URL(s)                                             | Sections / anchors used                                              |
| --------------------------------------------------------------- | -------------------------------------------------------------------- |
| `../guild-economy/spec.md`（spec 文件）                          | BalanceService、BalanceAdjustmentService、CurrencyConfigService、CurrencyTransactionService、GameTokenService、GameTokenTransactionService、DiceGame1ConfigRepository、DiceGame2ConfigRepository 的規格定義 |
| `../guild-economy/contract.md`（contract 文件，如已存在）         | 公開介面簽名、錯誤型別、事件型別                                          |

**Version revision assumed:** `@ltdjms/economy` 的公開 API（與 coordination.md 中 guild-economy spec 定義一致）

#### Facts we rely on (must be citeable)

| Fact / capability needed                                                                                 | Doc location                                                          |
| ------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `BalanceService.getBalance(guildId, userId)` 回傳 `Result<Balance, DomainError>`                         | guild-economy spec: BalanceService                                   |
| `BalanceAdjustmentService.addBalance(guildId, userId, amount, reason, actorId)` 回傳 `Result<Balance, ...>` | guild-economy spec: BalanceAdjustmentService                         |
| `BalanceAdjustmentService.deductBalance(...)` 檢查餘額是否足夠                                             | guild-economy spec: BalanceAdjustmentService                         |
| `BalanceAdjustmentService.setBalance(...)` 直接設定餘額（用於管理操作）                                     | guild-economy spec: BalanceAdjustmentService                         |
| `GameTokenService.getTokens(guildId, userId)` 回傳 `Result<GameToken, ...>`                              | guild-economy spec: GameTokenService                                 |
| `GameTokenTransactionService` 提供調整代幣的方法                                                           | guild-economy spec: GameTokenTransactionService                      |
| `CurrencyTransactionService.getTransactions(guildId, userId, page, pageSize)` 回傳分頁交易記錄              | guild-economy spec: CurrencyTransactionService                       |
| `DiceGame1ConfigRepository.get(guildId)` / `.save(guildId, config)`                                      | guild-economy spec: DiceGame1ConfigRepository                        |
| `DiceGame2ConfigRepository.get(guildId)` / `.save(guildId, config)`                                      | guild-economy spec: DiceGame2ConfigRepository                        |
| `BalanceChangedEvent(guildId, userId, oldBalance, newBalance, source)`                                  | guild-economy spec: DomainEvent 定義                                  |
| `GameTokenChangedEvent(guildId, userId, oldTokens, newTokens, source)`                                  | guild-economy spec: DomainEvent 定義                                  |
| `CurrencyConfigChangedEvent(guildId, oldConfig, newConfig)`                                             | guild-economy spec: DomainEvent 定義                                  |
| `DiceGameConfigChangedEvent(guildId, gameType, oldConfig, newConfig)`                                   | guild-economy spec: DomainEvent 定義                                  |

#### Limits & failures (coding obligations)

| Category                         | Doc fact                                                                                 | Meaning while executing **`tasks.md`**                                                              |
| -------------------------------- | ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 餘額不足                          | `deductBalance()` 在餘額不足時回傳 `Err(DomainError("INSUFFICIENT_BALANCE", ...))`          | Facade 將此錯誤向上傳播；handler 透過 BotErrorHandler 轉換為用戶訊息「目標用戶餘額不足」                    |
| 代幣不足                          | 代幣扣除時檢查餘額，不足時回傳 `Err(DomainError("INSUFFICIENT_TOKENS", ...))`               | 同上，用戶訊息為「目標用戶代幣不足」                                                                     |
| 調整金額驗證                       | 正整數驗證由 economy module 的 service 層負責                                                | Facade 可以再做一次前端驗證（提供更友善的錯誤訊息），但最終信任 economy module 的驗證                         |
| 交易記錄分頁                       | Repository 層支援 LIMIT/OFFSET 分頁                                                         | 用戶面板的分頁查詢每次請求 pageSize 筆；facade 封裝分頁邏輯（hasNext、totalPages 計算）                       |

#### Security & secrets (policy level)

| Concern  | Constraint                                                                     |
| -------- | ------------------------------------------------------------------------------ |
| 權限      | 所有管理操作（`adjustBalance`、`setBalance`、`setTokens` 等）僅限 admin panel 呼叫，而 admin panel 已有 ADMINISTRATOR 權限檢查 |

#### Integration anchors (`EXT-###`)

| ID        | What we integrate at this boundary *(doc-named surface)*                                   | Non‑negotiables (handling, retries, idempotency *per doc*)                                                                           | Forbidden assumptions                                                                   |
| --------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `EXT-010` | `BalanceService.getBalance()` / `BalanceAdjustmentService.*`                               | 所有呼叫回傳 `Result<T, DomainError>`；必須以 `isOk()`/`isErr()` 判斷結果；不得直接存取 `.value` 或 `.error`                                | 不假設餘額永遠存在（新用戶可能無餘額記錄）                                                     |
| `EXT-011` | `GameTokenService.getTokens()` / `GameTokenTransactionService.*`                            | 同上                                                                                                                                | 不假設代幣記錄永遠存在                                                                      |
| `EXT-012` | Economy module 發布的 DomainEvent（BalanceChanged、GameTokenChanged、CurrencyConfigChanged） | Event 在 economy module 的 service 層發布（在 DB 交易完成後）；administration 的 listener 被動接收                                          | 不假設 event 的發布順序與 handler 執行順序一致（非同步）                                        |

**Doc-level ordering constraint (if any):** `None`（所有 economy service 呼叫為獨立操作，無順序依賴）

#### Trace hooks (no task parroting)

- Spec IDs covered: R2.1–R2.6、R3.1–R3.4、R4.1–R4.5、R11.2–R11.5、R13.1–R13.3、R13.5
- Related **`design.md`** module keys / `INT-###`: `INT-001`、`INT-002`、`INT-003`、`INT-007`
- **Unknown / `TBD`:** `None`

---

### @ltdjms/shop — Product, Redemption, RedemptionCode Contracts

#### Evidence

| Primary docs URL(s)                                        | Sections / anchors used                                              |
| ---------------------------------------------------------- | -------------------------------------------------------------------- |
| `../shop-payment/spec.md`（spec 文件）                      | ProductService、ProductRepository、RedemptionService、RedemptionCodeRepository、ProductRedemptionTransactionService 的規格定義 |
| `../shop-payment/contract.md`（contract 文件，如已存在）    | 公開介面簽名、ECPay 相關錯誤型別                                        |

**Version revision assumed:** `@ltdjms/shop` 的公開 API（與 coordination.md 中 shop-payment spec 定義一致）

#### Facts we rely on (must be citeable)

| Fact / capability needed                                                                   | Doc location                                                          |
| ------------------------------------------------------------------------------------------ | --------------------------------------------------------------------- |
| `ProductService.createProduct(...)` / `.updateProduct(...)` / `.deleteProduct(guildId, productId)` | shop-payment spec: ProductService                                    |
| `ProductService.listProducts(guildId, page, pageSize)` 回傳分頁產品列表                      | shop-payment spec: ProductService                                    |
| `ProductRepository.findById(productId)` 查詢單一產品                                         | shop-payment spec: ProductRepository                                 |
| `RedemptionCodeRepository.generateCodes(productId, quantity, note)` 批次生成兌換碼           | shop-payment spec: RedemptionCodeRepository                          |
| `RedemptionCodeRepository.listByProduct(productId, page, pageSize)` 列出產品的兌換碼          | shop-payment spec: RedemptionCodeRepository                          |
| `RedemptionService.redeemCode(guildId, userId, code)` 兌換兌換碼                              | shop-payment spec: RedemptionService                                 |
| `ProductRedemptionTransactionService.getHistory(guildId, userId, page, pageSize)` 查詢兌換記錄 | shop-payment spec: ProductRedemptionTransactionService               |
| `ProductChangedEvent(guildId, productId, changeType)` 產品變更事件                           | shop-payment spec: DomainEvent 定義                                   |
| `RedemptionCodesGeneratedEvent(guildId, productId, quantity)` 兌換碼生成事件                  | shop-payment spec: DomainEvent 定義                                   |

#### Limits & failures (coding obligations)

| Category                         | Doc fact                                                                                 | Meaning while executing **`tasks.md`**                                                              |
| -------------------------------- | ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 兌換碼格式驗證                     | 兌換碼輸入最少需要 16 字元（shop module 定義）                                                | Modal `TextInputBuilder` 設定 `setMinLength(16)`；Discord 前端驗證阻止提交                              |
| 兌換碼重複使用                     | 已使用的兌換碼不可再次兌換；shop module 回傳 `Err(DomainError("CODE_ALREADY_USED", ...))`    | Handler 將錯誤轉換為用戶訊息「此兌換碼已被使用」                                                          |
| 產品刪除限制                       | 有活躍引用時（如存在的兌換碼、進行中的訂單）不可刪除（具體限制由 shop spec 定義）                  | Handler 收到刪除失敗時顯示具體原因                                                                      |
| 產品列表分頁                       | 與 economy module 相同的分頁模式                                                             | 分頁參數（page、pageSize）由 handler 管理；「上一頁」「下一頁」按鈕的 customId 攜帶當前頁碼                     |

#### Security & secrets (policy level)

| Concern  | Constraint                                                                     |
| -------- | ------------------------------------------------------------------------------ |
| 產品管理權限 | 所有產品 CRUD 操作僅限管理面板（已檢查 ADMINISTRATOR 權限）                         |
| 兌換碼兌換  | 任何用戶皆可兌換（用戶面板）；兌換結果通知用戶但不洩漏其他用戶的兌換碼                           |

#### Integration anchors (`EXT-###`)

| ID        | What we integrate at this boundary *(doc-named surface)*                                   | Non‑negotiables (handling, retries, idempotency *per doc*)                                                                           | Forbidden assumptions                                                                   |
| --------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `EXT-020` | `ProductService.*` / `ProductRepository.*`（產品 CRUD）                                      | 所有建立／更新操作需完整驗證 Modal 輸入（名稱非空、價格正整數、URL 格式）；錯誤時回傳欄位級錯誤訊息                                             | 不假設產品 ID 永遠有效（查詢失敗時顯示「產品不存在」）                                          |
| `EXT-021` | `RedemptionCodeRepository.generateCodes()`（批次生成）                                       | 生成數量限制 1–100；大量生成時 deferReply 確保不超時；生成後顯示清單（可考慮 DM 發送以保護兌換碼安全）                                          | 不假設生成永遠成功（庫存不足時回傳錯誤）                                                       |
| `EXT-022` | `RedemptionService.redeemCode()`（兌換）                                                     | 兌換成功後更新用戶面板（或發布 event 由 UpdateListener 更新）；兌換失敗時區分錯誤類型（無效碼、已使用、已過期、庫存不足）並顯示對應訊息                  | 不假設兌換碼格式正確（需 trim 前後空白）；不假設用戶有足夠權限                                      |

**Doc-level ordering constraint (if any):** `None`

#### Trace hooks (no task parroting)

- Spec IDs covered: R5.1–R5.8、R11.6–R11.8、R13.5（RedemptionService 部分）
- Related **`design.md`** module keys / `INT-###`: `INT-004`、`INT-008`
- **Unknown / `TBD`:** `None`

---

### @ltdjms/dispatch — Dispatch After-Sales, Escort Pricing, Escort Catalog Contracts

#### Evidence

| Primary docs URL(s)                                        | Sections / anchors used                                              |
| ---------------------------------------------------------- | -------------------------------------------------------------------- |
| `../escort-dispatch/spec.md`（spec 文件）                   | DispatchAfterSalesStaffService、EscortOptionPricingService、EscortOptionCatalogRepository 的規格定義 |
| `../escort-dispatch/contract.md`（contract 文件，如已存在） | 公開介面簽名、派單狀態機、handoff 契約                                  |

**Version revision assumed:** `@ltdjms/dispatch` 的公開 API（與 coordination.md 中 escort-dispatch spec 定義一致）

#### Facts we rely on (must be citeable)

| Fact / capability needed                                                                               | Doc location                                                          |
| ------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------- |
| `DispatchAfterSalesStaffService.addStaff(guildId, userId)` / `.removeStaff(guildId, userId)` / `.listStaffs(guildId)` | escort-dispatch spec: DispatchAfterSalesStaffService                 |
| `EscortOptionPricingService.getGuildPrices(guildId)` / `.setGuildPrice(guildId, optionId, price)` / `.resetGuildPrice(guildId, optionId)` | escort-dispatch spec: EscortOptionPricingService                     |
| `EscortOptionCatalogRepository.findAll()` / `.create(...)` / `.update(...)` / `.delete(optionId)`        | escort-dispatch spec: EscortOptionCatalogRepository                   |
| 目錄刪除的參照完整性檢查（查詢是否有 guild 引用該項目）                                                      | escort-dispatch spec: EscortOptionCatalogRepository.delete() 行為定義  |
| `DispatchAfterSalesConfigChangedEvent(guildId, staffIds)`                                               | escort-dispatch spec: DomainEvent 定義                                |
| `EscortPricingChangedEvent(guildId, optionId, newPrice)`                                                | escort-dispatch spec: DomainEvent 定義                                |
| `EscortCatalogChangedEvent(optionId, changeType)`                                                       | escort-dispatch spec: DomainEvent 定義                                |

#### Limits & failures (coding obligations)

| Category                         | Doc fact                                                                                 | Meaning while executing **`tasks.md`**                                                              |
| -------------------------------- | ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 售後人員重複新增                   | 已存在的售後人員不可重複新增；dispatch module 回傳 `Err(DomainError("DUPLICATE_STAFF", ...))`   | Handler 顯示「該成員已是售後人員」                                                                       |
| 目錄刪除參照完整性                  | 有 guild 引用時阻止刪除並回傳引用詳情                                                            | Handler 在刪除失敗時顯示引用該項目的 guild 名稱清單；不可在前端做 optimistic delete                             |
| 定價重設                          | 重設至全域預設值後，guild-level override 被移除                                                  | Handler 在重設成功後更新面板顯示（回復至全域預設價格）                                                        |

#### Security & secrets (policy level)

| Concern  | Constraint                                                                     |
| -------- | ------------------------------------------------------------------------------ |
| 售後人員管理 | 僅管理員可新增／移除售後人員（已在 handler 層檢查 ADMINISTRATOR）                     |
| 目錄管理    | 僅管理員可 CRUD 全域護航目錄（同上）                                                |

#### Integration anchors (`EXT-###`)

| ID        | What we integrate at this boundary *(doc-named surface)*                                   | Non‑negotiables (handling, retries, idempotency *per doc*)                                                                           | Forbidden assumptions                                                                   |
| --------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `EXT-030` | `DispatchAfterSalesStaffService.*`                                                         | 新增前檢查是否已存在；移除前檢查是否為售後人員；操作後更新列表顯示                                                                          | 不假設 member select menu 選取的用戶仍在 guild 中                                           |
| `EXT-031` | `EscortOptionPricingService.*`                                                             | 價格欄位驗證為正整數；編輯 Modal 區分「新增覆寫」與「修改既有覆寫」兩種情境                                                                   | 不假設所有 guild 都有護航選項的 pricing override                                           |
| `EXT-032` | `EscortOptionCatalogRepository.*`                                                          | 建立／編輯時所有欄位必填；刪除時必須呼叫含參照檢查的方法；錯誤訊息需包含引用 guild 清單                                                         | 不假設目錄永遠非空；不假設刪除永遠成功                                                        |

**Doc-level ordering constraint (if any):** `None`

#### Trace hooks (no task parroting)

- Spec IDs covered: R8.1–R8.4、R9.1–R9.5、R10.1–R10.6
- Related **`design.md`** module keys / `INT-###`: `INT-006`
- **Unknown / `TBD`:** `None`

---

### @ltdjms/ai — AI Channel & Agent Config Contracts

#### Evidence

| Primary docs URL(s)                                        | Sections / anchors used                                              |
| ---------------------------------------------------------- | -------------------------------------------------------------------- |
| `../ai-chat-agent/spec.md`（spec 文件）                     | AIChannelRestrictionService、AIAgentChannelConfigService 的規格定義    |
| `../ai-chat-agent/contract.md`（contract 文件，如已存在）   | AI 路由規則、Agent 模式定義、Markdown 驗證規則                          |

**Version revision assumed:** `@ltdjms/ai` 的公開 API（與 coordination.md 中 ai-chat-agent spec 定義一致）

#### Facts we rely on (must be citeable)

| Fact / capability needed                                                                               | Doc location                                                          |
| ------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------- |
| `AIChannelRestrictionService.addAllowedChannel(guildId, channelId)` / `.removeAllowedChannel(...)` / `.getAllowedChannels(guildId)` | ai-chat-agent spec: AIChannelRestrictionService                      |
| `AIChannelRestrictionService.addAllowedCategory(guildId, categoryId)` / `.removeAllowedCategory(...)` / `.getAllowedCategories(guildId)` | ai-chat-agent spec: AIChannelRestrictionService                      |
| `AIAgentChannelConfigService.enableAgent(guildId, channelId, mode)` / `.disableAgent(guildId, channelId)` / `.removeAgentConfig(guildId, channelId)` / `.getAgentConfigs(guildId)` | ai-chat-agent spec: AIAgentChannelConfigService                      |
| `AIChannelConfigChangedEvent(guildId, channelId, changeType)`                                          | ai-chat-agent spec: DomainEvent 定義                                  |
| `AIAgentConfigChangedEvent(guildId, channelId, mode, enabled)`                                         | ai-chat-agent spec: DomainEvent 定義                                  |

#### Limits & failures (coding obligations)

| Category                         | Doc fact                                                                                 | Meaning while executing **`tasks.md`**                                                              |
| -------------------------------- | ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 頻道重複新增                       | 已存在的頻道／分類不可重複新增；AI module 回傳 `Err(DomainError("DUPLICATE_CHANNEL/CATEGORY", ...))` | Handler 顯示「此頻道／分類已在 AI 白名單中」                                                              |
| 頻道不存在                         | 不存在的頻道 ID 導致 `Err(DomainError("CHANNEL_NOT_FOUND", ...))`                         | Handler 顯示「找不到此頻道」                                                                            |
| Agent 模式驗證                     | Agent 模式 enum 由 AI module 定義（與 Java `AgentMode` enum 一致）                           | Handler 的 select menu 選項必須與 AI module 的 enum 值同步（不硬編碼選項文字，改從 enum 推導）               |

#### Security & secrets (policy level)

| Concern  | Constraint                                                                     |
| -------- | ------------------------------------------------------------------------------ |
| AI 設定管理 | 僅管理員可變更 AI 頻道白名單與 Agent 設定（已在 handler 層檢查 ADMINISTRATOR）        |

#### Integration anchors (`EXT-###`)

| ID        | What we integrate at this boundary *(doc-named surface)*                                   | Non‑negotiables (handling, retries, idempotency *per doc*)                                                                           | Forbidden assumptions                                                                   |
| --------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `EXT-040` | `AIChannelRestrictionService.*`                                                            | 使用 Discord channel select menu 取得 channelId/categoryId；handler 驗證回傳的 ID 格式有效後才呼叫 service                                   | 不假設 Discord channel select menu 回傳的 ID 仍然有效（頻道可能已被刪除）                        |
| `EXT-041` | `AIAgentChannelConfigService.*`                                                            | Agent 模式選項從 AI module 的 `AgentMode` enum 匯出；handler 的 select menu 選項動態生成；變更後即時反映在 management panel 的列表顯示       | 不假設所有頻道都適合啟用 Agent 模式（AI module 內部可能有額外限制）                                |

**Doc-level ordering constraint (if any):** `None`

#### Trace hooks (no task parroting)

- Spec IDs covered: R6.1–R6.5、R7.1–R7.4、R13.4
- Related **`design.md`** module keys / `INT-###`: `INT-005`
- **Unknown / `TBD`:** `None`

---

### @ltdjms/shared — Shared Infrastructure Contracts

#### Evidence

| Primary docs URL(s)                                        | Sections / anchors used                                                  |
| ---------------------------------------------------------- | ------------------------------------------------------------------------ |
| `../shared-infrastructure/spec.md`（spec 文件）             | Result、DomainError、Config、Database、Redis Cache、DomainEvent、Logging、DiscordInteraction、DiscordContext、DiscordEmbedBuilder、DiscordRuntimeGateway、DiscordSessionManager、DI Container 的規格定義 |
| `../shared-infrastructure/design.md`（design 文件）         | DI 容器設定、模組邊界                                                      |
| `../shared-infrastructure/contract.md`（contract 文件，如已存在） | 對 discord.js、ioredis、drizzle、tsyringe 的外部依賴定義                    |

**Version revision assumed:** `@ltdjms/shared` 的公開 API（與 coordination.md 中 shared-infrastructure spec 定義一致）

#### Facts we rely on (must be citeable)

| Fact / capability needed                                                                                 | Doc location                                                          |
| ------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `DiscordInteraction` 介面：`deferReply()`、`reply()`、`editReply()`、`followUp()`                         | shared-infrastructure spec: DiscordInteraction                       |
| `DiscordContext` 介面：`guildId`、`userId`、`channelId`、`userMention`                                    | shared-infrastructure spec: DiscordContext                           |
| `DiscordEmbedBuilder` 介面：建構符合 Discord 限制的 embed                                                | shared-infrastructure spec: DiscordEmbedBuilder                      |
| `DiscordSessionManager` 介面：`createSession()`、`getSession()`、`removeSession()`、`cleanExpiredSessions()` | shared-infrastructure spec: DiscordSessionManager                    |
| `DomainEventPublisher`：`register(listener)`、`publish(event)`                                           | shared-infrastructure spec: DomainEventPublisher                     |
| `Result<T, E>`：`isOk()`、`isErr()`、`getValue()`、`getError()`                                           | shared-infrastructure spec: Result                                   |
| `DomainError`：`category`（enum）、`message`、`cause`、工廠方法                                              | shared-infrastructure spec: DomainError                              |
| `SelectMenuUtil.splitOptions()`：自動分割超過 25 選項的 select menu                                        | shared-infrastructure spec: SelectMenuUtil                           |
| DI 容器：`@singleton()`、`@inject()`、`@injectAll()`                                                      | shared-infrastructure spec: DI Container                             |

#### Limits & failures (coding obligations)

| Category                         | Doc fact                                                                                 | Meaning while executing **`tasks.md`**                                                              |
| -------------------------------- | ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| DiscordSessionManager TTL         | TTL 預設 15 分鐘；Redis 不可用時降級為 in-memory Map                                        | AdminPanelSessionManager 與 PanelSessionManager 使用此預設 TTL；不自行管理 TTL                         |
| DomainEventPublisher 同步分發      | 所有 listener 在同一個 event loop iteration 中執行；單一 listener 例外不影響其他 listener       | UpdateListener 中的非同步操作（如 editReply）使用 fire-and-forget，不阻塞事件分發鏈                         |
| Embed 限制強制                      | DiscordEmbedBuilder 在內容超長時自動截斷並 logged warning                                     | Handler 傳入超長內容時的降級行為已由 shared module 處理；handler 不需自己做截斷                              |
| DI 容器註冊                         | 所有 service、handler、listener 需以 `@singleton()` 註冊                                      | 每個新的 handler、facade、listener 類別必須加上 `@singleton()` 裝飾器；facade 透過 `@inject()` 注入依賴 service |

#### Security & secrets (policy level)

| Concern  | Constraint                                                                     |
| -------- | ------------------------------------------------------------------------------ |
| 無直接 secrets 存取 | Administration 不直接存取 Config 的敏感值（如 bot token、ECPay keys）；所有設定由 shared module 管理並透過 DI 提供型別安全的 Config 物件 |

#### Integration anchors (`EXT-###`)

| ID        | What we integrate at this boundary *(doc-named surface)*                                   | Non‑negotiables (handling, retries, idempotency *per doc*)                                                                           | Forbidden assumptions                                                                   |
| --------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `EXT-050` | `DiscordInteraction` — 所有 Discord 互動的抽象層                                             | 所有 handler 透過 `DiscordInteraction` 介面而非直接使用 discord.js 型別；這確保 handler 可用 MockDiscordInteraction 進行單元測試                | 不假設 `DiscordInteraction` 的底層實作細節（如 discord.js 版本）                                |
| `EXT-051` | `DiscordSessionManager` — 面板 session 儲存                                                 | Session key 格式：`admin_panel:{guildId}:{userId}` 或 `user_panel:{guildId}:{userId}`；session value 包含 state、InteractionHook reference、createdAt | 不假設 session 永遠存在（每次 handler 執行前檢查 `getSession()` 回傳非 null）                    |
| `EXT-052` | `DomainEventPublisher` — 事件監聽註冊                                                        | Listener 在 constructor 中透過 `eventPublisher.register(this)` 註冊；使用 `@injectAll()` 取得所有 listener token 進行批次註冊                | 不假設 listener 的註冊順序                                                                  |
| `EXT-053` | `SelectMenuUtil.splitOptions()` — 處理選項超限                                                | 在 handler 建構 select menu 前，若選項數量可能超過 25，必須呼叫此工具分割為多個 select menu component                                           | 不自行實作選項分割邏輯                                                                       |

**Doc-level ordering constraint (if any):** shared-infrastructure 必須最先完成（阻斷性前置依賴，見 coordination.md merge order）

#### Trace hooks (no task parroting)

- Spec IDs covered: R1.1–R1.5、R12.1–R12.5、R14.1–R14.6
- Related **`design.md`** module keys / `INT-###`: `INT-009`、`INT-010`、`INT-011`、`INT-012`
- **Unknown / `TBD`:** `None`
