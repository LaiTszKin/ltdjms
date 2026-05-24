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

export interface AgentCheckpointMessage {
  role: string;
  content: string;
}

/** LangGraph checkpoint TTL — aligns with Java RedisPostgresChatMemoryStore 3600s intent. */
export const CHECKPOINT_CACHE_TTL_SECONDS = 3600;

const ConversationState = Annotation.Root({
  turnCount: Annotation<number>(),
  messages: Annotation<AgentCheckpointMessage[]>({
    reducer: (_current, next) => next,
    default: () => [],
  }),
});

/**
 * LangGraph checkpoint provider — Postgres authoritative, Redis optional cache layer.
 * Equivalent to deprecated Java RedisPostgresChatMemoryStore hybrid strategy.
 */
export class LangGraphCheckpointProvider {
  private postgresSaver: PostgresSaver | null = null;
  private redisSaver: RedisSaver | null = null;
  private postgresApp: ReturnType<LangGraphCheckpointProvider['buildConversationGraph']> | null =
    null;
  private redisApp: ReturnType<LangGraphCheckpointProvider['buildConversationGraph']> | null = null;
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

    if (this.redisUrl) {
      try {
        this.redisSaver = await RedisSaver.fromUrl(this.redisUrl, {
          defaultTTL: CHECKPOINT_CACHE_TTL_SECONDS,
          refreshOnRead: true,
        });
        this.logger.info('LangGraph checkpoint: Postgres authoritative + Redis cache (3600s TTL)');
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.logger.warn(
          { err: message },
          'Redis checkpoint unavailable — Postgres-only fallback (ai-agent-java-parity)',
        );
        this.redisSaver = null;
      }
    } else {
      this.logger.info('LangGraph checkpoint: Postgres-only mode');
    }
  }

  getCheckpointer(): CheckpointSaver {
    if (!this.postgresSaver) {
      throw new Error('LangGraphCheckpointProvider not initialized — call initialize() first');
    }
    return this.postgresSaver;
  }

  isPostgresOnly(): boolean {
    return this.redisSaver === null;
  }

  /** Minimal graph for checkpoint roundtrip / restart survival tests. */
  buildConversationGraph(checkpointer: CheckpointSaver = this.getCheckpointer()) {
    return new StateGraph(ConversationState)
      .addNode('increment', (state) => ({
        turnCount: (state.turnCount ?? 0) + 1,
        messages: state.messages ?? [],
      }))
      .addEdge(START, 'increment')
      .addEdge('increment', END)
      .compile({ checkpointer });
  }

  private getPostgresApp() {
    if (!this.postgresApp) {
      this.postgresApp = this.buildConversationGraph(this.getCheckpointer());
    }
    return this.postgresApp;
  }

  private getRedisApp() {
    if (!this.redisSaver) {
      return null;
    }
    if (!this.redisApp) {
      this.redisApp = this.buildConversationGraph(this.redisSaver);
    }
    return this.redisApp;
  }

  private threadConfig(conversationId: string) {
    return { configurable: { thread_id: conversationId } };
  }

  private async readState(conversationId: string) {
    const config = this.threadConfig(conversationId);

    if (this.redisSaver) {
      try {
        const redisApp = this.getRedisApp();
        if (redisApp) {
          const redisSnapshot = await redisApp.getState(config);
          if (
            (redisSnapshot.values.turnCount ?? 0) > 0 ||
            (redisSnapshot.values.messages?.length ?? 0) > 0
          ) {
            return redisSnapshot;
          }
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.logger.debug(
          { err: message },
          'Redis checkpoint read miss — falling back to Postgres',
        );
      }
    }

    const postgresApp = this.getPostgresApp();
    return postgresApp.getState(config);
  }

  /** Records one agent turn and optional message snapshot for the conversation thread. */
  async recordAgentTurn(
    conversationId: string,
    messages: AgentCheckpointMessage[] = [],
  ): Promise<void> {
    const config = this.threadConfig(conversationId);
    const payload = { messages };

    const postgresApp = this.getPostgresApp();
    await postgresApp.invoke(payload, config);

    const redisApp = this.getRedisApp();
    if (redisApp) {
      try {
        await redisApp.invoke(payload, config);
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.logger.warn(
          { err: message },
          'Redis checkpoint write failed — Postgres remains authoritative',
        );
      }
    }
  }

  /** Returns persisted agent turn count for the conversation thread. */
  async getAgentTurnCount(conversationId: string): Promise<number> {
    const snapshot = await this.readState(conversationId);
    return snapshot.values.turnCount ?? 0;
  }

  /** Returns persisted agent message snapshot for restart hydration. */
  async getAgentMessages(conversationId: string): Promise<AgentCheckpointMessage[]> {
    const snapshot = await this.readState(conversationId);
    return snapshot.values.messages ?? [];
  }

  async shutdown(): Promise<void> {
    const redis = this.redisSaver as { close?: () => Promise<void> } | null;
    await redis?.close?.();
    this.redisSaver = null;
    this.postgresSaver = null;
    this.postgresApp = null;
    this.redisApp = null;
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
