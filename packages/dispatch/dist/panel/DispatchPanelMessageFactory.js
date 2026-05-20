import { EscortDispatchOrderStatus, isManualSource, } from '../domain/index.js';
import { COLOR_INFO, COLOR_WARNING, COLOR_ERROR } from './DispatchPanelView.js';
// ============================================================
// Embed Builders — 12+ variants using INFO / WARNING / ERROR
// ============================================================
/** 訂單建立成功通知。 */
export function buildOrderCreatedEmbed(order) {
    return {
        title: `📋 派單已建立 #${order.orderNumber}`,
        description: `護航者 <@${order.escortUserId}> 請確認接單。`,
        color: COLOR_INFO,
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '護航者', value: `<@${order.escortUserId}>`, inline: true },
            { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
            { name: '建立時間', value: order.createdAt.toLocaleString('zh-TW'), inline: false },
        ],
        footer: '等待護航者確認中...',
    };
}
/** 手動開立訂單成功。 */
export function buildManualOrderCreatedEmbed(order) {
    return {
        title: `📋 手動開立訂單 #${order.orderNumber}`,
        description: `客戶 <@${order.customerUserId}> 的護航訂單已建立，護航品類代碼：${order.sourceEscortOptionCode ?? '無'}`,
        color: COLOR_INFO,
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
            { name: '護航品類', value: order.sourceEscortOptionCode ?? '未指定', inline: true },
            { name: '來源', value: isManualSource(order) ? '手動開立' : '自動交接', inline: true },
        ],
        footer: '請指派護航者',
    };
}
/** 訂單已指派護航者。 */
export function buildOrderAssignedEmbed(order) {
    return {
        title: `📌 訂單已指派 #${order.orderNumber}`,
        description: `護航者 <@${order.escortUserId}> 已被指派此訂單，請確認接單。`,
        color: COLOR_WARNING,
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '護航者', value: `<@${order.escortUserId}>`, inline: true },
            { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
            { name: '指派時間', value: order.updatedAt.toLocaleString('zh-TW'), inline: false },
        ],
        footer: '等待護航者確認中...',
    };
}
/** 護航者已確認接單通知。 */
export function buildOrderConfirmedEmbed(order) {
    return {
        title: `✅ 訂單已確認 #${order.orderNumber}`,
        description: `護航者 <@${order.escortUserId}> 已確認接單，服務進行中。`,
        color: COLOR_INFO,
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '護航者', value: `<@${order.escortUserId}>`, inline: true },
            { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
            { name: '確認時間', value: order.confirmedAt?.toLocaleString('zh-TW') ?? 'N/A', inline: false },
        ],
        footer: '服務進行中',
    };
}
/** 護航者送出完成，等待客戶確認。 */
export function buildPendingCustomerConfirmationEmbed(order) {
    return {
        title: `🔔 等待客戶確認 #${order.orderNumber}`,
        description: `護航者已送出完成，請客戶 <@${order.customerUserId}> 確認。若 24 小時內未確認，系統將自動完成。`,
        color: COLOR_WARNING,
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '護航者', value: `<@${order.escortUserId}>`, inline: true },
            { name: '送出時間', value: order.completionRequestedAt?.toLocaleString('zh-TW') ?? 'N/A', inline: false },
        ],
        footer: '等待客戶確認中（24 小時超時）',
    };
}
/** 客戶已確認完成通知。 */
export function buildOrderCompletedEmbed(order) {
    return {
        title: `🎉 訂單已完成 #${order.orderNumber}`,
        description: `訂單已由客戶確認完成，感謝護航者 <@${order.escortUserId}> 的服務！`,
        color: COLOR_INFO,
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '護航者', value: `<@${order.escortUserId}>`, inline: true },
            { name: '完成時間', value: order.completedAt?.toLocaleString('zh-TW') ?? 'N/A', inline: false },
        ],
        footer: '訂單已完成',
    };
}
/** 訂單超時自動完成通知。 */
export function buildOrderTimedOutEmbed(order) {
    return {
        title: `⏰ 訂單已自動完成 #${order.orderNumber}`,
        description: `由於客戶未在 24 小時內確認，系統已自動完成此訂單。`,
        color: COLOR_WARNING,
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '護航者', value: `<@${order.escortUserId}>`, inline: true },
            { name: '自動完成時間', value: order.completedAt?.toLocaleString('zh-TW') ?? 'N/A', inline: false },
        ],
        footer: '系統自動完成',
    };
}
/** 客戶已申請售後通知。 */
export function buildAfterSalesRequestedEmbed(order) {
    return {
        title: `🔧 售後申請 #${order.orderNumber}`,
        description: `客戶 <@${order.customerUserId}> 已提出售後申請，請售後人員處理。`,
        color: COLOR_WARNING,
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
            { name: '申請時間', value: order.afterSalesRequestedAt?.toLocaleString('zh-TW') ?? 'N/A', inline: false },
        ],
        footer: '等待售後人員接手',
    };
}
/** 售後案件已被接手通知。 */
export function buildAfterSalesClaimedEmbed(order) {
    return {
        title: `🛠️ 售後已接手 #${order.orderNumber}`,
        description: `售後人員已接手處理訂單 #${order.orderNumber} 的售後案件。`,
        color: COLOR_INFO,
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '售後人員', value: `<@${order.afterSalesAssigneeUserId}>`, inline: true },
            { name: '接手時間', value: order.afterSalesAssignedAt?.toLocaleString('zh-TW') ?? 'N/A', inline: false },
        ],
        footer: '售後處理中',
    };
}
/** 售後案件已結案通知。 */
export function buildAfterSalesClosedEmbed(order) {
    return {
        title: `✅ 售後已結案 #${order.orderNumber}`,
        description: `訂單 #${order.orderNumber} 的售後案件已結案。`,
        color: COLOR_INFO,
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '售後人員', value: `<@${order.afterSalesAssigneeUserId}>`, inline: true },
            { name: '結案時間', value: order.afterSalesClosedAt?.toLocaleString('zh-TW') ?? 'N/A', inline: false },
        ],
        footer: '售後已結案',
    };
}
/** 訂單詳情嵌入（依狀態顯示不同資訊）。 */
export function buildOrderDetailEmbed(order) {
    const statusLabel = getStatusLabel(order.status);
    return {
        title: `📄 訂單詳情 #${order.orderNumber}`,
        description: `狀態：**${statusLabel}**`,
        color: getStatusColor(order.status),
        fields: [
            { name: '訂單編號', value: order.orderNumber, inline: true },
            { name: '狀態', value: statusLabel, inline: true },
            { name: '護航者', value: order.escortUserId > 0 ? `<@${order.escortUserId}>` : '尚未指派', inline: true },
            { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
            { name: '建立者', value: `<@${order.assignedByUserId}>`, inline: true },
            { name: '護航品類', value: order.sourceEscortOptionCode ?? '一般', inline: true },
            { name: '建立時間', value: order.createdAt.toLocaleString('zh-TW'), inline: true },
            { name: '更新時間', value: order.updatedAt.toLocaleString('zh-TW'), inline: true },
            ...(order.sourceReference != null
                ? [{ name: '來源參考', value: order.sourceReference, inline: false }]
                : []),
        ],
        footer: `訂單 ID: ${order.id ?? '新建立'}`,
    };
}
/** 訂單列表嵌入（多筆訂單摘要）。 */
export function buildOrderListEmbed(title, orders, emptyMessage) {
    if (orders.length === 0) {
        return {
            title,
            description: emptyMessage,
            color: COLOR_INFO,
            footer: '護航派單系統',
        };
    }
    return {
        title,
        description: orders
            .map((o, i) => {
            const status = getStatusLabel(o.status);
            return `**${i + 1}.** #${o.orderNumber} — ${status} | 護航者: ${o.escortUserId > 0 ? `<@${o.escortUserId}>` : '待指派'} | 客戶: <@${o.customerUserId}>`;
        })
            .join('\n'),
        color: COLOR_INFO,
        footer: `共 ${orders.length} 筆訂單`,
    };
}
// ============================================================
// Error & Info Embeds
// ============================================================
export function buildErrorEmbed(message) {
    return {
        title: '❌ 操作失敗',
        description: message,
        color: COLOR_ERROR,
        footer: '護航派單系統',
    };
}
export function buildSuccessEmbed(message) {
    return {
        title: '✅ 操作成功',
        description: message,
        color: COLOR_INFO,
        footer: '護航派單系統',
    };
}
export function buildWarningEmbed(message) {
    return {
        title: '⚠️ 提示',
        description: message,
        color: COLOR_WARNING,
        footer: '護航派單系統',
    };
}
// ============================================================
// Helpers
// ============================================================
function getStatusLabel(status) {
    switch (status) {
        case EscortDispatchOrderStatus.PENDING_CONFIRMATION:
            return '等待護航者確認';
        case EscortDispatchOrderStatus.CONFIRMED:
            return '護航者已確認（進行中）';
        case EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION:
            return '等待客戶確認完成';
        case EscortDispatchOrderStatus.COMPLETED:
            return '已完成';
        case EscortDispatchOrderStatus.AFTER_SALES_REQUESTED:
            return '售後申請中';
        case EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS:
            return '售後處理中';
        case EscortDispatchOrderStatus.AFTER_SALES_CLOSED:
            return '售後已結案';
    }
}
function getStatusColor(status) {
    switch (status) {
        case EscortDispatchOrderStatus.PENDING_CONFIRMATION:
        case EscortDispatchOrderStatus.PENDING_CUSTOMER_CONFIRMATION:
        case EscortDispatchOrderStatus.AFTER_SALES_REQUESTED:
            return COLOR_WARNING;
        case EscortDispatchOrderStatus.COMPLETED:
        case EscortDispatchOrderStatus.AFTER_SALES_CLOSED:
            return COLOR_INFO;
        case EscortDispatchOrderStatus.CONFIRMED:
        case EscortDispatchOrderStatus.AFTER_SALES_IN_PROGRESS:
            return COLOR_INFO;
    }
}
//# sourceMappingURL=DispatchPanelMessageFactory.js.map