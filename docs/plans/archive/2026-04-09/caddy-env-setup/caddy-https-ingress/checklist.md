# Checklist: Caddy HTTPS ingress

- Date: 2026-04-09
- Feature: Caddy HTTPS ingress

## Usage Notes
- This checklist is a starter template. Add, remove, or rewrite items based on actual scope.
- Use `- [ ]` for all items; mark completed items as `- [x]`.
- The final completion summary section may use structured placeholders instead of checkboxes.
- If an item is not applicable, keep `N/A` with a concrete reason.
- Do not mark placeholder examples or mutually exclusive alternatives as completed unless they were actually selected and executed.
- Duplicate or remove decision-record blocks as needed; the final document should contain as many records as the real change requires.
- Duplicate or remove completion-record blocks as needed; the final document should contain as many records as the real change requires.
- Suggested test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.
- For business-logic changes, property-based coverage is required unless a concrete `N/A` reason is recorded.
- Each checklist item should map to a distinct risk; avoid repeating shallow happy-path cases.

## Clarification & Approval Gate (required when clarification replies exist)
- [x] User clarification responses are recorded (map to `spec.md`; if none, mark `N/A`).
- [x] Affected plans are reviewed/updated (`spec.md` / `tasks.md` / `checklist.md` / `contract.md` / `design.md`; if no updates needed, mark `N/A` + reason).
- [x] Explicit user approval on updated specs is obtained (date/conversation reference: 2026-04-09 current thread requested `$implement-specs-with-worktree` for `caddy-https-ingress`).

## Behavior-to-Test Checklist (customizable)

- [x] CL-01 Caddy service exposes HTTPS ingress and certificate persistence
  - Requirement mapping: `R1.1-R1.3`
  - Actual test case IDs: `IT-CADDY-01`
  - Test level: Integration
  - Risk class: external failure
  - Property/matrix focus: external state matrix
  - External dependency strategy: near-real dependency
  - Oracle/assertion focus: exact compose expansion, persisted volume mounts, published `80` / `443`
  - Test result: `PASS`
  - Notes (optional): `docker compose config` with sample env verified `caddy` service, published `80/443`, named volumes, and required env wiring; live ACME issuance still requires real DNS and open ports

- [x] CL-02 Caddy proxies landing page and callback path to bot loopback server
  - Requirement mapping: `R2.1-R2.2`
  - Actual test case IDs: `IT-CADDY-02`
  - Test level: Integration
  - Risk class: regression
  - Property/matrix focus: external state matrix
  - External dependency strategy: near-real dependency
  - Oracle/assertion focus: route target remains `127.0.0.1:8085`, no public bind introduced
  - Test result: `PASS`
  - Notes (optional): `CaddyIngressConfigTest` asserts repo-managed Caddyfile proxies to `127.0.0.1:8085` and that Compose keeps `network_mode: service:bot`

- [x] CL-03 Operator-facing env/documentation describe HTTPS prerequisites and failure modes
  - Requirement mapping: `R2.3`
  - Actual test case IDs: `DOC-CADDY-01`
  - Test level: Integration
  - Risk class: external failure
  - Property/matrix focus: external state matrix
  - External dependency strategy: none
  - Oracle/assertion focus: docs mention DNS, `80/443`, domain/email inputs, and log-driven diagnosis
  - Test result: `PASS`
  - Notes (optional): `.env.example`, `README.md`, `docs/configuration.md`, and `docs/getting-started.md` now describe domain/email prerequisites and Caddy log-based diagnosis

## Required Hardening Records
- [x] Regression tests are added/updated for bug-prone or high-risk behavior, or `N/A` is recorded with a concrete reason.
- [x] Property-based coverage is added/updated for changed business logic, or `N/A` is recorded with a concrete reason.
- [x] External services in the business logic chain are mocked/faked for scenario testing, or `N/A` is recorded with a concrete reason.
- [x] Adversarial/penetration-style cases are added/updated for abuse paths and edge combinations, or `N/A` is recorded with a concrete reason.
- [x] Authorization, invalid transition, replay/idempotency, and concurrency risks are evaluated; uncovered items are marked `N/A` with concrete reasons.
- [x] Assertions verify business outcomes and side effects/no-side-effects, not only "returns 200" or "does not throw".
- [x] Test fixtures are reproducible (fixed seed/clock/fixtures) or `N/A` is recorded with a concrete reason.

Notes:
- Property-based coverage: `N/A` — this spec changes deployment wiring and operator config, not app business logic.
- External service mocks: `N/A` — ingress wiring is better validated through compose expansion and config inspection than synthetic mocks.
- Authorization/idempotency/concurrency: `N/A` beyond preserving existing callback bind boundary; no business transition semantics change.
- Adversarial coverage: `N/A` — this change introduces no custom request parsing or auth logic; abuse-path confidence comes from preserving loopback bind and requiring explicit domain/TLS env.
- Reproducible fixtures: `PASS` — tests use repository files and fixed sample env values.

## E2E / Integration Decision Records

### Decision Record 1: HTTPS ingress wiring
- Requirement mapping: `R1.1-R1.3 / CL-01`
- Decision: Cover with integration instead
- Linked case IDs: `IT-CADDY-01`
- Reason: live ACME issuance depends on real DNS and open public ports; repo-side validation should focus on deterministic compose/caddy configuration

### Decision Record 2: Callback path proxy preservation
- Requirement mapping: `R2.1-R2.2 / CL-02`
- Decision: Cover with integration instead
- Linked case IDs: `IT-CADDY-02`
- Reason: the main risk is route/proxy miswiring, not browser UX; config-level integration checks are higher signal than brittle E2E

## Execution Summary (fill with actual results)
- [x] Unit tests: `N/A`
- [x] Regression tests: `PASS`
- [x] Property-based tests: `N/A`
- [x] Integration tests: `PASS`
- [x] E2E tests: `N/A`
- [x] External service mock scenarios: `N/A`
- [x] Adversarial/penetration-style cases: `N/A`

## Completion Records

### Completion Record 1: Caddy ingress implementation
- Requirement mapping: `R1.1-R2.3 / Task 1-3 / CL-01..03`
- Completion status: completed
- Remaining applicable items: live VPS smoke validation only
- Notes: `docker-compose.yml`, `docker/caddy/Caddyfile`, `.env.example`, deployment docs, and `CaddyIngressConfigTest` are updated; `docker compose config` passed with sample envs

### Completion Record 2: Live certificate issuance validation
- Requirement mapping: `R1.2-R1.3 / CL-01`
- Completion status: deferred
- Remaining applicable items: runtime validation against real DNS and open `80/443`
- Notes: local `docker compose config` passed, but live ACME issuance still needs a real VPS; additional static `docker run ... caddy validate` check was blocked because the local Docker daemon was unavailable
