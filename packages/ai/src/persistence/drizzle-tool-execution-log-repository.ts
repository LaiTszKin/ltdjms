import { desc, eq } from 'drizzle-orm';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { DomainError, err, ok, safeSnowflakeToNumber, type Result } from '@ltdjms/shared';
import { type ToolExecutionLog, ToolExecutionStatus } from '../domain/tool-execution-log.js';
import { aiToolExecutionLog } from './schema.js';
import pino from 'pino';

export interface ToolExecutionLogRepository {
  save(log: ToolExecutionLog): Promise<Result<ToolExecutionLog, DomainError>>;
  findByChannelId(
    channelId: string,
    limit: number,
  ): Promise<Result<ToolExecutionLog[], DomainError>>;
}

function mapRow(row: typeof aiToolExecutionLog.$inferSelect): ToolExecutionLog {
  return {
    id: row.id,
    guildId: String(row.guildId),
    channelId: String(row.channelId),
    triggerUserId: String(row.triggerUserId),
    toolName: row.toolName,
    parameters:
      row.parameters === null || row.parameters === undefined
        ? '{}'
        : typeof row.parameters === 'string'
          ? row.parameters
          : JSON.stringify(row.parameters),
    executionResult: row.executionResult,
    errorMessage: row.errorMessage,
    status: row.status as ToolExecutionStatus,
    executedAt: row.executedAt,
  };
}

export class DrizzleToolExecutionLogRepository implements ToolExecutionLogRepository {
  private readonly log: pino.Logger;

  constructor(
    private readonly db: NodePgDatabase,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ name: 'drizzle-tool-execution-log-repository' });
  }

  async save(entry: ToolExecutionLog): Promise<Result<ToolExecutionLog, DomainError>> {
    try {
      const [row] = await this.db
        .insert(aiToolExecutionLog)
        .values({
          guildId: safeSnowflakeToNumber(entry.guildId),
          channelId: Number(entry.channelId),
          triggerUserId: Number(entry.triggerUserId),
          toolName: entry.toolName,
          parameters: entry.parameters,
          executionResult: entry.executionResult,
          errorMessage: entry.errorMessage,
          status: entry.status,
          executedAt: entry.executedAt,
        })
        .returning();
      return ok(mapRow(row));
    } catch (cause) {
      this.log.error({ err: cause, toolName: entry.toolName }, 'Failed to save tool execution log');
      return err(
        DomainError.persistenceFailure(
          'Failed to save tool execution log',
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async findByChannelId(
    channelId: string,
    limit: number,
  ): Promise<Result<ToolExecutionLog[], DomainError>> {
    try {
      const rows = await this.db
        .select()
        .from(aiToolExecutionLog)
        .where(eq(aiToolExecutionLog.channelId, Number(channelId)))
        .orderBy(desc(aiToolExecutionLog.executedAt))
        .limit(limit);
      return ok(rows.map(mapRow));
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to find tool execution logs for channel ${channelId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }
}

export class InMemoryToolExecutionLogRepository implements ToolExecutionLogRepository {
  private store: ToolExecutionLog[] = [];
  private nextId = 1;

  async save(entry: ToolExecutionLog): Promise<Result<ToolExecutionLog, DomainError>> {
    const saved = { ...entry, id: this.nextId++ };
    this.store.push(saved);
    return ok(saved);
  }

  async findByChannelId(
    channelId: string,
    limit: number,
  ): Promise<Result<ToolExecutionLog[], DomainError>> {
    const logs = this.store
      .filter((l) => l.channelId === channelId)
      .sort((a, b) => b.executedAt.getTime() - a.executedAt.getTime())
      .slice(0, limit);
    return ok(logs);
  }
}
