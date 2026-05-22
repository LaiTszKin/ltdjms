/**
 * 派單系統的護航訂單領域模型。
 * 7 狀態機：PENDING_CONFIRMATION -> CONFIRMED -> PENDING_CUSTOMER_CONFIRMATION -> COMPLETED
 *                                                |                                  |
 *                                                └-> AFTER_SALES_REQUESTED <────────┘
 *                                                                  |
 *                                                                  v
 *                                                         AFTER_SALES_IN_PROGRESS
 *                                                                  |
 *                                                                  v
 *                                                         AFTER_SALES_CLOSED
 */

import { ok, err, DomainError, type Result } from '@ltdjms/shared';

/** 客戶確認超時門檻：24 小時（毫秒）。 */
export const CUSTOMER_CONFIRM_TIMEOUT_MS = 24 * 60 * 60 * 1000;

/** 訂單狀態。 */
export enum EscortDispatchOrderStatus {
  /** 已建立，等待護航者確認。 */
  PENDING_CONFIRMATION = 'PENDING_CONFIRMATION',
  /** 護航者已確認接單。 */
  CONFIRMED = 'CONFIRMED',
  /** 護航者已送出完單，等待客戶確認或申請售後。 */
  PENDING_CUSTOMER_CONFIRMATION = 'PENDING_CUSTOMER_CONFIRMATION',
  /** 訂單已完成（客戶確認或超時自動完成）。 */
  COMPLETED = 'COMPLETED',
  /** 客戶已提出售後申請。 */
  AFTER_SALES_REQUESTED = 'AFTER_SALES_REQUESTED',
  /** 售後人員已接手處理。 */
  AFTER_SALES_IN_PROGRESS = 'AFTER_SALES_IN_PROGRESS',
  /** 售後案件已結案。 */
  AFTER_SALES_CLOSED = 'AFTER_SALES_CLOSED',
}

/** 護航訂單來源類型。 */
export enum SourceType {
  MANUAL = 'MANUAL',
  CURRENCY_PURCHASE = 'CURRENCY_PURCHASE',
  FIAT_PAYMENT = 'FIAT_PAYMENT',
}

export interface EscortDispatchOrder {
  readonly id: number | null;
  readonly orderNumber: string;
  readonly guildId: number;
  readonly assignedByUserId: number;
  readonly escortUserId: number;
  readonly customerUserId: number;
  readonly createdAt: Date;
  readonly confirmedAt: Date | null;
  readonly completionRequestedAt: Date | null;
  readonly completedAt: Date | null;
  readonly afterSalesRequestedAt: Date | null;
  readonly afterSalesAssigneeUserId: number | null;
  readonly afterSalesAssignedAt: Date | null;
  readonly afterSalesClosedAt: Date | null;
  readonly updatedAt: Date;
  readonly sourceType: SourceType;
  readonly sourceReference: string | null;
  readonly sourceProductId: number | null;
  readonly sourceProductName: string | null;
  readonly sourceCurrencyPrice: number | null;
  readonly sourceFiatPriceTwd: number | null;
  readonly sourceEscortOptionCode: string | null;
  readonly status: EscortDispatchOrderStatus;
}

// ---- Validation ----

function validateOrder(order: EscortDispatchOrder): string | null {
  if (!order.orderNumber || order.orderNumber.trim().length === 0) {
    return 'orderNumber must not be blank';
  }
  if (order.orderNumber.length > 32) {
    return 'orderNumber must not exceed 32 characters';
  }
  if (order.escortUserId === order.customerUserId) {
    return 'escortUserId and customerUserId must be different';
  }
  const snapshotErr = validateSourceSnapshot(order);
  if (snapshotErr != null) {
    return snapshotErr;
  }
  return validateRequiredTimestamps(order);
}

