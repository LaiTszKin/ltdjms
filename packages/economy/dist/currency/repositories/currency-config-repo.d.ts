import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type { GuildCurrencyConfig } from '../../domain/types.js';
/**
 * Repository for guild currency configuration operations using Drizzle ORM.
 * Matches Java GuildCurrencyConfigRepository behavior.
 */
export declare class CurrencyConfigRepository {
    private readonly db;
    constructor(db: NodePgDatabase);
    /**
     * Finds currency configuration by guild ID.
     * Returns null if no configuration exists.
     */
    findByGuildId(guildId: number): Promise<GuildCurrencyConfig | null>;
    /**
     * Saves or updates currency configuration (upsert).
     * Uses INSERT...ON CONFLICT UPDATE pattern.
     */
    saveOrUpdate(config: GuildCurrencyConfig): Promise<GuildCurrencyConfig>;
    /**
     * Deletes currency configuration for a guild.
     */
    deleteByGuildId(guildId: number): Promise<void>;
}
