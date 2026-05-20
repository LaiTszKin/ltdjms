import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { DomainError, type Result } from '@ltdjms/shared';
import type { AIAgentChannelConfig } from '../services/ai-chat-service.js';
import type { AIAgentChannelConfigRepository } from '../services/routing/agent-config-service.js';
import pino from 'pino';
export declare class DrizzleAIAgentChannelConfigRepository implements AIAgentChannelConfigRepository {
    private readonly db;
    private readonly log;
    constructor(db: NodePgDatabase, logger?: pino.Logger);
    findByGuildAndChannel(guildId: string, channelId: string): Promise<Result<AIAgentChannelConfig | null, DomainError>>;
    upsert(guildId: string, channelId: string, enabled: boolean): Promise<Result<AIAgentChannelConfig, DomainError>>;
    findEnabledByGuild(guildId: string): Promise<Result<string[], DomainError>>;
    remove(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
}