function validateSourceSnapshot(order: EscortDispatchOrder): string | null {
  if (order.sourceType === SourceType.MANUAL) {
    if (
      order.sourceReference != null ||
      order.sourceProductId != null ||
      order.sourceProductName != null ||
      order.sourceCurrencyPrice != null ||
      order.sourceFiatPriceTwd != null
    ) {
      return 'manual dispatch order must not carry source snapshot';
    }
    return null;
  }
  // Auto-sourced orders require source snapshot
  if (!order.sourceReference || order.sourceReference.trim().length === 0) {
    return 'sourceReference must not be blank';
  }
  if (order.sourceProductId == null) {
    return 'sourceProductId must not be null';
  }
  if (!order.sourceProductName || order.sourceProductName.trim().length === 0) {
    return 'sourceProductName must not be blank';
  }
  if (!order.sourceEscortOptionCode || order.sourceEscortOptionCode.trim().length === 0) {
    return 'sourceEscortOptionCode must not be blank';
  }
  if (order.sourceCurrencyPrice == null && order.sourceFiatPriceTwd == null) {
    return 'source price snapshot must not be empty';
  }
  return null;
}

function validateRequiredTimestamps(order: EscortDispatchOrder): string | null {
  const s = order.status;
  switch (s) {
    case EscortDispatchOrderStatus.PENDING_CONFIRMATION:
      break;
    case EscortDispatchOrderStatus.CONFIRMED: {
      const errMsg = requireField('confirmedAt', order.confirmedAt, s);
      if (errMsg != null) return errMsg;
      break;
    }
    case EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION: {
      let errMsg = requireField('confirmedAt', order.confirmedAt, s);
      if (errMsg != null) return errMsg;
      errMsg = requireField('completionRequestedAt', order.completionRequestedAt, s);
      if (errMsg != null) return errMsg;
      break;
    }
    case EscortDispatchOrderStatus.COMPLETED: {
      const errMsg = requireField('completedAt', order.completedAt, s);
      if (errMsg != null) return errMsg;
      break;
    }
    case EscortDispatchOrderStatus.AFTER_SALES_REQUESTED: {
      const errMsg = requireField('afterSalesRequestedAt', order.afterSalesRequestedAt, s);
      if (errMsg != null) return errMsg;
      break;
    }
    case EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS: {
      let errMsg = requireField('afterSalesRequestedAt', order.afterSalesRequestedAt, s);
      if (errMsg != null) return errMsg;
      errMsg = requireField('afterSalesAssigneeUserId', order.afterSalesAssigneeUserId, s);
      if (errMsg != null) return errMsg;
      errMsg = requireField('afterSalesAssignedAt', order.afterSalesAssignedAt, s);
      if (errMsg != null) return errMsg;
      break;
    }
    case EscortDispatchOrderStatus.AFTER_SALES_CLOSED: {
      let errMsg = requireField('afterSalesRequestedAt', order.afterSalesRequestedAt, s);
      if (errMsg != null) return errMsg;
      errMsg = requireField('afterSalesAssigneeUserId', order.afterSalesAssigneeUserId, s);
      if (errMsg != null) return errMsg;
      errMsg = requireField('afterSalesClosedAt', order.afterSalesClosedAt, s);
      if (errMsg != null) return errMsg;
      break;
    }
  }
  return null;
}

function requireField(
  name: string,
  value: unknown,
  status?: EscortDispatchOrderStatus,
): string | null {
  if (value == null) {
    return `${name} must not be null for status ${status ?? 'unknown'}`;
  }
  return null;
}

function now(): Date {
  return new Date();
}

