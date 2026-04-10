# Tasks: Caddy HTTPS ingress

- Date: 2026-04-09
- Feature: Caddy HTTPS ingress

## **Task 1: Replace ingress container**

以 Caddy sidecar 取代 Nginx sidecar，對應 `R1.1-R1.3`，核心目標是讓 Compose 對外 ingress 直接支援 automatic HTTPS 與憑證狀態持久化。

- 1. [x] 更新 Compose ingress service
  - 1.1 [x] 將 `nginx` service 替換為 `caddy` service，暴露 `80` / `443`
  - 1.2 [x] 新增 Caddy data / config volume，保留 ACME 與 runtime 狀態
  - 1.3 [x] 保持與 bot 共用 network namespace，延續 loopback proxy 模式

## **Task 2: Define repo-managed Caddy config**

對應 `R1.2`, `R2.1-R2.3`，核心目標是以 repo 管理的 Caddyfile 代理首頁與 callback，同時保留現有安全邊界與 env contract。

- 2. [x] 建立 Caddy ingress 設定
  - 2.1 [x] 定義公開 domain、ACME email 與 proxy target 的配置方式
  - 2.2 [x] 代理 `/` 與 `ECPAY_CALLBACK_PATH` 到 `127.0.0.1:8085`
  - 2.3 [x] 明確保留 callback server loopback bind，避免 public bind regression

## **Task 3: Validate deployment contract**

對應 `R1.x`, `R2.x`，核心目標是驗證 Compose/Caddy 設定可展開、回歸風險有覆蓋、且 operator 能看出 TLS 依賴條件。

- 3. [x] 補 deployment 驗證與回歸檢查
  - 3.1 [x] 以 `docker compose config` 驗證 service / volume / env wiring
  - 3.2 [x] 補最小 regression coverage 或明確記錄 `N/A`
  - 3.3 [x] 驗證文件與範本已反映 domain / TLS 前提

## Notes
- Task order should reflect actual implementation sequence.
- Every main task maps back to `spec.md` requirement IDs.
- Integration coverage should focus on ingress wiring and config validation; property-based testing is expected to be `N/A` unless app business logic changes.
- After execution, the agent must update each checkbox (`[x]` for done, `[ ]` for not done).
