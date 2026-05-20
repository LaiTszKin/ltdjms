# Spec: Escort Dispatch

- Date: 2026-05-20
- Feature: Escort Dispatch
- Owner: laitszkin

## Goal

將 Java 護航派單系統完整移植到 TypeScript，在 `packages/dispatch/` 內實作護航訂單的完整生命週期（7 狀態機）、從商店付款自動交接（idempotent handoff）、Discord 面板互動、DM 通知、售後人員管理與 Guild 層級定價覆寫，所有功能行為與 Java 原版 100% 一致。

## Scope

### In Scope

- 護航派單訂單的 7 狀態生命週期（PENDING_CONFIRMATION → CONFIRMED → PENDING_CUSTOMER_CONFIRMATION → COMPLETED / AFTER_SALES_REQUESTED → AFTER_SALES_IN_PROGRESS → AFTER_SALES_CLOSED）
- 三種訂單來源（MANUAL、CURRENCY_PURCHASE、FIAT_PAYMENT），含完整來源快照（productId、productName、currencyPrice、fiatPriceTwd、escortOptionCode）
- 從商店付款自動交接（EscortDispatchHandoffService），含 findBySourceIdentity 冪等檢查
- 24 小時客戶確認超時自動完成
- 訂單編號格式 ESC-YYYYMMDD-XXXXXX（6 位英數字，不含混淆字元）
- `/dispatch-panel` Discord 指令（管理員限定）與完整面板互動（開單/派單流程、歷史記錄）
- 多角色 DM 通知鏈：護航者接單確認、護航者完單請求、客戶確認完成/申請售後、售後人員接手/結案、客戶狀態更新
- 售後人員管理（新增/移除/查詢）
- Guild 層級護航選項定價覆寫（upsert/delete/getEffectivePrice）
- Drizzle ORM schema 定義（escort_dispatch_order、guild_escort_option_price、dispatch_after_sales_staff）
- Repository 層的原子條件式 UPDATE（WHERE status=X AND escort_user_id=0 RETURNING *）

### Out of Scope

- 護航選項目錄（escort_option_catalog）的 CRUD 管理介面（屬於 administration 模組）
- 商店模組內部的付款邏輯（dispatch 僅消費 handoff 介面）
- 貨幣/代幣系統的餘額操作（dispatch 僅紀錄來源快照，不操作餘額）
- Discord 機器人的啟動/連線/事件分發基礎設施（屬於 shared-infrastructure）

## Functional Behaviors (BDD)

### Requirement 1: 建立待確認護航訂單

**GIVEN** 管理員在 Discord guild 中使用派單面板
**AND** 管理員已選擇客戶（Discord 用戶）與護航者（Discord 用戶）
**WHEN** 管理員點擊「建立派單」按鈕
**THEN** 系統產生格式為 `ESC-YYYYMMDD-XXXXXX` 的唯一訂單編號
**AND** 系統儲存訂單至資料庫，狀態為 `PENDING_CONFIRMATION`
**AND** 管理員收到建立成功的 ephemeral 回覆，顯示訂單編號

**Requirements**:
- [ ] R1.1 訂單編號格式：`ESC-` 前綴 + 8 位日期（YYYYMMDD）+ 連字號 + 6 位英數字尾碼（不含 I、O、0、1 等混淆字元）
- [ ] R1.2 訂單編號產生器確保唯一性：最多重試 20 次
- [ ] R1.3 護航者與客戶不可為同一人，否則回傳 DomainError("護航者與客戶不能是同一人")
- [ ] R1.4 手動建立的訂單 sourceType 為 MANUAL，sourceReference/sourceProductId/sourceProductName/sourceCurrencyPrice/sourceFiatPriceTwd 均為 null

### Requirement 2: 開立未指定護航者的手動訂單（Create Mode）

**GIVEN** 管理員在派單面板中選擇「開單」模式
**AND** 管理員已選擇客戶（Discord 用戶）與護航品類（如 CONF_DAM_300W）
**WHEN** 管理員點擊「建立護航訂單」按鈕
**THEN** 系統建立 escortUserId=0 的 PENDING_CONFIRMATION 訂單
**AND** 訂單帶有 sourceEscortOptionCode 欄位
**AND** 管理員收到成功訊息，提示可至「派單」流程指派護航者