/** Creates an EscortDispatchOrder with validation. */
function createOrder(params: {
  id?: number | null;
  orderNumber: string;
  guildId: number;
  assignedByUserId: number;
  escortUserId: number;
  customerUserId: number;
  createdAt?: Date;
  confirmedAt?: Date | null;
  completionRequestedAt?: Date | null;
  completedAt?: Date | null;
  afterSalesRequestedAt?: Date | null;
  afterSalesAssigneeUserId?: number | null;
  afterSalesAssignedAt?: Date | null;
  afterSalesClosedAt?: Date | null;
  updatedAt?: Date;
  sourceType: SourceType;
  sourceReference?: string | null;
  sourceProductId?: number | null;
  sourceProductName?: string | null;
  sourceCurrencyPrice?: number | null;
  sourceFiatPriceTwd?: number | null;
  sourceEscortOptionCode?: string | null;
  status: EscortDispatchOrderStatus;
}): Result<EscortDispatchOrder, DomainError> {
  const ts = now();
  const order: EscortDispatchOrder = {
    id: params.id ?? null,
    orderNumber: params.orderNumber,
    guildId: params.guildId,
    assignedByUserId: params.assignedByUserId,
    escortUserId: params.escortUserId,
    customerUserId: params.customerUserId,
    createdAt: params.createdAt ?? ts,
    confirmedAt: params.confirmedAt ?? null,
    completionRequestedAt: params.completionRequestedAt ?? null,
    completedAt: params.completedAt ?? null,
    afterSalesRequestedAt: params.afterSalesRequestedAt ?? null,
    afterSalesAssigneeUserId: params.afterSalesAssigneeUserId ?? null,
    afterSalesAssignedAt: params.afterSalesAssignedAt ?? null,
    afterSalesClosedAt: params.afterSalesClosedAt ?? null,
    updatedAt: params.updatedAt ?? ts,
    sourceType: params.sourceType,
    sourceReference: params.sourceReference ?? null,
    sourceProductId: params.sourceProductId ?? null,
    sourceProductName: params.sourceProductName ?? null,
    sourceCurrencyPrice: params.sourceCurrencyPrice ?? null,
    sourceFiatPriceTwd: params.sourceFiatPriceTwd ?? null,
    sourceEscortOptionCode: params.sourceEscortOptionCode ?? null,
    status: params.status,
  };
  const validationError = validateOrder(order);
  if (validationError != null) {
    return err(DomainError.invalidInput(validationError));
  }
  return ok(order);
}

// ---- Factory Functions ----

/** 建立待確認的新訂單（尚未持久化，id 為 null）。MANUAL 來源。 */
export function createPending(
  orderNumber: string,
  guildId: number,
  assignedByUserId: number,
  escortUserId: number,
  customerUserId: number,
): Result<EscortDispatchOrder, DomainError> {
  if (customerUserId <= 0) {
    return err(DomainError.invalidInput('customerUserId must be greater than 0'));
  }
  return createOrder({
    orderNumber,
    guildId,
    assignedByUserId,
    escortUserId,
    customerUserId,
    sourceType: SourceType.MANUAL,
    status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
  });
}

/**
 * Creates a manual dispatch order (no escort assigned yet).
 */
export function createManualOpenOrder(
  orderNumber: string,
  guildId: number,
  assignedByUserId: number,
  customerUserId: number,
  sourceEscortOptionCode: string,
): Result<EscortDispatchOrder, DomainError> {
  if (customerUserId <= 0) {
    return err(DomainError.invalidInput('customerUserId must be greater than 0'));
  }
  return createOrder({
    orderNumber,
    guildId,
    assignedByUserId,
    escortUserId: 0,
    customerUserId,
    sourceType: SourceType.MANUAL,
    sourceEscortOptionCode,
    status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
  });
}

/** Creates an EscortDispatchOrder from database row, preserving ALL columns exactly as stored. */
export function fromDbRow(params: {
  id: number | null;
  orderNumber: string;
  guildId: number;
  assignedByUserId: number;
  escortUserId: number;
  customerUserId: number;
  status: EscortDispatchOrderStatus;
  createdAt: Date;
  confirmedAt: Date | null;
  completionRequestedAt: Date | null;
  completedAt: Date | null;
  afterSalesRequestedAt: Date | null;
  afterSalesAssigneeUserId: number | null;
  afterSalesAssignedAt: Date | null;
  afterSalesClosedAt: Date | null;
  updatedAt: Date;
  sourceType: SourceType;
  sourceReference: string | null;
  sourceProductId: number | null;
  sourceProductName: string | null;
  sourceCurrencyPrice: number | null;
  sourceFiatPriceTwd: number | null;
  sourceEscortOptionCode: string | null;
}): Result<EscortDispatchOrder, DomainError> {
  return createOrder({
    id: params.id,
    orderNumber: params.orderNumber,
    guildId: params.guildId,
    assignedByUserId: params.assignedByUserId,
    escortUserId: params.escortUserId,
    customerUserId: params.customerUserId,
    status: params.status,
    createdAt: params.createdAt,
    confirmedAt: params.confirmedAt,
    completionRequestedAt: params.completionRequestedAt,
    completedAt: params.completedAt,
    afterSalesRequestedAt: params.afterSalesRequestedAt,
    afterSalesAssigneeUserId: params.afterSalesAssigneeUserId,
    afterSalesAssignedAt: params.afterSalesAssignedAt,
    afterSalesClosedAt: params.afterSalesClosedAt,
    updatedAt: params.updatedAt,
    sourceType: params.sourceType,
    sourceReference: params.sourceReference,
    sourceProductId: params.sourceProductId,
    sourceProductName: params.sourceProductName,
    sourceCurrencyPrice: params.sourceCurrencyPrice,
    sourceFiatPriceTwd: params.sourceFiatPriceTwd,
    sourceEscortOptionCode: params.sourceEscortOptionCode,
  });
}

