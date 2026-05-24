import { describe, it, expect, afterAll } from 'vitest';
import { Pool } from 'pg';
import { ToolExecutionInterceptor } from '../../services/ToolExecutionInterceptor.js';
import { ToolExecutionContext } from '../../tools/ToolExecutionContext.js';
import { DrizzleToolExecutionLogRepository } from '../../persistence/drizzle-tool-execution-log-repository.js';
import {
  createFailureToolExecutionLog,
  createSuccessToolExecutionLog,
} from '../../domain/tool-execution-log.js';
import { drizzle } from 'drizzle-orm/node-postgres';

const CONNECTION_URL = process.env.__TEST_CONTAINER_URL;

/** UT-AG-024 / INT-520 — ToolExecutionLogIntegrationTest.java */
describe('UT-AG-024 tool execution log integration', () => {
  let pool: Pool | undefined;

  afterAll(async () => {
    await pool?.end();
  });

  it('persists redacted audit log to Postgres', async () => {
    if (!CONNECTION_URL) {
      throw new Error('__TEST_CONTAINER_URL is required (run with shared vitest globalSetup)');
    }

    pool = new Pool({ connectionString: CONNECTION_URL, max: 2 });
    const db = drizzle(pool);

    await pool.query(`
      CREATE TABLE IF NOT EXISTS ai_tool_execution_log (
        id BIGSERIAL PRIMARY KEY,
        guild_id BIGINT NOT NULL,
        channel_id BIGINT NOT NULL,
        trigger_user_id BIGINT NOT NULL,
        tool_name VARCHAR(100) NOT NULL,
        parameters JSONB,
        execution_result TEXT,
        error_message TEXT,
        status VARCHAR(20) NOT NULL,
        executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      )
    `);
    await pool.query('TRUNCATE TABLE ai_tool_execution_log');

    const repository = new DrizzleToolExecutionLogRepository(db);
    const interceptor = new ToolExecutionInterceptor(repository);

    await ToolExecutionContext.run(
      { guildId: '100', channelId: '200', userId: '300' },
      async () => {
        await interceptor.runTracked(
          'create_channel',
          { name: 'secret-name' },
          async () => 'created',
        );
      },
    );

    const result = await repository.findByChannelId('200', 5);
    expect(result.isOk()).toBe(true);
    const logs = result.getValue();
    expect(logs).toHaveLength(1);
    expect(logs[0].toolName).toBe('create_channel');
    expect(logs[0].parameters).toContain('"redacted":true');
    expect(logs[0].parameters).not.toContain('secret-name');
    expect(logs[0].status).toBe('SUCCESS');
  });

  it('persists failed audit log and supports channel query ordering and limits', async () => {
    if (!CONNECTION_URL) {
      throw new Error('__TEST_CONTAINER_URL is required (run with shared vitest globalSetup)');
    }

    pool = new Pool({ connectionString: CONNECTION_URL, max: 2 });
    const db = drizzle(pool);

    await pool.query(`
      CREATE TABLE IF NOT EXISTS ai_tool_execution_log (
        id BIGSERIAL PRIMARY KEY,
        guild_id BIGINT NOT NULL,
        channel_id BIGINT NOT NULL,
        trigger_user_id BIGINT NOT NULL,
        tool_name VARCHAR(100) NOT NULL,
        parameters JSONB,
        execution_result TEXT,
        error_message TEXT,
        status VARCHAR(20) NOT NULL,
        executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      )
    `);
    await pool.query('TRUNCATE TABLE ai_tool_execution_log');

    const repository = new DrizzleToolExecutionLogRepository(db);
    const interceptor = new ToolExecutionInterceptor(repository);

    await ToolExecutionContext.run(
      { guildId: '100', channelId: '200', userId: '300' },
      async () => {
        await expect(
          interceptor.runTracked('create_channel', { name: 'fail' }, async () => {
            throw new Error('Permission denied');
          }),
        ).rejects.toThrow('Permission denied');
      },
    );

    await repository.save(
      createSuccessToolExecutionLog('100', '200', '300', 'tool1', '{}', 'Result 1'),
    );
    await repository.save(
      createSuccessToolExecutionLog('100', '200', '300', 'tool2', '{}', 'Result 2'),
    );
    await repository.save(
      createSuccessToolExecutionLog('100', '201', '300', 'tool3', '{}', 'Result 3'),
    );
    await repository.save(
      createFailureToolExecutionLog('100', '200', '300', 'tool4', '{}', 'Error 4'),
    );

    const channelLogs = await repository.findByChannelId('200', 10);
    expect(channelLogs.isOk()).toBe(true);
    const logs = channelLogs.getValue();
    expect(logs.length).toBeGreaterThanOrEqual(3);
    expect(logs.some((log) => log.status === 'FAILED')).toBe(true);
    expect(logs.some((log) => log.toolName === 'tool2')).toBe(true);

    const limited = await repository.findByChannelId('200', 2);
    expect(limited.isOk()).toBe(true);
    expect(limited.getValue()).toHaveLength(2);

    const otherChannel = await repository.findByChannelId('201', 10);
    expect(otherChannel.isOk()).toBe(true);
    expect(otherChannel.getValue()).toHaveLength(1);
    expect(otherChannel.getValue()[0].toolName).toBe('tool3');
  });
});
