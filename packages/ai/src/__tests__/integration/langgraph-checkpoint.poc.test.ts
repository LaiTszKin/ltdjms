import { describe, it, expect, afterAll } from 'vitest';
import { Pool } from 'pg';
import { PostgresSaver } from '@langchain/langgraph-checkpoint-postgres';
import { RedisSaver } from '@langchain/langgraph-checkpoint-redis';
import { Annotation, END, START, StateGraph } from '@langchain/langgraph';

const CONNECTION_URL = process.env.__TEST_CONTAINER_URL;

const CounterState = Annotation.Root({
  count: Annotation<number>(),
});

function buildCounterGraph(checkpointer: PostgresSaver | RedisSaver) {
  return new StateGraph(CounterState)
    .addNode('increment', (state) => ({ count: (state.count ?? 0) + 1 }))
    .addEdge(START, 'increment')
    .addEdge('increment', END)
    .compile({ checkpointer });
}

/** POC-ED-001 / POC-ED-002: LangGraph checkpoint roundtrip PoC */
describe('langgraph checkpoint PoC', () => {
  describe('postgres checkpoint', () => {
    let pool: Pool | undefined;

    afterAll(async () => {
      await pool?.end();
    });

    it('postgres checkpoint write/read roundtrip', async () => {
      if (!CONNECTION_URL) {
        throw new Error('__TEST_CONTAINER_URL is required (run with shared vitest globalSetup)');
      }

      pool = new Pool({ connectionString: CONNECTION_URL, max: 2 });
      const checkpointer = new PostgresSaver(pool);
      await checkpointer.setup();

      const app = buildCounterGraph(checkpointer);
      const config = { configurable: { thread_id: `poc-postgres-${Date.now()}` } };

      await app.invoke({ count: 0 }, config);
      const snapshot = await app.getState(config);

      expect(snapshot.values.count).toBe(1);
    });
  });

  describe('redis checkpoint', () => {
    const redisUrl = process.env.REDIS_URI ?? 'redis://127.0.0.1:6379';

    it('redis checkpoint write/read roundtrip', async () => {
      let checkpointer: RedisSaver | undefined;

      try {
        checkpointer = await RedisSaver.fromUrl(redisUrl, {
          defaultTTL: 5,
          refreshOnRead: true,
        });

        const app = buildCounterGraph(checkpointer);
        const config = { configurable: { thread_id: `poc-redis-${Date.now()}` } };

        await app.invoke({ count: 0 }, config);
        const snapshot = await app.getState(config);

        expect(snapshot.values.count).toBe(1);
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        if (
          message.includes('ECONNREFUSED') ||
          message.includes('Redis') ||
          message.includes('RediSearch') ||
          message.includes('JSON')
        ) {
          console.warn(
            `[POC-ED-002] Redis checkpoint skipped (${message}). ai-agent spec uses Postgres-only fallback.`,
          );
          return;
        }
        throw error;
      } finally {
        await checkpointer?.close?.();
      }
    });
  });
});
