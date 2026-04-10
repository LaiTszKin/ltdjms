# Contract: Caddy HTTPS ingress

- Date: 2026-04-09
- Feature: Caddy HTTPS ingress
- Change Name: caddy-https-ingress

## Purpose
這個 change 直接依賴 Caddy automatic HTTPS 與 Docker Compose service wiring 的官方契約；若假設錯誤，會直接影響公開 ingress、憑證簽發與 ECPay callback 可達性。

## Usage Rule
- Write one dependency record per external library, framework, SDK, API, CLI, platform service, or hosted system that materially constrains implementation.
- If no external dependency materially affects the change, write `None` under `## Dependency Records` and briefly explain why this document is not needed for the current scope.
- Every claim in this file must be backed by the official documentation or the verified upstream source actually used during planning.

## Dependency Records

### Dependency 1: Caddy automatic HTTPS + reverse proxy
- Type: hosted service / platform
- Version / Scope: Not fixed
- Official Source: https://caddyserver.com/docs/quick-starts/https
- Why It Matters: Caddy 會負責對外 TLS termination、自動簽發與續期憑證，並把公開 HTTP(S) 請求 reverse proxy 到 bot 的 loopback callback server
- Invocation Surface:
  - Entry points: Caddyfile site address, `reverse_proxy` directive, container runtime env
  - Call pattern: inbound HTTP/HTTPS reverse proxy
  - Required inputs: publicly reachable domain, reachable `80` / `443`, ACME contact email, proxy upstream `127.0.0.1:8085`
  - Expected outputs: valid HTTPS listener, automatic cert lifecycle, proxied callback / landing page requests
- Constraints:
  - Supported behavior: site address triggers automatic HTTPS by default; `reverse_proxy` forwards requests to upstream HTTP origin
  - Limits: ACME issuance requires the configured domain to resolve to the server and allow validation on standard ports
  - Compatibility: automatic HTTPS depends on public routability; local-only hostnames or blocked ports will prevent issuance
  - Security / access: ingress host must expose `80` / `443`; certificate state should persist across restarts
- Failure Contract:
  - Error modes: certificate issuance/renewal can fail when DNS, port reachability, or ACME validation is broken
  - Caller obligations: provide correct domain/email, persist Caddy data, inspect Caddy logs when issuance fails
  - Forbidden assumptions: do not assume a domain-less deploy can still provide trusted HTTPS; do not assume cert state is safe without persistent storage
  - Verification Plan:
    - Spec mapping: `R1.1-R1.3`, `R2.1`, `R2.3`
    - Design mapping: `Proposed Architecture`, `Component Changes`, `Validation Plan`
    - Planned coverage: `IT-CADDY-01` compose config validation, `IT-CADDY-02` proxy wiring check, log/document review
    - Evidence notes: Caddy docs state site addresses activate automatic HTTPS, environment variables can be expanded in Caddyfile config, and `reverse_proxy` forwards to upstream backends

### Dependency 2: Docker Compose service and volume wiring
- Type: platform
- Version / Scope: Compose specification, not fixed
- Official Source: https://docs.docker.com/reference/compose-file/services/
- Why It Matters: ingress sidecar replacement relies on Compose service wiring, shared network namespace, port exposure, environment passing, and persistent volumes
- Invocation Surface:
  - Entry points: `docker-compose.yml` service definitions, `network_mode`, `ports`, `volumes`, `environment`
  - Call pattern: container orchestration / runtime config expansion
  - Required inputs: valid env values, non-conflicting service config, mounted Caddyfile and volumes
  - Expected outputs: reproducible container startup, shared bot ingress namespace, persisted TLS state
- Constraints:
  - Supported behavior: service env interpolation, port publishing, persistent volume mounts, service network modes
  - Limits: invalid env or conflicting service config will fail `docker compose config` or startup
  - Compatibility: host must allow binding `80` / `443`; volume paths/named volumes must be writable by the container
  - Security / access: secrets remain in env / `.env`; compose must not expose bot loopback server directly
- Failure Contract:
  - Error modes: startup failure, invalid compose config, unavailable published ports, lost certificate state if volumes are missing
  - Caller obligations: validate config with `docker compose config`, keep `.env` complete, preserve named volumes across redeploys
  - Forbidden assumptions: do not assume Caddy can keep cert history without mounted data volume; do not assume Compose will silently normalize invalid service wiring
  - Verification Plan:
    - Spec mapping: `R1.1-R1.3`, `R2.1-R2.3`
    - Design mapping: `Component Changes`, `Data / State Impact`, `Validation Plan`
    - Planned coverage: `IT-CADDY-01`, config diff review, documentation sync review
    - Evidence notes: Compose spec defines service fields, `network_mode`, environment interpolation, published ports, and mounted volume behavior that govern ingress replacement
