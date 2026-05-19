import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, and, ne, isNull, sql } from 'drizzle-orm';
import { escortDispatchOrder } from '../schema/escort-dispatch-order.sql.js';
import {
  type EscortDispatchOrder,
  EscortDispatchOrderStatus,
  SourceType,
  createPendingFull,
} from '../domain/index.js';
import type { EscortDispatchOrderRepo } from './escort-dispatch-order.repo.js';

/** Drizzle ORM implementation of EscortDispatchOrderRepo. */
export class DrizzleEscortDispatchOrderRepo implements EscortDispatchOrderRepo {
  constructor(private readonly db: NodePgDatabase) {}

  async save(order: EscortDispatchOrder): Promise<EscortDispatchOrder> {
    const rows = await this.db
      .insert(escortDispatchOrder)
      .values({
        orderNumber: order.orderNumber,
        guildId: order.guildId,
        assignedByUserId: order.assignedByUserId,
        escortUserId: order.escortUserId,
        customerUserId: order.customerUserId,
        status: order.status,
        createdAt: order.createdAt,
        confirmedAt: order.confirmedAt,
        completionRequestedAt: order.completionRequestedAt,
        completedAt: order.completedAt,
        afterSalesRequestedAt: order.afterSalesRequestedAt,
        afterSalesAssigneeUserId: order.afterSalesAssigneeUserId,
        afterSalesAssignedAt: order.afterSalesAssignedAt,
        afterSalesClosedAt: order.afterSalesClosedAt,
        updatedAt: order.updatedAt,
        sourceType: order.sourceType,
        sourceReference: order.sourceReference,
        sourceProductId: order.sourceProductId,
        sourceProductName: order.sourceProductName,
        sourceCurrencyPrice: order.sourceCurrencyPrice,
        sourceFiatPriceTwd: order.sourceFiatPriceTwd,
        sourceEscortOptionCode: order.sourceEscortOptionCode,
      })
      .returning();

    return mapRowToDomain(rows[0]);
  }

  async update(order: EscortDispatchOrder): Promise<EscortDispatchOrder> {
    if (order.id == null) {
      throw new Error('Cannot update order without ID');
    }
    const rows = await this.db
      .update(escortDispatchOrder)
      .set({
        status: order.status,
        confirmedAt: order.confirmedAt,
        completionRequestedAt: order.completionRequestedAt,
        completedAt: order.completedAt,
        afterSalesRequestedAt: order.afterSalesRequestedAt,
        afterSalesAssigneeUserId: order.afterSalesAssigneeUserId,
        afterSalesAssignedAt: order.afterSalesAssignedAt,
        afterSalesClosedAt: order.afterSalesClosedAt,
        updatedAt: order.updatedAt,
        escortUserId: order.escortUserId,
        assignedByUserId: order.assignedByUserId,
      })
      .where(eq(escortDispatchOrder.id, order.id))
      .returning();

    if (rows.length === 0) {
      throw new Error(`Escort dispatch order not found, id=${order.id}`);
    }
    return mapRowToDomain(rows[0]);
  }

  async findByOrderNumber(orderNumber: string): Promise<EscortDispatchOrder | null> {
    const rows = await this.db
      .select()
      .from(escortDispatchOrder)
      .where(eq(escortDispatchOrder.orderNumber, orderNumber))
      .limit(1);

    return rows.length > 0 ? mapRowToDomain(rows[0]) : null;
  }

  async findBySourceIdentity(
    sourceType: SourceType,
    sourceReference: string,
  ): Promise<EscortDispatchOrder | null> {
    if (!sourceReference || sourceReference.trim().length === 0) {
      return null;
    }
    const rows = await this.db
      .select()
      .from(escortDispatchOrder)
      .where(
        and(
          eq(escortDispatchOrder.sourceType, sourceType),
          eq(escortDispatchOrder.sourceReference, sourceReference),
        ),
      )
      .limit(1);

    return rows.length > 0 ? mapRowToDomain(rows[0]) : null;
  }

  async findRecentByGuildId(guildId: number, limit: number): Promise<EscortDispatchOrder[]> {
    const rows = await this.db
      .select()
      .from(escortDispatchOrder)
      .where(eq(escortDispatchOrder.guildId, guildId))
      .orderBy(sql`${escortDispatchOrder.createdAt} DESC`)
      .limit(limit);

    return rows.map(mapRowToDomain);
  }

