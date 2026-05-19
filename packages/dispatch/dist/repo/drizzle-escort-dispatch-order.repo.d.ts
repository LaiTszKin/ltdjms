import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { type EscortDispatchOrder, SourceType } from '../domain/index.js';
import type { EscortDispatchOrderRepo } from './escort-dispatch-order.repo.js';
/** Drizzle ORM implementation of EscortDispatchOrderRepo. */
export declare class DrizzleEscortDispatchOrderRepo implements EscortDispatchOrderRepo {
    private readonly db;
    constructor(db: NodePgDatabase);
    save(order: EscortDispatchOrder): Promise<EscortDispatchOrder>;
    update(order: EscortDispatchOrder): Promise<EscortDispatchOrder>;
    findByOrderNumber(orderNumber: string): Promise<EscortDispatchOrder | null>;
    findBySourceIdentity(sourceType: SourceType, sourceReference: string): Promise<EscortDispatchOrder | null>;
    findRecentByGuildId(guildId: number, limit: number): Promise<EscortDispatchOrder[]>;
    findPendingAssignmentByGuildId(guildId: number, limit: number): Promise<EscortDispatchOrder[]>;
    assignEscort(orderNumber: string, assignedByUserId: number, escortUserId: number, assignedAt: Date): Promise<EscortDispatchOrder | null>;
    claimAfterSales(orderNumber: string, assigneeUserId: number, assignedAt: Date): Promise<EscortDispatchOrder | null>;
    closeAfterSales(orderNumber: string, assigneeUserId: number, closedAt: Date): Promise<EscortDispatchOrder | null>;
    existsByOrderNumber(orderNumber: string): Promise<boolean>;
}
