# Design: admin-membership-management

## 分層

```
AdminPanelButtonHandler
  → AdminPanelService.adjustMembershipSpend / setMembershipTier
    → MembershipManagementFacade
      → MembershipAdminService
        → MembershipSpendRepository.insertAdminAdjust(...)
        → MembershipRepository.save(...)
        → EventPublisher.publish(MembershipTierChangedEvent)
```

## MembershipAdminService

```java
public Result<MembershipAdminDetail, DomainError> getDetail(long userId);
public Result<Unit, DomainError> adjustPeriodSpend(
    long userId, long guildId, long adminUserId, SpendAdjustMode mode, long amountM);
public Result<MembershipTier, DomainError> setTier(
    long userId, long adminUserId, MembershipTier newTier);
```

### adjustPeriodSpend 演算法

```
currentSum = spendRepo.sumListPriceInPeriod(userId, period)
delta = switch(mode) {
  ADD -> amountM
  DEDUCT -> -amountM
  SET -> amountM - currentSum
}
insert ADMIN_ADJUST entry(list_price_twd=delta, guild_id, source_reference=unique)
return OK
```

**Period sum 顯示：** `max(0, sum)` 在 UI；ledger 保留真實 sum 供 settlement。

### setTier 演算法

```
previous = effectiveTier(current, bronzeFlag)
membership = repo.findByUserId.orElseCreate
membership = membership.withCurrentTier(newTier)
if newTier >= BRONZE: set hasQualifyingBronzeOrder=true
if newTier == NONE: set hasQualifyingBronzeOrder=false
repo.save(membership)
if previous != newEffective: publish MembershipTierChangedEvent
```

## Spend Repository 擴充

```java
void insertAdminAdjust(
    long userId,
    long guildId,
    long listPriceTwd,  // signed
    String sourceReference,
    Instant paidAt);
```

實作可複用 `JdbcMembershipSpendCoordinator` INSERT，新增 `source_type = 'ADMIN_ADJUST'` 常數。

**無 Flyway：** `source_type` 為 VARCHAR，新值不需 migration。

## Admin UI 流程

```
Main menu + button "🏅 會員等級管理"
  → Embed: 說明 + EntitySelectMenu USER
  → On select: show detail embed
      fields: tier, join, period M, remaining, settlement, bronze
      buttons: "調整消費 M" | "設定等級" | "返回"
  → 調整消費:
      StringSelect: 增加/減少/設為
      Modal: amount (正整數)
      submit → facade.adjustPeriodSpend
  → 設定等級:
      StringSelect: NONE, BRONZE, ..., BLACK
      Button: 確認
      → facade.setTier
```

Modal customId 範例：`admin_modal_membership_spend:{userId}:{mode}`

## MembershipManagementFacade

```java
record MembershipAdminDetail(
  MembershipPanelSummary summary,  // reuse extended summary from user-panel spec
  boolean hasQualifyingBronzeOrder
) {}
```

依賴 `MembershipQueryService`（read）與 `MembershipAdminService`（write）。

## DI

- `MembershipModule`: provide `MembershipAdminService`
- `CommandHandlerModule`: provide `MembershipManagementFacade`, inject into `AdminPanelService`

## 檔案清單

| 檔案 | 動作 |
|------|------|
| `membership/services/MembershipAdminService.java` | NEW |
| `membership/persistence/MembershipSpendRepository.java` | +insertAdminAdjust |
| `membership/persistence/JdbcMembershipSpendRepository.java` | impl |
| `panel/services/MembershipManagementFacade.java` | NEW |
| `panel/services/AdminPanelService.java` | +delegate methods |
| `panel/commands/AdminPanelButtonHandler.java` | +routes, session fields |
| `panel/commands/AdminPanelCommandHandler.java` | +main menu button |
| `AdminPanelViewFactory` 或 inline embed builders | membership sub-panel |
| `membership/di/MembershipModule.java` | wiring |
| `shared/di/CommandHandlerModule.java` | facade wiring |
| `docs/features/administration.md` | 會員管理章節 |

## 測試策略

| ID | 層級 | 目標 | Oracle |
|----|------|------|--------|
| UT-01 | Unit | adjust ADD +3000 | period sum +3000 |
| UT-02 | Unit | adjust SET to 14000 | sum equals target |
| UT-03 | Unit | setTier GOLD | current_tier=GOLD, event fired |
| UT-04 | Unit | setTier NONE | bronze flag cleared |
| IT-01 | Integration | ADMIN_ADJUST insert + sum | PostgresIntegrationTestBase |
| IT-02 | Integration | setTier → UserPanelUpdateListener | mock event subscriber |

Authorization: reuse admin permission helper — unit test with mock member permissions.

## 風險

- **Settlement 覆寫 tier**：admin embed footer 提示「手動等級將於下次結算依消費重算」
- **負 period sum**：settlement evaluator 應 treat avg M >= 0；spec 要求 UI clamp 0