**Requirements**:
- [ ] R2.1 escortOptionCode 必須是 EscortOrderOptionCatalog 中的有效代碼，否則回傳 DomainError("護航品類無效，可用代碼：...")
- [ ] R2.2 customerUserId 必須大於 0，否則回傳 DomainError("請選擇客戶")
- [ ] R2.3 護航品類 select menu 自動分頁：超過 25 個選項時分割為 SELECT_ORDER_OPTION + SELECT_ORDER_OPTION_EXTRA 兩個選單

### Requirement 3: 派發現有待派單訂單（Assign Mode）

**GIVEN** 管理員在派單面板中選擇「派單」模式
**AND** 系統載入該 guild 中 status=PENDING_CONFIRMATION 且 escort_user_id=0 的訂單清單
**AND** 管理員選擇一張待派單訂單與一位護航者（Discord 用戶）
**WHEN** 管理員點擊「派發訂單」按鈕
**THEN** 系統以原子 UPDATE 更新訂單（WHERE order_number=? AND status='PENDING_CONFIRMATION' AND escort_user_id=0），設定 assignedByUserId、escortUserId、updatedAt
**AND** 若 UPDATE 無匹配行（已被其他人派發），回傳 DomainError("此訂單已被派發或目前不可派發")
**AND** 系統發送 DM 給護航者，內含訂單資訊與「確認接單」按鈕
**AND** 管理員收到派發成功的 ephemeral 回覆

**Requirements**:
- [ ] R3.1 待派單訂單查詢：依 created_at ASC 排序，預設上限 5 筆，最大 25 筆
- [ ] R3.2 assignEscort 使用條件式 UPDATE：`WHERE order_number = ? AND status = 'PENDING_CONFIRMATION' AND escort_user_id = 0 AND customer_user_id <> ?`
- [ ] R3.3 若已選訂單不在 pendingOrders 清單中（已過期），不可派發
- [ ] R3.4 若無法發送 DM 給護航者（私訊關閉），仍視為派發成功但提示管理員手動通知

### Requirement 4: 護航者確認接單

**GIVEN** 護航者收到 DM 通知，內含「確認接單」按鈕
**AND** 訂單狀態為 PENDING_CONFIRMATION
**WHEN** 護航者點擊「確認接單」按鈕
**THEN** 系統驗證點擊者為訂單指定的護航者（escortUserId），否則回傳 DomainError("只有被指派的護航者可以確認此訂單")
**AND** 系統將訂單狀態更新為 CONFIRMED，設定 confirmedAt 時間戳
**AND** 護航者的 DM 訊息更新為「已確認接單」embed + 「完成訂單」按鈕
**AND** 系統發送 DM 給客戶，通知護航者已確認接單

**Requirements**:
- [ ] R4.1 僅護航者本人可確認（canBeConfirmedBy 檢查 escortUserId == confirmerUserId）
- [ ] R4.2 已確認的訂單不可重複確認（狀態檢查 isPendingEscortConfirmation）
- [ ] R4.3 確認必須在私訊中操作（event.isFromGuild() 為 false），否則提示「請在機器人私訊中確認接單」

### Requirement 5: 護航者完成服務，等待客戶確認

**GIVEN** 護航者已確認接單（狀態為 CONFIRMED）
**AND** 護航者的 DM 中顯示「完成訂單」按鈕
**WHEN** 護航者點擊「完成訂單」按鈕
**THEN** 系統驗證點擊者為訂單指定的護航者
**AND** 系統將訂單狀態更新為 PENDING_CUSTOMER_CONFIRMATION，設定 completionRequestedAt 時間戳
**AND** 護航者的 DM 訊息更新為「已送出完成請求」embed，按鈕移除
**AND** 系統發送 DM 給客戶，內含訂單資訊與兩顆按鈕：「確認完成」、「申請售後」

**Requirements**:
- [ ] R5.1 僅護航者本人可請求完成（canBeCompletedByEscort 檢查 escortUserId）
- [ ] R5.2 僅 CONFIRMED 狀態可轉換至 PENDING_CUSTOMER_CONFIRMATION
- [ ] R5.3 客戶 DM 的 footer 標註「24 小時未確認將視為訂單完成」

