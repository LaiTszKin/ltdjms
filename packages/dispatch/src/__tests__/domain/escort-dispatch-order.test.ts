import { describe, it, expect } from 'vitest';
import { isErr } from '@ltdjms/shared';
import {
  EscortDispatchOrderStatus,
  SourceType,
  CUSTOMER_CONFIRM_TIMEOUT_MS,
  createPending,
  createManualOpenOrder,
  createAutoHandoff,
  fromDbRow,
  withConfirmed,
  withCompletionRequested,
  withCompleted,
  withAfterSalesRequested,
  withAfterSalesInProgress,
  withAfterSalesClosed,
  withAssignedEscort,
  isPendingEscortConfirmation,
  isConfirmed,
  isPendingCustomerConfirmation,
  isAfterSalesRequested,
  isAfterSalesInProgress,
  isCompleted,
  canBeConfirmedBy,
  canBeCompletedByEscort,
  canBeConfirmedByCustomer,
  isAfterSalesAssignee,
  isManualSource,
  isAutoSource,
  hasCustomerConfirmationTimedOut,
} from '../../domain/escort-dispatch-order.js';
import type { EscortDispatchOrder } from '../../domain/escort-dispatch-order.js';

// Shared helpers
const GUILD_ID = 100;
const ASSIGNED_BY = 200;
const ESCORT_USER_ID = 300;
const CUSTOMER_USER_ID = 400;
const ORDER_NUMBER = 'ESC-20260521-ABC123';

/**
 * Creates a base order via fromDbRow with all timestamps consistent with PENDING_CONFIRMATION (MANUAL source).
 * Overrides can change the state, but callers must provide all required fields for the target state.
 */
function makeBaseOrder(overrides?: Partial<EscortDispatchOrder>): EscortDispatchOrder {
  const result = fromDbRow({
    id: 1,
    orderNumber: ORDER_NUMBER,
    guildId: GUILD_ID,
    assignedByUserId: ASSIGNED_BY,
    escortUserId: ESCORT_USER_ID,
    customerUserId: CUSTOMER_USER_ID,
    createdAt: new Date('2026-05-21T10:00:00Z'),
    confirmedAt: null,
    completionRequestedAt: null,
    completedAt: null,
    afterSalesRequestedAt: null,
    afterSalesAssigneeUserId: null,
    afterSalesAssignedAt: null,
    afterSalesClosedAt: null,
    updatedAt: new Date('2026-05-21T10:00:00Z'),
    sourceType: SourceType.MANUAL,
    sourceReference: null,
    sourceProductId: null,
    sourceProductName: null,
    sourceCurrencyPrice: null,
    sourceFiatPriceTwd: null,
    sourceEscortOptionCode: null,
    status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
    ...overrides,
  });
  return result.getValue();
}

/**
 * Creates a valid non-MANUAL source order (CURRENCY_PURCHASE) for testing.
 */
function makeAutoOrder(overrides?: Partial<EscortDispatchOrder>): EscortDispatchOrder {
  const result = fromDbRow({
    id: 1,
    orderNumber: ORDER_NUMBER,
    guildId: GUILD_ID,
    assignedByUserId: 0,
    escortUserId: ESCORT_USER_ID,
    customerUserId: CUSTOMER_USER_ID,
    createdAt: new Date('2026-05-21T10:00:00Z'),
    confirmedAt: null,
    completionRequestedAt: null,
    completedAt: null,
    afterSalesRequestedAt: null,
    afterSalesAssigneeUserId: null,
    afterSalesAssignedAt: null,
    afterSalesClosedAt: null,
    updatedAt: new Date('2026-05-21T10:00:00Z'),
    sourceType: SourceType.CURRENCY_PURCHASE,
    sourceReference: 'REF-001',
    sourceProductId: 42,
    sourceProductName: 'Product A',
    sourceCurrencyPrice: 5000,
    sourceFiatPriceTwd: null,
    sourceEscortOptionCode: 'PVE',
    status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
    ...overrides,
  });
  return result.getValue();
}

