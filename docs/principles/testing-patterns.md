# Testing Patterns

## Test Organization

Tests live alongside source code in `packages/*/src/__tests__/`. Each package has its own `vitest.config.ts`. The root `Makefile` orchestrates cross-package test runs.

- `*.test.ts` — standard unit tests (no external dependencies)
- `*.pbt.test.ts` — property-based tests using fast-check (uses Testcontainers PostgreSQL)
- `*-e2e.test.ts` — end-to-end tests (ECPay Stage API, controlled by `RUN_ECPAY_E2E`)

## Test Categories

### Pure Unit Tests
- Use vitest' `describe`/`it`/`expect` with `vi.fn()` mocks
- Test both `Ok` and `Err` paths of `Result<T, DomainError>`
- Test `DomainError` factory methods and error categories

### Property-Based Tests (PBT)
- Use `fast-check` (`fc.assert`, `fc.asyncProperty`, `fc.integer`, `fc.record`, etc.)
- Generate random inputs within business constraints using shared arbitraries
- Each PBT file runs in an isolated vitest process via the Makefile to avoid tsyringe DI container contamination
- DB state is reset per-test via `cleanAllTestTables()` (DELETE-based, connection-safe)
- DI container is re-initialized per-test via `resetRootContainer()`

**Pattern**:
```typescript
import * as fc from 'fast-check';
import { guildId, userId, positiveAmount } from '@ltdjms/shared/__tests__/arbitrary';

it('should conserve total balance', async () => {
  await fc.assert(
    fc.asyncProperty(
      guildId(),
      fc.array(userId(), { minLength: 2, maxLength: 5 }),
      positiveAmount(100, 10000),
      async (gId, users, initialBalance) => {
        // Seed data, execute operation, verify invariants
      },
    ),
    { numRuns: 50 },
  );
});
```

### Integration PBT Tests
- Use Testcontainers PostgreSQL via `@testcontainers/postgresql`
- Container lifecycle: `vitest.globalSetup.ts` starts a container, runs migrations, creates a `template_clean` database
- Container is reused across sequential `vitest run` processes via `/tmp/ltdjms-testcontainers.json`
- Ryuk is disabled for cross-process reuse; container is shared until `make test` completes
- Seed data factory (`seedGuild`, `seedUserAccount`, `seedProduct`, `seedRedemptionCode`, etc.) provides sensible defaults with partial override support
- All modules resolve real services from tsyringe DI container

**Pattern**:
```typescript
beforeEach(async () => {
  currentPool = getTestPool(CONNECTION_URL);
  container.clearInstances();
  initializeContainer({ databasePool: currentPool, logger: pino({ level: 'silent' }) });
  configureEconomyContainer();
  db = drizzle(currentPool);
  testService = container.resolve(TOKENS.SomeService);
});

it('should persist entity correctly', async () => {
  await fc.assert(fc.asyncProperty(guildId(), async (gId) => {
    await cleanAllTestTables(CONNECTION_URL);
    const result = await testService.someOperation(gId);
    expect(isOk(result)).toBe(true);
  }));
});
```

### E2E Tests (ECPay)
- Controlled by `RUN_ECPAY_E2E=true` environment variable; skipped by default
- Use real ECPay Stage API (MerchantID `2000132`) for CVS payment code generation
- Build callback payloads manually with AES-256-CBC encryption + CheckMacValue
- Verify full round-trip: seed DB order → call ECPay API → process callback → assert DB state
- `retryOnTimeout` helper with linear backoff (3 retries) for transient network issues

### Mock-Based Tests (Admin Facade)
Admin facade tests use `vi.fn()` mocks to verify delegation, event publishing, and input validation without real DB.

**Pattern**:
```typescript
beforeEach(() => {
  mockBalanceService = { getBalance: vi.fn() };
  facade = new CurrencyManagementFacade(
    mockBalanceService as BalanceService,
    mockAdjustService as BalanceAdjustmentService,
    mockConfigService as CurrencyConfigService,
  );
});

it('should reject invalid input', async () => {
  await fc.assert(fc.asyncProperty(guildId(), userId(), fc.integer({ max: 0 }),
    async (gId, uId, amount) => {
      const result = await facade.adjustBalance(String(gId), String(uId), amount);
      expect(result.isErr()).toBe(true);
      expect(result.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
    },
  ));
});
```

## Shared Test Infrastructure

Located in `packages/shared/src/__tests__/`:

| File | Exports | Purpose |
|------|---------|---------|
| `arbitrary.ts` | `guildId()`, `userId()`, `positiveAmount()`, `betAmount()`, `multiplier()`, `transferRequest()`, `diceGamePlay()`, etc. | Domain-specific fast-check generators |
| `seed-factory.ts` | `seedGuild()`, `seedUserAccount()`, `seedProduct()`, `seedRedemptionCode()`, `seedDiceGame1Config()`, `seedFiatOrder()` | DB seed helpers with sensible defaults |
| `test-container.ts` | `createTestContainer()`, `resetRootContainer()` | tsyringe DI container helpers |
| `assertion-helper.ts` | `assertBalanceConserved()`, `assertStateTransition()`, `measureResponseTime()` | Common test assertions |
| `vitest.globalSetup.ts` | `setup()` | Testcontainer lifecycle, migration runner |
| `vitest.globalTeardown.ts` | `teardown()` | No-op (Ryuk disabled, container reused) |

Database utilities in `packages/shared/src/infra/database/`:

| Function | Purpose |
|----------|---------|
| `getTestPool(connectionUrl)` | Creates a single-connection pg Pool for testing |
| `cleanAllTestTables(connectionUrl)` | DELETE all public tables with FK bypass for safe per-test cleanup |
| `resetDatabase(connectionUrl)` | DROP/CREATE DATABASE FROM TEMPLATE for full isolation (terminates connections) |
| `initProjectDatabase(projectName)` | Creates a dedicated database per workspace project |

## Running Tests

```bash
make test          # Full suite: unit + PBT (sequential), skips ECPay E2E
RUN_ECPAY_E2E=true make test   # Includes ECPay Stage API tests (requires network)
pnpm vitest run --project @ltdjms/economy packages/economy/src/__tests__/balance-transfer.pbt.test.ts  # Single PBT file
```

## Mock/Fake Strategy

- Discord runtime gateway is mocked via `runtimeGateway` object in test DI container initialization (all methods return no-op/default values)
- Redis is replaced by `NoOpCacheService` (no test uses real Redis)
- ECPay external API is real in E2E tests, not mocked
- Admin facade tests use `vi.fn()` mocks at the service boundary

## Assertion Style

- `expect(isOk(result)).toBe(true)` / `expect(isErr(result)).toBe(true)` for Result type
- `result.getValue()` to access success value; `result.getError()` for error
- DB state verification (query tables after operation) for integration tests
- `expect(purchaseResult.newBalance).toBe(expectedNewBalance)` for balance calculations