### Requirement 6: 客戶確認完成

**GIVEN** 客戶收到 DM 通知，內含「確認完成」與「申請售後」按鈕
**AND** 訂單狀態為 PENDING_CUSTOMER_CONFIRMATION
**WHEN** 客戶點擊「確認完成」按鈕
**THEN** 系統驗證點擊者為訂單指定的客戶（customerUserId），否則回傳 DomainError("只有訂單客戶可以確認完成")
**AND** 系統先檢查 24 小時超時：若已超時則自動完成（idempotent）
**AND** 系統將訂單狀態更新為 COMPLETED，設定 completedAt 時間戳
**AND** 客戶的 DM 訊息更新為「訂單已完成」embed，按鈕移除
**AND** 系統發送 DM 給護航者，通知客戶已確認完成

**Requirements**:
- [ ] R6.1 僅客戶本人可確認完成（canBeConfirmedByCustomer 檢查 customerUserId）
- [ ] R6.2 若訂單已是 COMPLETED 狀態，直接回傳 ok（idempotent）
- [ ] R6.3 24 小時超時邏輯：completionRequestedAt + 24h < now 時自動觸發完成
- [ ] R6.4 自動完成失敗時（DB 錯誤），不阻斷主流程，回傳未變更的訂單

### Requirement 7: 客戶申請售後

**GIVEN** 客戶收到 DM 通知，內含「確認完成」與「申請售後」按鈕
**AND** 訂單狀態為 PENDING_CUSTOMER_CONFIRMATION 或 COMPLETED
**WHEN** 客戶點擊「申請售後」按鈕
**THEN** 系統驗證點擊者為訂單指定的客戶
**AND** 系統先檢查 24 小時超時：若已超時則先自動完成，再拒絕售後申請（COMPLETED 不可申請售後... 等等，實際上 Java 實作中 COMPLETED 狀態是可以申請售後的）
**AND** 系統將訂單狀態更新為 AFTER_SALES_REQUESTED，設定 afterSalesRequestedAt 時間戳
**AND** 客戶的 DM 訊息更新為「已提交售後申請」embed，按鈕移除
**AND** 系統發送 DM 給售後人員（優先通知在線者，無在線者則通知全部設定人員）

**Requirements**:
- [ ] R7.1 僅客戶本人可申請售後（canBeConfirmedByCustomer 檢查 customerUserId）
- [ ] R7.2 可從 PENDING_CUSTOMER_CONFIRMATION 或 COMPLETED 狀態申請售後
- [ ] R7.3 若已在售後流程中（AFTER_SALES_REQUESTED / AFTER_SALES_IN_PROGRESS / AFTER_SALES_CLOSED），回傳 DomainError("此訂單已在售後流程中")
- [ ] R7.4 售後通知策略：先篩選在線（OnlineStatus.ONLINE）的售後人員；若無在線者則通知全部設定人員；若無設定售後人員則提示「目前尚未設定售後人員」
- [ ] R7.5 售後人員清單從 dispatch_after_sales_staff 表查詢

### Requirement 8: 售後人員接手案件

**GIVEN** 售後人員收到 DM 通知，內含「接手案件」按鈕
**AND** 訂單狀態為 AFTER_SALES_REQUESTED
**WHEN** 售後人員點擊「接手案件」按鈕
**THEN** 系統驗證點擊者為該 guild 設定的售後人員，否則回傳 DomainError("你不是此伺服器設定的售後人員")
**AND** 系統以原子 UPDATE 接手案件（WHERE order_number=? AND status='AFTER_SALES_REQUESTED' AND after_sales_assignee_user_id IS NULL）
**AND** 若 UPDATE 無匹配行（已被其他人接手），回傳 DomainError("此售後案件已由其他售後人員接手")
**AND** 系統將訂單狀態更新為 AFTER_SALES_IN_PROGRESS，設定 afterSalesAssigneeUserId、afterSalesAssignedAt
**AND** 售後人員的 DM 訊息更新為「已接手售後案件」embed + 「完成 / close file」按鈕
**AND** 系統發送 DM 給客戶，通知售後已由專人接手

