# Checklist: Interactive env setup assistant

- Date: 2026-04-09
- Feature: Interactive env setup assistant

## Usage Notes
- This checklist is tailored for a shell/operator workflow change rather than application business logic.
- Use `- [ ]` for all items; mark completed items as `- [x]`.
- Property-based coverage is expected to be `N/A` unless implementation unexpectedly introduces non-trivial parsing logic worth generative testing.

## Clarification & Approval Gate (required when clarification replies exist)
- [x] User clarification responses are recorded (map to `spec.md`; if none, mark `N/A`).
- [x] Affected plans are reviewed/updated (`spec.md` / `tasks.md` / `checklist.md` / `contract.md` / `design.md`; if no updates needed, mark `N/A` + reason).
- [x] Explicit user approval on updated specs is obtained (date/conversation reference: 2026-04-09 current thread invoked `$implement-specs-with-worktree` for this spec path).

## Behavior-to-Test Checklist

- [x] CL-SETUP-01 New `setup-env` creates or updates `.env` through interactive prompts
  - Requirement mapping: `R1.1-R1.3`
  - Actual test case IDs: `IT-SETUP-01`
  - Test level: Integration
  - Risk class: regression
  - Property/matrix focus: external state matrix
  - External dependency strategy: none
  - Oracle/assertion focus: exact `.env` output, summary text, normalized `APP_PUBLIC_BASE_URL`
  - Test result: `PASS`
  - Notes (optional): `scripts/setup-env.test.sh` `test_setup_env_creates_and_normalizes_values`

- [x] CL-SETUP-02 Existing secrets survive interactive setup unless explicitly replaced
  - Requirement mapping: `R2.1-R2.2`
  - Actual test case IDs: `IT-SETUP-02`
  - Test level: Integration
  - Risk class: data integrity
  - Property/matrix focus: external state matrix
  - External dependency strategy: none
  - Oracle/assertion focus: preserved key/value pairs and backup file creation
  - Test result: `PASS`
  - Notes (optional): `scripts/setup-env.test.sh` `test_setup_env_preserves_existing_secrets_and_backup`

- [x] CL-SETUP-03 `update-env` preserves existing non-interactive sync semantics
  - Requirement mapping: `R2.3`
  - Actual test case IDs: `IT-SETUP-03`
  - Test level: Integration
  - Risk class: regression
  - Property/matrix focus: external state matrix
  - External dependency strategy: none
  - Oracle/assertion focus: missing keys added, stale keys removed, existing values preserved
  - Test result: `PASS`
  - Notes (optional): `scripts/setup-env.test.sh` `test_update_env_preserves_sync_semantics`

## Required Hardening Records
- [x] Regression tests are added/updated for bug-prone or high-risk behavior, or `N/A` is recorded with a concrete reason.
- [x] Property-based coverage is added/updated for changed business logic, or `N/A` is recorded with a concrete reason.
- [x] External services in the business logic chain are mocked/faked for scenario testing, or `N/A` is recorded with a concrete reason.
- [x] Adversarial/penetration-style cases are added/updated for abuse paths and edge combinations, or `N/A` is recorded with a concrete reason.
- [x] Authorization, invalid transition, replay/idempotency, and concurrency risks are evaluated; uncovered items are marked `N/A` with concrete reasons.
- [x] Assertions verify business outcomes and side effects/no-side-effects, not only "returns 200" or "does not throw".
- [x] Test fixtures are reproducible (fixed seed/clock/fixtures) or `N/A` is recorded with a concrete reason.

Notes:
- Property-based coverage: `N/A` — the planned change is operator I/O workflow, not app business logic.
- External services: `N/A` — script runs locally against fixture files.
- Authorization/idempotency/concurrency: `N/A` except ensuring repeat runs do not clobber unconfirmed values.

## E2E / Integration Decision Records

### Decision Record 1: Interactive setup flow
- Requirement mapping: `R1.1-R1.3 / CL-SETUP-01`
- Decision: Cover with integration instead
- Linked case IDs: `IT-SETUP-01`
- Reason: shell prompting is best validated with scripted stdin/stdout fixture runs, not browser-style E2E

### Decision Record 2: Legacy sync compatibility
- Requirement mapping: `R2.3 / CL-SETUP-03`
- Decision: Existing coverage already sufficient
- Linked case IDs: `scripts/db/create-db.test.sh` style shell tests pattern + new env fixture checks
- Reason: risk is regression in file-sync semantics, which deterministic shell fixture tests can catch

## Execution Summary (fill with actual results)
- [x] Unit tests: `N/A`
- [x] Regression tests: `PASS`
- [x] Property-based tests: `N/A`
- [x] Integration tests: `PASS`
- [x] E2E tests: `N/A`
- [x] External service mock scenarios: `N/A`
- [x] Adversarial/penetration-style cases: `PASS`

## Completion Records

### Completion Record 1: Interactive setup implementation
- Requirement mapping: `R1.1-R2.3 / Task 1-3 / CL-SETUP-01..03`
- Completion status: completed
- Remaining applicable items: none
- Notes: `scripts/setup-env.sh` 新增互動式入口，保留 `scripts/sync-env.sh` 作為 `update-env`，並以 `scripts/setup-env.test.sh` 驗證建立 / 保留 / 取消 / 非 TTY / 同步相容性。

### Completion Record 2: Legacy command rename rollout
- Requirement mapping: `R2.3 / Task 1 / CL-SETUP-03`
- Completion status: completed
- Remaining applicable items: none
- Notes: `Makefile`、`README.md`、`docs/getting-started.md`、`docs/configuration.md` 已更新為 `setup-env` / `update-env` 新語意。
