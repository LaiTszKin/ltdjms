import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Ok, Err, DomainError } from '@ltdjms/shared';
import type { DiscordRuntimeGateway, TokenMap } from '@ltdjms/shared';
import { EscortDispatchOrderService } from '../../service/escort-dispatch-order.service.js';
import { EscortDispatchOrderNumberGenerator } from '../../domain/order-number-generator.js';
import {
  EscortDispatchOrderStatus,
  SourceType,
  createPending,
  withCompleted,
  isPendingCustomerConfirmation,
  CUSTOMER_CONFIRM_TIMEOUT_MS,
} from '../../domain/escort-dispatch-order.js';
import type { EscortDispatchOrder } from '../../domain/escort-dispatch-order.js';
import type { EscortDispatchOrderRepo } from '../../repo/escort-dispatch-order.repo.js';
import type { EscortOptionCatalogRepository } from '../../repo/escort-option-catalog.repo.js';
import type { DispatchAfterSalesStaffService } from '../../service/dispatch-after-sales-staff.service.js';
import type { DispatchNotificationService } from '../../notification/DispatchNotificationService.js';

// ---- Shared test data ----
const GUILD_ID = 100;
const ASSIGNED_BY = 200;
const ESCORT_USER_ID = 300;
const CUSTOMER_USER_ID = 400;
const ORDER_NUMBER = 'ESC-20260521-ABC123';
const ANOTHER_NUMBER = 'ESC-20260521-DEF456';

function makeOrder(overrides?: Partial<EscortDispatchOrder>): EscortDispatchOrder {
  const result = createPending(
    ORDER_NUMBER,
    GUILD_ID,
    ASSIGNED_BY,
    ESCORT_USER_ID,
    CUSTOMER_USER_ID,
  );
  return { ...result.getValue(), id: 1, ...overrides };
}

function makeOrderWithCompletionRequestedAt(
  status: EscortDispatchOrderStatus,
  completionRequestedAt: Date,
): EscortDispatchOrder {
  const order = makeOrderFromStatus(status);
  return { ...order, completionRequestedAt };
}

function makeOrderFromStatus(status: EscortDispatchOrderStatus): EscortDispatchOrder {
  const base = makeOrder();
  switch (status) {
    case EscortDispatchOrderStatus.PENDING_CONFIRMATION:
      return base;
    case EscortDispatchOrderStatus.CONFIRMED:
      return { ...base, status, confirmedAt: new Date() };
    case EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION:
      return { ...base, status, confirmedAt: new Date(), completionRequestedAt: new Date() };
    case EscortDispatchOrderStatus.COMPLETED:
      return { ...base, status, completedAt: new Date() };
    case EscortDispatchOrderStatus.AFTER_SALES_REQUESTED:
      return { ...base, status, afterSalesRequestedAt: new Date() };
    case EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS:
      return {
        ...base,
        status,
        afterSalesRequestedAt: new Date(),
        afterSalesAssigneeUserId: 500,
        afterSalesAssignedAt: new Date(),
      };
    case EscortDispatchOrderStatus.AFTER_SALES_CLOSED:
      return {
        ...base,
        status,
        afterSalesRequestedAt: new Date(),
        afterSalesAssigneeUserId: 500,
        afterSalesAssignedAt: new Date(),
        afterSalesClosedAt: new Date(),
      };
    default:
      return base;
  }
}

// Fixed clock for deterministic testing
const FIXED_DATE = new Date('2026-05-21T12:00:00Z');
const FIXED_CLOCK = () => FIXED_DATE.getTime();

// ---- Mock types ----
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

function createMockGateway(): DiscordRuntimeGateway {
  return {
    retrieveMemberById: vi.fn(),
    sendDM: vi.fn(),
    isMemberOnline: vi.fn(),
    findGuild: vi.fn(),
    findGuildChannel: vi.fn(),
    findThreadChannel: vi.fn(),
    isReady: vi.fn(),
    publishReady: vi.fn(),
    requireReadyClient: vi.fn(),
    selfUserId: vi.fn(),
  };
}

function createMockCatalogRepo(): EscortOptionCatalogRepository {
  return {
    findAll: vi.fn().mockResolvedValue([]),
    findByCode: vi.fn().mockResolvedValue(null),
    existsByCode: vi.fn().mockResolvedValue(true),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  };
}

