# Design: user-panel-membership-info

## 資料擴充

```java
public record MembershipPanelSummary(
    MembershipTier tier,
    long periodSpendListPriceM,
    long nextTierThresholdM,
    Instant nextSettlementAt,
    BigDecimal discountRate,
    Instant earliestGuildJoinAt,   // NEW nullable
    long remainingToNextTierM,     // NEW computed
    int monthlyTokenGrant            // NEW from tier
) {
  public static long computeRemaining(long spent, long threshold) {
    if (threshold <= 0) return 0;
    return Math.max(0, threshold - spent);
  }
}
```

## Query 流程

```
MembershipQueryService.getPanelSummary(userId)
  → membershipRepository.findByUserId
  → sumListPriceInPeriod (existing)
  → effectiveTier (existing)
  → nextTierThresholdM (existing)
  → earliestGuildJoinAt = membership.earliestGuildJoinAt()
  → remainingToNextTierM = computeRemaining(periodSpendM, nextTierThresholdM)
  → monthlyTokenGrant = effectiveTier.monthlyTokenGrant()
```

## UI 欄位順序（會員 embed field）

| 行 | 內容 |
|----|------|
| 加入日期 | `<t:epoch:D>` 或「尚未記錄」 |
| 等級 | tier 中文名 |
| 目前權益 | 護航折扣 + 每月贈幣 |
| 本週期累計 M | 既有 |
| 距下一等級 | 還需 N M |
| 下一門檻進度 | 既有 X% (a/b M) |
| 下次結算日 | 既有 |

NONE tier：等級顯示「尚未達標」；距下一等級指向白銀門檻；權益區顯示青銅升級提示。

## 檔案

| 檔案 | 變更 |
|------|------|
| `MembershipPanelSummary.java` | 新增 3 欄位 + helper |
| `MembershipQueryService.java` | populate 新欄位 |
| `UserPanelView.java` | formatMembershipField 文案 |
| `UserPanelEmbedBuilderTest.java` | 斷言新文案 |
| `UserPanelUpdateListenerTest.java` | 更新 summary fixture |
| `MemberInfoFacadeTest.java` | 若 mock summary 需更新 |
| `docs/features/membership-tiers.md` | User Panel 需求 |

## 測試策略

| ID | 層級 | 目標 | Oracle |
|----|------|------|--------|
| UT-01 | Unit | `computeRemaining` | threshold=14000, spent=3000 → 11000 |
| UT-02 | Unit | `UserPanelView` GOLD tier | 含加入日、還需 M、每月贈幣 200 |
| UT-03 | Unit | NONE tier | 距白銀、無贈幣、青銅提示 |
| UT-04 | Unit | BLACK tier | 「已達最高等級」 |
| IT-01 | Integration | `MembershipQueryService` + test DB | join_at 非空時 summary 帶入 |

跳過 E2E：Discord 渲染由 unit 覆蓋 markdown 字串；無新 API endpoint。
