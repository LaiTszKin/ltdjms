import type { Result, TokenMap } from '@ltdjms/shared';
import { Ok, Err, DomainError } from '@ltdjms/shared';

import type { EscortDispatchOrderRepo } from '../repo/escort-dispatch-order.repo.js';
import { EscortDispatchOrderNumberGenerator, generateUniqueOrderNumber } from '../domain/order-number-generator.js';
import type { EscortOptionCatalogRepository } from './escort-option-pricing.service.js';
import { type DispatchAfterSalesStaffService } from './dispatch-after-sales-staff.service.js';
import type { DispatchNotificationService } from '../notification/DispatchNotificationService.js';
import {
  type EscortDispatchOrder,
  EscortDispatchOrderStatus,
  createPending,
  createManualOpenOrder,
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
  hasCustomerConfirmationTimedOut,
} from '../domain/index.js';

const MAX_ORDER_NUMBER_RETRIES = 20;
const DEFAULT_HISTORY_LIMIT = 10;
const DEFAULT_PENDING_ASSIGNMENT_LIMIT = 5;
const MAX_HISTORY_LIMIT = 20;
const MAX_PENDING_ASSIGNMENT_LIMIT = 25;

/**
 * 派單護航訂單核心服務。
 * Matches Java EscortDispatchOrderService exactly.
 */
export class EscortDispatchOrderService {
  private readonly orderNumberGenerator: EscortDispatchOrderNumberGenerator;
  private readonly clock: () => number;

  constructor(
    private readonly repository: EscortDispatchOrderRepo,
    orderNumberGenerator?: EscortDispatchOrderNumberGenerator,
    clock?: () => number,
    private readonly catalogRepository?: EscortOptionCatalogRepository,
    private readonly afterSalesStaffService?: DispatchAfterSalesStaffService,
    private readonly logger?: TokenMap['Logger'],
    private readonly notificationService?: DispatchNotificationService,
  ) {
    this.orderNumberGenerator = orderNumberGenerator ?? new EscortDispatchOrderNumberGenerator();
    this.clock = clock ?? (() => Date.now());
  }