**Requirements**:
- [ ] R8.1 僅 guild 設定的售後人員可接手（透過 DispatchAfterSalesStaffService.isAfterSalesStaff 驗證）
- [ ] R8.2 claimAfterSales 使用條件式 UPDATE：`WHERE order_number = ? AND status = 'AFTER_SALES_REQUESTED' AND after_sales_assignee_user_id IS NULL`
- [ ] R8.3 若已由同一售後人員接手，回傳 DomainError("你已接手此售後案件")
- [ ] R8.4 若案件已結案（AFTER_SALES_CLOSED），回傳 DomainError("此售後案件已結案")

### Requirement 9: 售後人員結案

**GIVEN** 售後人員已接手案件（狀態為 AFTER_SALES_IN_PROGRESS）
**AND** 售後人員的 DM 中顯示「完成 / close file」按鈕
**WHEN** 售後人員點擊按鈕
**THEN** 系統驗證點擊者為該案件的接手人（afterSalesAssigneeUserId）
**AND** 系統以原子 UPDATE 結案（WHERE order_number=? AND status='AFTER_SALES_IN_PROGRESS' AND after_sales_assignee_user_id=?）
**AND** 系統將訂單狀態更新為 AFTER_SALES_CLOSED，設定 afterSalesClosedAt
**AND** 售後人員的 DM 訊息更新為「售後案件已結案」embed，按鈕移除
**AND** 系統發送 DM 給客戶，通知售後案件已完成處理並結案

**Requirements**:
- [ ] R9.1 僅接手人本人可結案（isAfterSalesAssignee 檢查 afterSalesAssigneeUserId == userId）
- [ ] R9.2 closeAfterSales 使用條件式 UPDATE：`WHERE order_number = ? AND status = 'AFTER_SALES_IN_PROGRESS' AND after_sales_assignee_user_id = ?`
- [ ] R9.3 非 AFTER_SALES_IN_PROGRESS 狀態不可結案，回傳 DomainError("此售後案件目前不可結案")

### Requirement 10: 24 小時客戶確認超時自動完成

**GIVEN** 訂單狀態為 PENDING_CUSTOMER_CONFIRMATION
**AND** completionRequestedAt + 24 小時 ≤ 當前時間
**WHEN** 任何操作觸發查詢該訂單（如客戶確認完成、客戶申請售後、查詢歷史記錄）
**THEN** 系統在查詢時自動將訂單狀態更新為 COMPLETED，設定 completedAt 時間戳
**AND** 若自動完成成功，回傳 COMPLETED 狀態的訂單
**AND** 若自動完成失敗（DB 錯誤），回傳原始狀態的訂單（不阻斷主流程）

**Requirements**:
- [ ] R10.1 超時門檻：24 小時（CUSTOMER_CONFIRM_TIMEOUT = Duration.ofHours(24)）
- [ ] R10.2 超時檢查在每次查詢訂單時執行（ensureTimeoutCompletion）
- [ ] R10.3 自動完成失敗時記錄 warn 日誌，不拋出例外

### Requirement 11: 從商店付款自動交接 (Handoff)

**GIVEN** 用戶完成商店購買（貨幣購買或法幣付款）
**AND** 商品設定了自動護航開單（product.shouldAutoCreateEscortOrder()）
**WHEN** 商店模組呼叫 EscortDispatchHandoffService.handoffFromCurrencyPurchase() 或 handoffFromFiatPayment()
**THEN** 系統先以 sourceType + sourceReference 查詢是否已存在交接訂單（findBySourceIdentity idempotency check）
**AND** 若已存在，直接回傳既有的訂單（idempotent）
**AND** 若不存在，以 product 的完整快照建立新的 PENDING_CONFIRMATION 訂單（escortUserId=0, assignedByUserId=0）
**AND** 若建立過程中發生例外，再次查詢 findBySourceIdentity 作為 fallback（防止 race condition 導致的重複建立）
**AND** 若 fallback 查詢也找不到，回傳 DomainError.persistenceFailure

