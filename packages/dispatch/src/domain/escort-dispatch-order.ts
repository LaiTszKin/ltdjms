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

function validateOrder(order: EscortDispatchOrder): void {
  if (!order.orderNumber || order.orderNumber.trim().length === 0) {
    throw new Error('orderNumber must not be blank');
  }
  if (order.orderNumber.length > 32) {
    throw new Error('orderNumber must not exceed 32 characters');
  }
  if (order.escortUserId === order.customerUserId) {
    throw new Error('escortUserId and customerUserId must be different');
  }
  validateSourceSnapshot(order);
  validateRequiredTimestamps(order);
}

function validateSourceSnapshot(order: EscortDispatchOrder): void {
  if (order.sourceType === SourceType.MANUAL) {
    if (order.sourceReference != null || order.sourceProductId != null ||
        order.sourceProductName != null || order.sourceCurrencyPrice != null ||
        order.sourceFiatPriceTwd != null) {
      throw new Error('manual dispatch order must not carry source snapshot');
    }
    return;
  }
  // Auto-sourced orders require source snapshot
  if (!order.sourceReference || order.sourceReference.trim().length === 0) {
    throw new Error('sourceReference must not be blank');
  }
  if (order.sourceProductId == null) {
    throw new Error('sourceProductId must not be null');
  }
  if (!order.sourceProductName || order.sourceProductName.trim().length === 0) {
    throw new Error('sourceProductName must not be blank');
  }
  if (!order.sourceEscortOptionCode || order.sourceEscortOptionCode.trim().length === 0) {
    throw new Error('sourceEscortOptionCode must not be blank');
  }
  if (order.sourceCurrencyPrice == null && order.sourceFiatPriceTwd == null) {
    throw new Error('source price snapshot must not be empty');
  }
}

function validateRequiredTimestamps(order: EscortDispatchOrder): void {
  const s = order.status;
  switch (s) {
    case EscortDispatchOrderStatus.PENDING_CONFIRMATION:
      break;
    case EscortDispatchOrderStatus.CONFIRMED:
      requireField('confirmedAt', order.confirmedAt, s);
      break;
    case EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION:
      requireField('confirmedAt', order.confirmedAt, s);
      requireField('completionRequestedAt', order.completionRequestedAt, s);
      break;
    case EscortDispatchOrderStatus.COMPLETED:
      requireField('completedAt', order.completedAt, s);
      break;
    case EscortDispatchOrderStatus.AFTER_SALES_REQUESTED:
      requireField('afterSalesRequestedAt', order.afterSalesRequestedAt, s);
      break;
    case EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS:
      requireField('afterSalesRequestedAt', order.afterSalesRequestedAt, s);
      requireField('afterSalesAssigneeUserId', order.afterSalesAssigneeUserId, s);
      requireField('afterSalesAssignedAt', order.afterSalesAssignedAt, s);
      break;
    case EscortDispatchOrderStatus.AFTER_SALES_CLOSED:
      requireField('afterSalesRequestedAt', order.afterSalesRequestedAt, s);
      requireField('afterSalesAssigneeUserId', order.afterSalesAssigneeUserId, s);
      requireField('afterSalesClosedAt', order.afterSalesClosedAt, s);
      break;
  }
}

function requireField(name: string, value: unknown, status?: EscortDispatchOrderStatus): void {
  if (value == null) {
    throw new Error(`${name} must not be null for status ${status ?? 'unknown'}`);
  }
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
}): EscortDispatchOrder {
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
  validateOrder(order);
  return order;
}

// ---- Factory Functions ----

/** 建立待確認的新訂單（尚未持久化，id 為 null）。MANUAL 來源。 */
export function createPending(
  orderNumber: string,
  guildId: number,
  assignedByUserId: number,
  escortUserId: number,
  customerUserId: number,
): EscortDispatchOrder {
  if (customerUserId <= 0) {
    throw new Error('customerUserId must be greater than 0');
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

/** 手動開立尚未派發給護航者的護航訂單（escortUserId=0）。 */
export function createManualOpenOrder(
  orderNumber: string,
  guildId: number,
  assignedByUserId: number,
  customerUserId: number,
  sourceEscortOptionCode: string,
): EscortDispatchOrder {
  if (customerUserId <= 0) {
    throw new Error('customerUserId must be greater than 0');
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
}): EscortDispatchOrder {
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
): EscortDispatchOrder {
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
): EscortDispatchOrder {
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
): EscortDispatchOrder {
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
): EscortDispatchOrder {
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
): EscortDispatchOrder {
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
): EscortDispatchOrder {
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
): EscortDispatchOrder {
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
): EscortDispatchOrder {
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
  return order.escortUserId === userId;
}

/** 僅護航者本人可送出完成。 */
export function canBeCompletedByEscort(order: EscortDispatchOrder, userId: number): boolean {
  return order.escortUserId === userId;
}

/** 僅客戶本人可確認完成或申請售後。 */
export function canBeConfirmedByCustomer(order: EscortDispatchOrder, userId: number): boolean {
  return order.customerUserId === userId;
}

/** 是否為售後案件的接手人。 */
export function isAfterSalesAssignee(order: EscortDispatchOrder, userId: number): boolean {
  return order.afterSalesAssigneeUserId != null && order.afterSalesAssigneeUserId === userId;
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