/**
 * Creates a raw EscortDispatchOrder object WITHOUT validation.
 * Only for testing predicates that should handle logically impossible states gracefully.
 */
function makeRawOrder(overrides?: Partial<EscortDispatchOrder>): EscortDispatchOrder {
  return {
    id: 1,
    orderNumber: ORDER_NUMBER,
    guildId: GUILD_ID,
    assignedByUserId: ASSIGNED_BY,
    escortUserId: ESCORT_USER_ID,
    customerUserId: CUSTOMER_USER_ID,
    createdAt: new Date('2026-05-21T10:00:00Z'),
    confirmedAt: null,
    completionRequestedAt: null,
    completedAt: null,
    afterSalesRequestedAt: null,
    afterSalesAssigneeUserId: null,
    afterSalesAssignedAt: null,
    afterSalesClosedAt: null,
    updatedAt: new Date('2026-05-21T10:00:00Z'),
    sourceType: SourceType.MANUAL,
    sourceReference: null,
    sourceProductId: null,
    sourceProductName: null,
    sourceCurrencyPrice: null,
    sourceFiatPriceTwd: null,
    sourceEscortOptionCode: null,
    status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
    ...overrides,
  };
}

describe('EscortDispatchOrder — factory functions', () => {
  describe('createPending', () => {
    it('should create a PENDING_CONFIRMATION order with MANUAL source', () => {
      const result = createPending(ORDER_NUMBER, GUILD_ID, ASSIGNED_BY, ESCORT_USER_ID, CUSTOMER_USER_ID);
      expect(result.isOk()).toBe(true);
      if (!result.isOk()) return;
      const order = result.getValue();

      expect(order.status).toBe(EscortDispatchOrderStatus.PENDING_CONFIRMATION);
      expect(order.sourceType).toBe(SourceType.MANUAL);
      expect(order.orderNumber).toBe(ORDER_NUMBER);
      expect(order.guildId).toBe(GUILD_ID);
      expect(order.assignedByUserId).toBe(ASSIGNED_BY);
      expect(order.escortUserId).toBe(ESCORT_USER_ID);
      expect(order.customerUserId).toBe(CUSTOMER_USER_ID);
      expect(order.id).toBeNull();
      expect(order.sourceReference).toBeNull();
      expect(order.sourceProductId).toBeNull();
      expect(order.sourceCurrencyPrice).toBeNull();
      expect(order.sourceFiatPriceTwd).toBeNull();
    });

    it('should return error when customerUserId <= 0', () => {
      expect(isErr(createPending(ORDER_NUMBER, GUILD_ID, ASSIGNED_BY, ESCORT_USER_ID, 0))).toBe(true);
      expect(isErr(createPending(ORDER_NUMBER, GUILD_ID, ASSIGNED_BY, ESCORT_USER_ID, -1))).toBe(true);
    });

    it('should return error when orderNumber is blank', () => {
      const result = createPending('', GUILD_ID, ASSIGNED_BY, ESCORT_USER_ID, CUSTOMER_USER_ID);
      expect(isErr(result)).toBe(true);
      if (isErr(result)) {
        expect(result.getError().message).toContain('orderNumber must not be blank');
      }
    });

    it('should return error when escort and customer are the same', () => {
      const result = createPending(ORDER_NUMBER, GUILD_ID, ASSIGNED_BY, 400, 400);
      expect(isErr(result)).toBe(true);
      if (isErr(result)) {
        expect(result.getError().message).toContain('escortUserId and customerUserId must be different');
      }
    });

    it('should return error when orderNumber exceeds 32 characters', () => {
      const long = 'X'.repeat(33);
      const result = createPending(long, GUILD_ID, ASSIGNED_BY, ESCORT_USER_ID, CUSTOMER_USER_ID);
      expect(isErr(result)).toBe(true);
      if (isErr(result)) {
        expect(result.getError().message).toContain('orderNumber must not exceed 32 characters');
      }
    });
  });

  describe('createManualOpenOrder', () => {
    it('should create an unassigned manual order with escortUserId=0', () => {
      const result = createManualOpenOrder(
        ORDER_NUMBER, GUILD_ID, ASSIGNED_BY, CUSTOMER_USER_ID, 'PVE-BASIC',
      );
      expect(result.isOk()).toBe(true);
      if (!result.isOk()) return;
      const order = result.getValue();

      expect(order.status).toBe(EscortDispatchOrderStatus.PENDING_CONFIRMATION);
      expect(order.sourceType).toBe(SourceType.MANUAL);
      expect(order.escortUserId).toBe(0);
      expect(order.sourceEscortOptionCode).toBe('PVE-BASIC');
    });

    it('should return error when customerUserId <= 0', () => {
      expect(isErr(createManualOpenOrder(ORDER_NUMBER, GUILD_ID, ASSIGNED_BY, 0, 'PVE-BASIC'))).toBe(true);
    });

    it('should return error for blank orderNumber', () => {
      const result = createManualOpenOrder('', GUILD_ID, ASSIGNED_BY, CUSTOMER_USER_ID, 'PVE-BASIC');
      expect(isErr(result)).toBe(true);
      if (isErr(result)) {
        expect(result.getError().message).toContain('orderNumber must not be blank');
      }
    });
  });

  describe('createAutoHandoff', () => {
    it('should create an auto-handoff order with CURRENCY_PURCHASE source', () => {
      const result = createAutoHandoff(
        ORDER_NUMBER, GUILD_ID, 0, 0, CUSTOMER_USER_ID,
        SourceType.CURRENCY_PURCHASE, 'REF-001', 42, 'Product A',
        5000, null, 'PVE-ADV',
      );
      expect(result.isOk()).toBe(true);
      if (!result.isOk()) return;
      const order = result.getValue();

      expect(order.status).toBe(EscortDispatchOrderStatus.PENDING_CONFIRMATION);
      expect(order.assignedByUserId).toBe(0);
      expect(order.escortUserId).toBe(0);
      expect(order.sourceType).toBe(SourceType.CURRENCY_PURCHASE);
      expect(order.sourceReference).toBe('REF-001');
      expect(order.sourceProductId).toBe(42);
      expect(order.sourceProductName).toBe('Product A');
      expect(order.sourceCurrencyPrice).toBe(5000);
      expect(order.sourceFiatPriceTwd).toBeNull();
      expect(order.sourceEscortOptionCode).toBe('PVE-ADV');
    });

    it('should create auto-handoff order with FIAT_PAYMENT source', () => {
      const result = createAutoHandoff(
        ORDER_NUMBER, GUILD_ID, 0, 0, CUSTOMER_USER_ID,
        SourceType.FIAT_PAYMENT, 'REF-002', 99, 'Product B',
        null, 1500, 'PVP-BASIC',
      );
      expect(result.isOk()).toBe(true);
      if (!result.isOk()) return;
      const order = result.getValue();

      expect(order.sourceType).toBe(SourceType.FIAT_PAYMENT);
      expect(order.sourceReference).toBe('REF-002');
      expect(order.sourceFiatPriceTwd).toBe(1500);
    });

    it('should reject blank sourceReference for non-MANUAL source', () => {
      const result = createAutoHandoff(
        ORDER_NUMBER, GUILD_ID, 0, 0, CUSTOMER_USER_ID,
        SourceType.CURRENCY_PURCHASE, '', 1, 'P', 100, null, 'PVE',
      );
      expect(isErr(result)).toBe(true);
      if (isErr(result)) {
        expect(result.getError().message).toContain('sourceReference must not be blank');
      }
    });

    it('should reject null sourceProductId for non-MANUAL source', () => {
      const result = createAutoHandoff(
        ORDER_NUMBER, GUILD_ID, 0, 0, CUSTOMER_USER_ID,
        SourceType.CURRENCY_PURCHASE, 'REF', null as unknown as number, 'P', 100, null, 'PVE',
      );
      expect(isErr(result)).toBe(true);
      if (isErr(result)) {
        expect(result.getError().message).toContain('sourceProductId must not be null');
      }
    });
  });

  describe('fromDbRow', () => {
    it('should reconstruct an order exactly from DB columns', () => {
      const confirmedAt = new Date('2026-05-21T11:00:00Z');
      const result = fromDbRow({
        id: 10,
        orderNumber: 'ESC-20260521-DEF456',
        guildId: 101,
        assignedByUserId: 201,
        escortUserId: 301,
        customerUserId: 401,
        createdAt: new Date('2026-05-21T09:00:00Z'),
        confirmedAt,
        completionRequestedAt: null,
        completedAt: null,
        afterSalesRequestedAt: null,
        afterSalesAssigneeUserId: null,
        afterSalesAssignedAt: null,
        afterSalesClosedAt: null,
        updatedAt: new Date('2026-05-21T11:00:00Z'),
        sourceType: SourceType.MANUAL,
        sourceReference: null,
        sourceProductId: null,
        sourceProductName: null,
        sourceCurrencyPrice: null,
        sourceFiatPriceTwd: null,
        sourceEscortOptionCode: null,
        status: EscortDispatchOrderStatus.CONFIRMED,
      });
      expect(result.isOk()).toBe(true);
      if (!result.isOk()) return;
      const order = result.getValue();

      expect(order.id).toBe(10);
      expect(order.orderNumber).toBe('ESC-20260521-DEF456');
      expect(order.status).toBe(EscortDispatchOrderStatus.CONFIRMED);
      expect(order.confirmedAt).toEqual(confirmedAt);
    });

    it('should validate all required timestamps per status', () => {
      const result = fromDbRow({
        id: 1,
        orderNumber: 'ESC-20260521-GHI789',
        guildId: 101,
        assignedByUserId: 201,
        escortUserId: 301,
        customerUserId: 401,
        createdAt: new Date(),
        confirmedAt: null, // required for CONFIRMED
        completionRequestedAt: null,
        completedAt: null,
        afterSalesRequestedAt: null,
        afterSalesAssigneeUserId: null,
        afterSalesAssignedAt: null,
        afterSalesClosedAt: null,
        updatedAt: new Date(),
        sourceType: SourceType.MANUAL,
        sourceReference: null,
        sourceProductId: null,
        sourceProductName: null,
        sourceCurrencyPrice: null,
        sourceFiatPriceTwd: null,
        sourceEscortOptionCode: null,
        status: EscortDispatchOrderStatus.CONFIRMED,
      });
      expect(isErr(result)).toBe(true);
      if (isErr(result)) {
        expect(result.getError().message).toContain('confirmedAt must not be null for status CONFIRMED');
      }
    });
  });
});

