# Design: Caddy HTTPS ingress

- Date: 2026-04-09
- Feature: Caddy HTTPS ingress
- Change Name: caddy-https-ingress

## Design Goal
以 repo 管理的 Caddy sidecar 取代 Nginx sidecar，將 TLS termination 與 ACME 自動續期收斂到 Compose 內，同時維持 bot 內嵌 callback server 的 loopback 安全邊界與現有付款路由真相來源。

## Change Summary
- Requested change: 新增自動 HTTPS ingress，並改用 Caddy 管理 VPS 對外公開 domain 的憑證與反向代理。
- Existing baseline: Compose 目前使用 Nginx sidecar 在 `80` 對外，代理到 bot 的 `127.0.0.1:8085`；TLS 與憑證管理完全不在 repo 內。
- Proposed design delta: 以 Caddy sidecar 取代 Nginx，暴露 `80/443`、持久化 Caddy runtime state、使用 domain/email env 啟用自動 HTTPS，並繼續代理既有 landing page/callback 路由到 bot loopback。

## Scope Mapping
- Spec requirements covered: `R1.1-R1.3`, `R2.1-R2.3`
- Affected modules: `docker-compose.yml`, `docker/caddy/Caddyfile`（新檔）, `.env.example`
- External contracts involved: `Caddy automatic HTTPS + reverse proxy`, `Docker Compose service and volume wiring`
- Coordination reference: `../coordination.md`

## Current Architecture
目前部署鏈路如下：
1. `docker-compose.yml` 啟動 `bot`、`postgres`、`redis` 與 `nginx` service。
2. `nginx` 以 `network_mode: service:bot` 共用 bot network namespace，代理所有對外 `80` 請求到 `http://127.0.0.1:8085`。
3. `EcpayCallbackHttpServer` 在 bot 內綁定 loopback，提供 `/` 與 `ECPAY_CALLBACK_PATH`。

現況問題：
- operator 仍需自己確保外部 HTTPS、憑證續期與安全對外入口。
- repo 內 ingress 只解決 HTTP proxy，沒有把公開 domain / TLS 契約收斂成受版本控管的部署能力。

## Proposed Architecture
- `docker-compose.yml` 以 `caddy` service 取代 `nginx` service，保留與 bot 共用 namespace 的設計，避免 callback server 改成 public bind。
- 新增 repo 管理的 `docker/caddy/Caddyfile`，以公開 domain 作為 site address，自動啟用 HTTPS，並將所有請求 reverse proxy 到 `127.0.0.1:8085`。
- 使用命名 volume 保存 Caddy `data` / `config`，確保 cert 與 ACME state 不因容器重建遺失。
- `APP_PUBLIC_BASE_URL` / `ECPAY_RETURN_URL` 的應用層語意不變；只改 ingress 實作與部署 env contract。

## Component Changes

### Component 1: `docker-compose.yml`
- Responsibility: 定義 Caddy sidecar、公開 `80/443`、volume 持久化與 bot namespace 共用方式
- Inputs: `APP_PUBLIC_DOMAIN` / `CADDY_ACME_EMAIL` 類 env（最終名稱由 batch coordination 定義）、Compose runtime
- Outputs: 可啟動的 ingress 容器與持久化 TLS state
- Dependencies: `Docker Compose service and volume wiring`
- Invariants: bot 仍只在 loopback 提供 callback server；Compose 不直接對外暴露 `8085`

### Component 2: `docker/caddy/Caddyfile`
- Responsibility: 定義公開站點、automatic HTTPS 與 reverse proxy 規則
- Inputs: 公開 domain、ACME email、upstream `127.0.0.1:8085`
- Outputs: 對外 HTTPS listener 與 proxied callback/landing page requests
- Dependencies: `Caddy automatic HTTPS + reverse proxy`
- Invariants: 所有 path 仍由 bot 內嵌 HTTP server 決定；Caddy 不改寫 callback payload，也不新增平行應用邏輯

## Sequence / Control Flow
1. operator 在 `.env` 提供公開 domain 與 ACME email，並確認 DNS 指向 VPS、`80/443` 可對外連入。
2. Compose 啟動 `caddy` service，Caddy 用 site address 啟用 automatic HTTPS，必要時先走 ACME 驗證。
3. 外部請求到達 `https://<domain>/` 或 `https://<domain>/<callback path>` 時，Caddy reverse proxy 到 bot 的 `127.0.0.1:8085`。
4. bot 內的 `EcpayCallbackHttpServer` 照舊處理首頁與 callback，應用層不感知 TLS termination。

## Data / State Impact
- Created or updated data: Caddy ingress config、新增 TLS state named volumes、新的 deployment env keys
- Consistency rules: `APP_PUBLIC_BASE_URL` 仍是應用層公開入口真相；Caddy 的公開 domain 必須與 operator 對外 DNS 配置一致
- Migration / rollout needs: operator 需在切換到 Caddy 前開放 `80/443`、保留或建立 Caddy volumes、確認 domain 已指向 VPS

## Risk and Tradeoffs
- Key risks: ACME 驗證失敗導致 HTTPS 無法簽出；錯誤 domain 造成 callback 無法到達；volume 遺失造成憑證狀態重建
- Rejected alternatives:
  - 保留 Nginx 並讓 operator 自己做 TLS：無法達成「repo 內自管 HTTPS」的目標
  - 讓 bot 直接 public bind `443`：會把 TLS 與 callback server 混在應用層，且破壞 stage mode loopback 安全預設
  - 使用 Vercel 作為正式 callback proxy：與目前 user 已確認的 `ltdj.ddns.net` 直連策略衝突
- Operational constraints: VPS 必須有穩定公開 domain 與開放 `80/443`；自動簽證是否成功取決於真實 DNS/網路環境，無法只靠本地單元測試保證

## Validation Plan
- Tests: `docker compose config`、`CaddyIngressConfigTest`、必要時在 VPS 以實際 domain 驗證 `https://` 可達；property-based `N/A`
- Contract checks: 對照 Caddy docs 確認 site address/automatic HTTPS/reverse_proxy 用法；對照 Compose docs 確認 ports/volumes/service wiring 合法
- Execution notes: 已以 sample env 執行 `docker compose config`；嘗試以 `docker run ... caddy validate` 做額外靜態檢查，但本機 Docker daemon 不可用，因此保留到 VPS/可用 Docker 環境再跑
- Rollback / fallback: 可回退到現有 Nginx sidecar 與外部 TLS termination；應用層 `APP_PUBLIC_BASE_URL` / callback route 不需回滾變更

## Open Questions
None
