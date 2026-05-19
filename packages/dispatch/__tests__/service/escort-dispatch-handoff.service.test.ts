import { describe, it, expect, vi, beforeEach } from 'vitest';
import { isOk, isErr } from '@ltdjms/shared';
import { EscortDispatchHandoffService } from '../../src/service/escort-dispatch-handoff.service.js';
import type { HandoffProductSnapshot } from '../../src/service/escort-dispatch-handoff.service.js';
import type { EscortDispatchOrderRepo } from '../../src/repo/escort-dispatch-order.repo.js';
import { EscortDispatchOrderStatus, SourceType } from '../../src/domain/escort-dispatch-order.js';
import type { EscortDispatchOrder } from '../../src/domain/escort-dispatch-order.js';

describe('EscortDispatchHandoffService', () => {
  let mockRepo: EscortDispatchOrderRepo;
  let service: EscortDispatchHandoffService;

  const NOW = new Date('2026-05-20T12:00:00Z');

  const validProduct: HandoffProductSnapshot = {
    id: 999,
    name: 'Test Product',
    currencyPrice: 50000,
    fiatPriceTwd: null,
    escortOptionCode: 'CONF_DAM_300W',
    shouldAutoCreateEscortOrder: true,
  };

  beforeEach(() => {
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

    service = new EscortDispatchHandoffService(mockRepo);
  });

  describe('handoffFromCurrencyPurchase', () => {
    it('should create a handoff order successfully', async () => {
      vi.mocked(mockRepo.findBySourceIdentity).mockResolvedValue(null);
      vi.mocked(mockRepo.existsByOrderNumber).mockResolvedValue(false);
      vi.mocked(mockRepo.save).mockResolvedValue({
        id: 1,
        orderNumber: 'ESC-20260520-A1B2C3',
        guildId: 100,
        assignedByUserId: 0,
        escortUserId: 0,
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
        sourceType: SourceType.CURRENCY_PURCHASE,
        sourceReference: 'purchase-ref-123',
        sourceProductId: 999,
        sourceProductName: 'Test Product',
        sourceCurrencyPrice: 50000,
        sourceFiatPriceTwd: null,
        sourceEscortOptionCode: 'CONF_DAM_300W',
        status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
      });

      const result = await service.handoffFromCurrencyPurchase(
        100, 400, validProduct, 'purchase-ref-123',
      );

      expect(isOk(result)).toBe(true);
    });

    it('should be idempotent when handoff already exists', async () => {
      const existingOrder: EscortDispatchOrder = {
        id: 1,
        orderNumber: 'ESC-20260520-EXIST1',
        guildId: 100,
        assignedByUserId: 0,
        escortUserId: 0,
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
        sourceType: SourceType.CURRENCY_PURCHASE,
        sourceReference: 'purchase-ref-123',
        sourceProductId: 999,
        sourceProductName: 'Test Product',
        sourceCurrencyPrice: 50000,
        sourceFiatPriceTwd: null,
        sourceEscortOptionCode: 'CONF_DAM_300W',
        status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
      };
      vi.mocked(mockRepo.findBySourceIdentity).mockResolvedValue(existingOrder);

      const result = await service.handoffFromCurrencyPurchase(
        100, 400, validProduct, 'purchase-ref-123',
      );

      expect(isOk(result)).toBe(true);
      // save should not be called for idempotent case
      expect(mockRepo.save).not.toHaveBeenCalled();
    });

    it('should return error for null product', async () => {
      const result = await service.handoffFromCurrencyPurchase(
        100, 400, null, 'purchase-ref-123',
      );

      expect(isErr(result)).toBe(true);
    });

    it('should return error when product has no auto-create', async () => {
      const noAutoProduct: HandoffProductSnapshot = {
        ...validProduct,
        shouldAutoCreateEscortOrder: false,
      };

      const result = await service.handoffFromCurrencyPurchase(
        100, 400, noAutoProduct, 'purchase-ref-123',
      );

      expect(isErr(result)).toBe(true);
    });

    it('should return error for empty sourceReference', async () => {
      const result = await service.handoffFromCurrencyPurchase(
        100, 400, validProduct, '',
      );

      expect(isErr(result)).toBe(true);
    });
  });

  describe('handoffFromFiatPayment', () => {
    it('should create a fiat handoff order', async () => {
      vi.mocked(mockRepo.findBySourceIdentity).mockResolvedValue(null);
      vi.mocked(mockRepo.existsByOrderNumber).mockResolvedValue(false);
      vi.mocked(mockRepo.save).mockResolvedValue({
        id: 1,
        orderNumber: 'ESC-20260520-FIAT1',
        guildId: 100,
        assignedByUserId: 0,
        escortUserId: 0,
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
        sourceType: SourceType.FIAT_PAYMENT,
        sourceReference: 'fiat-order-456',
        sourceProductId: 999,
        sourceProductName: 'Test Product',
        sourceCurrencyPrice: null,
        sourceFiatPriceTwd: 1000,
        sourceEscortOptionCode: 'CONF_DAM_300W',
        status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
      });

      const result = await service.handoffFromFiatPayment(
        100, 400, validProduct, 'fiat-order-456',
      );

      expect(isOk(result)).toBe(true);
    });
  });
});