describe('EscortDispatchOrder — state transitions', () => {
  it('withConfirmed: should transition from PENDING_CONFIRMATION to CONFIRMED', () => {
    const order = makeBaseOrder();
    const confirmedAt = new Date('2026-05-21T12:00:00Z');
    const result = withConfirmed(order, confirmedAt);
    expect(result.isOk()).toBe(true);
    if (!result.isOk()) return;
    const updated = result.getValue();

    expect(updated.status).toBe(EscortDispatchOrderStatus.CONFIRMED);
    expect(updated.confirmedAt).toEqual(confirmedAt);
    // Ensure immutable: original unchanged
    expect(order.status).toBe(EscortDispatchOrderStatus.PENDING_CONFIRMATION);
  });

  it('withCompletionRequested: should transition from CONFIRMED to PENDING_CUSTOMER_CONFIRMATION', () => {
    const confirmedAt = new Date('2026-05-21T11:00:00Z');
    const order = makeBaseOrder({ status: EscortDispatchOrderStatus.CONFIRMED, confirmedAt });
    const requestedAt = new Date('2026-05-21T13:00:00Z');
    const result = withCompletionRequested(order, requestedAt);
    expect(result.isOk()).toBe(true);
    if (!result.isOk()) return;
    const updated = result.getValue();

    expect(updated.status).toBe(EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION);
    expect(updated.completionRequestedAt).toEqual(requestedAt);
  });

  it('withCompleted: should transition to COMPLETED', () => {
    const order = makeBaseOrder({
      status: EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
      confirmedAt: new Date('2026-05-21T11:00:00Z'),
      completionRequestedAt: new Date('2026-05-21T13:00:00Z'),
    });
    const completedAt = new Date('2026-05-21T14:00:00Z');
    const result = withCompleted(order, completedAt);
    expect(result.isOk()).toBe(true);
    if (!result.isOk()) return;
    const updated = result.getValue();

    expect(updated.status).toBe(EscortDispatchOrderStatus.COMPLETED);
    expect(updated.completedAt).toEqual(completedAt);
  });

  it('withAfterSalesRequested: should transition to AFTER_SALES_REQUESTED', () => {
    const order = makeBaseOrder({ status: EscortDispatchOrderStatus.COMPLETED, completedAt: new Date() });
    const requestedAt = new Date('2026-05-22T10:00:00Z');
    const result = withAfterSalesRequested(order, requestedAt);
    expect(result.isOk()).toBe(true);
    if (!result.isOk()) return;
    const updated = result.getValue();

    expect(updated.status).toBe(EscortDispatchOrderStatus.AFTER_SALES_REQUESTED);
    expect(updated.afterSalesRequestedAt).toEqual(requestedAt);
  });

  it('withAfterSalesInProgress: should transition to AFTER_SALES_IN_PROGRESS', () => {
    const order = makeBaseOrder({
      status: EscortDispatchOrderStatus.AFTER_SALES_REQUESTED,
      afterSalesRequestedAt: new Date('2026-05-22T10:00:00Z'),
    });
    const assignedAt = new Date('2026-05-22T10:30:00Z');
    const result = withAfterSalesInProgress(order, 500, assignedAt);
    expect(result.isOk()).toBe(true);
    if (!result.isOk()) return;
    const updated = result.getValue();

    expect(updated.status).toBe(EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS);
    expect(updated.afterSalesAssigneeUserId).toBe(500);
    expect(updated.afterSalesAssignedAt).toEqual(assignedAt);
  });

  it('withAfterSalesClosed: should transition to AFTER_SALES_CLOSED', () => {
    const order = makeBaseOrder({
      status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
      afterSalesRequestedAt: new Date('2026-05-22T10:00:00Z'),
      afterSalesAssigneeUserId: 500,
      afterSalesAssignedAt: new Date('2026-05-22T10:30:00Z'),
    });
    const closedAt = new Date('2026-05-22T12:00:00Z');
    const result = withAfterSalesClosed(order, closedAt);
    expect(result.isOk()).toBe(true);
    if (!result.isOk()) return;
    const updated = result.getValue();

    expect(updated.status).toBe(EscortDispatchOrderStatus.AFTER_SALES_CLOSED);
    expect(updated.afterSalesClosedAt).toEqual(closedAt);
  });

  it('withAssignedEscort: should update escortUserId while keeping PENDING_CONFIRMATION', () => {
    const order = makeBaseOrder();
    const assignedAt = new Date('2026-05-21T10:30:00Z');
    const result = withAssignedEscort(order, ASSIGNED_BY, 350, assignedAt);
    expect(result.isOk()).toBe(true);
    if (!result.isOk()) return;
    const updated = result.getValue();

    expect(updated.status).toBe(EscortDispatchOrderStatus.PENDING_CONFIRMATION);
    expect(updated.escortUserId).toBe(350);
    expect(updated.assignedByUserId).toBe(ASSIGNED_BY);
    expect(updated.updatedAt).toEqual(assignedAt);
  });

  it('withCompletionRequested should reset after-sales fields', () => {
    const order = makeBaseOrder({
      status: EscortDispatchOrderStatus.CONFIRMED,
      confirmedAt: new Date(),
      afterSalesRequestedAt: new Date('2026-05-22T10:00:00Z'),
      afterSalesAssigneeUserId: 500,
      afterSalesAssignedAt: new Date('2026-05-22T10:30:00Z'),
      afterSalesClosedAt: new Date('2026-05-22T12:00:00Z'),
    });
    const result = withCompletionRequested(order, new Date());
    expect(result.isOk()).toBe(true);
    if (!result.isOk()) return;
    const updated = result.getValue();

    expect(updated.afterSalesRequestedAt).toBeNull();
    expect(updated.afterSalesAssigneeUserId).toBeNull();
    expect(updated.afterSalesAssignedAt).toBeNull();
    expect(updated.afterSalesClosedAt).toBeNull();
    expect(updated.completedAt).toBeNull();
  });
});

