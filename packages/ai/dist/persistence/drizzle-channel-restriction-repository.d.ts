import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { DomainError, type Result } from '@ltdjms/shared';
import type { AllowedChannel, AllowedCategory, AIChannelRestriction } from '../services/ai-chat-service.js';
import type { AIChannelRestrictionRepository } from '../services/routing/channel-restriction-service.js';
import pino from 'pino';
export declare class DrizzleAIChannelRestrictionRepository implements AIChannelRestrictionRepository {
    private readonly db;
    private readonly log;
    constructor(db: NodePgDatabase, logger?: pino.Logger);
    findByGuildId(guildId: string): Promise<AllowedChannel[]>;
    findRestrictionByGuildId(guildId: string): Promise<AIChannelRestriction>;
    findAllowedCategories(guildId: string): Promise<AllowedCategory[]>;
    addChannel(guildId: string, channel: Omit<AllowedChannel, 'guildId'>): Promise<Result<AllowedChannel, DomainError>>;
    addCategory(guildId: string, category: Omit<AllowedCategory, 'guildId'>): Promise<Result<AllowedCategory, DomainError>>;
    removeChannel(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
    removeCategory(guildId: string, categoryId: string): Promise<Result<void, DomainError>>;
    deleteRemovedChannels(guildId: string, validChannelIds: string[]): Promise<void>;
}
