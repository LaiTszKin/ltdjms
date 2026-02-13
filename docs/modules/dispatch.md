# 模組說明：派單護航系統

本文件介紹 `dispatch/` 模組的實作，涵蓋 `/dispatch-panel` 指令、互動面板流程、訂單狀態流轉與資料庫結構。

## 1. 功能概觀

派單護航系統提供一條「管理員建立派單 → 護航者私訊確認 → 客戶收到確認通知」的流程。

主要能力：

- 管理員透過 `/dispatch-panel` 在面板中選擇：
  - 護航群組（Role）
  - 客戶群組（Role）
  - 護航者（User）
  - 客戶（User）
- 系統建立唯一訂單編號並持久化（預設狀態：`PENDING_CONFIRMATION`）
- 系統私訊護航者，附上「確認接單」按鈕
- 護航者確認後，訂單狀態改為 `CONFIRMED`，並通知護航者與客戶

## 2. 主要程式結構

### 2.1 指令與互動層（commands）

- `DispatchPanelCommandHandler`
  - 處理 `/dispatch-panel` 指令
  - 僅允許 guild 內使用
  - 回覆 ephemeral 派單面板

- `DispatchPanelInteractionHandler`
  - 處理 Entity Select（角色／使用者選擇）與按鈕互動
  - 維護每位管理員在每個 guild 的面板暫存狀態（`sessionStates`）
  - 建立派單前驗證：
    - 四項選擇是否完整
    - 護航者與客戶不可同一人
    - 成員是否仍在 guild
    - 護航者是否屬於護航群組、客戶是否屬於客戶群組
  - 處理護航者 DM 中的「確認接單」按鈕

- `DispatchPanelView`
  - 組裝面板 Embed 與元件（4 個選單 + 1 個建立按鈕）
  - 未完成選擇前，建立按鈕為停用狀態

### 2.2 領域層（domain）

- `EscortDispatchOrder`
  - 訂單實體（record）
  - 狀態：
    - `PENDING_CONFIRMATION`
    - `CONFIRMED`
  - 不變條件：
    - `orderNumber` 不可空白、長度不可超過 32
    - `escortUserId` 與 `customerUserId` 不可相同
    - `CONFIRMED` 狀態必須有 `confirmedAt`

- `EscortDispatchOrderRepository`
  - 訂單儲存介面，提供 `save`、`update`、`findByOrderNumber`、`existsByOrderNumber`

### 2.3 服務層（services）

- `EscortDispatchOrderService`
  - 建立訂單：`createOrder(...)`
  - 確認訂單：`confirmOrder(orderNumber, confirmerUserId)`
  - 訂單編號唯一性保證：最多重試 20 次

- `EscortDispatchOrderNumberGenerator`
  - 訂單編號格式：`ESC-YYYYMMDD-XXXXXX`
  - 後綴使用 `SecureRandom` 與可讀字元集（排除易混淆字元）

### 2.4 持久化層（persistence）

- `JdbcEscortDispatchOrderRepository`
  - JDBC 實作 `EscortDispatchOrderRepository`
  - 以 `order_number` 查詢與唯一性檢查
  - 更新時僅更新狀態／確認時間／更新時間

## 3. 流程說明

### 3.1 建立派單流程

1. 管理員執行 `/dispatch-panel`
2. 面板顯示護航群組、客戶群組、護航者與客戶選單
3. 管理員完成選擇並點擊「✅ 建立派單」
4. 系統驗證選擇內容與角色對應關係
5. `EscortDispatchOrderService.createOrder(...)` 建立 `PENDING_CONFIRMATION` 訂單
6. 系統私訊護航者，附上「✅ 確認接單」按鈕

> 若護航者私訊失敗：訂單仍會保留，系統會回覆管理員需手動通知。

### 3.2 護航者確認流程

1. 護航者在 Bot 私訊中點擊確認按鈕
2. 系統檢查：
   - 訂單是否存在
   - 是否為被指派護航者
   - 訂單是否仍為 `PENDING_CONFIRMATION`
3. 驗證通過後更新為 `CONFIRMED`
4. 系統通知：
   - 更新護航者原私訊為已確認
   - 另行私訊客戶已確認資訊

## 4. 資料庫設計（V014）

Migration：`src/main/resources/db/migration/V014__create_escort_dispatch_order.sql`

### 4.1 資料表

- `escort_dispatch_order`
  - 主鍵：`id`
  - 唯一鍵：`order_number`
  - 欄位：`guild_id`、`assigned_by_user_id`、`escort_role_id`、`customer_role_id`、`escort_user_id`、`customer_user_id`、`status`、`created_at`、`confirmed_at`、`updated_at`

### 4.2 約束與索引

- `status` 檢查約束：僅允許 `PENDING_CONFIRMATION` 或 `CONFIRMED`
- 使用者檢查約束：`escort_user_id <> customer_user_id`
- 索引：
  - `idx_escort_dispatch_order_guild_id`
  - `idx_escort_dispatch_order_status`
  - `idx_escort_dispatch_order_escort_user_id`

### 4.3 觸發器

- `update_escort_dispatch_order_updated_at`
  - `BEFORE UPDATE`
  - 使用共用函式 `update_updated_at_column()` 自動更新 `updated_at`

## 5. DI 與啟動註冊

- `DispatchModule` 提供 repository、service、command handler、interaction handler
- `SlashCommandListener` 註冊 `/dispatch-panel` 指令（管理員限定）
- `DiscordCurrencyBot` 將 `DispatchPanelInteractionHandler` 註冊為事件監聽器

## 6. 測試現況

目前已有：

- `EscortDispatchOrderServiceTest`
  - 建立訂單成功與失敗情境
  - 訂單編號衝突重試
  - 非指定護航者確認失敗
  - 重複確認失敗
  - 成功確認流程

## 7. 已知範圍

目前版本聚焦「建立與確認」主流程，尚未包含：

- 訂單取消
- 重新指派
- 訂單列表查詢

## 相關文件

- [Slash Commands 參考](../api/slash-commands.md#dispatch-panel--派單護航面板)
- [計畫文件：派單護航系統](../plans/2026-02-13-escort-dispatch-system.md)
- [系統架構總覽](../architecture/overview.md)
