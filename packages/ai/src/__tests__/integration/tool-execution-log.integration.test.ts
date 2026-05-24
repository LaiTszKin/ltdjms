import { describe, it, expect, afterAll } from 'vitest';
import { Pool } from 'pg';
import { ToolExecutionInterceptor } from '../../services/ToolExecutionInterceptor.js';
import { ToolExecutionContext } from '../../tools/ToolExecutionContext.js';
import { DrizzleToolExecutionLogRepository } from '../../persistence/drizzle-tool-execution-log-repository.js';
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

    ToolExecutionContext.run({ guildId: '100', channelId: '200', userId: '300' }, () => {
      interceptor.onToolExecutionStarted('create_channel', { name: 'secret-name' });
      interceptor.onToolExecutionCompleted('created');
    });

    const result = await repository.findByChannelId('200', 5);
    expect(result.isOk()).toBe(true);
    const logs = result.getValue();
    expect(logs).toHaveLength(1);
    expect(logs[0].toolName).toBe('create_channel');
    expect(logs[0].parameters).toContain('"redacted":true');
    expect(logs[0].parameters).not.toContain('secret-name');
    expect(logs[0].status).toBe('SUCCESS');
  });
});
