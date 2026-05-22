import { describe, it, expect, vi, beforeEach } from 'vitest';
import { EscortDispatchHandoffService } from '../../service/escort-dispatch-handoff.service.js';
import type { HandoffProductSnapshot } from '../../service/escort-dispatch-handoff.service.js';
import { EscortDispatchOrderStatus, SourceType } from '../../domain/escort-dispatch-order.js';
import type { EscortDispatchOrderRepo } from '../../repo/escort-dispatch-order.repo.js';
import type { EscortDispatchOrder } from '../../domain/escort-dispatch-order.js';

const GUILD_ID = 100;
const BUYER_USER_ID = 400;
const SOURCE_REFERENCE = 'ORDER-001';

const BASE_PRODUCT: HandoffProductSnapshot = {
  id: 42,
  name: 'Product A',
  currencyPrice: 5000,
  fiatPriceTwd: null,
  escortOptionCode: 'PVE-BASIC',
  shouldAutoCreateEscortOrder: true,
};

function makeOrder(overrides?: Partial<EscortDispatchOrder>): EscortDispatchOrder {
  return {
    id: 1,
    orderNumber: 'ESC-20260521-ABC123',
    guildId: GUILD_ID,
    assignedByUserId: 0,
    escortUserId: 0,
    customerUserId: BUYER_USER_ID,
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
    sourceReference: SOURCE_REFERENCE,
    sourceProductId: 42,
    sourceProductName: 'Product A',
    sourceCurrencyPrice: 5000,
    sourceFiatPriceTwd: null,
    sourceEscortOptionCode: 'PVE-BASIC',
    status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
    ...overrides,
  };
}

function createMockRepo(): EscortDispatchOrderRepo {
  return {
    save: vi.fn(),
    update: vi.fn(),
    findByOrderNumber: vi.fn(),
    findBySourceIdentity: vi.fn(),
    findRecentByGuildId: vi.fn(),
    findPendingAssignmentByGuildId: vi.fn(),
    assignEscort: vi.fn(),
    claimAfterSales: vi.fn(),
    closeAfterSales: vi.fn(),
    confirmOrder: vi.fn(),
    existsByOrderNumber: vi.fn(),
    countActiveByGuildId: vi.fn(),
    batchTimeoutCompletion: vi.fn(),
  };
}