  /** 建立待確認的新派單訂單。 */
  async createOrder(
    guildId: number,
    assignedByUserId: number,
    escortUserId: number,
    customerUserId: number,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    if (escortUserId === customerUserId) {
      return new Err(DomainError.invalidInput('護航者與客戶不能是同一人'));
    }

    try {
      const orderNumber = await this.generateUniqueOrderNumber();
      const order = createPending(orderNumber, guildId, assignedByUserId, escortUserId, customerUserId);
      const saved = await this.repository.save(order);
      return new Ok(saved);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('建立派單失敗', err));
    }
  }

  /** 手動建立尚未派發給護航者的護航訂單。 */
  async createManualOpenOrder(
    guildId: number,
    assignedByUserId: number,
    customerUserId: number,
    escortOptionCode: string,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    if (customerUserId <= 0) {
      return new Err(DomainError.invalidInput('請選擇客戶'));
    }

    if (!escortOptionCode || escortOptionCode.trim().length === 0) {
      return new Err(DomainError.invalidInput('護航品類代碼無效'));
    }

    if (this.catalogRepository) {
      const exists = await this.catalogRepository.existsByCode(escortOptionCode.trim().toUpperCase());
      if (!exists) {
        const allCodes = (await this.catalogRepository.findAll())
          .map((c) => c.code)
          .join(', ');
        return new Err(DomainError.invalidInput(`護航品類無效，可用代碼：${allCodes}`));
      }
    }

    try {
      const orderNumber = await this.generateUniqueOrderNumber();
      const order = createManualOpenOrder(
        orderNumber,
        guildId,
        assignedByUserId,
        customerUserId,
        escortOptionCode,
      );
      const saved = await this.repository.save(order);
      return new Ok(saved);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('建立護航訂單失敗', err));
    }
  }

  /** 將既有待派發護航訂單派給指定護航者。 */
  async assignPendingOrder(
    orderNumber: string,
    assignedByUserId: number,
    escortUserId: number,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    const orderResult = await this.findOrder(orderNumber);
    if (orderResult.isErr()) {
      return orderResult;
    }

    const order = orderResult.getValue();
    if (!isPendingEscortConfirmation(order)) {
      return new Err(DomainError.invalidInput('此訂單目前不可派發'));
    }
    if (order.escortUserId !== 0) {
      return new Err(DomainError.invalidInput('此訂單已派發給護航者'));
    }
    if (escortUserId <= 0) {
      return new Err(DomainError.invalidInput('請選擇護航者'));
    }
    if (escortUserId === order.customerUserId) {
      return new Err(DomainError.invalidInput('護航者與客戶不能是同一人'));
    }

    try {
      const assigned = await this.repository.assignEscort(
        order.orderNumber,
        assignedByUserId,
        escortUserId,
        new Date(this.clock!()),
      );
      if (assigned != null) {
        return new Ok(assigned);
      }
      return new Err(DomainError.invalidInput('此訂單已被派發或目前不可派發'));
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('派發護航訂單失敗', err));
    }
  }

  /** 護航者確認接單。 */
  async confirmOrder(
    orderNumber: string,
    confirmerUserId: number,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    const orderResult = await this.findOrder(orderNumber);
    if (orderResult.isErr()) {
      return orderResult;
    }

    const order = orderResult.getValue();
    if (!canBeConfirmedBy(order, confirmerUserId)) {
      return new Err(DomainError.invalidInput('只有被指派的護航者可以確認此訂單'));
    }
    if (!isPendingEscortConfirmation(order)) {
      return new Err(DomainError.invalidInput('此訂單已確認'));
    }

    try {
      const confirmedAt = new Date(this.clock!());
      const updated = await this.repository.confirmOrder(
        order.orderNumber,
        confirmerUserId,
        confirmedAt,
      );
      if (updated != null) {
        return new Ok(updated);
      }
      return new Err(DomainError.invalidInput('此訂單已被確認或目前不可確認'));
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('確認訂單失敗', err));
    }
  }

  /** 護航者完成服務，等待客戶確認。 */
  async requestCompletion(
    orderNumber: string,
    escortUserId: number,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    const orderResult = await this.findOrder(orderNumber);
    if (orderResult.isErr()) {
      return orderResult;
    }

    const order = orderResult.getValue();
    if (!canBeCompletedByEscort(order, escortUserId)) {
      return new Err(DomainError.invalidInput('只有被指派的護航者可以送出完成'));
    }
    if (!isConfirmed(order)) {
      return new Err(DomainError.invalidInput('此訂單目前不可送出完成'));
    }

    try {
      const updated = await this.repository.update(
        withCompletionRequested(order, new Date(this.clock!())),
        EscortDispatchOrderStatus.CONFIRMED,
      );
      return new Ok(updated);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('送出完成失敗', err));
    }
  }

  /** 客戶確認完成。 */
  async customerConfirmCompletion(
    orderNumber: string,
    customerUserId: number,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    const orderResult = await this.findOrder(orderNumber);
    if (orderResult.isErr()) {
      return orderResult;
    }

    const order = orderResult.getValue();
    if (!canBeConfirmedByCustomer(order, customerUserId)) {
      return new Err(DomainError.invalidInput('只有訂單客戶可以確認完成'));
    }
    if (isCompleted(order)) {
      return new Ok(order);
    }
    if (!isPendingCustomerConfirmation(order)) {
      return new Err(DomainError.invalidInput('此訂單目前不可由客戶確認完成'));
    }

    try {
      const normalized = await this.ensureTimeoutCompletion(order);
      if (isCompleted(normalized)) {
        return new Ok(normalized);
      }

      const updated = await this.repository.update(
        withCompleted(normalized, new Date(this.clock!())),
        EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
      );
      return new Ok(updated);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('客戶確認完成失敗', err));
    }
  }

  /** 客戶提出售後申請。 */
  async requestAfterSales(
    orderNumber: string,
    customerUserId: number,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    const orderResult = await this.findOrder(orderNumber);
    if (orderResult.isErr()) {
      return orderResult;
    }

    const order = orderResult.getValue();
    if (!canBeConfirmedByCustomer(order, customerUserId)) {
      return new Err(DomainError.invalidInput('只有訂單客戶可以申請售後'));
    }

    try {
      const normalized = await this.ensureTimeoutCompletion(order);

      if (
        normalized.status === EscortDispatchOrderStatus.AFTER_SALES_REQUESTED ||
        normalized.status === EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS ||
        normalized.status === EscortDispatchOrderStatus.AFTER_SALES_CLOSED
      ) {
        return new Err(DomainError.invalidInput('此訂單已在售後流程中'));
      }

      if (
        normalized.status !== EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION &&
        normalized.status !== EscortDispatchOrderStatus.COMPLETED
      ) {
        return new Err(DomainError.invalidInput('此訂單目前不可申請售後'));
      }

      const expectedStatus =
        normalized.status === EscortDispatchOrderStatus.COMPLETED
          ? EscortDispatchOrderStatus.COMPLETED
          : EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION;
      const updated = await this.repository.update(
        withAfterSalesRequested(normalized, new Date(this.clock!())),
        expectedStatus,
      );
      return new Ok(updated);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('申請售後失敗', err));
    }
  }

  /** 售後人員接手案件。 */
  async claimAfterSales(
    orderNumber: string,
    afterSalesUserId: number,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    const orderResult = await this.findOrder(orderNumber);
    if (orderResult.isErr()) {
      return orderResult;
    }

    const order = orderResult.getValue();

    // R8.1: Verify user is an after-sales staff member
    if (this.afterSalesStaffService) {
      const isStaff = await this.afterSalesStaffService.isAfterSalesStaff(order.guildId, afterSalesUserId);
      if (!isStaff) {
        return new Err(DomainError.invalidInput('你不是售後人員，無法接手售後案件'));
      }
    }

    if (!isAfterSalesRequested(order)) {
      if (isAfterSalesInProgress(order)) {
        if (isAfterSalesAssignee(order, afterSalesUserId)) {
          return new Err(DomainError.invalidInput('你已接手此售後案件'));
        }
        return new Err(DomainError.invalidInput('此售後案件已由其他售後人員接手'));
      }
      if (order.status === EscortDispatchOrderStatus.AFTER_SALES_CLOSED) {
        return new Err(DomainError.invalidInput('此售後案件已結案'));
      }
      return new Err(DomainError.invalidInput('此訂單目前不可接手售後'));
    }

    try {
      const claimed = await this.repository.claimAfterSales(
        order.orderNumber,
        afterSalesUserId,
        new Date(this.clock!()),
      );
      if (claimed != null) {
        return new Ok(claimed);
      }

      // R8.3: Re-query to distinguish between "already claimed by me" and "claimed by someone else"
      const latest = await this.repository.findByOrderNumber(order.orderNumber);
      if (latest != null && isAfterSalesInProgress(latest)) {
        if (isAfterSalesAssignee(latest, afterSalesUserId)) {
          return new Err(DomainError.invalidInput('你已接手此售後案件'));
        }
        return new Err(DomainError.invalidInput('此售後案件已由其他售後人員接手'));
      }
      return new Err(DomainError.invalidInput('此售後案件目前不可接手'));
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('接手售後案件失敗', err));
    }
  }

  /** 售後人員完成結案。 */
  async closeAfterSales(
    orderNumber: string,
    afterSalesUserId: number,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    const orderResult = await this.findOrder(orderNumber);
    if (orderResult.isErr()) {
      return orderResult;
    }

    const order = orderResult.getValue();
    if (!isAfterSalesInProgress(order)) {
      return new Err(DomainError.invalidInput('此售後案件目前不可結案'));
    }
    if (!isAfterSalesAssignee(order, afterSalesUserId)) {
      return new Err(DomainError.invalidInput('只有接手此案件的售後人員可以結案'));
    }

    try {
      const closed = await this.repository.closeAfterSales(
        order.orderNumber,
        afterSalesUserId,
        new Date(this.clock!()),
      );
      if (closed != null) {
        return new Ok(closed);
      }
      return new Err(DomainError.invalidInput('此售後案件目前不可結案'));
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('售後結案失敗', err));
    }
  }

  /** 查詢最近訂單（預設 10 筆，最多 20 筆）。 */
  async findRecentOrders(
    guildId: number,
    limit?: number,
  ): Promise<Result<EscortDispatchOrder[], DomainError>> {
    const safeLimit = this.normalizeLimit(limit ?? DEFAULT_HISTORY_LIMIT, MAX_HISTORY_LIMIT);
    try {
      const orders = await this.repository.findRecentByGuildId(guildId, safeLimit);
      // NOTE(P3-8): 每個 PENDING_CUSTOMER_CONFIRMATION 訂單獨立執行 UPDATE，
      // 若大量訂單同時逾時會產生 N 次 DB 呼叫。可改為批次 UPDATE
      // （WHERE status = PENDING_CUSTOMER_CONFIRMATION AND confirmedAt < ?），
      // 但需注意 in-memory order 物件與 DB 狀態的同步。
      const normalizedOrders = await Promise.all(
        orders.map(async (o) => {
          if (!isPendingCustomerConfirmation(o)) return o;
          return this.ensureTimeoutCompletion(o);
        }),
      );
      return new Ok(normalizedOrders);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('查詢歷史訂單失敗', err));
    }
  }

  /** 公開查詢單筆訂單。委派給內部 findOrder。 */
  async findByOrderNumber(
    orderNumber: string,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    return this.findOrder(orderNumber);
  }

  /** 查詢尚未指定護航者的自動交接訂單（預設 5 筆，最多 25 筆）。 */
  async findPendingAssignmentOrders(
    guildId: number,
    limit?: number,
  ): Promise<Result<EscortDispatchOrder[], DomainError>> {
    const safeLimit = this.normalizeLimit(limit ?? DEFAULT_PENDING_ASSIGNMENT_LIMIT, MAX_PENDING_ASSIGNMENT_LIMIT);
    try {
      const orders = await this.repository.findPendingAssignmentByGuildId(guildId, safeLimit);
      return new Ok(orders);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('查詢待派單訂單失敗', err));
    }
  }

  /** Counts non-terminal orders for a guild. */
  async countActiveOrders(guildId: number): Promise<Result<number, DomainError>> {
    try {
      const count = await this.repository.countActiveByGuildId(guildId);
      return new Ok(count);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('Failed to count active orders', err));
    }
  }

  // ---- Private Helpers ----

  private async findOrder(
    orderNumber: string,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    if (!orderNumber || orderNumber.trim().length === 0) {
      return new Err(DomainError.invalidInput('訂單編號無效'));
    }

    const normalizedOrderNumber = orderNumber.trim().toUpperCase();
    try {
      const order = await this.repository.findByOrderNumber(normalizedOrderNumber);
      if (order == null) {
        return new Err(DomainError.invalidInput('找不到該訂單'));
      }
      const normalized = await this.ensureTimeoutCompletion(order);
      return new Ok(normalized);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('查詢訂單失敗', err));
    }
  }

  private async ensureTimeoutCompletion(order: EscortDispatchOrder): Promise<EscortDispatchOrder> {
    // Status guard: only auto-complete orders in PENDING_CUSTOMER_CONFIRMATION status
    if (!isPendingCustomerConfirmation(order)) {
      return order;
    }
    if (!hasCustomerConfirmationTimedOut(order, new Date(this.clock!()))) {
      return order;
    }

    try {
      const completed = withCompleted(order, new Date(this.clock!()));
      const updated = await this.repository.update(completed, EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION);

      // P2-2: Notify customer and escort about auto-completion due to timeout
      if (this.notificationService) {
        this.notificationService.notifyCustomerConfirmed(updated)
          .catch((notifyErr) => {
            this.logWarn('Failed to send auto-completion notification', {
              orderNumber: order.orderNumber,
              error: notifyErr instanceof Error ? notifyErr.message : String(notifyErr),
            });
          });
      }

      return updated;
    } catch (e) {
      // If auto-complete persist fails, log warning and return original order (non-blocking)
      const errMsg = e instanceof Error ? e.message : String(e);
      this.logWarn('Failed to persist timeout auto-completion', {
        orderNumber: order.orderNumber,
        error: errMsg,
      });
      return order;
    }
  }

  private logWarn(message: string, data?: Record<string, unknown>): void {
    if (this.logger) {
      this.logger.warn({ ...data, module: 'EscortDispatchOrderService' }, message);
    } else {
      const entry = {
        timestamp: new Date().toISOString(),
        level: 'warn',
        module: 'EscortDispatchOrderService',
        message,
        ...data,
      };
      console.warn(JSON.stringify(entry));
    }
  }

  private normalizeLimit(limit: number, maxLimit: number): number {
    if (limit <= 0) {
      const defaultForMax = maxLimit === MAX_HISTORY_LIMIT ? DEFAULT_HISTORY_LIMIT : DEFAULT_PENDING_ASSIGNMENT_LIMIT;
      return defaultForMax;
    }
    return Math.min(limit, maxLimit);
  }

  private async generateUniqueOrderNumber(): Promise<string> {
    return generateUniqueOrderNumber(
      this.orderNumberGenerator!,
      (orderNumber) => this.repository.existsByOrderNumber(orderNumber),
      MAX_ORDER_NUMBER_RETRIES,
    );
  }
}
