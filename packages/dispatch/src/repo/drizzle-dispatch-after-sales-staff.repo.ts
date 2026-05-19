import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, and, sql } from 'drizzle-orm';
import { dispatchAfterSalesStaff } from '../schema/dispatch-after-sales-staff.sql.js';
import type { DispatchAfterSalesStaffRepo } from './dispatch-after-sales-staff.repo.js';

/** Drizzle implementation of DispatchAfterSalesStaffRepo. */
export class DrizzleDispatchAfterSalesStaffRepo implements DispatchAfterSalesStaffRepo {
  constructor(private readonly db: NodePgDatabase) {}

  async findStaffUserIds(guildId: number): Promise<Set<number>> {
    const rows = await this.db
      .select({ userId: dispatchAfterSalesStaff.userId })
      .from(dispatchAfterSalesStaff)
      .where(eq(dispatchAfterSalesStaff.guildId, guildId))
      .orderBy(dispatchAfterSalesStaff.createdAt);

    return new Set(rows.map((r) => r.userId));
  }

  async addStaff(guildId: number, userId: number): Promise<boolean> {
    const result = await this.db
      .insert(dispatchAfterSalesStaff)
      .values({ guildId, userId })
      .onConflictDoNothing()
      .returning({ id: sql`1` });

    return result.length > 0;
  }

  async removeStaff(guildId: number, userId: number): Promise<boolean> {
    // Use raw SQL with RETURNING to reliably get affected row count
    const result = await this.db.execute(
      sql`DELETE FROM dispatch_after_sales_staff WHERE guild_id = ${guildId} AND user_id = ${userId} RETURNING 1`,
    );

    return result.rowCount != null && result.rowCount > 0;
  }
}
