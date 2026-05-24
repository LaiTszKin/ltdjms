# Spec: dependency-upgrade-express

- Date: 2026-05-24
- Feature: dependency-upgrade-express
- Owner: laitszkin

## Goal

將 `@ltdjms/shop` 的 Express HTTP callback server 從 Express 4 升級至 Express 5.2.1，確保 ECPay callback、health check 與 middleware 行為不變。

## Scope

### In Scope
- 升級 `express@^5.2.1`、`@types/express@^5.0.6`
- 修復 Express 5 breaking changes（path routing、middleware signature、removed APIs）
- 更新 shop callback server 與相關測試
- 驗證 ECPay callback 路由仍正確處理 POST body 與 signature

### Out of Scope
- ECPay 業務邏輯變更
- nginx/ingress 配置
- user-panel、AI

## Functional Behaviors (BDD)

### Requirement 1: Express 5 升級
**GIVEN** shop callback server 使用 Express 4
**WHEN** 升級至 Express 5.2.1
**THEN** 所有 HTTP route handler 行為不變
**AND** shop 測試全綠

**Requirements**:
- [x] R1.1 bump express + @types/express
- [x] R1.2 修復 Express 5 API breaking changes（參考 [Express 5 migration guide](https://expressjs.com/en/guide/migrating-5.html)）
- [x] R1.3 `pnpm vitest run --project @ltdjms/shop` 全綠

### Requirement 2: Callback 契約不變
**GIVEN** ECPay stage callback 測試存在
**WHEN** Express 5 升級完成
**THEN** callback auth、body parsing、response status code 與升級前一致

**Requirements**:
- [x] R2.1 callback route integration 測試全綠
- [x] R2.2 `RUN_ECPAY_E2E=true make test` 通過（若環境可用）— N/A：Docker runtime 不可用

## Error and Edge Cases
- [x] Express 5 移除 `app.del()` — 改用 `app.delete()`（shop 未使用 `app.del()`）
- [x] wildcard route 語法變更 — 更新 route pattern（shop 使用固定/設定路徑，無 wildcard）
- [x] async error handler — 確保 unhandled rejection 仍進 error middleware（callback POST handler 以 try/catch 處理）

## References
- [Express 5 Migration Guide](https://expressjs.com/en/guide/migrating-5.html)
- `packages/shop/src/` callback server