describe('EscortDispatchHandoffService', () => {
  let repo: ReturnType<typeof createMockRepo>;
  let service: EscortDispatchHandoffService;

  beforeEach(() => {
    repo = createMockRepo();
    service = new EscortDispatchHandoffService(repo);
  });

  // ============================================================
  // handoffFromCurrencyPurchase
  // ============================================================
  describe('handoffFromCurrencyPurchase', () => {
    it('should create an auto-handoff order successfully', async () => {
      vi.spyOn(repo, 'findBySourceIdentity').mockResolvedValue(null);
      vi.spyOn(repo, 'existsByOrderNumber').mockResolvedValue(false);
      vi.spyOn(repo, 'save').mockResolvedValue(makeOrder());

      const result = await service.handoffFromCurrencyPurchase(
        GUILD_ID,
        BUYER_USER_ID,
        BASE_PRODUCT,
        SOURCE_REFERENCE,
      );

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        const order = result.getValue();
        expect(order.sourceType).toBe(SourceType.CURRENCY_PURCHASE);
        expect(order.sourceReference).toBe(SOURCE_REFERENCE);
        expect(order.customerUserId).toBe(BUYER_USER_ID);
        expect(order.assignedByUserId).toBe(0);
        expect(order.escortUserId).toBe(0);
      }
    });

    it('should be idempotent — return existing order when source identity matches', async () => {
      const existingOrder = makeOrder();
      vi.spyOn(repo, 'findBySourceIdentity').mockResolvedValue(existingOrder);

      const result = await service.handoffFromCurrencyPurchase(
        GUILD_ID,
        BUYER_USER_ID,
        BASE_PRODUCT,
        SOURCE_REFERENCE,
      );

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue()).toBe(existingOrder);
      }
      expect(repo.save).not.toHaveBeenCalled();
    });

    it('should fail when product is null', async () => {
      const result = await service.handoffFromCurrencyPurchase(
        GUILD_ID,
        BUYER_USER_ID,
        null,
        SOURCE_REFERENCE,
      );

      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().message).toContain('找不到該商品');
      }
    });

    it('should fail when auto-create is disabled', async () => {
      const disabledProduct: HandoffProductSnapshot = {
        ...BASE_PRODUCT,
        shouldAutoCreateEscortOrder: false,
      };

      const result = await service.handoffFromCurrencyPurchase(
        GUILD_ID,
        BUYER_USER_ID,
        disabledProduct,
        SOURCE_REFERENCE,
      );

      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().message).toContain('尚未啟用自動護航開單');
      }
    });

    it('should fail when sourceReference is empty', async () => {
      const result = await service.handoffFromCurrencyPurchase(
        GUILD_ID,
        BUYER_USER_ID,
        BASE_PRODUCT,
        '',
      );

      expect(result.isErr()).toBe(true);
    });

    it('should fail when escortOptionCode is empty', async () => {
      const badProduct: HandoffProductSnapshot = { ...BASE_PRODUCT, escortOptionCode: '' };

      const result = await service.handoffFromCurrencyPurchase(
        GUILD_ID,
        BUYER_USER_ID,
        badProduct,
        SOURCE_REFERENCE,
      );

      expect(result.isErr()).toBe(true);
    });

    it('should return persistence error on DB save failure with fallback check', async () => {
      vi.spyOn(repo, 'findBySourceIdentity').mockResolvedValue(null);
      vi.spyOn(repo, 'existsByOrderNumber').mockResolvedValue(false);
      vi.spyOn(repo, 'save').mockRejectedValue(new Error('DB deadlock'));

      const result = await service.handoffFromCurrencyPurchase(
        GUILD_ID,
        BUYER_USER_ID,
        BASE_PRODUCT,
        SOURCE_REFERENCE,
      );

      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().message).toContain('建立護航交接失敗');
      }
    });

    it('should recover from race condition via fallback query', async () => {
      vi.spyOn(repo, 'findBySourceIdentity')
        .mockResolvedValueOnce(null) // first check: nothing
        .mockResolvedValueOnce(makeOrder()); // fallback query: found
      vi.spyOn(repo, 'existsByOrderNumber').mockResolvedValue(false);
      vi.spyOn(repo, 'save').mockImplementation(async () => {
        throw new Error('duplicate key');
      });

      const result = await service.handoffFromCurrencyPurchase(
        GUILD_ID,
        BUYER_USER_ID,
        BASE_PRODUCT,
        SOURCE_REFERENCE,
      );

      // Fallback recovers and returns the existing order
      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().sourceReference).toBe(SOURCE_REFERENCE);
      }
    });
  });

  // ============================================================
  // handoffFromFiatPayment
  // ============================================================
  describe('handoffFromFiatPayment', () => {
    it('should create an auto-handoff order with FIAT_PAYMENT source', async () => {
      const fiatProduct: HandoffProductSnapshot = {
        ...BASE_PRODUCT,
        currencyPrice: null,
        fiatPriceTwd: 1500,
      };

      vi.spyOn(repo, 'findBySourceIdentity').mockResolvedValue(null);
      vi.spyOn(repo, 'existsByOrderNumber').mockResolvedValue(false);
      vi.spyOn(repo, 'save').mockResolvedValue(makeOrder({ sourceType: SourceType.FIAT_PAYMENT }));

      const result = await service.handoffFromFiatPayment(
        GUILD_ID,
        BUYER_USER_ID,
        fiatProduct,
        SOURCE_REFERENCE,
      );

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().sourceType).toBe(SourceType.FIAT_PAYMENT);
      }
    });

    it('should fail when product is null', async () => {
      const result = await service.handoffFromFiatPayment(
        GUILD_ID,
        BUYER_USER_ID,
        null,
        SOURCE_REFERENCE,
      );

      expect(result.isErr()).toBe(true);
    });
  });
});
