import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { Pool } from 'pg';
import { getTestPool, cleanAllTestTables } from '../infra/database/test-db-reset.js';
import { drizzle } from 'drizzle-orm/node-postgres';
import { sql } from 'drizzle-orm';
import {
  seedGuild,
  seedUserAccount,
  seedProduct,
  seedRedemptionCode,
  seedDiceGame1Config,
  seedFiatOrder,
} from './seed-factory.js';

const CONNECTION_URL = process.env.__TEST_CONTAINER_URL;

describe('Integration PBT Infrastructure Smoke Test', () => {
  let testPool: Pool;

  beforeAll(async () => {
    // cleanAllTestTables handles test isolation without terminating
    // other workspace projects' connections

    // Create a test pool for data operations on the target database
    testPool = getTestPool(CONNECTION_URL!);
  });

  afterAll(async () => {
    await testPool?.end().catch(() => {});
  });

  it('should reset database from template', async () => {
    await cleanAllTestTables(CONNECTION_URL!);

    // Connect to freshly reset database
    const pool = new Pool({ connectionString: CONNECTION_URL, max: 1 });
    try {
      const db = drizzle(pool);

      // Verify tables exist
      const result = await db.execute<{ count: number }>(
        sql`SELECT COUNT(*)::int AS count FROM information_schema.tables WHERE table_schema = 'public'`,
      );
      expect(Number(result.rows?.[0]?.count)).toBeGreaterThan(0);
    } finally {
      await pool.end();
    }
  });

  it('should seed guild config', async () => {
    await cleanAllTestTables(CONNECTION_URL!);
    const pool = new Pool({ connectionString: CONNECTION_URL, max: 1 });
    try {
      const db = drizzle(pool);
      const guild = await seedGuild(db, {
        guildId: 42,
        currencyName: 'Gold',
        currencyIcon: '\u{1F4B0}',
      });
      expect(guild.guildId).toBe(42);
      expect(guild.currencyName).toBe('Gold');
      expect(guild.currencyIcon).toBe('\u{1F4B0}');

      // Verify via query
      const result = await db.execute<{ guild_id: number }>(
        sql`SELECT guild_id FROM guild_currency_config WHERE guild_id = 42`,
      );
      expect(Number(result.rows?.[0]?.guild_id)).toBe(42);
    } finally {
      await pool.end();
    }
  });

  it('should seed user account', async () => {
    await cleanAllTestTables(CONNECTION_URL!);
    const pool = new Pool({ connectionString: CONNECTION_URL, max: 1 });
    try {
      const db = drizzle(pool);
      await seedGuild(db, { guildId: 1 });
      const account = await seedUserAccount(db, {
        guildId: 1,
        userId: 200,
        balance: 50000,
        tokenBalance: 200,
      });
      expect(account.balance).toBe(50000);
      expect(account.tokenBalance).toBe(200);

      // Verify balance
      const balanceResult = await db.execute<{ balance: number }>(
        sql`SELECT balance FROM member_currency_account WHERE guild_id = 1 AND user_id = 200`,
      );
      expect(Number(balanceResult.rows?.[0]?.balance)).toBe(50000);

      // Verify tokens
      const tokenResult = await db.execute<{ tokens: number }>(
        sql`SELECT tokens FROM game_token_account WHERE guild_id = 1 AND user_id = 200`,
      );
      expect(Number(tokenResult.rows?.[0]?.tokens)).toBe(200);
    } finally {
      await pool.end();
    }
  });

  it('should seed product and redemption code', async () => {
    await cleanAllTestTables(CONNECTION_URL!);
    const pool = new Pool({ connectionString: CONNECTION_URL, max: 1 });
    try {
      const db = drizzle(pool);
      await seedGuild(db, { guildId: 1 });

      const product = await seedProduct(db, {
        guildId: 1,
        name: 'VIP Pass',
        rewardType: 'CURRENCY',
        rewardAmount: 5000,
        currencyPrice: 1000,
      });
      expect(product.id).toBeGreaterThan(0);
      expect(product.name).toBe('VIP Pass');

      const code = await seedRedemptionCode(db, {
        guildId: 1,
        productId: product.id,
        code: 'VIP-2024-ABC',
        quantity: 5,
      });
      expect(code.id).toBeGreaterThan(0);
      expect(code.code).toBe('VIP-2024-ABC');
    } finally {
      await pool.end();
    }
  });

  it('should seed dice game config', async () => {
    await cleanAllTestTables(CONNECTION_URL!);
    const pool = new Pool({ connectionString: CONNECTION_URL, max: 1 });
    try {
      const db = drizzle(pool);

      const dice1 = await seedDiceGame1Config(db, {
        guildId: 1,
        minTokensPerPlay: 5,
        maxTokensPerPlay: 50,
        rewardPerDiceValue: 500000,
      });
      expect(dice1.maxTokensPerPlay).toBe(50);

      // Verify via query
      const result = await db.execute<{ min_tokens_per_play: number }>(
        sql`SELECT min_tokens_per_play FROM dice_game1_config WHERE guild_id = 1`,
      );
      expect(Number(result.rows?.[0]?.min_tokens_per_play)).toBe(5);
    } finally {
      await pool.end();
    }
  });

  it('should seed fiat order', async () => {
    await cleanAllTestTables(CONNECTION_URL!);
    const pool = new Pool({ connectionString: CONNECTION_URL, max: 1 });
    try {
      const db = drizzle(pool);
      await seedGuild(db, { guildId: 1 });
      const product = await seedProduct(db, {
        guildId: 1,
        name: 'Fiat Product',
        fiatPriceTwd: 500,
      });

      const order = await seedFiatOrder(db, {
        guildId: 1,
        buyerUserId: 100,
        productId: product.id,
        productName: 'Fiat Product',
        orderNumber: 'SMOKE-TEST-001',
        paymentNo: 'SMOKE-PAY-001',
        amountTwd: 500,
        status: 'PENDING_PAYMENT',
        expireAt: new Date(Date.now() + 86400000),
      });
      expect(order.id).toBeGreaterThan(0);
      expect(order.status).toBe('PENDING_PAYMENT');
    } finally {
      await pool.end();
    }
  });

  it('should fully reset DB between tests', async () => {
    // Start with a clean database
    await cleanAllTestTables(CONNECTION_URL!);

    // Seed some data
    const seedPool = new Pool({ connectionString: CONNECTION_URL, max: 1 });
    try {
      const db = drizzle(seedPool);
      await seedGuild(db, { guildId: 999 });
      const guildResult = await db.execute<{ count: number }>(
        sql`SELECT COUNT(*)::int AS count FROM guild_currency_config`,
      );
      expect(Number(guildResult.rows?.[0]?.count)).toBe(1);
    } finally {
      await seedPool.end();
    }

    // Reset
    await cleanAllTestTables(CONNECTION_URL!);

    // Verify it's clean (no custom data)
    const cleanPool = new Pool({ connectionString: CONNECTION_URL, max: 1 });
    try {
      const db = drizzle(cleanPool);

      // guild_currency_config should be empty after reset
      const guildResult = await db.execute<{ count: number }>(
        sql`SELECT COUNT(*)::int AS count FROM guild_currency_config`,
      );
      expect(Number(guildResult.rows?.[0]?.count)).toBe(0);

      // But tables should still exist (templates preserve schema)
      const tableResult = await db.execute<{ count: number }>(
        sql`SELECT COUNT(*)::int AS count FROM information_schema.tables WHERE table_schema = 'public'`,
      );
      expect(Number(tableResult.rows?.[0]?.count)).toBeGreaterThan(0);
    } finally {
      await cleanPool.end();
    }
  });
});
