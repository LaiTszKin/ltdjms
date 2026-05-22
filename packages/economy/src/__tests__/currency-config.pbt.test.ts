import { describe, it, beforeEach, expect } from 'vitest';
import * as fc from 'fast-check';
import { container, isOk } from '@ltdjms/shared';
import { getTestPool } from '@ltdjms/shared/infra/database/test-db-reset';
import { resetRootContainer } from '@ltdjms/shared/__tests__/test-container';
import { seedGuild } from '@ltdjms/shared/__tests__/seed-factory';
import { guildId, currencyName, currencyIcon } from '@ltdjms/shared/__tests__/arbitrary';
import { drizzle } from 'drizzle-orm/node-postgres';
import { configureEconomyContainer, ECONOMY_TOKENS } from '../di/economy-module.js';
import { DEFAULT_CURRENCY_NAME, DEFAULT_CURRENCY_ICON } from '../domain/types.js';
import type { CurrencyConfigService } from '../currency/services/currency-config-service.js';

/**
 * Fast cleanup of economy test tables between fast-check predicate runs.
 */
async function cleanTestTables(pool: ReturnType<typeof getTestPool>): Promise<void> {
  await pool.query('DELETE FROM guild_currency_config');
  await pool.query('DELETE FROM member_currency_account');
  await pool.query('DELETE FROM currency_transaction');
}

describe('CurrencyConfig PBT', () => {
  let pool: ReturnType<typeof getTestPool>;
  let configService: CurrencyConfigService;

  beforeEach(async () => {
    pool = getTestPool(process.env.__TEST_CONTAINER_URL!);
    await cleanTestTables(pool);
    resetRootContainer(pool);
    configureEconomyContainer();
    configService = container.resolve(ECONOMY_TOKENS.CurrencyConfigService);
  });

  // Querying the config after seeding returns the exact values that were seeded.
  it('should return the seeded currency config', async () => {
    await fc.assert(
      fc.asyncProperty(
        guildId(),
        currencyName(),
        currencyIcon(),
        async (gId, name, icon) => {
          await cleanTestTables(pool);
          const db = drizzle(pool);
          await seedGuild(db, { guildId: gId, currencyName: name, currencyIcon: icon });

          const result = await configService.tryGetConfig(gId);
          expect(isOk(result)).toBe(true);
          if (isOk(result)) {
            const config = result.getValue();
            expect(config.guildId).toBe(gId);
            expect(config.currencyName).toBe(name);
            expect(config.currencyIcon).toBe(icon);
          }
        },
      ),
      { numRuns: 50 },
    );
  });

  // Querying a guild with no seeded config returns defaults.
  it('should return default config for unconfigured guild', async () => {
    await fc.assert(
      fc.asyncProperty(guildId(), async (gId) => {
        await cleanTestTables(pool);
        const result = await configService.tryGetConfig(gId);
        expect(isOk(result)).toBe(true);
        if (isOk(result)) {
          const config = result.getValue();
          expect(config.guildId).toBe(gId);
          expect(config.currencyName).toBe(DEFAULT_CURRENCY_NAME);
          expect(config.currencyIcon).toBe(DEFAULT_CURRENCY_ICON);
        }
      }),
      { numRuns: 50 },
    );
  });
});
