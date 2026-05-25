# Contract: admin-membership-management

## `MembershipAdminService`

### `getDetail(userId) → Result<MembershipAdminDetail, DomainError>`

Aggregates extended `MembershipPanelSummary` + `hasQualifyingBronzeOrder`.

### `adjustPeriodSpend(userId, guildId, adminUserId, mode, amountM) → Result<Unit, DomainError>`

**Modes:** `ADD`, `DEDUCT`, `SET`

**Validation:**
- `amountM >= 0` for ADD/DEDUCT input
- SET target `amountM >= 0`
- `adminUserId > 0`

**Side effect:** Insert one `membership_spend_entry` row:
- `source_type = 'ADMIN_ADJUST'`
- `source_reference = 'admin:' + adminUserId + ':' + uuid`
- `list_price_twd` = computed delta (signed long)
- `paid_at = now()`

**Does NOT:** change `current_tier` or publish tier event.

### `setTier(userId, adminUserId, newTier) → Result<MembershipTier, DomainError>`

**Side effects:**
- Update `global_member_membership.current_tier`
- Update `has_qualifying_bronze_order` per spec R3.2
- Publish `MembershipTierChangedEvent` if effective tier changed

## `MembershipManagementFacade`

Panel-layer delegate; maps `DomainError` to admin-facing messages.

## `AdminPanelService`

```java
Result<MembershipAdminDetail, DomainError> getMembershipDetail(long userId);
Result<Unit, DomainError> adjustMembershipSpend(...);
Result<MembershipTier, DomainError> setMembershipTier(...);
```

## Events

Reuses existing:

```java
MembershipTierChangedEvent(String previousTierCode, String currentTierCode, long userId)
```

## Dependencies

- user-panel-membership-info extended `MembershipPanelSummary` (read fields)
- `MembershipRepository`, `MembershipSpendRepository`
- `EventPublisher` / domain event bus (existing)

## External

- Discord Entity Select (USER), Modal text input, String Select — standard JDA 5.x