describe('EscortDispatchOrder — predicate helpers', () => {
  it('isPendingEscortConfirmation', () => {
    const pending = makeBaseOrder();
    expect(isPendingEscortConfirmation(pending)).toBe(true);

    const confirmed = makeBaseOrder({ status: EscortDispatchOrderStatus.CONFIRMED, confirmedAt: new Date() });
    expect(isPendingEscortConfirmation(confirmed)).toBe(false);
  });

  it('isConfirmed', () => {
    expect(isConfirmed(makeBaseOrder({ status: EscortDispatchOrderStatus.CONFIRMED, confirmedAt: new Date() }))).toBe(true);
    expect(isConfirmed(makeBaseOrder())).toBe(false);
  });

  it('isPendingCustomerConfirmation', () => {
    expect(isPendingCustomerConfirmation(makeBaseOrder({
      status: EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
      confirmedAt: new Date(),
      completionRequestedAt: new Date(),
    }))).toBe(true);
    expect(isPendingCustomerConfirmation(makeBaseOrder())).toBe(false);
  });

  it('isAfterSalesRequested', () => {
    expect(isAfterSalesRequested(makeBaseOrder({
      status: EscortDispatchOrderStatus.AFTER_SALES_REQUESTED,
      afterSalesRequestedAt: new Date(),
    }))).toBe(true);
    expect(isAfterSalesRequested(makeBaseOrder())).toBe(false);
  });

  it('isAfterSalesInProgress', () => {
    expect(isAfterSalesInProgress(makeBaseOrder({
      status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
      afterSalesRequestedAt: new Date(),
      afterSalesAssigneeUserId: 500,
      afterSalesAssignedAt: new Date(),
    }))).toBe(true);
    expect(isAfterSalesInProgress(makeBaseOrder())).toBe(false);
  });

  it('isCompleted should return true for COMPLETED and AFTER_SALES_CLOSED', () => {
    expect(isCompleted(makeBaseOrder({ status: EscortDispatchOrderStatus.COMPLETED, completedAt: new Date() }))).toBe(true);
    expect(isCompleted(makeBaseOrder({
      status: EscortDispatchOrderStatus.AFTER_SALES_CLOSED,
      afterSalesRequestedAt: new Date(),
      afterSalesAssigneeUserId: 500,
      afterSalesAssignedAt: new Date(),
      afterSalesClosedAt: new Date(),
    }))).toBe(true);
    expect(isCompleted(makeBaseOrder())).toBe(false);
    expect(isCompleted(makeBaseOrder({ status: EscortDispatchOrderStatus.CONFIRMED, confirmedAt: new Date() }))).toBe(false);
    expect(isCompleted(makeBaseOrder({ status: EscortDispatchOrderStatus.AFTER_SALES_REQUESTED, afterSalesRequestedAt: new Date() }))).toBe(false);
  });
});

