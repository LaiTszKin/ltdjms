import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type { EscortOptionPriceRepo } from './escort-option-price.repo.js';
/** Drizzle implementation of EscortOptionPriceRepo. */
export declare class DrizzleEscortOptionPriceRepo implements EscortOptionPriceRepo {
    private readonly db;
    constructor(db: NodePgDatabase);
    findAllByGuildId(guildId: number): Promise<Map<string, number>>;
    findByGuildIdAndOptionCode(guildId: number, optionCode: string): Promise<number | null>;
    upsert(guildId: number, optionCode: string, priceTwd: number, updatedByUserId: number | null): Promise<void>;
    delete(guildId: number, optionCode: string): Promise<boolean>;
}
