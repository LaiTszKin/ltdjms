import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { productRedemptionTransaction as txTable } from './schema.js';

/**
 * Records product redemption transactions in the database.
 * Implements the RedemptionTransactionService interface expected by RedemptionService.
 */
export class DrizzleRedemptionTransactionService {
  constructor(private readonly db: NodePgDatabase) {}

  async recordTransaction(
    guildId: number,
    userId: number,
    product: { id: number | null; name: string },
    code: { id?: number | null; code: string },
  ): Promise<unknown> {
    return await this.db.insert(txTable).values({
      guildId: guildId,
      userId: userId,
      productId: product.id as number,
      productName: product.name,
      redemptionCodeId: (code.id as number) ?? 0,
      code: code.code,
    });
  }
}
