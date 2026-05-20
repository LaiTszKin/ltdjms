import { type EmbedView } from '@ltdjms/shared';
import { type EscortDispatchOrder } from '../domain/index.js';
/** 訂單建立成功通知。 */
export declare function buildOrderCreatedEmbed(order: EscortDispatchOrder): EmbedView;
/** 手動開立訂單成功。 */
export declare function buildManualOrderCreatedEmbed(order: EscortDispatchOrder): EmbedView;
/** 訂單已指派護航者。 */
export declare function buildOrderAssignedEmbed(order: EscortDispatchOrder): EmbedView;
/** 護航者已確認接單通知。 */
export declare function buildOrderConfirmedEmbed(order: EscortDispatchOrder): EmbedView;
/** 護航者送出完成，等待客戶確認。 */
export declare function buildPendingCustomerConfirmationEmbed(order: EscortDispatchOrder): EmbedView;
/** 客戶已確認完成通知。 */
export declare function buildOrderCompletedEmbed(order: EscortDispatchOrder): EmbedView;
/**
 * 訂單超時自動完成通知。
 * 由 ensureTimeoutCompletion 觸發，可於面板顯示超時資訊。
 */
export declare function buildOrderTimedOutEmbed(order: EscortDispatchOrder): EmbedView;
/** 客戶已申請售後通知。 */
export declare function buildAfterSalesRequestedEmbed(order: EscortDispatchOrder): EmbedView;
/** 售後案件已被接手通知。 */
export declare function buildAfterSalesClaimedEmbed(order: EscortDispatchOrder): EmbedView;
/** 售後案件已結案通知。 */
export declare function buildAfterSalesClosedEmbed(order: EscortDispatchOrder): EmbedView;
/** 訂單詳情嵌入（依狀態顯示不同資訊）。 */
export declare function buildOrderDetailEmbed(order: EscortDispatchOrder): EmbedView;
/** 訂單列表嵌入（多筆訂單摘要）。 */
export declare function buildOrderListEmbed(title: string, orders: EscortDispatchOrder[], emptyMessage: string): EmbedView;
export declare function buildErrorEmbed(message: string): EmbedView;
export declare function buildSuccessEmbed(message: string): EmbedView;
export declare function buildWarningEmbed(message: string): EmbedView;
