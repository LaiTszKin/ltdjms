import { DomainError, type Result } from '@ltdjms/shared';
import type { AllowedChannel, AllowedCategory, AIChannelRestriction } from '../ai-chat-service.js';
import { z } from 'zod';
export interface AIChannelRestrictionRepository {
    findByGuildId(guildId: string): Promise<AllowedChannel[]>;
    findRestrictionByGuildId(guildId: string): Promise<AIChannelRestriction>;
    findAllowedCategories(guildId: string): Promise<AllowedCategory[]>;
    addChannel(guildId: string, channel: Omit<AllowedChannel, 'guildId'>): Promise<Result<AllowedChannel, DomainError>>;
    addCategory(guildId: string, category: Omit<AllowedCategory, 'guildId'>): Promise<Result<AllowedCategory, DomainError>>;
    removeChannel(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
    removeCategory(guildId: string, categoryId: string): Promise<Result<void, DomainError>>;
    deleteRemovedChannels(guildId: string, validChannelIds: string[]): Promise<void>;
}
export declare const aiAllowedChannelsSchema: z.ZodObject<{
    guildId: z.ZodString;
    channelId: z.ZodString;
    channelName: z.ZodString;
}, "strip", z.ZodTypeAny, {
    guildId: string;
    channelId: string;
    channelName: string;
}, {
    guildId: string;
    channelId: string;
    channelName: string;
}>;
export declare const aiAllowedCategoriesSchema: z.ZodObject<{
    guildId: z.ZodString;
    categoryId: z.ZodString;
    categoryName: z.ZodString;
}, "strip", z.ZodTypeAny, {
    guildId: string;
    categoryId: string;
    categoryName: string;
}, {
    guildId: string;
    categoryId: string;
    categoryName: string;
}>;
export declare class InMemoryAIChannelRestrictionRepository implements AIChannelRestrictionRepository {
    private channels;
    private categories;
    private channelKey;
    private categoryKey;
    findByGuildId(guildId: string): Promise<AllowedChannel[]>;
    findRestrictionByGuildId(guildId: string): Promise<AIChannelRestriction>;
    findAllowedCategories(guildId: string): Promise<AllowedCategory[]>;
    addChannel(guildId: string, channel: Omit<AllowedChannel, 'guildId'>): Promise<Result<AllowedChannel, DomainError>>;
    addCategory(guildId: string, category: Omit<AllowedCategory, 'guildId'>): Promise<Result<AllowedCategory, DomainError>>;
    removeChannel(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
    removeCategory(guildId: string, categoryId: string): Promise<Result<void, DomainError>>;
    deleteRemovedChannels(_guildId: string, _validChannelIds: string[]): Promise<void>;
}
export interface AIChannelRestrictionService {
    isChannelAllowed(guildId: string, channelId: string, categoryId?: string): boolean | Promise<boolean>;
    getAllowedChannels(guildId: string): Promise<Result<AllowedChannel[], DomainError>>;
    getAllowedCategories(guildId: string): Promise<Result<AllowedCategory[], DomainError>>;
    addAllowedChannel(guildId: string, channel: Omit<AllowedChannel, 'guildId'>): Promise<Result<AllowedChannel, DomainError>>;
    addAllowedCategory(guildId: string, category: Omit<AllowedCategory, 'guildId'>): Promise<Result<AllowedCategory, DomainError>>;
    removeAllowedChannel(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
    removeAllowedCategory(guildId: string, categoryId: string): Promise<Result<void, DomainError>>;
    deleteRemovedChannels(guildId: string, validChannelIds: string[]): Promise<void>;
}
export declare class DefaultAIChannelRestrictionService implements AIChannelRestrictionService {
    private readonly repository;
    private readonly cacheTtlMs;
    private static readonly DEFAULT_TTL_MS;
    private cache;
    constructor(repository: AIChannelRestrictionRepository, cacheTtlMs?: number);
    isChannelAllowed(guildId: string, channelId: string, categoryId?: string): Promise<boolean>;
    getAllowedChannels(guildId: string): Promise<Result<AllowedChannel[], DomainError>>;
    getAllowedCategories(guildId: string): Promise<Result<AllowedCategory[], DomainError>>;
    addAllowedChannel(guildId: string, channel: Omit<AllowedChannel, 'guildId'>): Promise<Result<AllowedChannel, DomainError>>;
    addAllowedCategory(guildId: string, category: Omit<AllowedCategory, 'guildId'>): Promise<Result<AllowedCategory, DomainError>>;
    removeAllowedChannel(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
    removeAllowedCategory(guildId: string, categoryId: string): Promise<Result<void, DomainError>>;
    private invalidateGuildCache;
    deleteRemovedChannels(guildId: string, validChannelIds: string[]): Promise<void>;
}
