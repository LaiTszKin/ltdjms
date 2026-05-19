/**
 * 派單系統的護航訂單領域模型。
 * 7 狀態機：PENDING_CONFIRMATION -> CONFIRMED -> PENDING_CUSTOMER_CONFIRMATION -> COMPLETED
 *                                           └-> AFTER_SALES_REQUESTED -> AFTER_SALES_IN_PROGRESS
 *                                                                         -> AFTER_SALES_CLOSED
 */
/** 客戶確認超時門檻：24 小時（毫秒）。 */
export const CUSTOMER_CONFIRM_TIMEOUT_MS = 24 * 60 * 60 * 1000;
/** 訂單狀態。 */
export var EscortDispatchOrderStatus;
(function (EscortDispatchOrderStatus) {
    /** 已建立，等待護航者確認。 */
    EscortDispatchOrderStatus["PENDING_CONFIRMATION"] = "PENDING_CONFIRMATION";
    /** 護航者已確認接單。 */
    EscortDispatchOrderStatus["CONFIRMED"] = "CONFIRMED";
    /** 護航者已送出完單，等待客戶確認或申請售後。 */
    EscortDispatchOrderStatus["PENDING_CUSTOMER_CONFIRMATION"] = "PENDING_CUSTOMER_CONFIRMATION";
    /** 訂單已完成（客戶確認或超時自動完成）。 */
    EscortDispatchOrderStatus["COMPLETED"] = "COMPLETED";
    /** 客戶已提出售後申請。 */
    EscortDispatchOrderStatus["AFTER_SALES_REQUESTED"] = "AFTER_SALES_REQUESTED";
    /** 售後人員已接手處理。 */
    EscortDispatchOrderStatus["AFTER_SALES_IN_PROGRESS"] = "AFTER_SALES_IN_PROGRESS";
    /** 售後案件已結案。 */
    EscortDispatchOrderStatus["AFTER_SALES_CLOSED"] = "AFTER_SALES_CLOSED";
})(EscortDispatchOrderStatus || (EscortDispatchOrderStatus = {}));
/** 護航訂單來源類型。 */
export var SourceType;
(function (SourceType) {
    SourceType["MANUAL"] = "MANUAL";
    SourceType["CURRENCY_PURCHASE"] = "CURRENCY_PURCHASE";
    SourceType["FIAT_PAYMENT"] = "FIAT_PAYMENT";
})(SourceType || (SourceType = {}));
// ---- Validation ----
function validateOrder(order) {
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
function validateSourceSnapshot(order) {
    if (order.sourceType === SourceType.MANUAL) {
        // Manual orders must not carry source snapshot fields
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
function validateRequiredTimestamps(order) {
    switch (order.status) {
        case EscortDispatchOrderStatus.PENDING_CONFIRMATION:
            break;
        case EscortDispatchOrderStatus.CONFIRMED:
            requireField('confirmedAt', order.confirmedAt);
            break;
        case EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION:
            requireField('confirmedAt', order.confirmedAt);
            requireField('completionRequestedAt', order.completionRequestedAt);
            break;
        case EscortDispatchOrderStatus.COMPLETED:
            requireField('completedAt', order.completedAt);
            break;
        case EscortDispatchOrderStatus.AFTER_SALES_REQUESTED:
            requireField('afterSalesRequestedAt', order.afterSalesRequestedAt);
            break;
        case EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS:
            requireField('afterSalesRequestedAt', order.afterSalesRequestedAt);
            requireField('afterSalesAssigneeUserId', order.afterSalesAssigneeUserId);
            requireField('afterSalesAssignedAt', order.afterSalesAssignedAt);
            break;
        case EscortDispatchOrderStatus.AFTER_SALES_CLOSED:
            requireField('afterSalesAssigneeUserId', order.afterSalesAssigneeUserId);
            requireField('afterSalesClosedAt', order.afterSalesClosedAt);
            break;
    }
}
function requireField(name, value) {
    if (value == null) {
        throw new Error(`${name} must not be null for status`);
    }
}
function now() {
    return new Date();
}
/** Creates an EscortDispatchOrder with validation. */
function createOrder(params) {
    const ts = now();
    const order = {
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
export function createPending(orderNumber, guildId, assignedByUserId, escortUserId, customerUserId) {
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
export function createManualOpenOrder(orderNumber, guildId, assignedByUserId, customerUserId, sourceEscortOptionCode) {
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
/** 完整參數版本的 createPending（含自動交接快照欄位的有條件校驗）。 */
export function createPendingFull(orderNumber, guildId, assignedByUserId, escortUserId, customerUserId, sourceType, sourceReference, sourceProductId, sourceProductName, sourceCurrencyPrice, sourceFiatPriceTwd, sourceEscortOptionCode) {
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
/** 從商店付款自動交接建立的訂單（escortUserId=0, assignedByUserId=0）。 */
export function createAutoHandoff(orderNumber, guildId, assignedByUserId, escortUserId, customerUserId, sourceType, sourceReference, sourceProductId, sourceProductName, sourceCurrencyPrice, sourceFiatPriceTwd, sourceEscortOptionCode) {
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
/** 指定待派發訂單的護航者後回傳新狀態物件。 */
export function withAssignedEscort(order, assignedByUserId, escortUserId, assignedAt) {
    return {
        ...order,
        assignedByUserId,
        escortUserId,
        updatedAt: assignedAt,
    };
}
/** 由指定護航者確認後回傳新狀態物件。 */
export function withConfirmed(order, confirmedAt) {
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
export function withCompletionRequested(order, requestedAt) {
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
export function withCompleted(order, completedAt) {
    return createOrder({
        ...order,
        id: order.id,
        completedAt,
        afterSalesRequestedAt: order.afterSalesRequestedAt,
        afterSalesAssigneeUserId: null,
        afterSalesAssignedAt: null,
        afterSalesClosedAt: null,
        status: EscortDispatchOrderStatus.COMPLETED,
    });
}
/** 客戶申請售後。 */
export function withAfterSalesRequested(order, requestedAt) {
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
export function withAfterSalesInProgress(order, assigneeUserId, assignedAt) {
    return createOrder({
        ...order,
        id: order.id,
        afterSalesAssigneeUserId: assigneeUserId,
        afterSalesAssignedAt: assignedAt,
        afterSalesClosedAt: null,
        status: EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS,
    });
}
/** 售後人員結案。 */
export function withAfterSalesClosed(order, closedAt) {
    return createOrder({
        ...order,
        id: order.id,
        afterSalesClosedAt: closedAt,
        status: EscortDispatchOrderStatus.AFTER_SALES_CLOSED,
    });
}
// ---- Predicate Helpers ----
export function isPendingEscortConfirmation(order) {
    return order.status === EscortDispatchOrderStatus.PENDING_CONFIRMATION;
}
export function isConfirmed(order) {
    return order.status === EscortDispatchOrderStatus.CONFIRMED;
}
export function isPendingCustomerConfirmation(order) {
    return order.status === EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION;
}
export function isAfterSalesRequested(order) {
    return order.status === EscortDispatchOrderStatus.AFTER_SALES_REQUESTED;
}
export function isAfterSalesInProgress(order) {
    return order.status === EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS;
}
/** COMPLETED 或 AFTER_SALES_CLOSED 視為已完成。 */
export function isCompleted(order) {
    return (order.status === EscortDispatchOrderStatus.COMPLETED ||
        order.status === EscortDispatchOrderStatus.AFTER_SALES_CLOSED);
}
// ---- Authorization Checks ----
/** 僅護航者本人可確認接單。 */
export function canBeConfirmedBy(order, userId) {
    return order.escortUserId === userId;
}
/** 僅護航者本人可送出完成。 */
export function canBeCompletedByEscort(order, userId) {
    return order.escortUserId === userId;
}
/** 僅客戶本人可確認完成或申請售後。 */
export function canBeConfirmedByCustomer(order, userId) {
    return order.customerUserId === userId;
}
/** 是否為售後案件的接手人。 */
export function isAfterSalesAssignee(order, userId) {
    return order.afterSalesAssigneeUserId != null && order.afterSalesAssigneeUserId === userId;
}
// ---- Source Type Helpers ----
export function isManualSource(order) {
    return order.sourceType === SourceType.MANUAL;
}
export function isAutoSource(order) {
    return order.sourceType !== SourceType.MANUAL;
}
// ---- Timeout Check ----
/** 客戶確認是否已超時（24 小時）。 */
export function hasCustomerConfirmationTimedOut(order, nowTimestamp) {
    if (!isPendingCustomerConfirmation(order) || order.completionRequestedAt == null) {
        return false;
    }
    const deadline = order.completionRequestedAt.getTime() + CUSTOMER_CONFIRM_TIMEOUT_MS;
    return nowTimestamp.getTime() >= deadline;
}
//# sourceMappingURL=escort-dispatch-order.js.map