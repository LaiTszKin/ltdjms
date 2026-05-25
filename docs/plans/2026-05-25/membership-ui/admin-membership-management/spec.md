# Spec: admin-membership-management

- Date: 2026-05-25
- Feature: admin-membership-management
- Owner: laitszkin

## Goal

在 `/admin-panel` 提供管理員調整指定用戶本週期消費 M 與會員等級的能力，支援營運補登、纠错與測試，並即時刷新用戶 open panel 與商店折扣。

## Scope

### In Scope
- 新增 `MembershipAdminService`（寫入 use cases + 驗證）
- 新增 `MembershipManagementFacade`（panel 邊界，對齊 `CurrencyManagementFacade` 模式）
- Admin 主選單新增「🏅 會員等級管理」子面板
- 流程：User Select → 顯示詳情 → 調整消費 M（add/deduct/set via modal）→ 設定 tier（StringSelect + confirm）
- `membership_spend_entry` 新增 `ADMIN_ADJUST` source type；`source_reference` 含 adminId + timestamp UUID
- 設 tier 時更新 `current_tier`、必要時更新 `has_qualifying_bronze_order`；發布 `MembershipTierChangedEvent`
- Unit + integration tests

### Out of Scope
- 永久 tier override 直到手動解除（需 migration；見 Clarification）
- 修改歷史 fiat 訂單 ledger
- 手動觸發 settlement
- TypeScript admin parity
- Admin 操作 audit 獨立表（預設 ledger reference 即 audit）

## Functional Behaviors (BDD)

### Requirement 1: 查看用戶會員詳情
**GIVEN** 管理員具 Manage Server 或 Administrator 權限
**WHEN** 在會員管理子面板選擇一 user
**THEN** embed 顯示：等級、加入日期、本週期累計 M、距下一等級、下次結算日、青銅保底 flag

**Uncertainty Level**: Known

**Requirements**:
- [x] R1.1 權限檢查與既有 admin panel 一致
- [x] R1.2 資料來自 `MembershipQueryService` + `MembershipRepository`（admin detail DTO）
- [x] R1.3 無 membership 列時顯示 NONE 預設並允許後續調整

### Requirement 2: 調整本週期消費 M
**GIVEN** 管理員已選用戶
**WHEN** 選擇模式「增加 / 減少 / 設為」並輸入非負整數 M，提交 modal
**THEN** 寫入 `membership_spend_entry`（`source_type=ADMIN_ADJUST`），本週期 sum 反映變更
**AND** 成功後刷新 admin embed 顯示新 period spend

**Uncertainty Level**: Exploratory（set 模式需計算 delta 或多筆 ledger）

**Requirements**:
- [x] R2.1 **增加**：insert `list_price_twd=+amount`
- [x] R2.2 **減少**：insert `list_price_twd=-amount`（允許 period sum 下降）
- [x] R2.3 **設為**：insert 一筆使 `sum + delta = target`（delta 可正可負）
- [x] R2.4 `guild_id` = 管理員操作所在 guild
- [x] R2.5 `source_reference` = `admin:{adminUserId}:{uuid}` 保證 unique
- [x] R2.6 不立即重算 tier（tier 仍待 settlement）；若需即時 tier 由 R3 處理

### Requirement 3: 設定會員等級
**GIVEN** 管理員已選用戶
**WHEN** 從 tier 選單選擇目標等級並確認
**THEN** `current_tier` 更新為選定值
**AND** 若目標 ≥ BRONZE 則設 `has_qualifying_bronze_order=true`；若低於 BRONZE 則清除該 flag
**AND** 發布 `MembershipTierChangedEvent(previous, current, userId)`

**Uncertainty Level**: Known

**Requirements**:
- [x] R3.1 立即生效（shop 折扣、user panel）
- [x] R3.2 下次 settlement 可能依 ledger 重算覆寫 tier（文件與 admin embed 提示）
- [x] R3.3 不可設 NONE 以下；NONE 為合法選項（降級）

### Requirement 4: Admin UI 互動
**GIVEN** 管理員在會員管理子面板
**WHEN** 操作成功
**THEN** ephemeral 成功訊息 + 刷新子面板 embed
**WHEN** 驗證失敗（非法 amount、tier）
**THEN** ephemeral 錯誤訊息，不寫 DB

**Uncertainty Level**: Known

**Requirements**:
- [x] R4.1 複用 EntitySelectMenu + Modal + StringSelect 模式（參考 balance 調整）
- [x] R4.2 Session state 存 `selectedUserId`（guild session 內）

## Error and Edge Cases
- [x] 非管理員 → ephemeral 拒絕
- [x] amount 非整數 / 負數輸入 → validation error
- [x] set 模式 target < 0 → reject
- [x] 同一 admin 快速雙擊 → unique source_reference 不衝突
- [x] DB 寫入失敗 → Result error，不發 event
- [x] tier 未變更 → 不發 `MembershipTierChangedEvent`

## Clarification Questions

- [ ] Q1: 管理員設等級是否需「鎖定至下次結算」？（預設：**否**，settlement 可覆寫）
- [ ] Q2: 減少消費是否允許使 period sum 低於 0？（預設：**否**，clamp sum 顯示為 0，ledger 仍可负向调整）
- [ ] Q3: Admin 是否需填寫調整原因？（預設：**否**，reference 含 adminId 即可）

## References
- Official docs:
  - [Discord Modals](https://discord.com/developers/docs/components/reference)
  - [Discord User Select](https://docs.discord.com/developers/components/reference)
- Related code files:
  - `src/main/java/ltdjms/discord/panel/services/AdminPanelService.java`
  - `src/main/java/ltdjms/discord/panel/commands/AdminPanelButtonHandler.java`
  - `src/main/java/ltdjms/discord/panel/services/CurrencyManagementFacade.java`
  - `src/main/java/ltdjms/discord/membership/persistence/JdbcMembershipSpendCoordinator.java`
