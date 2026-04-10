# Coordination: Caddy HTTPS ingress

- Date: 2026-04-09
- Batch: caddy-env-setup

## Coordination Goal
把自架部署的公開入口、TLS、自動憑證與 `.env` operator 體驗收斂成一致的受版本控管流程：Caddy spec 負責 ingress/TLS，設定助手 spec 負責讓 operator 正確填出同一組 env contract。

## Batch Scope
- Included spec sets: `None`
- Archived completed spec sets:
  - `interactive-env-setup-assistant` → `docs/plans/archive/2026-04-09/caddy-env-setup/interactive-env-setup-assistant`
  - `caddy-https-ingress` → `docs/plans/archive/2026-04-09/caddy-env-setup/caddy-https-ingress`
- Shared outcome: operator 只需在互動式 `setup-env` 中回答公開 domain / TLS 等問題，就能生成符合 Caddy ingress 的 `.env`，並由 Compose 以 Caddy 安全對外提供 landing page 與 ECPay callback
- Out of scope: Vercel proxy callback、DNS provider API 自動化、付款 business logic 變更
- Independence rule: 兩份 spec 都以同一組 env names 為前提，但各自都能獨立實作與驗證；設定助手不依賴 Caddy code 先落地，Caddy spec 也不依賴互動腳本先存在

## Shared Context
- Current baseline: Compose 目前使用 Nginx sidecar 代理到 bot loopback；`make setup-env` 目前只是 `.env` 同步腳本，不是互動式初始化
- Shared constraints:
  - `EcpayCallbackHttpServer` 必須維持 loopback bind 與既有 callback 路由
  - `APP_PUBLIC_BASE_URL` / `ECPAY_RETURN_URL` 的應用層優先序不可改變
  - DNS / ACME live validation 只能在真實 VPS 環境驗證
- Shared invariants:
  - 對外公開入口必須是 HTTPS
  - callback 路徑語意不得改變
  - `.env.example` 仍是設定模板真相來源

## Shared Preparation

### Shared Fields / Contracts
- Shared fields to introduce or reuse:
  - `APP_PUBLIC_DOMAIN`（供 ingress/TLS 使用的公開 domain；最終名稱可在實作前最後確認）
  - `CADDY_ACME_EMAIL`
  - `APP_PUBLIC_BASE_URL`
  - `ECPAY_RETURN_URL`
  - `ECPAY_CALLBACK_PATH`
- Canonical source of truth:
  - ingress / TLS env contract 由 `caddy-https-ingress` spec 定義
  - operator 填值流程由 `interactive-env-setup-assistant` spec 產生並寫入上述欄位
- Required preparation before implementation:
  - 確認新舊命令命名：`setup-env` = 互動式流程，`update-env` = 現有同步流程
  - 確認 `APP_PUBLIC_BASE_URL` 與公開 domain 的對映規則（預設 `https://<domain>`）

### Replacement / Legacy Direction
- Legacy behavior being replaced: Nginx sidecar ingress 與名稱誤導的 `setup-env` 同步命令
- Required implementation direction: replace in place for ingress；rename + introduce new interactive entry for env workflow
- Compatibility window: temporary coexistence period for command rename (`update-env` should preserve old sync behavior)
- Cleanup required after cutover: 更新 README / Makefile 說明，移除對舊 `setup-env` 語意的舊描述

## Spec Ownership Map

### Spec Set 1: `caddy-https-ingress`
- Primary concern: 對外 ingress container、automatic HTTPS、cert persistence、loopback proxy wiring
- Allowed touch points: `docker-compose.yml`, `docker/caddy/**`, `.env.example`
- Must not change: shell setup scripts 與 Makefile 命令命名，除非僅為讀取已協調好的 env names
- Depends on shared preparation: shared env names only
- Cross-spec implementation dependency: None

### Spec Set 2: `interactive-env-setup-assistant`
- Primary concern: `setup-env` / `update-env` 命名與互動式 `.env` 生成體驗
- Allowed touch points: `scripts/**`, `Makefile`, operator-facing setup docs
- Must not change: ingress container implementation細節，除非更新對應的 env prompt 文案
- Depends on shared preparation: shared env names and command naming
- Cross-spec implementation dependency: None

## Conflict Boundaries
- Shared files requiring coordination: `.env.example`, `README.md`
- Merge order / landing order: optional convenience order only;建議先落 Caddy env contract，再落互動式腳本與 docs
- Worktree notes: 若分開實作，兩邊都必須使用同一組 env key 名稱與 `setup-env` / `update-env` 命名

## Integration Checkpoints
- Combined behaviors to verify after merge:
  - `setup-env` 產生的 `.env` 能被 Caddy ingress / bot 直接使用
  - `APP_PUBLIC_BASE_URL` 與 Caddy domain 對外入口一致
  - `https://<domain>/<callback path>` 會經 Caddy 到達 bot loopback callback server
- Required final test scope: compose config validation、shell fixture tests、真實 VPS 上的 HTTPS/callback smoke verification
- Rollback notes: 可先回退到 Nginx + 手動 `.env`；兩份 spec 都不應改動付款 business logic 或資料庫 state

## Open Questions
None
