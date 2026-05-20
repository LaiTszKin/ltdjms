import pino from 'pino';
export class FiatOrderBuyerNotificationService {
    discordRuntimeGateway;
    log;
    constructor(discordRuntimeGateway, logger) {
        this.discordRuntimeGateway = discordRuntimeGateway;
        this.log = logger ?? pino({ level: 'warn' });
    }
    notifyPaymentSucceeded(order) {
        if (!order)
            return;
        try {
            const client = this.discordRuntimeGateway.requireReadyClient();
            const message = this.buildPaymentSucceededMessage(order);
            client.users.fetch(order.buyerUserId.toString()).then((buyerUser) => {
                buyerUser.send(message).catch((err) => this.log.warn({ orderNumber: order.orderNumber, buyerUserId: order.buyerUserId, error: err }, 'Failed to DM buyer paid notification'));
            }, (err) => this.log.warn({ orderNumber: order.orderNumber, buyerUserId: order.buyerUserId, error: err }, 'Failed to retrieve buyer user for paid notification'));
        }
        catch (e) {
            this.log.warn({ orderNumber: order.orderNumber, buyerUserId: order.buyerUserId, error: e }, 'Unexpected error while notifying buyer payment success');
        }
    }
    buildPaymentSucceededMessage(order) {
        const lines = [];
        lines.push('✅ 付款成功！\n');
        lines.push(`**商品：** ${order.productName}`);
        lines.push(`**訂單編號：** \`${order.orderNumber}\``);
        lines.push(`**超商代碼：** \`${order.paymentNo}\``);
        lines.push(`**金額：** NT$ ${order.amountTwd}\n`);
        lines.push('我們已收到你的付款，後續若需查詢訂單進度，請提供訂單編號給管理員。');
        return lines.join('\n');
    }
}
//# sourceMappingURL=fiat-order-buyer-notification.service.js.map