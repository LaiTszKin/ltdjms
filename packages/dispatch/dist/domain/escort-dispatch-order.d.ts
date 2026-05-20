/**
 * 派單系統的護航訂單領域模型。
 * 7 狀態機：PENDING_CONFIRMATION -> CONFIRMED -> PENDING_CUSTOMER_CONFIRMATION -> COMPLETED
 *                                           └-> AFTER_SALES_REQUESTED -> AFTER_SALES_IN_PROGRESS
 *                                                                         -> AFTER_SALES_CLOSED
 */
/** 客戶確認超時門檻：24 小時（毫秒）。 */
export declare const CUSTOMER_CONFIRM_TIMEOUT_MS: number;
/** 訂單狀態。 */
export declare enum EscortDispatchOrderStatus {
    /** 已建立，等待護航者確認。 */
    PENDING_CONFIRMATION = "PENDING_CONFIRMATION",
    /** 護航者已確認接單。 */
    CONFIRMED = "CONFIRMED",
    /** 護航者已送出完單，等待客戶確認或申請售後。 */
    PENDING_CUSTOMER_CONFIRMATION = "PENDING_CUSTOMER_CONFIRMATION",
    /** 訂單已完成（客戶確認或超時自動完成）。 */
    COMPLETED = "COMPLETED",
    /** 客戶已提出售後申請。 */
    AFTER_SALES_REQUESTED = "AFTER_SALES_REQUESTED",
    /** 售後人員已接手處理。 */
    AFTER_SALES_IN_PROGRESS = "AFTER_SALES_IN_PROGRESS",
    /** 售後案件已結案。 */
    AFTER_SALES_CLOSED = "AFTER_SALES_CLOSED"
}
/** 護航訂單來源類型。 */
export declare enum SourceType {
    MANUAL = "MANUAL",
    CURRENCY_PURCHASE = "CURRENCY_PURCHASE",
    FIAT_PAYMENT = "FIAT_PAYMENT"
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
/** 建立待確認的新訂單（尚未持久化，id 為 null）。MANUAL 來源。 */
export declare function createPending(orderNumber: string, guildId: number, assignedByUserId: number, escortUserId: number, customerUserId: number): EscortDispatchOrder;
/** 手動開立尚未派發給護航者的護航訂單（escortUserId=0）。 */
export declare function createManualOpenOrder(orderNumber: string, guildId: number, assignedByUserId: number, customerUserId: number, sourceEscortOptionCode: string): EscortDispatchOrder;
/** Creates an EscortDispatchOrder from database row, preserving ALL columns exactly as stored. */
export declare function fromDbRow(params: {
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
}): EscortDispatchOrder;
/** 完整參數版本的 createPending（含自動交接快照欄位的有條件校驗）。 */
export declare function createPendingFull(orderNumber: string, guildId: number, assignedByUserId: number, escortUserId: number, customerUserId: number, sourceType: SourceType, sourceReference: string | null, sourceProductId: number | null, sourceProductName: string | null, sourceCurrencyPrice: number | null, sourceFiatPriceTwd: number | null, sourceEscortOptionCode: string | null): EscortDispatchOrder;
/** 從商店付款自動交接建立的訂單（escortUserId=0, assignedByUserId=0）。 */
export declare function createAutoHandoff(orderNumber: string, guildId: number, assignedByUserId: number, escortUserId: number, customerUserId: number, sourceType: SourceType, sourceReference: string, sourceProductId: number, sourceProductName: string, sourceCurrencyPrice: number | null, sourceFiatPriceTwd: number | null, sourceEscortOptionCode: string): EscortDispatchOrder;
/** 由指定護航者確認後回傳新狀態物件。 */
export declare function withConfirmed(order: EscortDispatchOrder, confirmedAt: Date): EscortDispatchOrder;
/** 護航者送出完單，等待客戶確認。 */
export declare function withCompletionRequested(order: EscortDispatchOrder, requestedAt: Date): EscortDispatchOrder;
/** 訂單完成。 */
export declare function withCompleted(order: EscortDispatchOrder, completedAt: Date): EscortDispatchOrder;
/** 客戶申請售後。 */
export declare function withAfterSalesRequested(order: EscortDispatchOrder, requestedAt: Date): EscortDispatchOrder;
/** 售後人員接手案件。 */
export declare function withAfterSalesInProgress(order: EscortDispatchOrder, assigneeUserId: number, assignedAt: Date): EscortDispatchOrder;
/** 售後人員結案。 */
export declare function withAfterSalesClosed(order: EscortDispatchOrder, closedAt: Date): EscortDispatchOrder;
export declare function isPendingEscortConfirmation(order: EscortDispatchOrder): boolean;
export declare function isConfirmed(order: EscortDispatchOrder): boolean;
export declare function isPendingCustomerConfirmation(order: EscortDispatchOrder): boolean;
export declare function isAfterSalesRequested(order: EscortDispatchOrder): boolean;
export declare function isAfterSalesInProgress(order: EscortDispatchOrder): boolean;
/** COMPLETED 或 AFTER_SALES_CLOSED 視為已完成。 */
export declare function isCompleted(order: EscortDispatchOrder): boolean;
/** 僅護航者本人可確認接單。 */
export declare function canBeConfirmedBy(order: EscortDispatchOrder, userId: number): boolean;
/** 僅護航者本人可送出完成。 */
export declare function canBeCompletedByEscort(order: EscortDispatchOrder, userId: number): boolean;
/** 僅客戶本人可確認完成或申請售後。 */
export declare function canBeConfirmedByCustomer(order: EscortDispatchOrder, userId: number): boolean;
/** 是否為售後案件的接手人。 */
export declare function isAfterSalesAssignee(order: EscortDispatchOrder, userId: number): boolean;
export declare function isManualSource(order: EscortDispatchOrder): boolean;
export declare function isAutoSource(order: EscortDispatchOrder): boolean;
/** 客戶確認是否已超時（24 小時）。 */
export declare function hasCustomerConfirmationTimedOut(order: EscortDispatchOrder, nowTimestamp: Date): boolean;
