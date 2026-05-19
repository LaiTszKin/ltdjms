import { describe, it, expect } from 'vitest';
import {
  EscortDispatchOrderStatus,
  SourceType,
  CUSTOMER_CONFIRM_TIMEOUT_MS,
  createPending,
  createManualOpenOrder,
  createPendingFull,
  createAutoHandoff,
  withAssignedEscort,
  withConfirmed,
  withCompletionRequested,
  withCompleted,
  withAfterSalesRequested,
  withAfterSalesInProgress,
  withAfterSalesClosed,
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
  type EscortDispatchOrder,
} from '../../src/domain/escort-dispatch-order.js';

function makeDefaultOrder(overrides?: Partial<EscortDispatchOrder>): EscortDispatchOrder {
  return createPending('ESC-20260520-A1B2C3', 100, 200, 300, 400);
}

describe('EscortDispatchOrder', () => {
  describe('factory: createPending', () => {
    it('should create a PENDING_CONFIRMATION order with MANUAL source', () => {
      const order = createPending('ESC-20260520-A1B2C3', 100, 200, 300, 400);

      expect(order.id).toBeNull();
      expect(order.orderNumber).toBe('ESC-20260520-A1B2C3');
      expect(order.guildId).toBe(100);
      expect(order.assignedByUserId).toBe(200);
      expect(order.escortUserId).toBe(300);
      expect(order.customerUserId).toBe(400);
      expect(order.status).toBe(EscortDispatchOrderStatus.PENDING_CONFIRMATION);
      expect(order.sourceType).toBe(SourceType.MANUAL);
      expect(order.sourceReference).toBeNull();
      expect(order.sourceProductId).toBeNull();
      expect(order.sourceProductName).toBeNull();
      expect(order.sourceCurrencyPrice).toBeNull();
      expect(order.sourceFiatPriceTwd).toBeNull();
      expect(order.sourceEscortOptionCode).toBeNull();
      expect(order.createdAt).toBeInstanceOf(Date);
      expect(order.updatedAt).toBeInstanceOf(Date);
      expect(order.confirmedAt).toBeNull();
      expect(order.completionRequestedAt).toBeNull();
      expect(order.completedAt).toBeNull();
      expect(order.afterSalesRequestedAt).toBeNull();
      expect(order.afterSalesAssigneeUserId).toBeNull();
      expect(order.afterSalesAssignedAt).toBeNull();
      expect(order.afterSalesClosedAt).toBeNull();
    });

    it('should reject same escortUserId and customerUserId', () => {
      expect(() => createPending('ESC-20260520-AAAAAA', 100, 200, 300, 300)).toThrow(
        'escortUserId and customerUserId must be different',
      );
    });

    it('should reject blank orderNumber', () => {
      expect(() => createPending('', 100, 200, 300, 400)).toThrow(
        'orderNumber must not be blank',
      );
    });
  });

  describe('factory: createManualOpenOrder', () => {
    it('should create a PENDING_CONFIRMATION order with escortUserId=0', () => {
      const order = createManualOpenOrder('ESC-20260520-B2C3D4', 100, 200, 400, 'CONF_DAM_300W');

      expect(order.escortUserId).toBe(0);
      expect(order.customerUserId).toBe(400);
      expect(order.sourceEscortOptionCode).toBe('CONF_DAM_300W');
      expect(order.sourceType).toBe(SourceType.MANUAL);
      expect(order.status).toBe(EscortDispatchOrderStatus.PENDING_CONFIRMATION);
    });

    it('should reject customerUserId <= 0', () => {
      expect(() => createManualOpenOrder('ESC-20260520-B2C3D4', 100, 200, 0, 'CONF_DAM_300W')).toThrow(
        'customerUserId must be greater than 0',
      );
    });
  });

  describe('factory: createAutoHandoff', () => {
    it('should create a PENDING_CONFIRMATION order with source snapshot', () => {
      const order = createAutoHandoff(
        'ESC-20260520-C3D4E5',
        100,
        0,
        0,
        400,
        SourceType.CURRENCY_PURCHASE,
        'ref-123',
        999,
        'Test Product',
        50000,
        null,
        'CONF_DAM_300W',
      );

      expect(order.escortUserId).toBe(0);
      expect(order.assignedByUserId).toBe(0);
      expect(order.customerUserId).toBe(400);
      expect(order.sourceType).toBe(SourceType.CURRENCY_PURCHASE);
      expect(order.sourceReference).toBe('ref-123');
      expect(order.sourceProductId).toBe(999);
      expect(order.sourceProductName).toBe('Test Product');
      expect(order.sourceCurrencyPrice).toBe(50000);
      expect(order.sourceFiatPriceTwd).toBeNull();
      expect(order.sourceEscortOptionCode).toBe('CONF_DAM_300W');
      expect(order.status).toBe(EscortDispatchOrderStatus.PENDING_CONFIRMATION);
    });

    it('should reject non-MANUAL source without sourceReference', () => {
      expect(() =>
        createAutoHandoff(
          'ESC-20260520-D4E5F6',
          100,
          0,
          0,
          400,
          SourceType.CURRENCY_PURCHASE,
          '',
          999,
          'Test',
          50000,
          null,
          'CONF_DAM_300W',
        ),
      ).toThrow('sourceReference must not be blank');
    });
  });

  describe('state transitions', () => {
    it('should transition PENDING_CONFIRMATION -> CONFIRMED', () => {
      const order = makeDefaultOrder();
      const confirmedAt = new Date();
      const confirmed = withConfirmed(order, confirmedAt);

      expect(confirmed.status).toBe(EscortDispatchOrderStatus.CONFIRMED);
      expect(confirmed.confirmedAt).toBe(confirmedAt);
      expect(confirmed.completionRequestedAt).toBeNull();
      expect(confirmed.completedAt).toBeNull();
    });

    it('should transition CONFIRMED -> PENDING_CUSTOMER_CONFIRMATION', () => {
      const order = makeDefaultOrder();
      const confirmed = withConfirmed(order, new Date());
      const requestedAt = new Date();
      const pendingCustomer = withCompletionRequested(confirmed, requestedAt);

      expect(pendingCustomer.status).toBe(EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION);
      expect(pendingCustomer.completionRequestedAt).toBe(requestedAt);
    });

    it('should transition PENDING_CUSTOMER_CONFIRMATION -> COMPLETED', () => {
      const order = makeDefaultOrder();
      const confirmed = withConfirmed(order, new Date());
      const pendingCustomer = withCompletionRequested(confirmed, new Date());
      const completedAt = new Date();
      const completed = withCompleted(pendingCustomer, completedAt);

      expect(completed.status).toBe(EscortDispatchOrderStatus.COMPLETED);
      expect(completed.completedAt).toBe(completedAt);
    });

    it('should transition PENDING_CUSTOMER_CONFIRMATION -> AFTER_SALES_REQUESTED', () => {
      const order = makeDefaultOrder();
      const confirmed = withConfirmed(order, new Date());
      const pendingCustomer = withCompletionRequested(confirmed, new Date());
      const afterSalesAt = new Date();
      const afterSales = withAfterSalesRequested(pendingCustomer, afterSalesAt);

      expect(afterSales.status).toBe(EscortDispatchOrderStatus.AFTER_SALES_REQUESTED);
      expect(afterSales.afterSalesRequestedAt).toBe(afterSalesAt);
    });

    it('should transition AFTER_SALES_REQUESTED -> AFTER_SALES_IN_PROGRESS', () => {
      const order = makeDefaultOrder();
      const confirmed = withConfirmed(order, new Date());
      const pendingCustomer = withCompletionRequested(confirmed, new Date());
      const afterSales = withAfterSalesRequested(pendingCustomer, new Date());
      const assigneeUserId = 500;
      const assignedAt = new Date();
      const inProgress = withAfterSalesInProgress(afterSales, assigneeUserId, assignedAt);

      expect(inProgress.status).toBe(EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS);
      expect(inProgress.afterSalesAssigneeUserId).toBe(assigneeUserId);
      expect(inProgress.afterSalesAssignedAt).toBe(assignedAt);
    });

    it('should transition AFTER_SALES_IN_PROGRESS -> AFTER_SALES_CLOSED', () => {
      const order = makeDefaultOrder();
      const confirmed = withConfirmed(order, new Date());
      const pendingCustomer = withCompletionRequested(confirmed, new Date());
      const afterSales = withAfterSalesRequested(pendingCustomer, new Date());
      const inProgress = withAfterSalesInProgress(afterSales, 500, new Date());
      const closedAt = new Date();
      const closed = withAfterSalesClosed(inProgress, closedAt);

      expect(closed.status).toBe(EscortDispatchOrderStatus.AFTER_SALES_CLOSED);
      expect(closed.afterSalesClosedAt).toBe(closedAt);
    });
  });

  describe('predicates', () => {
    it('isPendingEscortConfirmation', () => {
      expect(isPendingEscortConfirmation(makeDefaultOrder())).toBe(true);
      expect(isPendingEscortConfirmation(withConfirmed(makeDefaultOrder(), new Date()))).toBe(false);
    });

    it('isConfirmed', () => {
      const confirmed = withConfirmed(makeDefaultOrder(), new Date());
      expect(isConfirmed(confirmed)).toBe(true);
      expect(isConfirmed(makeDefaultOrder())).toBe(false);
    });

    it('isPendingCustomerConfirmation', () => {
      const confirmed = withConfirmed(makeDefaultOrder(), new Date());
      const pending = withCompletionRequested(confirmed, new Date());
      expect(isPendingCustomerConfirmation(pending)).toBe(true);
    });

    it('isCompleted returns true for COMPLETED and AFTER_SALES_CLOSED', () => {
      const order = makeDefaultOrder();
      const confirmed = withConfirmed(order, new Date());
      const completed = withCompleted(confirmed, new Date());
      expect(isCompleted(completed)).toBe(true);

      const afterSales = withAfterSalesRequested(confirmed, new Date());
      const inProgress = withAfterSalesInProgress(afterSales, 500, new Date());
      const closed = withAfterSalesClosed(inProgress, new Date());
      expect(isCompleted(closed)).toBe(true);
    });

    it('isCompleted returns false for other statuses', () => {
      expect(isCompleted(makeDefaultOrder())).toBe(false);
      const confirmed = withConfirmed(makeDefaultOrder(), new Date());
      expect(isCompleted(confirmed)).toBe(false);
    });
  });

  describe('authorization checks', () => {
    it('canBeConfirmedBy checks escortUserId', () => {
      const order = makeDefaultOrder();
      expect(canBeConfirmedBy(order, 300)).toBe(true);
      expect(canBeConfirmedBy(order, 999)).toBe(false);
    });

    it('canBeCompletedByEscort checks escortUserId', () => {
      const order = makeDefaultOrder();
      expect(canBeCompletedByEscort(order, 300)).toBe(true);
    });

    it('canBeConfirmedByCustomer checks customerUserId', () => {
      const order = makeDefaultOrder();
      expect(canBeConfirmedByCustomer(order, 400)).toBe(true);
      expect(canBeConfirmedByCustomer(order, 300)).toBe(false);
    });

    it('isAfterSalesAssignee checks afterSalesAssigneeUserId', () => {
      const order = makeDefaultOrder();
      expect(isAfterSalesAssignee(order, 500)).toBe(false);

      const afterSales = withAfterSalesRequested(order, new Date());
      const inProgress = withAfterSalesInProgress(afterSales, 500, new Date());
      expect(isAfterSalesAssignee(inProgress, 500)).toBe(true);
      expect(isAfterSalesAssignee(inProgress, 600)).toBe(false);
    });
  });

  describe('source type helpers', () => {
    it('isManualSource returns true for MANUAL', () => {
      const order = makeDefaultOrder();
      expect(isManualSource(order)).toBe(true);
      expect(isAutoSource(order)).toBe(false);
    });

    it('isAutoSource returns true for non-MANUAL', () => {
      const order = createAutoHandoff(
        'ESC-TEST',
        100, 0, 0, 400,
        SourceType.CURRENCY_PURCHASE,
        'ref-1', 999, 'Test', 50000, null, 'CONF_DAM_300W',
      );
      expect(isAutoSource(order)).toBe(true);
      expect(isManualSource(order)).toBe(false);
    });
  });

  describe('timeout check', () => {
    it('hasCustomerConfirmationTimedOut returns false for non-pending-customer status', () => {
      const order = makeDefaultOrder();
      expect(hasCustomerConfirmationTimedOut(order, new Date())).toBe(false);
    });

    it('hasCustomerConfirmationTimedOut returns false when within 24h', () => {
      const now = new Date();
      const order = createPendingFull(
        'ESC-TEST', 100, 200, 300, 400,
        SourceType.MANUAL, null, null, null, null, null, null,
      );
      // Manually create a PENDING_CUSTOMER_CONFIRMATION order via transitions
      const confirmed = withConfirmed(order, new Date(now.getTime() - 1000));
      const pending = withCompletionRequested(confirmed, new Date(now.getTime() - 1000));

      expect(hasCustomerConfirmationTimedOut(pending, now)).toBe(false);
    });

    it('hasCustomerConfirmationTimedOut returns true after 24h+1ms', () => {
      const completionRequestedAt = new Date();
      const past = new Date(completionRequestedAt.getTime() - CUSTOMER_CONFIRM_TIMEOUT_MS - 1);

      // We need a PENDING_CUSTOMER_CONFIRMATION order
      const order = makeDefaultOrder();
      // Manually construct via transitions
      const c = withConfirmed(order, new Date(past.getTime() - 1000));
      const pending = withCompletionRequested(c, past);

      expect(hasCustomerConfirmationTimedOut(pending, completionRequestedAt)).toBe(true);
    });

    it('CUSTOMER_CONFIRM_TIMEOUT_MS equals 24 hours', () => {
      expect(CUSTOMER_CONFIRM_TIMEOUT_MS).toBe(24 * 60 * 60 * 1000);
    });
  });

  describe('immutability', () => {
    it('original order is not mutated by transitions', () => {
      const order = makeDefaultOrder();
      const orderSnapshot = { ...order };

      withConfirmed(order, new Date());

      expect(order.status).toBe(orderSnapshot.status);
    });
  });
});
