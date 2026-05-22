# Checklist: Guild Economy

- Date: 2026-05-20
- Feature: Guild Economy

## Behavior-to-Test Checklist

- [x] CL-01: `BalanceService.getBalance` 首次查詢自動建立帳戶、回傳 BalanceView — R1.1-R1.2 → UT-Balance-01 — Result: `PASS`
- [x] CL-02: 餘額快取 300 秒 TTL — R1.3 → IT-Cache-01 — Result: `PASS`
- [x] CL-03: `adjustBalance` deduct 導致負數回傳 `INSUFFICIENT_BALANCE` — R2.2 → UT-Adjust-01 — Result: `PASS`
- [x] CL-04: `adjustBalance` 發布 `BalanceChangedEvent` — R2.3 → UT-Adjust-02 — Result: `PASS`
- [x] CL-05: `CurrencyConfigService.updateConfig` 名稱超長回傳 Err — R3.1 → UT-Config-01 — Result: `PASS`
- [x] CL-06: `GameTokenService.deductTokens` 不足回傳 `INSUFFICIENT_TOKENS` — R4.2 → UT-Token-01 — Result: `PASS`
- [x] CL-07: DiceGame1 獎勵 = sum(dice) × rewardPerDiceValue — R5.3 → UT-Dice1-01 (golden value cross-check with Java) — Result: `PASS`
- [x] CL-08: DiceGame2 straight 偵測（連續遞增 ≥3）— R6.1 → UT-Dice2-01 (golden value cross-check) — Result: `PASS`
- [x] CL-09: DiceGame2 triple 偵測（恰好 3 相同、不與 straight 重疊）— R6.2 → UT-Dice2-02 — Result: `PASS`
- [x] CL-10: GameRewardService 大額獎勵分割 — R5.4 → UT-Reward-01 — Result: `PASS`
- [x] CL-11: zh-TW 在地化訊息格式 — R5 → UT-L10n-01 — Result: `PASS`

## Hardening Checklist

- N/A 對隨機骰子序列，TS 和 Java 產出相同獎勵總額（cross-check harness）
- [x] Adversarial: tokenCount=0、負數 tokenCount、超大 tokenCount
- [x] Concurrency: 兩個 adjustBalance 同時執行 → DB constraint 保證最終一致性
- [x] Fixtures: seeded random (seed=42) 確保測試可重現

## Execution Summary

- [x] Unit: `COMPLETED`
- N/A `COMPLETED`
- [x] Integration: `COMPLETED`
