import { productRedemptionTransaction as txTable } from './schema.js';
/**
 * Records product redemption transactions in the database.
 * Implements the RedemptionTransactionService interface expected by RedemptionService.
 */
export class DrizzleRedemptionTransactionService {
    db;
    constructor(db) {
        this.db = db;
    }
    async recordTransaction(guildId, userId, product, code) {
        return await this.db.insert(txTable).values({
            guildId: guildId,
            userId: userId,
            productId: product.id,
            productName: product.name,
            redemptionCodeId: code.id ?? 0,
            code: code.code,
        });
    }
}
//# sourceMappingURL=drizzle-redemption-transaction-service.js.map