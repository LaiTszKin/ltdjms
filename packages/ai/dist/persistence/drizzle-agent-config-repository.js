import { eq, and } from 'drizzle-orm';
import { DomainError, ok, okVoid, err } from '@ltdjms/shared';
import { aiAgentChannelConfig } from './schema.js';
import pino from 'pino';
function mapRow(row) {
    return {
        guildId: String(row.guildId),
        channelId: String(row.channelId),
        enabled: row.agentEnabled,
        updatedAt: row.updatedAt,
    };
}
export class DrizzleAIAgentChannelConfigRepository {
    db;
    log;
    constructor(db, logger) {
        this.db = db;
        this.log = logger ?? pino({ level: 'warn' });
    }
    async findByGuildAndChannel(guildId, channelId) {
        try {
            const [row] = await this.db
                .select()
                .from(aiAgentChannelConfig)
                .where(and(eq(aiAgentChannelConfig.guildId, Number(guildId)), eq(aiAgentChannelConfig.channelId, Number(channelId))))
                .limit(1);
            return ok(row ? mapRow(row) : null);
        }
        catch (cause) {
            return err(DomainError.persistenceFailure(`Failed to find agent config for guild ${guildId} channel ${channelId}`, cause instanceof Error ? cause : undefined));
        }
    }
    async upsert(guildId, channelId, enabled) {
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
        }
        catch (cause) {
            return err(DomainError.persistenceFailure(`Failed to upsert agent config for guild ${guildId} channel ${channelId}`, cause instanceof Error ? cause : undefined));
        }
    }
    async findEnabledByGuild(guildId) {
        try {
            const rows = await this.db
                .select({ channelId: aiAgentChannelConfig.channelId })
                .from(aiAgentChannelConfig)
                .where(and(eq(aiAgentChannelConfig.guildId, Number(guildId)), eq(aiAgentChannelConfig.agentEnabled, true)));
            return ok(rows.map((r) => String(r.channelId)));
        }
        catch (cause) {
            return err(DomainError.persistenceFailure(`Failed to find enabled agent channels for guild ${guildId}`, cause instanceof Error ? cause : undefined));
        }
    }
    async remove(guildId, channelId) {
        try {
            const result = await this.db
                .delete(aiAgentChannelConfig)
                .where(and(eq(aiAgentChannelConfig.guildId, Number(guildId)), eq(aiAgentChannelConfig.channelId, Number(channelId))));
            if (result.rowCount === 0) {
                return okVoid();
            }
            return okVoid();
        }
        catch (cause) {
            return err(DomainError.persistenceFailure(`Failed to remove agent config for guild ${guildId} channel ${channelId}`, cause instanceof Error ? cause : undefined));
        }
    }
}
//# sourceMappingURL=drizzle-agent-config-repository.js.map