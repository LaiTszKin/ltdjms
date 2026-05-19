import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type { DiceGame1Config, DiceGame2Config } from '../../domain/types.js';
/**
 * Repository for dice game configuration operations using Drizzle ORM.
 * Handles both DiceGame1 and DiceGame2 configs.
 */
export declare class DiceConfigRepository {
    private readonly db;
    constructor(db: NodePgDatabase);
    /**
     * Finds dice game 1 configuration by guild ID.
     * Returns null if not found.
     */
    findDice1Config(guildId: number): Promise<DiceGame1Config | null>;
    /**
     * Upserts dice game 1 configuration.
     */
    upsertDice1Config(config: DiceGame1Config): Promise<DiceGame1Config>;
    /**
     * Deletes dice game 1 configuration.
     */
    deleteDice1Config(guildId: number): Promise<void>;
    /**
     * Finds dice game 2 configuration by guild ID.
     * Returns null if not found.
     */
    findDice2Config(guildId: number): Promise<DiceGame2Config | null>;
    /**
     * Upserts dice game 2 configuration.
     */
    upsertDice2Config(config: DiceGame2Config): Promise<DiceGame2Config>;
    /**
     * Deletes dice game 2 configuration.
     */
    deleteDice2Config(guildId: number): Promise<void>;
}
