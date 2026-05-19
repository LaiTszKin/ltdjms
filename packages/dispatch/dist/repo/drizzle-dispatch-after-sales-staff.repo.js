import { eq, sql } from 'drizzle-orm';
import { dispatchAfterSalesStaff } from '../schema/dispatch-after-sales-staff.sql.js';
/** Drizzle implementation of DispatchAfterSalesStaffRepo. */
export class DrizzleDispatchAfterSalesStaffRepo {
    db;
    constructor(db) {
        this.db = db;
    }
    async findStaffUserIds(guildId) {
        const rows = await this.db
            .select({ userId: dispatchAfterSalesStaff.userId })
            .from(dispatchAfterSalesStaff)
            .where(eq(dispatchAfterSalesStaff.guildId, guildId))
            .orderBy(dispatchAfterSalesStaff.createdAt);
        return new Set(rows.map((r) => r.userId));
    }
    async addStaff(guildId, userId) {
        const result = await this.db
            .insert(dispatchAfterSalesStaff)
            .values({ guildId, userId })
            .onConflictDoNothing()
            .returning({ id: sql `1` });
        return result.length > 0;
    }
    async removeStaff(guildId, userId) {
        // Use raw SQL with RETURNING to reliably get affected row count
        const result = await this.db.execute(sql `DELETE FROM dispatch_after_sales_staff WHERE guild_id = ${guildId} AND user_id = ${userId} RETURNING 1`);
        return result.rowCount != null && result.rowCount > 0;
    }
}
//# sourceMappingURL=drizzle-dispatch-after-sales-staff.repo.js.map