**Requirements**:
- [ ] R11.1 handoffFromCurrencyPurchase 建立 sourceType=CURRENCY_PURCHASE 的訂單
- [ ] R11.2 handoffFromFiatPayment 建立 sourceType=FIAT_PAYMENT 的訂單
- [ ] R11.3 來源快照欄位：sourceReference（付款參考）、sourceProductId、sourceProductName、sourceCurrencyPrice、sourceFiatPriceTwd、sourceEscortOptionCode
- [ ] R11.4 若 product 為 null，回傳 DomainError("找不到該商品")
- [ ] R11.5 若 product 未啟用自動護航開單，回傳 DomainError("此商品尚未啟用自動護航開單")
- [ ] R11.6 若 sourceReference 為空，回傳 DomainError("來源參考無效")
- [ ] R11.7 例外 fallback：create 失敗後再次 findBySourceIdentity，若找到即回傳既有訂單

### Requirement 12: 護航選項定價覆寫

**GIVEN** 管理員需要為 guild 設定護航選項的自訂價格
**WHEN** 管理員呼叫 EscortOptionPricingService.updateOptionPrice(guildId, updatedByUserId, optionCode, priceTwd)
**THEN** 系統驗證 optionCode 存在於 EscortOptionCatalog
**AND** 系統驗證 priceTwd > 0
**AND** 系統以 upsert 寫入 guild_escort_option_price 表（ON CONFLICT (guild_id, option_code) DO UPDATE）
**AND** 回傳 OptionPriceView，標記 overridden=true

**Requirements**:
- [ ] R12.1 upsert 使用 ON CONFLICT (guild_id, option_code) DO UPDATE 語法
- [ ] R12.2 getEffectivePrice：優先取 guild override，若無 override 則取 catalog 預設價格
- [ ] R12.3 resetOptionPrice：從 guild_escort_option_price 刪除該 guild 的特定 optionCode override
- [ ] R12.4 listOptionPrices：合併 catalog 與 override，對每個 optionCode 回傳 OptionPriceView

### Requirement 13: 售後人員管理

**GIVEN** 管理員需要設定 guild 的售後人員名單
**WHEN** 管理員呼叫 DispatchAfterSalesStaffService 的新增/移除/查詢方法
**THEN** addStaff 使用 ON CONFLICT DO NOTHING 實現 idempotent 新增
**AND** 若使用者已在名單中，回傳 DomainError("該成員已在售後名單中")
**AND** removeStaff 刪除指定使用者
**AND** 若使用者不在名單中，回傳 DomainError("該成員不在售後名單中")
**AND** getStaffUserIds 回傳該 guild 的所有售後人員 userId 集合

**Requirements**:
- [ ] R13.1 addStaff 使用 INSERT ... ON CONFLICT DO NOTHING，回傳 boolean（插入成功 true，已存在 false）
- [ ] R13.2 removeStaff 使用 DELETE WHERE guild_id = ? AND user_id = ?，回傳 boolean
- [ ] R13.3 getStaffUserIds 依 created_at ASC 排序
- [ ] R13.4 isAfterSalesStaff 用於權限檢查，例外時回傳 false（安全預設）

### Requirement 14: 派單面板互動（管理員）

**GIVEN** 管理員在 Discord guild 中執行 /dispatch-panel 指令
**WHEN** 指令觸發
**THEN** 系統驗證呼叫者為管理員（ADMINISTRATOR 權限或 guild owner），否則回傳「你沒有權限使用派單面板」
**AND** 系統顯示 ephemeral 模式選擇 embed（開單 / 派單）
**AND** 每個管理員在每個 guild 中有獨立的 session state（ConcurrentHashMap keyed by guildId:userId）

**Requirements**:
- [ ] R14.1 面板僅限管理員使用（hasPermission(ADMINISTRATOR) || guild.getOwnerIdLong() == member.getIdLong()）
- [ ] R14.2 Session state 儲存目前模式、已選客戶/護航者/品類/訂單、狀態訊息
- [ ] R14.3 Mode switch（開單↔派單）會重置 session state
- [ ] R14.4 返回按鈕（BUTTON_BACK_TO_MODE）清除 session state 並回到模式選擇

### Requirement 15: 歷史記錄查詢