describe('EscortDispatchOrder — authorization checks', () => {
  const order = makeBaseOrder();

  it('canBeConfirmedBy should only allow escortUserId to confirm', () => {
    expect(canBeConfirmedBy(order, ESCORT_USER_ID)).toBe(true);
    expect(canBeConfirmedBy(order, 999)).toBe(false);
  });

  it('canBeCompletedByEscort should only allow escortUserId to complete', () => {
    expect(canBeCompletedByEscort(order, ESCORT_USER_ID)).toBe(true);
    expect(canBeCompletedByEscort(order, 999)).toBe(false);
  });

  it('canBeConfirmedByCustomer should only allow customerUserId', () => {
    expect(canBeConfirmedByCustomer(order, CUSTOMER_USER_ID)).toBe(true);
    expect(canBeConfirmedByCustomer(order, ESCORT_USER_ID)).toBe(false);
  });

  it('isAfterSalesAssignee should check assignee', () => {
    const assigned = makeBaseOrder({
      status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
      afterSalesRequestedAt: new Date(),
      afterSalesAssigneeUserId: 500,
      afterSalesAssignedAt: new Date(),
    });
    expect(isAfterSalesAssignee(assigned, 500)).toBe(true);
    expect(isAfterSalesAssignee(assigned, 501)).toBe(false);
    expect(isAfterSalesAssignee(order, 500)).toBe(false); // null assignee
  });
});

