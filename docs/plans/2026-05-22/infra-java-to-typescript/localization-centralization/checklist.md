# Checklist: Localization Centralization

- Date: 2026-05-22
- Feature: localization-centralization

## Usage Notes

- 在地化字串必須與 Java 原始碼 1:1 一致，不得新增、刪除或修改翻譯內容。
- 測試重點在比對正確性（drift check against Java source）。
- Property-based coverage: `N/A` — 靜態字串對照，非邏輯密集。
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded (N/A — 需求明確).
- [ ] Affected plans updated after clarification (N/A).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

- [x] CL-01: 所有指令名稱 zh-TW 翻譯與 Java 一致 — R1.1 → T1.1 — Result: `PASS`
- [x] CL-02: 所有指令描述 zh-TW 翻譯與 Java 一致 — R1.2 → T1.1 — Result: `PASS`
- [x] CL-03: 所有選項名稱 zh-TW 翻譯與 Java 一致 — R1.3 → T1.1 — Result: `PASS`
- [x] CL-04: 所有 choice 值 zh-TW 翻譯與 Java 一致 — R1.5 → T1.1 — Result: `PASS`
- [x] CL-05: 骰子遊戲訊息與 Java `DiceGameMessages` 一致 — R2.1 → T2.1 — Result: `PASS`
- [x] CL-06: 既有功能在遷移 import 後不受影響 — R3.x → T3.1-T3.4 — Result: `PASS`

## Hardening Checklist

- [ ] Regression tests — 現有測試在 import 遷移後全數通過 (`make test`).
- [ ] Unit drift checks — 手動比對 TypeScript 與 Java 的在地化鍵值對.
- [ ] Property-based coverage (N/A — 靜態字串).
- [ ] External services mocked/faked (N/A — 無外部依賴).
- [ ] Adversarial cases (N/A).
- [ ] Authorization, idempotency, concurrency risks (N/A — 唯讀資料).
- [ ] Assertions verify outcomes — 測試驗證 `getNameLocalizations("balance")` 回傳 `{ "zh-TW": "餘額" }`.
- [ ] Fixtures reproducible (N/A).

## E2E / Integration Decisions

- [ ] Discord 指令註冊: Integration replacement — Reason: 需透過 Discord API 確認指令在地化正確顯示；可用 bot 啟動後檢查註冊的指令定義

## Execution Summary

- [x] Unit: `PASS`
- [x] Regression: `PASS`
- [ ] Property-based: `N/A`
- [ ] Integration: `N/A`
- [ ] E2E: `N/A`
- [x] Mock scenarios: `PASS`
- [ ] Adversarial: `N/A`

## Completion Records

- [x] Localization centralization: done — T1, T2, T3
