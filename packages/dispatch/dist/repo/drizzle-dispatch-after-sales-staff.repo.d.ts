import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type { DispatchAfterSalesStaffRepo } from './dispatch-after-sales-staff.repo.js';
/** Drizzle implementation of DispatchAfterSalesStaffRepo. */
export declare class DrizzleDispatchAfterSalesStaffRepo implements DispatchAfterSalesStaffRepo {
    private readonly db;
    constructor(db: NodePgDatabase);
    findStaffUserIds(guildId: number): Promise<Set<number>>;
    addStaff(guildId: number, userId: number): Promise<boolean>;
    removeStaff(guildId: number, userId: number): Promise<boolean>;
}