/** 從商店付款自動交接建立的訂單（escortUserId=0, assignedByUserId=0）。 */
export function createAutoHandoff(
  orderNumber: string,
  guildId: number,
  assignedByUserId: number,
  escortUserId: number,
  customerUserId: number,
  sourceType: SourceType,
  sourceReference: string,
  sourceProductId: number,
  sourceProductName: string,
  sourceCurrencyPrice: number | null,
  sourceFiatPriceTwd: number | null,
  sourceEscortOptionCode: string,
): Result<EscortDispatchOrder, DomainError> {
  return createOrder({
    orderNumber,
    guildId,
    assignedByUserId,
    escortUserId,
    customerUserId,
    sourceType,
    sourceReference,
    sourceProductId,
    sourceProductName,
    sourceCurrencyPrice,
    sourceFiatPriceTwd,
    sourceEscortOptionCode,
    status: EscortDispatchOrderStatus.PENDING_CONFIRMATION,
  });
}

// ---- Immutable Transition Functions ----

/** 由指定護航者確認後回傳新狀態物件。 */
export function withConfirmed(
  order: EscortDispatchOrder,
  confirmedAt: Date,
): Result<EscortDispatchOrder, DomainError> {
  return createOrder({
    ...order,
    id: order.id,
    confirmedAt,
    completionRequestedAt: null,
    completedAt: null,
    afterSalesRequestedAt: null,
    afterSalesAssigneeUserId: null,
    afterSalesAssignedAt: null,
    afterSalesClosedAt: null,
    status: EscortDispatchOrderStatus.CONFIRMED,
  });
}

/** 護航者送出完單，等待客戶確認。 */
export function withCompletionRequested(
  order: EscortDispatchOrder,
  requestedAt: Date,
): Result<EscortDispatchOrder, DomainError> {
  return createOrder({
    ...order,
    id: order.id,
    completionRequestedAt: requestedAt,
    completedAt: null,
    afterSalesRequestedAt: null,
    afterSalesAssigneeUserId: null,
    afterSalesAssignedAt: null,
    afterSalesClosedAt: null,
    status: EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION,
  });
}

/** 訂單完成。 */
export function withCompleted(
  order: EscortDispatchOrder,
  completedAt: Date,
): Result<EscortDispatchOrder, DomainError> {
  return createOrder({
    ...order,
    id: order.id,
    completedAt,
    status: EscortDispatchOrderStatus.COMPLETED,
  });
}

/** 客戶申請售後。 */
export function withAfterSalesRequested(
  order: EscortDispatchOrder,
  requestedAt: Date,
): Result<EscortDispatchOrder, DomainError> {
  return createOrder({
    ...order,
    id: order.id,
    afterSalesRequestedAt: requestedAt,
    afterSalesAssigneeUserId: null,
    afterSalesAssignedAt: null,
    afterSalesClosedAt: null,
    status: EscortDispatchOrderStatus.AFTER_SALES_REQUESTED,
  });
}

