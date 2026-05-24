# Contract: membership-settlement

## `MembershipSettlementService.settle(long discordUserId)`

- Reads: spend ledger, membership row
- Writes: current_tier, last/next settlement timestamps
- Emits: `MembershipTierChangedEvent` on tier change

## `MembershipRepository.findDueForSettlement(Instant before)`

Returns: `List<Long> userIds`

## Period semantics

- **Inclusive start**: `paid_at >= periodStart`
- **Exclusive end**: `paid_at < periodEnd`

## Dependencies

- membership-core, membership-spend-ledger, membership-join-tracking（next_settlement 需已初始化）
