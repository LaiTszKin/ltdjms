import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, and, ne, isNull, notInArray, sql } from 'drizzle-orm';
import { escortDispatchOrder, type EscortDispatchOrderSelect } from '../schema/escort-dispatch-order.sql.js';
import {
  type EscortDispatchOrder,
  EscortDispatchOrderStatus,
  SourceType,
  fromDbRow,
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

  async update(order: EscortDispatchOrder, expectedStatus?: EscortDispatchOrderStatus): Promise<EscortDispatchOrder> {
    if (order.id == null) {
      throw new Error('Cannot update order without ID');
    }
    const conditions = [eq(escortDispatchOrder.id, order.id)];
    if (expectedStatus != null) {
      conditions.push(eq(escortDispatchOrder.status, expectedStatus));
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
      })
      .where(and(...conditions))
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
    escortUserIdValue: number,
    assignedAt: Date,
  ): Promise<EscortDispatchOrder | null> {
    const rows = await this.db
      .update(escortDispatchOrder)
      .set({
        assignedByUserId,
        escortUserId: escortUserIdValue,
        updatedAt: assignedAt,
      })
      .where(
        and(
          eq(escortDispatchOrder.orderNumber, orderNumber),
          eq(escortDispatchOrder.status, EscortDispatchOrderStatus.PENDING_CONFIRMATION),
          eq(escortDispatchOrder.escortUserId, 0),
          ne(escortDispatchOrder.customerUserId, escortUserIdValue),
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
        afterSalesClosedAt: null,
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

  async confirmOrder(
    orderNumber: string,
    expectedEscortUserId: number,
    confirmedAt: Date,
  ): Promise<EscortDispatchOrder | null> {
    const rows = await this.db
      .update(escortDispatchOrder)
      .set({
        status: EscortDispatchOrderStatus.CONFIRMED,
        confirmedAt,
        completionRequestedAt: null,
        completedAt: null,
        afterSalesRequestedAt: null,
        afterSalesAssigneeUserId: null,
        afterSalesAssignedAt: null,
        afterSalesClosedAt: null,
        updatedAt: confirmedAt,
      })
      .where(
        and(
          eq(escortDispatchOrder.orderNumber, orderNumber),
          eq(escortDispatchOrder.status, EscortDispatchOrderStatus.PENDING_CONFIRMATION),
          eq(escortDispatchOrder.escortUserId, expectedEscortUserId),
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

  async batchTimeoutCompletion(): Promise<EscortDispatchOrder[]> {
    const rows = await this.db
      .update(escortDispatchOrder)
      .set({
        status: EscortDispatchOrderStatus.COMPLETED,
        completedAt: sql`NOW()`,
        updatedAt: sql`NOW()`,
      })
      .where(
        and(
          eq(escortDispatchOrder.status, EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION),
          sql`${escortDispatchOrder.completionRequestedAt} < NOW() - INTERVAL '24 hours'`,
        ),
      )
      .returning();

    return rows.map(mapRowToDomain);
  }

  async countActiveByGuildId(guildId: number): Promise<number> {
    const [row] = await this.db
      .select({ count: sql<number>`count(*)` })
      .from(escortDispatchOrder)
      .where(
        and(
          eq(escortDispatchOrder.guildId, guildId),
          notInArray(escortDispatchOrder.status, [
            EscortDispatchOrderStatus.COMPLETED,
            EscortDispatchOrderStatus.AFTER_SALES_CLOSED,
          ]),
        ),
      );
    return row ? Number(row.count) : 0;
  }
}

/** Maps a DB row to domain EscortDispatchOrder, preserving ALL stored columns. */
function mapRowToDomain(row: EscortDispatchOrderSelect): EscortDispatchOrder {
  return fromDbRow({
    id: (row.id as number) ?? null,
    orderNumber: row.orderNumber as string,
    guildId: row.guildId as number,
    assignedByUserId: row.assignedByUserId as number,
    escortUserId: row.escortUserId as number,
    customerUserId: row.customerUserId as number,
    status: row.status as EscortDispatchOrderStatus,
    createdAt: row.createdAt as Date,
    confirmedAt: (row.confirmedAt as Date) ?? null,
    completionRequestedAt: (row.completionRequestedAt as Date) ?? null,
    completedAt: (row.completedAt as Date) ?? null,
    afterSalesRequestedAt: (row.afterSalesRequestedAt as Date) ?? null,
    afterSalesAssigneeUserId: (row.afterSalesAssigneeUserId as number) ?? null,
    afterSalesAssignedAt: (row.afterSalesAssignedAt as Date) ?? null,
    afterSalesClosedAt: (row.afterSalesClosedAt as Date) ?? null,
    updatedAt: row.updatedAt as Date,
    sourceType: row.sourceType as SourceType,
    sourceReference: (row.sourceReference as string) ?? null,
    sourceProductId: (row.sourceProductId as number) ?? null,
    sourceProductName: (row.sourceProductName as string) ?? null,
    sourceCurrencyPrice: (row.sourceCurrencyPrice as number) ?? null,
    sourceFiatPriceTwd: (row.sourceFiatPriceTwd as number) ?? null,
    sourceEscortOptionCode: (row.sourceEscortOptionCode as string) ?? null,
  });
}