describe('EscortDispatchOrder — source type helpers', () => {
  it('isManualSource should return true for MANUAL and false for auto', () => {
    expect(isManualSource(makeBaseOrder())).toBe(true);
    expect(isManualSource(makeAutoOrder())).toBe(false);
  });

  it('isAutoSource should return true for non-MANUAL and false for MANUAL', () => {
    expect(isAutoSource(makeAutoOrder())).toBe(true);
    expect(isAutoSource(makeAutoOrder({ sourceType: SourceType.FIAT_PAYMENT }))).toBe(true);
    expect(isAutoSource(makeBaseOrder())).toBe(false);
  });
});

describe('EscortDispatchOrder — timeout check', () => {
  it('should return false when order is not PENDING_CUSTOMER_CONFIRMATION', () => {
    expect(hasCustomerConfirmationTimedOut(makeBaseOrder(), new Date())).toBe(false);
  });

  it('should return false when completionRequestedAt is null (defensive)', () => {
    // Use a raw object to simulate the logically impossible state
    // where status is PENDING_CUSTOMER_CONFIRMATION but completionRequestedAt is null.
    // This tests the defensive null guard in hasCustomerConfirmationTimedOut.
    const raw = makeRawOrder({
      status: EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
      confirmedAt: new Date(),
      completionRequestedAt: null,
    });
    expect(hasCustomerConfirmationTimedOut(raw, new Date())).toBe(false);
  });

  it('should return true when deadline has passed', () => {
    const requestedAt = new Date('2026-05-21T10:00:00Z');
    const order = makeBaseOrder({
      status: EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
      confirmedAt: new Date('2026-05-21T09:00:00Z'),
      completionRequestedAt: requestedAt,
    });
    const atDeadline = new Date(requestedAt.getTime() + CUSTOMER_CONFIRM_TIMEOUT_MS);
    expect(hasCustomerConfirmationTimedOut(order, atDeadline)).toBe(true);

    const pastDeadline = new Date(atDeadline.getTime() + 1000);
    expect(hasCustomerConfirmationTimedOut(order, pastDeadline)).toBe(true);
  });

  it('should return false when deadline has not yet passed', () => {
    const requestedAt = new Date('2026-05-21T10:00:00Z');
    const order = makeBaseOrder({
      status: EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
      confirmedAt: new Date('2026-05-21T09:00:00Z'),
      completionRequestedAt: requestedAt,
    });
    const beforeDeadline = new Date(requestedAt.getTime() + CUSTOMER_CONFIRM_TIMEOUT_MS - 1000);
    expect(hasCustomerConfirmationTimedOut(order, beforeDeadline)).toBe(false);
  });
});