**GIVEN** 管理員在派單面板中點擊「歷史記錄」按鈕
**WHEN** 查詢觸發
**THEN** 系統呼叫 findRecentOrders(guildId, 10)，回傳該 guild 最近 10 筆訂單
**AND** 以 ephemeral embed 顯示訂單列表（訂單編號、狀態、護航者、客戶、來源摘要、建立時間）
**AND** 歷史查詢時對每筆訂單執行超時檢查（ensureTimeoutCompletion）

**Requirements**:
- [ ] R15.1 預設查詢上限 10 筆，最大 20 筆
- [ ] R15.2 依 created_at DESC 排序
- [ ] R15.3 歷史上每筆訂單經過 ensureTimeoutCompletion 正規化

## Error and Edge Cases

- [ ] 訂單編號唯一性：產生器最多重試 20 次，超過拋出 IllegalStateException
- [ ] 護航者與客戶不可為同一人：createOrder 與 assignPendingOrder 兩處均有檢查
- [ ] 原子條件式 UPDATE 失敗處理：assignEscort 回傳空 Optional 時回傳 DomainError("此訂單已被派發或目前不可派發")，claimAfterSales 回傳空時區分「已被他人接手」與「其他原因」
- [ ] 非管理員點擊面板按鈕：回傳「你沒有權限使用派單面板」
- [ ] 在 guild 頻道中點擊 DM-only 按鈕（確認接單等）：回傳「請在機器人私訊中操作」
- [ ] DM 發送失敗（用戶關閉私訊）：記錄 warn 日誌，管理員操作仍視為成功但提示手動通知
- [ ] 護航品類選項超過 25 個：自動分為兩個 select menu（SELECT_ORDER_OPTION + SELECT_ORDER_OPTION_EXTRA）
- [ ] 待派單訂單清單為空時：select menu 顯示 disabled 的「目前沒有待派單訂單」選項
- [ ] 客戶已不在伺服器中：建立訂單前 retrieveMemberById 驗證，失敗時回傳「找不到指定客戶」
- [ ] PENDING_CUSTOMER_CONFIRMATION 狀態的超時自動完成：查詢時觸發，失敗不阻斷主流程，記錄 warn 日誌
- [ ] 同一來源重複 handoff：findBySourceIdentity 確保 idempotency
- [ ] Handoff 過程中 DB 例外：exception handler 內再次 findBySourceIdentity 作為 fallback

## Clarification Questions

None

## References

- Official docs:
  - Discord.js v14 documentation: https://discord.js.org/docs
  - Drizzle ORM documentation: https://orm.drizzle.team/docs/overview
- Related code files:
  - `src/main/java/ltdjms/discord/dispatch/domain/EscortDispatchOrder.java`
  - `src/main/java/ltdjms/discord/dispatch/services/EscortDispatchOrderService.java`
  - `src/main/java/ltdjms/discord/dispatch/services/EscortDispatchHandoffService.java`
  - `src/main/java/ltdjms/discord/dispatch/services/EscortDispatchOrderNumberGenerator.java`
  - `src/main/java/ltdjms/discord/dispatch/services/DispatchAfterSalesStaffService.java`
  - `src/main/java/ltdjms/discord/dispatch/services/EscortOptionPricingService.java`
  - `src/main/java/ltdjms/discord/dispatch/commands/DispatchPanelInteractionHandler.java`
  - `src/main/java/ltdjms/discord/dispatch/commands/DispatchPanelCommandHandler.java`
  - `src/main/java/ltdjms/discord/dispatch/commands/DispatchPanelView.java`
  - `src/main/java/ltdjms/discord/dispatch/commands/DispatchPanelMessageFactory.java`
  - `src/main/java/ltdjms/discord/dispatch/persistence/JdbcEscortDispatchOrderRepository.java`
  - `src/main/java/ltdjms/discord/dispatch/persistence/JdbcDispatchAfterSalesStaffRepository.java`
  - `src/main/java/ltdjms/discord/dispatch/persistence/JdbcEscortOptionPriceRepository.java`
  - `src/main/java/ltdjms/discord/shared/di/DispatchModule.java`
  - `src/main/java/ltdjms/discord/product/domain/EscortOrderOptionCatalog.java`
  - `src/main/java/ltdjms/discord/product/domain/EscortOptionCatalog.java`
  - `src/main/java/ltdjms/discord/product/domain/EscortOptionCatalogRepository.java`
