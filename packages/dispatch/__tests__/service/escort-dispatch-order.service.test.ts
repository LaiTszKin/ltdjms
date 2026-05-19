import { describe, it, expect, vi, beforeEach } from 'vitest';
import { isOk, isErr } from '@ltdjms/shared';
import { EscortDispatchOrderService } from '../../src/service/escort-dispatch-order.service.js';
import { EscortDispatchOrderNumberGenerator } from '../../src/domain/order-number-generator.js';
import type { EscortDispatchOrderRepo } from '../../src/repo/escort-dispatch-order.repo.js';
import {
  type EscortDispatchOrder,
  EscortDispatchOrderStatus,
  SourceType,
  createPending,
  createAutoHandoff,
  withConfirmed,
  withCompletionRequested,
} from '../../src/domain/escort-dispatch-order.js';

describe('EscortDispatchOrderService', () => {
  let mockRepo: EscortDispatchOrderRepo;
  let service: EscortDispatchOrderService;
  let fixedClock: () => number;

  const NOW = new Date('2026-05-20T12:00:00Z');

  function makeOrder(overrides?: Partial<EscortDispatchOrder>): EscortDispatchOrder {
    return {
      id: 1,
      orderNumber: 'ESC-20260520-A1B2C3',
      guildId: 100,
      assignedByUserId: 200,
      escortUserId: 300,
      customerUserId: 400,
      createdAt: NOW,
      confirmedAt: null,
      completionRequestedAt: null,
      completedAt: null,
      afterSalesRequestedAt: null,
      afterSalesAssigneeUserId: null,
      afterSalesAssignedAt: null,
      afterSalesClosedAt: null,
      updatedAt: NOW,
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

  beforeEach(() => {
    fixedClock = () => NOW.getTime();

    mockRepo = {
      save: vi.fn(),
      update: vi.fn(),
      findByOrderNumber: vi.fn(),
      findBySourceIdentity: vi.fn(),
      findRecentByGuildId: vi.fn(),
      findPendingAssignmentByGuildId: vi.fn(),
      assignEscort: vi.fn(),
      claimAfterSales: vi.fn(),
      closeAfterSales: vi.fn(),
      existsByOrderNumber: vi.fn(),
    };

    service = new EscortDispatchOrderService(mockRepo, undefined, fixedClock);
  });

  describe('createOrder', () => {
    it('should create a new order successfully', async () => {
      vi.mocked(mockRepo.existsByOrderNumber).mockResolvedValue(false);
      const savedOrder = makeOrder({ id: 1 });
      vi.mocked(mockRepo.save).mockResolvedValue(savedOrder);

      const result = await service.createOrder(100, 200, 300, 400);

      expect(isOk(result)).toBe(true);
      if (isOk(result)) {
        expect(result.getValue().orderNumber).toMatch(/^ESC-\d{8}-[A-Z0-9]{6}$/);
        expect(result.getValue().guildId).toBe(100);
      }
    });

    it('should return error when escort and customer are the same', async () => {
      const result = await service.createOrder(100, 200, 300, 300);

      expect(isErr(result)).toBe(true);
    });

    it('should retry on duplicate order number', async () => {
      // First call returns true (exists), second returns false (unique)
      vi.mocked(mockRepo.existsByOrderNumber)
        .mockResolvedValueOnce(true)
        .mockResolvedValueOnce(false);

      const savedOrder = makeOrder({ id: 1 });
      vi.mocked(mockRepo.save).mockResolvedValue(savedOrder);

      const result = await service.createOrder(100, 200, 300, 400);

      expect(isOk(result)).toBe(true);
      expect(mockRepo.existsByOrderNumber).toHaveBeenCalledTimes(2);
    });
  });

  describe('createManualOpenOrder', () => {
    it('should create a manual open order', async () => {
      vi.mocked(mockRepo.existsByOrderNumber).mockResolvedValue(false);
      const savedOrder = makeOrder({
        id: 1,
        escortUserId: 0,
        sourceEscortOptionCode: 'CONF_DAM_300W',
      });
      vi.mocked(mockRepo.save).mockResolvedValue(savedOrder);

      const result = await service.createManualOpenOrder(100, 200, 400, 'CONF_DAM_300W');

      expect(isOk(result)).toBe(true);
    });

    it('should return error when customerUserId <= 0', async () => {
      const result = await service.createManualOpenOrder(100, 200, 0, 'CONF_DAM_300W');

      expect(isErr(result)).toBe(true);
    });
  });

  describe('assignPendingOrder', () => {
    it('should assign an order successfully', async () => {
      const pendingOrder = makeOrder({ escortUserId: 0 });
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(pendingOrder);
      const assignedOrder = makeOrder({ escortUserId: 500, assignedByUserId: 200 });
      vi.mocked(mockRepo.assignEscort).mockResolvedValue(assignedOrder);

      const result = await service.assignPendingOrder('ESC-20260520-A1B2C3', 200, 500);

      expect(isOk(result)).toBe(true);
    });

    it('should return error if order is not in PENDING_CONFIRMATION', async () => {
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(
        makeOrder({ status: EscortDispatchOrderStatus.CONFIRMED }),
      );

      const result = await service.assignPendingOrder('ESC-20260520-A1B2C3', 200, 500);

      expect(isErr(result)).toBe(true);
    });

    it('should return error if escort equals customer', async () => {
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(makeOrder({ customerUserId: 500 }));

      const result = await service.assignPendingOrder('ESC-20260520-A1B2C3', 200, 500);

      expect(isErr(result)).toBe(true);
    });

    it('should return race condition error when assignEscort returns null', async () => {
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(makeOrder());
      vi.mocked(mockRepo.assignEscort).mockResolvedValue(null);

      const result = await service.assignPendingOrder('ESC-20260520-A1B2C3', 200, 500);

      expect(isErr(result)).toBe(true);
    });
  });

  describe('confirmOrder', () => {
    it('should confirm an order', async () => {
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(makeOrder());
      const confirmedOrder = makeOrder({
        status: EscortDispatchOrderStatus.CONFIRMED,
        confirmedAt: NOW,
      });
      vi.mocked(mockRepo.update).mockResolvedValue(confirmedOrder);

      const result = await service.confirmOrder('ESC-20260520-A1B2C3', 300);

      expect(isOk(result)).toBe(true);
    });

    it('should reject non-escort user', async () => {
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(makeOrder());

      const result = await service.confirmOrder('ESC-20260520-A1B2C3', 999);

      expect(isErr(result)).toBe(true);
    });
  });

  describe('requestCompletion', () => {
    it('should request completion', async () => {
      const confirmedOrder = makeOrder({
        status: EscortDispatchOrderStatus.CONFIRMED,
        confirmedAt: NOW,
      });
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(confirmedOrder);
      const pendingOrder = makeOrder({
        status: EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
        confirmedAt: NOW,
        completionRequestedAt: NOW,
      });
      vi.mocked(mockRepo.update).mockResolvedValue(pendingOrder);

      const result = await service.requestCompletion('ESC-20260520-A1B2C3', 300);

      expect(isOk(result)).toBe(true);
    });

    it('should reject non-escort user', async () => {
      const confirmedOrder = makeOrder({
        status: EscortDispatchOrderStatus.CONFIRMED,
        confirmedAt: NOW,
      });
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(confirmedOrder);

      const result = await service.requestCompletion('ESC-20260520-A1B2C3', 999);

      expect(isErr(result)).toBe(true);
    });
  });

  describe('customerConfirmCompletion', () => {
    it('should confirm completion by customer', async () => {
      const pendingOrder = makeOrder({
        status: EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
        confirmedAt: NOW,
        completionRequestedAt: new Date(NOW.getTime() - 1000),
      });
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(pendingOrder);
      const completedOrder = makeOrder({
        status: EscortDispatchOrderStatus.COMPLETED,
        confirmedAt: NOW,
        completionRequestedAt: NOW,
        completedAt: NOW,
      });
      vi.mocked(mockRepo.update).mockResolvedValue(completedOrder);

      const result = await service.customerConfirmCompletion('ESC-20260520-A1B2C3', 400);

      expect(isOk(result)).toBe(true);
    });

    it('should be idempotent when already COMPLETED', async () => {
      const completedOrder = makeOrder({
        status: EscortDispatchOrderStatus.COMPLETED,
        confirmedAt: NOW,
        completionRequestedAt: NOW,
        completedAt: NOW,
      });
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(completedOrder);

      const result = await service.customerConfirmCompletion('ESC-20260520-A1B2C3', 400);

      expect(isOk(result)).toBe(true);
    });
  });

  describe('requestAfterSales', () => {
    it('should request after-sales from PENDING_CUSTOMER_CONFIRMATION', async () => {
      const pendingOrder = makeOrder({
        status: EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
        confirmedAt: NOW,
        completionRequestedAt: NOW,
      });
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(pendingOrder);
      const afterSalesOrder = makeOrder({
        status: EscortDispatchOrderStatus.AFTER_SALES_REQUESTED,
        afterSalesRequestedAt: NOW,
      });
      vi.mocked(mockRepo.update).mockResolvedValue(afterSalesOrder);

      const result = await service.requestAfterSales('ESC-20260520-A1B2C3', 400);

      expect(isOk(result)).toBe(true);
    });

    it('should reject if already in after-sales flow', async () => {
      const inProgressOrder = makeOrder({
        status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
        afterSalesRequestedAt: NOW,
        afterSalesAssigneeUserId: 500,
        afterSalesAssignedAt: NOW,
      });
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(inProgressOrder);

      const result = await service.requestAfterSales('ESC-20260520-A1B2C3', 400);

      expect(isErr(result)).toBe(true);
    });
  });

  describe('claimAfterSales', () => {
    it('should claim after-sales case', async () => {
      const afterSalesOrder = makeOrder({
        status: EscortDispatchOrderStatus.AFTER_SALES_REQUESTED,
        afterSalesRequestedAt: NOW,
      });
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(afterSalesOrder);
      const claimedOrder = makeOrder({
        status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
        afterSalesRequestedAt: NOW,
        afterSalesAssigneeUserId: 500,
        afterSalesAssignedAt: NOW,
      });
      vi.mocked(mockRepo.claimAfterSales).mockResolvedValue(claimedOrder);

      const result = await service.claimAfterSales('ESC-20260520-A1B2C3', 500);

      expect(isOk(result)).toBe(true);
    });

    it('should reject if already claimed by someone else', async () => {
      const inProgressOrder = makeOrder({
        status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
        afterSalesRequestedAt: NOW,
        afterSalesAssigneeUserId: 600,
        afterSalesAssignedAt: NOW,
      });
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(inProgressOrder);

      const result = await service.claimAfterSales('ESC-20260520-A1B2C3', 500);

      expect(isErr(result)).toBe(true);
    });
  });

  describe('closeAfterSales', () => {
    it('should close after-sales case', async () => {
      const inProgressOrder = makeOrder({
        status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
        afterSalesRequestedAt: NOW,
        afterSalesAssigneeUserId: 500,
        afterSalesAssignedAt: NOW,
      });
      vi.mocked(mockRepo.findByOrderNumber).mockResolvedValue(inProgressOrder);
      const closedOrder = makeOrder({
        status: EscortDispatchOrderStatus.AFTER_SALES_CLOSED,
        afterSalesRequestedAt: NOW,
        afterSalesAssigneeUserId: 500,
        afterSalesAssignedAt: NOW,
        afterSalesClosedAt: NOW,
      });
      vi.mocked(mockRepo.closeAfterSales).mockResolvedValue(closedOrder);

      const result = await service.closeAfterSales('ESC-20260520-A1B2C3', 500);

      expect(isOk(result)).toBe(true);
    });
  });

  describe('findRecentOrders', () => {
    it('should return recent orders', async () => {
      const orders = [makeOrder()];
      vi.mocked(mockRepo.findRecentByGuildId).mockResolvedValue(orders);

      const result = await service.findRecentOrders(100);

      expect(isOk(result)).toBe(true);
      if (isOk(result)) {
        expect(result.getValue()).toHaveLength(1);
      }
    });

    it('should cap limit at 20', async () => {
      await service.findRecentOrders(100, 50);

      expect(mockRepo.findRecentByGuildId).toHaveBeenCalledWith(100, 20);
    });
  });

  describe('findPendingAssignmentOrders', () => {
    it('should return pending assignment orders', async () => {
      const orders = [makeOrder({ escortUserId: 0 })];
      vi.mocked(mockRepo.findPendingAssignmentByGuildId).mockResolvedValue(orders);

      const result = await service.findPendingAssignmentOrders(100);

      expect(isOk(result)).toBe(true);
      if (isOk(result)) {
        expect(result.getValue()).toHaveLength(1);
      }
    });
  });
});
