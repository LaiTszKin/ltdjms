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
});
