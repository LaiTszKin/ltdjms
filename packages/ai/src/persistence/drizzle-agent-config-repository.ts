import { eq, and } from 'drizzle-orm';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { DomainError, ok, okVoid, err, type Result } from '@ltdjms/shared';
import type { AIAgentChannelConfig } from '../services/ai-chat-service.js';
import type { AIAgentChannelConfigRepository } from '../services/routing/agent-config-service.js';
import { aiAgentChannelConfig } from './schema.js';
import pino from 'pino';

function mapRow(row: typeof aiAgentChannelConfig.$inferSelect): AIAgentChannelConfig {
  return {
    guildId: String(row.guildId),
    channelId: String(row.channelId),
    enabled: row.agentEnabled,
    updatedAt: row.updatedAt,
  };
}

export class DrizzleAIAgentChannelConfigRepository implements AIAgentChannelConfigRepository {
  private readonly log: pino.Logger;

  constructor(
    private readonly db: NodePgDatabase,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async findByGuildAndChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<AIAgentChannelConfig | null, DomainError>> {
    try {
      const [row] = await this.db
        .select()
        .from(aiAgentChannelConfig)
        .where(
          and(
            eq(aiAgentChannelConfig.guildId, Number(guildId)),
            eq(aiAgentChannelConfig.channelId, Number(channelId)),
          ),
        )
        .limit(1);
      return ok(row ? mapRow(row) : null);
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to find agent config for guild ${guildId} channel ${channelId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async upsert(
    guildId: string,
    channelId: string,
    enabled: boolean,
  ): Promise<Result<AIAgentChannelConfig, DomainError>> {
    try {
      const [row] = await this.db
        .insert(aiAgentChannelConfig)
        .values({
          guildId: Number(guildId),
          channelId: Number(channelId),
          agentEnabled: enabled,
        })
        .onConflictDoUpdate({
          target: aiAgentChannelConfig.channelId,
          set: {
            agentEnabled: enabled,
            updatedAt: new Date(),
          },
        })
        .returning();
      return ok(mapRow(row));
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to upsert agent config for guild ${guildId} channel ${channelId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async findEnabledByGuild(guildId: string): Promise<Result<string[], DomainError>> {
    try {
      const rows = await this.db
        .select({ channelId: aiAgentChannelConfig.channelId })
        .from(aiAgentChannelConfig)
        .where(
          and(
            eq(aiAgentChannelConfig.guildId, Number(guildId)),
            eq(aiAgentChannelConfig.agentEnabled, true),
          ),
        );
      return ok(rows.map((r) => String(r.channelId)));
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to find enabled agent channels for guild ${guildId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }

  async remove(
    guildId: string,
    channelId: string,
  ): Promise<Result<void, DomainError>> {
    try {
      const result = await this.db
        .delete(aiAgentChannelConfig)
        .where(
          and(
            eq(aiAgentChannelConfig.guildId, Number(guildId)),
            eq(aiAgentChannelConfig.channelId, Number(channelId)),
          ),
        );
      if (result.rowCount === 0) {
        return okVoid<DomainError>() as unknown as Result<void, DomainError>;
      }
      return okVoid<DomainError>() as unknown as Result<void, DomainError>;
    } catch (cause) {
      return err(
        DomainError.persistenceFailure(
          `Failed to remove agent config for guild ${guildId} channel ${channelId}`,
          cause instanceof Error ? cause : undefined,
        ),
      );
    }
  }
}
