import { describe, it, expect, afterAll } from 'vitest';
import { Pool } from 'pg';
import { LangGraphCheckpointProvider } from '../../services/memory/langgraph-checkpoint-provider.js';

const CONNECTION_URL = process.env.__TEST_CONTAINER_URL;

/** UT-AG-026 / INT-521 — conversation checkpoint restart survival */
describe('UT-AG-026 conversation memory checkpoint integration', () => {
  let pool: Pool | undefined;

  afterAll(async () => {
    await pool?.end();
  });

  it('postgres checkpoint survives simulated process restart', async () => {
    if (!CONNECTION_URL) {
      throw new Error('__TEST_CONTAINER_URL is required (run with shared vitest globalSetup)');
    }

    pool = new Pool({ connectionString: CONNECTION_URL, max: 2 });
    const threadId = LangGraphCheckpointProvider.conversationThreadId('100', '200', '300');
    const config = { configurable: { thread_id: `restart-${threadId}-${Date.now()}` } };

    const provider1 = new LangGraphCheckpointProvider(pool);
    await provider1.initialize();
    const app1 = provider1.buildConversationGraph();
    await app1.invoke({ turnCount: 0 }, config);
    await provider1.shutdown();

    const provider2 = new LangGraphCheckpointProvider(pool);
    await provider2.initialize();
    const app2 = provider2.buildConversationGraph();
    const snapshot = await app2.getState(config);
    await provider2.shutdown();

    expect(snapshot.values.turnCount).toBe(1);
    expect(provider2.isPostgresOnly()).toBe(true);
  });

  it('recordAgentTurn survives simulated process restart', async () => {
    if (!CONNECTION_URL) {
      throw new Error('__TEST_CONTAINER_URL is required (run with shared vitest globalSetup)');
    }

    pool = new Pool({ connectionString: CONNECTION_URL, max: 2 });
    const conversationId = `agent-turn-${Date.now()}`;

    const provider1 = new LangGraphCheckpointProvider(pool);
    await provider1.initialize();
    await provider1.recordAgentTurn(conversationId);
    await provider1.recordAgentTurn(conversationId);
    expect(await provider1.getAgentTurnCount(conversationId)).toBe(2);
    await provider1.shutdown();

    const provider2 = new LangGraphCheckpointProvider(pool);
    await provider2.initialize();
    expect(await provider2.getAgentTurnCount(conversationId)).toBe(2);
    await provider2.shutdown();
  });

  it('restores checkpoint tool summaries after restart for existing conversations', async () => {
    if (!CONNECTION_URL) {
      throw new Error('__TEST_CONTAINER_URL is required (run with shared vitest globalSetup)');
    }

    pool = new Pool({ connectionString: CONNECTION_URL, max: 2 });
    const conversationId = `agent-history-${Date.now()}`;
    const persistedMessages = [
      { role: 'user', content: '列出頻道' },
      {
        role: 'assistant',
        content: '工具「list_channels」已成功執行；完整結果不會保留於跨回合記憶。',
      },
    ];

    const provider1 = new LangGraphCheckpointProvider(pool);
    await provider1.initialize();
    await provider1.recordAgentTurn(conversationId, persistedMessages);
    await provider1.shutdown();

    const provider2 = new LangGraphCheckpointProvider(pool);
    await provider2.initialize();
    const restored = await provider2.getAgentMessages(conversationId);
    await provider2.shutdown();

    expect(restored).toEqual(persistedMessages);
  });

  it('uses hybrid redis cache when redis is available', async () => {
    if (!CONNECTION_URL) {
      throw new Error('__TEST_CONTAINER_URL is required (run with shared vitest globalSetup)');
    }

    const redisUrl = process.env.REDIS_URI ?? 'redis://127.0.0.1:6379';
    pool = new Pool({ connectionString: CONNECTION_URL, max: 2 });
    const conversationId = `agent-redis-${Date.now()}`;

    let provider: LangGraphCheckpointProvider | undefined;
    try {
      provider = new LangGraphCheckpointProvider(pool, redisUrl);
      await provider.initialize();
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.warn(`[UT-AG-026] Redis hybrid checkpoint skipped (${message})`);
      return;
    }

    if (provider.isPostgresOnly()) {
      console.warn('[UT-AG-026] Redis unavailable — hybrid path skipped');
      await provider.shutdown();
      return;
    }

    await provider.recordAgentTurn(conversationId);
    await provider.shutdown();

    const restarted = new LangGraphCheckpointProvider(pool, redisUrl);
    await restarted.initialize();
    expect(await restarted.getAgentTurnCount(conversationId)).toBe(1);
    await restarted.shutdown();
  });
});
