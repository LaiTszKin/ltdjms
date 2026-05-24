import { describe, it, expect, afterAll, beforeEach } from 'vitest';
import { Pool } from 'pg';
import { drizzle } from 'drizzle-orm/node-postgres';
import { DefaultAIChannelRestrictionService } from '../../services/routing/channel-restriction-service.js';
import { DrizzleAIChannelRestrictionRepository } from '../../persistence/drizzle-channel-restriction-repository.js';
import { DomainErrorCategory } from '@ltdjms/shared';

const CONNECTION_URL = process.env.__TEST_CONTAINER_URL;

/** UT-AIC-015 / R2.2 — AIChannelRestrictionIntegrationTest.java parity */
describe('UT-AIC-015 channel restriction integration', () => {
  let pool: Pool | undefined;

  beforeEach(async () => {
    if (!CONNECTION_URL) {
      return;
    }
    pool = new Pool({ connectionString: CONNECTION_URL, max: 2 });
    await pool.query(`
      CREATE TABLE IF NOT EXISTS ai_channel_restriction (
        guild_id BIGINT NOT NULL,
        channel_id BIGINT NOT NULL,
        channel_name TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        PRIMARY KEY (guild_id, channel_id)
      )
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS ai_category_restriction (
        guild_id BIGINT NOT NULL,
        category_id BIGINT NOT NULL,
        category_name TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        PRIMARY KEY (guild_id, category_id)
      )
    `);
    await pool.query('TRUNCATE TABLE ai_channel_restriction, ai_category_restriction');
  });

  afterAll(async () => {
    await pool?.end();
  });

  it('persists allowlist CRUD and isChannelAllowed against real repository', async () => {
    if (!CONNECTION_URL) {
      throw new Error('__TEST_CONTAINER_URL is required (run with shared vitest globalSetup)');
    }

    const db = drizzle(pool!);
    const repository = new DrizzleAIChannelRestrictionRepository(db);
    const service = new DefaultAIChannelRestrictionService(repository);
    const guildId = '123456789';
    const channelOne = '1001';
    const channelTwo = '1002';

    expect(await service.isChannelAllowed(guildId, channelOne)).toBe(false);

    const addResult = await service.addAllowedChannel(guildId, {
      channelId: channelOne,
      channelName: 'general',
    });
    expect(addResult.isOk()).toBe(true);

    expect(await service.isChannelAllowed(guildId, channelOne)).toBe(true);
    expect(await service.isChannelAllowed(guildId, channelTwo)).toBe(false);

    const duplicate = await service.addAllowedChannel(guildId, {
      channelId: channelOne,
      channelName: 'general',
    });
    expect(duplicate.isErr()).toBe(true);
    expect(duplicate.getError().category).toBe(DomainErrorCategory.DUPLICATE_CHANNEL);

    const removeResult = await service.removeAllowedChannel(guildId, channelOne);
    expect(removeResult.isOk()).toBe(true);
    expect(await service.isChannelAllowed(guildId, channelOne)).toBe(false);
  });

  it('allows channel when category is allowlisted', async () => {
    if (!CONNECTION_URL) {
      throw new Error('__TEST_CONTAINER_URL is required (run with shared vitest globalSetup)');
    }

    const db = drizzle(pool!);
    const repository = new DrizzleAIChannelRestrictionRepository(db);
    const service = new DefaultAIChannelRestrictionService(repository);
    const guildId = '123456789';
    const categoryId = '2001';
    const channelId = '3001';

    await service.addAllowedCategory(guildId, {
      categoryId,
      categoryName: 'allowed-category',
    });

    expect(await service.isChannelAllowed(guildId, channelId, categoryId)).toBe(true);
    expect(await service.isChannelAllowed(guildId, channelId, '9999')).toBe(false);
  });
});
