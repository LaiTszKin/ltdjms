import { isBuyerNotified, shouldAutoCreateEscortOrder, isAdminNotified, hasFulfillmentReward, isRewardGranted, toFulfillmentProduct, } from '../domain/fiat-order.js';
import pino from 'pino';
const DEFAULT_BATCH_SIZE = 20;
export class FiatOrderPostPaymentWorker {
    fiatOrderRepository;
    buyerNotificationService;
    escortDispatchHandoffService;
    escortOrderBuyerNotificationService;
    adminNotificationService;
    productRewardService;
    log;
    constructor(fiatOrderRepository, buyerNotificationService, escortDispatchHandoffService, escortOrderBuyerNotificationService, adminNotificationService, productRewardService, logger) {
        this.fiatOrderRepository = fiatOrderRepository;
        this.buyerNotificationService = buyerNotificationService;
        this.escortDispatchHandoffService = escortDispatchHandoffService;
        this.escortOrderBuyerNotificationService = escortOrderBuyerNotificationService;
        this.adminNotificationService = adminNotificationService;
        this.productRewardService = productRewardService;
        this.log = logger ?? pino({ level: 'warn' });
    }
    async processPendingOrders() {
        const orders = await this.fiatOrderRepository.findOrdersPendingPostPayment(DEFAULT_BATCH_SIZE);
        for (const order of orders) {
            await this.processSingleOrder(order);
        }
    }
    async processSingleOrder(order) {
        const claimTime = new Date();
        const claimed = await this.fiatOrderRepository.claimFulfillmentProcessing(order.orderNumber, claimTime);
        if (!claimed)
            return;
        try {
            const fulfillmentProduct = toFulfillmentProduct(order);
            // Step 1: Buyer notification (idempotent)
            if (!isBuyerNotified(order)) {
                this.buyerNotificationService.notifyPaymentSucceeded(order);
                await this.fiatOrderRepository.markBuyerNotifiedIfNeeded(order.orderNumber, new Date());
            }
            // Step 2: Escort handoff (conditional)
            if (shouldAutoCreateEscortOrder(order) && !isAdminNotified(order)) {
                const handoffResult = await this.escortDispatchHandoffService.handoffFromFiatPayment(order.guildId, order.buyerUserId, fulfillmentProduct, order.orderNumber);
                if (!handoffResult.isOk()) {
                    throw new Error(handoffResult.getError().message);
                }
                const dispatchOrder = handoffResult.getValue();
                const adminClaimTime = new Date();
                const adminClaimed = await this.fiatOrderRepository.claimAdminNotificationProcessing(order.orderNumber, adminClaimTime);
                if (adminClaimed) {
                    try {
                        this.escortOrderBuyerNotificationService.notifyEscortOrderCreated(dispatchOrder);
                        this.adminNotificationService.notifyAdminsOrderCreated(dispatchOrder.guildId, dispatchOrder.customerUserId, dispatchOrder);
                        await this.fiatOrderRepository.markAdminNotifiedIfNeeded(order.orderNumber, adminClaimTime);
                    }
                    catch (e) {
                        await this.fiatOrderRepository.releaseAdminNotificationProcessing(order.orderNumber);
                        throw e;
                    }
                }
                else {
                    throw new Error(`Fiat admin notification is already being processed: orderNumber=${order.orderNumber}`);
                }
            }
            // Step 3: Reward grant (idempotent)
            if (hasFulfillmentReward(order) && !isRewardGranted(order)) {
                const rewardResult = await this.productRewardService.grantReward({
                    guildId: order.guildId,
                    userId: order.buyerUserId,
                    product: fulfillmentProduct,
                    amount: fulfillmentProduct.rewardAmount,
                    description: `法幣商品獎勵: ${fulfillmentProduct.name}`,
                });
                if (rewardResult.isErr()) {
                    throw new Error(rewardResult.getError().message);
                }
                await this.fiatOrderRepository.markRewardGrantedIfNeeded(order.orderNumber, new Date());
            }
            // Step 4: Mark fulfilled
            await this.fiatOrderRepository.markFulfilledIfNeeded(order.orderNumber, new Date());
        }
        catch (e) {
            await this.fiatOrderRepository.releaseFulfillmentProcessing(order.orderNumber);
            this.log.warn({ orderNumber: order.orderNumber, error: e }, 'Failed to process paid fiat order');
        }
    }
}
//# sourceMappingURL=fiat-order-post-payment-worker.js.map