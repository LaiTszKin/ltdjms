import { Pool } from 'pg';
import { PostgresSaver } from '@langchain/langgraph-checkpoint-postgres';
import { RedisSaver } from '@langchain/langgraph-checkpoint-redis';
import { Annotation, END, START, StateGraph } from '@langchain/langgraph';
import pino from 'pino';

/** PostgresSaver.setup() is not idempotent when migrations already exist. */
function isCheckpointSetupAlreadyApplied(error: unknown): boolean {
  if (typeof error !== 'object' || error === null) {
    return false;
  }
  const pgError = error as { code?: string; constraint?: string };
  return pgError.code === '23505' && pgError.constraint === 'checkpoint_migrations_pkey';
}

type CheckpointSaver = PostgresSaver | RedisSaver;

/** LangGraph checkpoint TTL — aligns with Java RedisPostgresChatMemoryStore 3600s intent. */
export const CHECKPOINT_CACHE_TTL_SECONDS = 3600;

const ConversationState = Annotation.Root({
  turnCount: Annotation<number>(),
});

/**
 * LangGraph checkpoint provider — Postgres authoritative, Redis optional cache layer.
 * Equivalent to deprecated Java RedisPostgresChatMemoryStore hybrid strategy.
 */
export class LangGraphCheckpointProvider {
  private postgresSaver: PostgresSaver | null = null;
  private redisSaver: RedisSaver | null = null;
  private activeSaver: CheckpointSaver | null = null;
  private readonly logger: pino.Logger;

  constructor(
    private readonly pool: Pool,
    private readonly redisUrl?: string,
    logger?: pino.Logger,
  ) {
    this.logger = logger ?? pino({ name: 'langgraph-checkpoint-provider' });
  }

  /**
   * Maps Java/TS conversationId formats to LangGraph thread_id.
   * Thread: guildId:threadId:userId — Message: guildId:channelId:userId:messageId
   */
  static conversationThreadId(guildId: string, channelId: string, userId: string): string {
    return `${guildId}:${channelId}:${userId}`;
  }

  async initialize(): Promise<void> {
    this.postgresSaver = new PostgresSaver(this.pool);
    try {
      await this.postgresSaver.setup();
    } catch (error) {
      if (!isCheckpointSetupAlreadyApplied(error)) {
        throw error;
      }
    }
    this.activeSaver = this.postgresSaver;

    if (this.redisUrl) {
      try {
        this.redisSaver = await RedisSaver.fromUrl(this.redisUrl, {
          defaultTTL: CHECKPOINT_CACHE_TTL_SECONDS,
          refreshOnRead: true,
        });
        this.activeSaver = this.redisSaver;
        this.logger.info('LangGraph checkpoint: Postgres + Redis Stack (3600s TTL)');
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.logger.warn(
          { err: message },
          'Redis checkpoint unavailable — Postgres-only fallback (ai-agent-java-parity)',
        );
        this.redisSaver = null;
        this.activeSaver = this.postgresSaver;
      }
    } else {
      this.logger.info('LangGraph checkpoint: Postgres-only mode');
    }
  }

  getCheckpointer(): CheckpointSaver {
    if (!this.activeSaver) {
      throw new Error('LangGraphCheckpointProvider not initialized — call initialize() first');
    }
    return this.activeSaver;
  }

  isPostgresOnly(): boolean {
    return this.redisSaver === null;
  }

  /** Minimal graph for checkpoint roundtrip / restart survival tests. */
  buildConversationGraph() {
    const checkpointer = this.getCheckpointer();
    return new StateGraph(ConversationState)
      .addNode('increment', (state) => ({ turnCount: (state.turnCount ?? 0) + 1 }))
      .addEdge(START, 'increment')
      .addEdge('increment', END)
      .compile({ checkpointer });
  }

  async shutdown(): Promise<void> {
    const redis = this.redisSaver as { close?: () => Promise<void> } | null;
    await redis?.close?.();
    this.redisSaver = null;
    this.postgresSaver = null;
    this.activeSaver = null;
  }
}

/**
 * Creates a checkpoint provider when Postgres pool is available.
 * Returns null when pool is unavailable (in-memory-only dev mode).
 */
export async function createLangGraphCheckpointProvider(
  pool: Pool | null | undefined,
  redisUrl?: string,
): Promise<LangGraphCheckpointProvider | null> {
  if (!pool) {
    return null;
  }
  const provider = new LangGraphCheckpointProvider(pool, redisUrl);
  await provider.initialize();
  return provider;
}
