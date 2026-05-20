import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
/**
 * Records product redemption transactions in the database.
 * Implements the RedemptionTransactionService interface expected by RedemptionService.
 */
export declare class DrizzleRedemptionTransactionService {
    private readonly db;
    constructor(db: NodePgDatabase);
    recordTransaction(guildId: number, userId: number, product: {
        id: number | null;
        name: string;
    }, code: {
        id?: number | null;
        code: string;
    }): Promise<unknown>;
}