/** 售後人員接手案件。 */
export function withAfterSalesInProgress(
  order: EscortDispatchOrder,
  assigneeUserId: number,
  assignedAt: Date,
): Result<EscortDispatchOrder, DomainError> {
  return createOrder({
    ...order,
    id: order.id,
    afterSalesAssigneeUserId: assigneeUserId,
    afterSalesAssignedAt: assignedAt,
    afterSalesClosedAt: null,
    status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
  });
}

/** 指派護航者後回傳新狀態物件（escortUserId 從 0 更新為指定值，狀態維持 PENDING_CONFIRMATION）。 */
export function withAssignedEscort(
  order: EscortDispatchOrder,
  assignedBy: number,
  escortUserIdValue: number,
  assignedAt: Date,
): Result<EscortDispatchOrder, DomainError> {
  return createOrder({
    ...order,
    id: order.id,
    assignedByUserId: assignedBy,
    escortUserId: escortUserIdValue,
    updatedAt: assignedAt,
  });
}

/** 售後人員結案。 */
export function withAfterSalesClosed(
  order: EscortDispatchOrder,
  closedAt: Date,
): Result<EscortDispatchOrder, DomainError> {
  return createOrder({
    ...order,
    id: order.id,
    afterSalesClosedAt: closedAt,
    status: EscortDispatchOrderStatus.AFTER_SALES_CLOSED,
  });
}

// ---- Predicate Helpers ----

export function isPendingEscortConfirmation(order: EscortDispatchOrder): boolean {
  return order.status === EscortDispatchOrderStatus.PENDING_CONFIRMATION;
}

export function isConfirmed(order: EscortDispatchOrder): boolean {
  return order.status === EscortDispatchOrderStatus.CONFIRMED;
}

export function isPendingCustomerConfirmation(order: EscortDispatchOrder): boolean {
  return order.status === EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION;
}

export function isAfterSalesRequested(order: EscortDispatchOrder): boolean {
  return order.status === EscortDispatchOrderStatus.AFTER_SALES_REQUESTED;
}

export function isAfterSalesInProgress(order: EscortDispatchOrder): boolean {
  return order.status === EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS;
}

/** COMPLETED 或 AFTER_SALES_CLOSED 視為已完成。 */
export function isCompleted(order: EscortDispatchOrder): boolean {
  return (
    order.status === EscortDispatchOrderStatus.COMPLETED ||
    order.status === EscortDispatchOrderStatus.AFTER_SALES_CLOSED
  );
}

// ---- Authorization Checks ----

/** 僅護航者本人可確認接單。 */
export function canBeConfirmedBy(order: EscortDispatchOrder, userId: number): boolean {
  return order.escortUserId === Number(userId);
}

/** 僅護航者本人可送出完成。 */
export function canBeCompletedByEscort(order: EscortDispatchOrder, userId: number): boolean {
  return order.escortUserId === Number(userId);
}

/** 僅客戶本人可確認完成或申請售後。 */
export function canBeConfirmedByCustomer(order: EscortDispatchOrder, userId: number): boolean {
  return order.customerUserId === Number(userId);
}

/** 是否為售後案件的接手人。 */
export function isAfterSalesAssignee(order: EscortDispatchOrder, userId: number): boolean {
  return (
    order.afterSalesAssigneeUserId != null && order.afterSalesAssigneeUserId === Number(userId)
  );
}

// ---- Source Type Helpers ----

export function isManualSource(order: EscortDispatchOrder): boolean {
  return order.sourceType === SourceType.MANUAL;
}

export function isAutoSource(order: EscortDispatchOrder): boolean {
  return order.sourceType !== SourceType.MANUAL;
}

// ---- Timeout Check ----

/** 客戶確認是否已超時（24 小時）。 */
export function hasCustomerConfirmationTimedOut(
  order: EscortDispatchOrder,
  nowTimestamp: Date,
): boolean {
  if (!isPendingCustomerConfirmation(order) || order.completionRequestedAt == null) {
    return false;
  }
  const deadline = order.completionRequestedAt.getTime() + CUSTOMER_CONFIRM_TIMEOUT_MS;
  return nowTimestamp.getTime() >= deadline;
}
