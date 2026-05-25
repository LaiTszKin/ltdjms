# Spec: membership-settlement

- Date: 2026-05-24
- Feature: membership-settlement
- Owner: laitszkin

## Goal

依個人結算日每日掃描到期用戶，計算**上一結算週期月平均 M**，重算會員等級（可升可降；青銅永久保底），並推進 `next_settlement_at`。

## Scope

### In Scope
- `MembershipSettlementScheduler`（daily tick，類似 `FiatOrderProcessingScheduler`）
- `MembershipSettlementService.settleUser(userId)`
- 週期平均 M = `sumListPriceInPeriod(lastSettlement, currentSettlement) / 1`（一個月週期內總 M，等價月平均）
- 更新 `current_tier`、`last_settlement_at`、`next_settlement_at`
- 發布 domain event `MembershipTierChangedEvent`（供 benefits-ui 訂閱發幣）

### Out of Scope
- Token grant（benefits-ui）
- Payment discount

## Functional Behaviors (BDD)

### Requirement 1: 結算觸發
**GIVEN** `next_settlement_at <= now`
**WHEN** scheduler tick（每小時，結算精度 ≤1 小時）
**THEN** 對該用戶執行 settle

**Requirements**:
- [x] R1.1 結算後 `last_settlement_at = periodEnd`（結算週期結束錨點），`next_settlement_at` 推進一個月
- [x] R1.2 無 spend 的週期 avgM=0；有 bronze flag 仍至少 BRONZE

### Requirement 2: 等級重算
**GIVEN** avgM=15000、hasQualifyingBronzeOrder=true
**WHEN** settle
**THEN** current_tier=SILVER

**Requirements**:
- [x] R2.1 avgM 從 GOLD 降到 12000 → tier 降為 BRONZE（core 門檻：SILVER 需 avgM ≥ 15000）
- [x] R2.2 tier 變更時發送 `MembershipTierChangedEvent`

### Requirement 3: 週期定義
**GIVEN** last_settlement_at=L, next settlement at N
**WHEN** 計算 avgM
**THEN** sum M where `paid_at ∈ [L, N)`

## Error and Edge Cases
- [x] next_settlement_at null（未 join）→ skip
- [x] 並發雙 tick → claim 或 SELECT FOR UPDATE 避免雙重結算
- [x] settlement 失敗單用戶隔離，不阻斷 batch
- [x] 遲到 spend（paid_at 早於 last_settlement_at）→ spend coordinator reopen 已結算週期，下次 tick 重算 avgM

## Clarification Questions
None

## References
- `FiatOrderProcessingScheduler.java`（排程模式）
- `MembershipSpendRepository.sumListPriceInPeriod`
- `MembershipTierEvaluator`