describe('EscortDispatchOrderService', () => {
  let repo: ReturnType<typeof createMockRepo>;
  let gateway: ReturnType<typeof createMockGateway>;
  let service: EscortDispatchOrderService;

  let mockCatalogRepo: EscortOptionCatalogRepository;

  beforeEach(() => {
    repo = createMockRepo();
    gateway = createMockGateway();
    mockCatalogRepo = createMockCatalogRepo();
    // Gateway member lookup must succeed by default, otherwise all create* methods fail
    vi.mocked(gateway.retrieveMemberById).mockResolvedValue({} as never);
    service = new EscortDispatchOrderService(
      repo,
      mockCatalogRepo,
      undefined, // orderNumberGenerator (default)
      FIXED_CLOCK,
      undefined, // afterSalesStaffService
      undefined, // logger
      undefined, // notificationService
      gateway,
    );
  });

  // ============================================================
  // createOrder
  // ============================================================
  describe('createOrder', () => {
    it('should create an order successfully', async () => {
      vi.spyOn(repo, 'existsByOrderNumber').mockResolvedValue(false);
      const savedOrder = makeOrder();
      vi.spyOn(repo, 'save').mockResolvedValue(savedOrder);

      const result = await service.createOrder(
        GUILD_ID,
        ASSIGNED_BY,
        ESCORT_USER_ID,
        CUSTOMER_USER_ID,
      );

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().orderNumber).toMatch(/^ESC-\d{8}-[A-Z0-9]{6}$/);
        expect(result.getValue().status).toBe(EscortDispatchOrderStatus.PENDING_CONFIRMATION);
      }
    });

    it('should fail when escort and customer are the same', async () => {
      const result = await service.createOrder(GUILD_ID, ASSIGNED_BY, 400, 400);

      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().message).toContain('護航者與客戶不能是同一人');
      }
    });

    it('should fail when customer does not exist in guild (gateway check)', async () => {
      vi.spyOn(gateway, 'retrieveMemberById').mockResolvedValue(false);

      const result = await service.createOrder(
        GUILD_ID,
        ASSIGNED_BY,
        ESCORT_USER_ID,
        CUSTOMER_USER_ID,
      );

      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().message).toContain('找不到指定客戶');
      }
    });

    it('should proceed when gateway is not configured', async () => {
      // Service without gateway
      const noGatewayService = new EscortDispatchOrderService(
        repo,
        mockCatalogRepo,
        undefined,
        FIXED_CLOCK,
        undefined,
        undefined,
        undefined,
        undefined,
      );
      vi.spyOn(repo, 'existsByOrderNumber').mockResolvedValue(false);
      vi.spyOn(repo, 'save').mockResolvedValue(makeOrder());

      const result = await noGatewayService.createOrder(
        GUILD_ID,
        ASSIGNED_BY,
        ESCORT_USER_ID,
        CUSTOMER_USER_ID,
      );
      expect(result.isOk()).toBe(true);
    });

    it('should return persistence error when DB save fails', async () => {
      vi.spyOn(repo, 'existsByOrderNumber').mockResolvedValue(false);
      vi.spyOn(repo, 'save').mockRejectedValue(new Error('DB connection error'));

      const result = await service.createOrder(
        GUILD_ID,
        ASSIGNED_BY,
        ESCORT_USER_ID,
        CUSTOMER_USER_ID,
      );

      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().category).toBe('PERSISTENCE_FAILURE');
      }
    });
  });

  // ============================================================
  // createManualOpenOrder
  // ============================================================
  describe('createManualOpenOrder', () => {
    const mockCatalogRepo: EscortOptionCatalogRepository = {
      findAll: vi
        .fn()
        .mockResolvedValue([
          {
            code: 'PVE-BASIC',
            type: 'PVE',
            level: 'BASIC',
            mapScope: 'ANY',
            target: 'Normal',
            priceTwd: 1000,
          },
        ]),
      findByCode: vi.fn().mockResolvedValue(null),
      existsByCode: vi.fn().mockResolvedValue(true),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    };

    function createServiceWithCatalog(): EscortDispatchOrderService {
      return new EscortDispatchOrderService(
        repo,
        mockCatalogRepo,
        undefined,
        FIXED_CLOCK,
        undefined,
        undefined,
        undefined,
        gateway,
      );
    }

    it('should create a manual open order with unassigned escort', async () => {
      vi.spyOn(repo, 'existsByOrderNumber').mockResolvedValue(false);
      vi.spyOn(repo, 'save').mockResolvedValue(makeOrder({ escortUserId: 0 }));

      const svc = createServiceWithCatalog();
      const result = await svc.createManualOpenOrder(
        GUILD_ID,
        ASSIGNED_BY,
        CUSTOMER_USER_ID,
        'PVE-BASIC',
      );

      expect(result.isOk()).toBe(true);
    });

    it('should fail when customerUserId <= 0', async () => {
      const result = await service.createManualOpenOrder(GUILD_ID, ASSIGNED_BY, 0, 'PVE-BASIC');
      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().message).toContain('請選擇客戶');
      }
    });

    it('should fail when escortOptionCode is empty', async () => {
      const result = await service.createManualOpenOrder(
        GUILD_ID,
        ASSIGNED_BY,
        CUSTOMER_USER_ID,
        '',
      );
      expect(result.isErr()).toBe(true);
    });

    it('should fail when option code is invalid', async () => {
      const svc = createServiceWithCatalog();
      // existsByCode returns false
      vi.spyOn(mockCatalogRepo, 'existsByCode').mockResolvedValueOnce(false);

      const result = await svc.createManualOpenOrder(
        GUILD_ID,
        ASSIGNED_BY,
        CUSTOMER_USER_ID,
        'INVALID',
      );

      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().message).toContain('護航品類無效');
      }
    });
  });

  // ============================================================
  // assignPendingOrder
  // ============================================================
  describe('assignPendingOrder', () => {
    it('should assign an unassigned pending order', async () => {
      const unassigned = makeOrder({ escortUserId: 0 });
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(unassigned);
      const assigned = makeOrder({ escortUserId: 350, assignedByUserId: ASSIGNED_BY });
      vi.spyOn(repo, 'assignEscort').mockResolvedValue(assigned);

      const result = await service.assignPendingOrder(ORDER_NUMBER, ASSIGNED_BY, 350);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().escortUserId).toBe(350);
      }
    });

    it('should fail when order already has an escort', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(makeOrder());

      const result = await service.assignPendingOrder(ORDER_NUMBER, ASSIGNED_BY, 350);
      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().message).toContain('此訂單已派發');
      }
    });

    it('should fail when order is not pending confirmation', async () => {
      const confirmed = makeOrderFromStatus(EscortDispatchOrderStatus.CONFIRMED);
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(confirmed);

      const result = await service.assignPendingOrder(ORDER_NUMBER, ASSIGNED_BY, 350);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when escortUserId <= 0', async () => {
      const unassigned = makeOrder({ escortUserId: 0 });
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(unassigned);

      const result = await service.assignPendingOrder(ORDER_NUMBER, ASSIGNED_BY, 0);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when escort equals customer', async () => {
      const unassigned = makeOrder({ escortUserId: 0 });
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(unassigned);

      const result = await service.assignPendingOrder(ORDER_NUMBER, ASSIGNED_BY, CUSTOMER_USER_ID);
      expect(result.isErr()).toBe(true);
    });

    it('should return "not found" for invalid order number', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(null);

      const result = await service.assignPendingOrder(ORDER_NUMBER, ASSIGNED_BY, 350);
      expect(result.isErr()).toBe(true);
    });
  });

  // ============================================================
  // confirmOrder
  // ============================================================
  describe('confirmOrder', () => {
    it('should confirm order by the assigned escort', async () => {
      const pending = makeOrder();
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(pending);
      const confirmed = makeOrderFromStatus(EscortDispatchOrderStatus.CONFIRMED);
      vi.spyOn(repo, 'confirmOrder').mockResolvedValue(confirmed);

      const result = await service.confirmOrder(ORDER_NUMBER, ESCORT_USER_ID);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().status).toBe(EscortDispatchOrderStatus.CONFIRMED);
      }
    });

    it('should fail when wrong user tries to confirm', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(makeOrder());

      const result = await service.confirmOrder(ORDER_NUMBER, 999);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when order is already confirmed', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(
        makeOrderFromStatus(EscortDispatchOrderStatus.CONFIRMED),
      );

      const result = await service.confirmOrder(ORDER_NUMBER, ESCORT_USER_ID);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when order not found', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(null);

      const result = await service.confirmOrder(ORDER_NUMBER, ESCORT_USER_ID);
      expect(result.isErr()).toBe(true);
    });
  });

  // ============================================================
  // requestCompletion
  // ============================================================
  describe('requestCompletion', () => {
    it('should request completion from CONFIRMED state', async () => {
      const confirmed = makeOrderFromStatus(EscortDispatchOrderStatus.CONFIRMED);
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(confirmed);
      const pendingCustomer = makeOrderFromStatus(
        EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
      );
      vi.spyOn(repo, 'update').mockResolvedValue(pendingCustomer);

      const result = await service.requestCompletion(ORDER_NUMBER, ESCORT_USER_ID);

      expect(result.isOk()).toBe(true);
    });

    it('should fail when wrong user requests completion', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(
        makeOrderFromStatus(EscortDispatchOrderStatus.CONFIRMED),
      );

      const result = await service.requestCompletion(ORDER_NUMBER, 999);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when order is not in CONFIRMED state', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(makeOrder());

      const result = await service.requestCompletion(ORDER_NUMBER, ESCORT_USER_ID);
      expect(result.isErr()).toBe(true);
    });
  });

  // ============================================================
  // customerConfirmCompletion
  // ============================================================
  describe('customerConfirmCompletion', () => {
    it('should confirm completion from PENDING_CUSTOMER_CONFIRMATION', async () => {
      const pending = makeOrderFromStatus(EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION);
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(pending);
      const completed = makeOrderFromStatus(EscortDispatchOrderStatus.COMPLETED);
      vi.spyOn(repo, 'update').mockResolvedValue(completed);

      const result = await service.customerConfirmCompletion(ORDER_NUMBER, CUSTOMER_USER_ID);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().status).toBe(EscortDispatchOrderStatus.COMPLETED);
      }
    });

    it('should succeed (idempotent) when order is already completed', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(
        makeOrderFromStatus(EscortDispatchOrderStatus.COMPLETED),
      );

      const result = await service.customerConfirmCompletion(ORDER_NUMBER, CUSTOMER_USER_ID);

      expect(result.isOk()).toBe(true);
    });

    it('should fail when wrong user tries to confirm completion', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(
        makeOrderFromStatus(EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION),
      );

      const result = await service.customerConfirmCompletion(ORDER_NUMBER, ESCORT_USER_ID);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when order is not in applicable state', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(makeOrder());

      const result = await service.customerConfirmCompletion(ORDER_NUMBER, CUSTOMER_USER_ID);
      expect(result.isErr()).toBe(true);
    });
  });

  // ============================================================
  // requestAfterSales
  // ============================================================
  describe('requestAfterSales', () => {
    it('should request after-sales from COMPLETED state', async () => {
      const completed = makeOrderFromStatus(EscortDispatchOrderStatus.COMPLETED);
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(completed);
      const afterSales = makeOrderFromStatus(EscortDispatchOrderStatus.AFTER_SALES_REQUESTED);
      vi.spyOn(repo, 'update').mockResolvedValue(afterSales);

      const result = await service.requestAfterSales(ORDER_NUMBER, CUSTOMER_USER_ID);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().status).toBe(EscortDispatchOrderStatus.AFTER_SALES_REQUESTED);
      }
    });

    it('should reject after-sales when already in after-sales flow', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(
        makeOrderFromStatus(EscortDispatchOrderStatus.AFTER_SALES_REQUESTED),
      );

      const result = await service.requestAfterSales(ORDER_NUMBER, CUSTOMER_USER_ID);
      expect(result.isErr()).toBe(true);
    });

    it('should reject after-sales when order is not COMPLETED or PENDING_CUSTOMER_CONFIRMATION', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(makeOrder());

      const result = await service.requestAfterSales(ORDER_NUMBER, CUSTOMER_USER_ID);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when wrong user requests after-sales', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(
        makeOrderFromStatus(EscortDispatchOrderStatus.COMPLETED),
      );

      const result = await service.requestAfterSales(ORDER_NUMBER, ESCORT_USER_ID);
      expect(result.isErr()).toBe(true);
    });
  });

  // ============================================================
  // claimAfterSales
  // ============================================================
  describe('claimAfterSales', () => {
    const afterSalesStaffService = {
      repository: {} as any,
      getStaffUserIds: vi.fn(),
      addStaff: vi.fn(),
      removeStaff: vi.fn(),
      isAfterSalesStaff: vi.fn(),
    } as unknown as DispatchAfterSalesStaffService;

    function createServiceWithStaff(): EscortDispatchOrderService {
      return new EscortDispatchOrderService(
        repo,
        mockCatalogRepo,
        undefined,
        FIXED_CLOCK,
        afterSalesStaffService,
        undefined,
        undefined,
        gateway,
      );
    }

    it('should claim after-sales order by authorized staff', async () => {
      const requested = makeOrderFromStatus(EscortDispatchOrderStatus.AFTER_SALES_REQUESTED);
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(requested);
      vi.spyOn(afterSalesStaffService, 'isAfterSalesStaff').mockResolvedValue(true);
      const inProgress = makeOrderFromStatus(EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS);
      vi.spyOn(repo, 'claimAfterSales').mockResolvedValue(inProgress);

      const svc = createServiceWithStaff();
      const result = await svc.claimAfterSales(ORDER_NUMBER, 500);

      expect(result.isOk()).toBe(true);
    });

    it('should fail when user is not after-sales staff', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(
        makeOrderFromStatus(EscortDispatchOrderStatus.AFTER_SALES_REQUESTED),
      );
      vi.spyOn(afterSalesStaffService, 'isAfterSalesStaff').mockResolvedValue(false);

      const svc = createServiceWithStaff();
      const result = await svc.claimAfterSales(ORDER_NUMBER, 500);

      expect(result.isErr()).toBe(true);
    });

    it('should fail when order is not in AFTER_SALES_REQUESTED', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(makeOrder());
      vi.spyOn(afterSalesStaffService, 'isAfterSalesStaff').mockResolvedValue(true);

      const svc = createServiceWithStaff();
      const result = await svc.claimAfterSales(ORDER_NUMBER, 500);

      expect(result.isErr()).toBe(true);
    });

    it('should report "already claimed by me" when re-claiming same order', async () => {
      const requested = makeOrderFromStatus(EscortDispatchOrderStatus.AFTER_SALES_REQUESTED);
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(requested);
      vi.spyOn(afterSalesStaffService, 'isAfterSalesStaff').mockResolvedValue(true);
      // claimAfterSales returns null (optimistic lock failed)
      vi.spyOn(repo, 'claimAfterSales').mockResolvedValue(null);
      // Re-query shows already claimed by same user
      const inProgress = {
        ...makeOrderFromStatus(EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS),
        afterSalesAssigneeUserId: 500,
      };
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(inProgress);

      const svc = createServiceWithStaff();
      const result = await svc.claimAfterSales(ORDER_NUMBER, 500);

      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().message).toContain('你已接手此售後案件');
      }
    });
  });

  // ============================================================
  // closeAfterSales
  // ============================================================
  describe('closeAfterSales', () => {
    it('should close after-sales by the assignee', async () => {
      const inProgress = makeOrderFromStatus(EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS);
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(inProgress);
      const closed = makeOrderFromStatus(EscortDispatchOrderStatus.AFTER_SALES_CLOSED);
      vi.spyOn(repo, 'closeAfterSales').mockResolvedValue(closed);

      const result = await service.closeAfterSales(ORDER_NUMBER, 500);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().status).toBe(EscortDispatchOrderStatus.AFTER_SALES_CLOSED);
      }
    });

    it('should fail when order is not in AFTER_SALES_IN_PROGRESS', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(makeOrder());

      const result = await service.closeAfterSales(ORDER_NUMBER, 500);
      expect(result.isErr()).toBe(true);
    });

    it('should fail when wrong user tries to close', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(
        makeOrderFromStatus(EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS),
      );

      const result = await service.closeAfterSales(ORDER_NUMBER, 999);
      expect(result.isErr()).toBe(true);
    });
  });

  // ============================================================
  // findRecentOrders
  // ============================================================
  describe('findRecentOrders', () => {
    it('should return recent orders', async () => {
      vi.spyOn(repo, 'batchTimeoutCompletion').mockResolvedValue([]);
      vi.spyOn(repo, 'findRecentByGuildId').mockResolvedValue([makeOrder()]);

      const result = await service.findRecentOrders(GUILD_ID);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue()).toHaveLength(1);
      }
    });

    it('should enforce max limit of 20', async () => {
      vi.spyOn(repo, 'batchTimeoutCompletion').mockResolvedValue([]);
      vi.spyOn(repo, 'findRecentByGuildId').mockResolvedValue([]);

      await service.findRecentOrders(GUILD_ID, 100);

      expect(repo.findRecentByGuildId).toHaveBeenCalledWith(GUILD_ID, 20);
    });

    it('should normalize limit of 0 to default', async () => {
      vi.spyOn(repo, 'batchTimeoutCompletion').mockResolvedValue([]);
      vi.spyOn(repo, 'findRecentByGuildId').mockResolvedValue([]);

      await service.findRecentOrders(GUILD_ID, 0);

      expect(repo.findRecentByGuildId).toHaveBeenCalledWith(GUILD_ID, 10);
    });

    it('should return empty array on DB failure with warning', async () => {
      vi.spyOn(repo, 'batchTimeoutCompletion').mockRejectedValue(new Error('DB error'));

      const result = await service.findRecentOrders(GUILD_ID);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue()).toEqual([]);
      }
    });
  });

  // ============================================================
  // findPendingAssignmentOrders
  // ============================================================
  describe('findPendingAssignmentOrders', () => {
    it('should return pending assignment orders', async () => {
      vi.spyOn(repo, 'findPendingAssignmentByGuildId').mockResolvedValue([
        makeOrder({ escortUserId: 0 }),
      ]);

      const result = await service.findPendingAssignmentOrders(GUILD_ID);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue()).toHaveLength(1);
      }
    });

    it('should enforce max limit of 25', async () => {
      vi.spyOn(repo, 'findPendingAssignmentByGuildId').mockResolvedValue([]);

      await service.findPendingAssignmentOrders(GUILD_ID, 100);

      expect(repo.findPendingAssignmentByGuildId).toHaveBeenCalledWith(GUILD_ID, 25);
    });
  });

  // ============================================================
  // countActiveOrders
  // ============================================================
  describe('countActiveOrders', () => {
    it('should return active order count', async () => {
      vi.spyOn(repo, 'countActiveByGuildId').mockResolvedValue(5);

      const result = await service.countActiveOrders(GUILD_ID);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue()).toBe(5);
      }
    });
  });

  // ============================================================
  // findByOrderNumber
  // ============================================================
  describe('findByOrderNumber', () => {
    it('should find an order by number', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(makeOrder());

      const result = await service.findByOrderNumber(ORDER_NUMBER);

      expect(result.isOk()).toBe(true);
    });

    it('should return error when order not found', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(null);

      const result = await service.findByOrderNumber(ORDER_NUMBER);

      expect(result.isErr()).toBe(true);
    });

    it('should return error for blank order number', async () => {
      const result = await service.findByOrderNumber('');
      expect(result.isErr()).toBe(true);
    });

    it('should normalize order number to uppercase', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(makeOrder());

      await service.findByOrderNumber('esc-20260521-abc123');

      expect(repo.findByOrderNumber).toHaveBeenCalledWith('ESC-20260521-ABC123');
    });
  });

  // ============================================================
  // Timeout auto-completion
  // ============================================================
  describe('timeout auto-completion', () => {
    it('should auto-complete timed-out order when queried', async () => {
      const oldRequestedAt = new Date(FIXED_DATE.getTime() - CUSTOMER_CONFIRM_TIMEOUT_MS - 5000);
      const timedOut = makeOrderWithCompletionRequestedAt(
        EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
        oldRequestedAt,
      );
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(timedOut);
      const completed = {
        ...timedOut,
        status: EscortDispatchOrderStatus.COMPLETED,
        completedAt: FIXED_DATE,
      };
      vi.spyOn(repo, 'update').mockResolvedValue(completed);

      const result = await service.findByOrderNumber(ORDER_NUMBER);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().status).toBe(EscortDispatchOrderStatus.COMPLETED);
      }
      expect(repo.update).toHaveBeenCalled();
    });

    it('should NOT auto-complete order within timeout window', async () => {
      const recentRequestedAt = new Date(FIXED_DATE.getTime() - 1000); // 1 second ago
      const notTimedOut = makeOrderWithCompletionRequestedAt(
        EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
        recentRequestedAt,
      );
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(notTimedOut);

      const result = await service.findByOrderNumber(ORDER_NUMBER);

      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().status).toBe(
          EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
        );
      }
      expect(repo.update).not.toHaveBeenCalled();
    });

    it('should not auto-complete non-PENDING_CUSTOMER_CONFIRMATION orders', async () => {
      vi.spyOn(repo, 'findByOrderNumber').mockResolvedValue(makeOrder()); // PENDING_CONFIRMATION

      const result = await service.findByOrderNumber(ORDER_NUMBER);

      expect(result.isOk()).toBe(true);
      expect(repo.update).not.toHaveBeenCalled();
    });
  });
});