  async findPendingAssignmentByGuildId(
    guildId: number,
    limit: number,
  ): Promise<EscortDispatchOrder[]> {
    const rows = await this.db
      .select()
      .from(escortDispatchOrder)
      .where(
        and(
          eq(escortDispatchOrder.guildId, guildId),
          eq(escortDispatchOrder.status, EscortDispatchOrderStatus.PENDING_CONFIRMATION),
          eq(escortDispatchOrder.escortUserId, 0),
        ),
      )
      .orderBy(sql`${escortDispatchOrder.createdAt} ASC`)
      .limit(limit);

    return rows.map(mapRowToDomain);
  }

  async assignEscort(
    orderNumber: string,
    assignedByUserId: number,
    escortUserId: number,
    assignedAt: Date,
  ): Promise<EscortDispatchOrder | null> {
    const rows = await this.db
      .update(escortDispatchOrder)
      .set({
        assignedByUserId,
        escortUserId,
        updatedAt: assignedAt,
      })
      .where(
        and(
          eq(escortDispatchOrder.orderNumber, orderNumber),
          eq(escortDispatchOrder.status, EscortDispatchOrderStatus.PENDING_CONFIRMATION),
          eq(escortDispatchOrder.escortUserId, 0),
          ne(escortDispatchOrder.customerUserId, escortUserId),
        ),
      )
      .returning();

    return rows.length > 0 ? mapRowToDomain(rows[0]) : null;
  }

  async claimAfterSales(
    orderNumber: string,
    assigneeUserId: number,
    assignedAt: Date,
  ): Promise<EscortDispatchOrder | null> {
    const rows = await this.db
      .update(escortDispatchOrder)
      .set({
        status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
        afterSalesAssigneeUserId: assigneeUserId,
        afterSalesAssignedAt: assignedAt,
        updatedAt: assignedAt,
      })
      .where(
        and(
          eq(escortDispatchOrder.orderNumber, orderNumber),
          eq(escortDispatchOrder.status, EscortDispatchOrderStatus.AFTER_SALES_REQUESTED),
          isNull(escortDispatchOrder.afterSalesAssigneeUserId),
        ),
      )
      .returning();

    return rows.length > 0 ? mapRowToDomain(rows[0]) : null;
  }

  async closeAfterSales(
    orderNumber: string,
    assigneeUserId: number,
    closedAt: Date,
  ): Promise<EscortDispatchOrder | null> {
    const rows = await this.db
      .update(escortDispatchOrder)
      .set({
        status: EscortDispatchOrderStatus.AFTER_SALES_CLOSED,
        afterSalesClosedAt: closedAt,
        updatedAt: closedAt,
      })
      .where(
        and(
          eq(escortDispatchOrder.orderNumber, orderNumber),
          eq(escortDispatchOrder.status, EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS),
          eq(escortDispatchOrder.afterSalesAssigneeUserId, assigneeUserId),
        ),
      )
      .returning();

    return rows.length > 0 ? mapRowToDomain(rows[0]) : null;
  }

  async existsByOrderNumber(orderNumber: string): Promise<boolean> {
    const rows = await this.db
      .select({ id: escortDispatchOrder.id })
      .from(escortDispatchOrder)
      .where(eq(escortDispatchOrder.orderNumber, orderNumber))
      .limit(1);

    return rows.length > 0;
  }
}

/** Maps a DB row to domain EscortDispatchOrder. */
function mapRowToDomain(row: Record<string, unknown>): EscortDispatchOrder {
  return createPendingFull(
    row.orderNumber as string,
    row.guildId as number,
    row.assignedByUserId as number,
    row.escortUserId as number,
    row.customerUserId as number,
    row.sourceType as SourceType,
    (row.sourceReference as string) ?? null,
    (row.sourceProductId as number) ?? null,
    (row.sourceProductName as string) ?? null,
    (row.sourceCurrencyPrice as number) ?? null,
    (row.sourceFiatPriceTwd as number) ?? null,
    (row.sourceEscortOptionCode as string) ?? null,
  );
}