describe('EscortDispatchOrder — validation edge cases', () => {
  it('MANUAL source with non-null sourceReference should fail', () => {
    const result = fromDbRow({
      id: 1,
      orderNumber: 'ESC-TEST',
      guildId: 1,
      assignedByUserId: 1,
      escortUserId: 2,
      customerUserId: 3,
      createdAt: new Date(),
      confirmedAt: null,
      completionRequestedAt: null,
      completedAt: null,
      afterSalesRequestedAt: null,
      afterSalesAssigneeUserId: null,
      afterSalesAssignedAt: null,
      afterSalesClosedAt: null,
      updatedAt: new Date(),
      sourceType: SourceType.MANUAL,
      sourceReference: 'SHOULD-FAIL',
      sourceProductId: null,
      sourceProductName: null,
      sourceCurrencyPrice: null,
      sourceFiatPriceTwd: null,
      sourceEscortOptionCode: null,
      status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
    });
    expect(isErr(result)).toBe(true);
    if (isErr(result)) {
      expect(result.getError().message).toContain('manual dispatch order must not carry source snapshot');
    }
  });

  it('non-MANUAL source with both price snapshots null should fail', () => {
    const result = createAutoHandoff(
      ORDER_NUMBER, GUILD_ID, 0, 0, CUSTOMER_USER_ID,
      SourceType.CURRENCY_PURCHASE, 'REF', 1, 'P',
      null, null, 'PVE',
    );
    expect(isErr(result)).toBe(true);
    if (isErr(result)) {
      expect(result.getError().message).toContain('source price snapshot must not be empty');
    }
  });

  it('non-MANUAL source with empty sourceReference should fail', () => {
    const result = createAutoHandoff(
      ORDER_NUMBER, GUILD_ID, 0, 0, CUSTOMER_USER_ID,
      SourceType.CURRENCY_PURCHASE, '', 1, 'P',
      100, null, 'PVE',
    );
    expect(isErr(result)).toBe(true);
    if (isErr(result)) {
      expect(result.getError().message).toContain('sourceReference must not be blank');
    }
  });

  it('should validate AFTER_SALES_IN_PROGRESS requires afterSalesAssigneeUserId', () => {
    const result = fromDbRow({
      id: 1,
      orderNumber: 'ESC-TEST',
      guildId: 1,
      assignedByUserId: 1,
      escortUserId: 2,
      customerUserId: 3,
      createdAt: new Date(),
      confirmedAt: new Date(),
      completionRequestedAt: new Date(),
      completedAt: new Date(),
      afterSalesRequestedAt: new Date(),
      afterSalesAssigneeUserId: null,
      afterSalesAssignedAt: null,
      afterSalesClosedAt: null,
      updatedAt: new Date(),
      sourceType: SourceType.MANUAL,
      sourceReference: null,
      sourceProductId: null,
      sourceProductName: null,
      sourceCurrencyPrice: null,
      sourceFiatPriceTwd: null,
      sourceEscortOptionCode: null,
      status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
    });
    expect(isErr(result)).toBe(true);
    if (isErr(result)) {
      expect(result.getError().message).toContain('afterSalesAssigneeUserId');
    }
  });

  it('should validate AFTER_SALES_CLOSED requires afterSalesClosedAt', () => {
    const result = fromDbRow({
      id: 1,
      orderNumber: 'ESC-TEST',
      guildId: 1,
      assignedByUserId: 1,
      escortUserId: 2,
      customerUserId: 3,
      createdAt: new Date(),
      confirmedAt: new Date(),
      completionRequestedAt: new Date(),
      completedAt: new Date(),
      afterSalesRequestedAt: new Date(),
      afterSalesAssigneeUserId: 500,
      afterSalesAssignedAt: new Date(),
      afterSalesClosedAt: null,
      updatedAt: new Date(),
      sourceType: SourceType.MANUAL,
      sourceReference: null,
      sourceProductId: null,
      sourceProductName: null,
      sourceCurrencyPrice: null,
      sourceFiatPriceTwd: null,
      sourceEscortOptionCode: null,
      status: EscortDispatchOrderStatus.AFTER_SALES_CLOSED,
    });
    expect(isErr(result)).toBe(true);
    if (isErr(result)) {
      expect(result.getError().message).toContain('afterSalesClosedAt');
    }
  });
});
