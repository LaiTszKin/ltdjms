import { processWithConcurrencyLimit } from '@ltdjms/shared';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import type { FiatOrder } from '../domain/fiat-order.js';
import {
  isBuyerNotified,
  shouldAutoCreateEscortOrder,
  isAdminNotified,
  hasFulfillmentReward,
  isRewardGranted,
  toFulfillmentProduct,
} from '../domain/fiat-order.js';
import type { DispatchOrderSnapshot, EscortDispatchHandoffService } from '../domain/escort-dispatch-handoff-service.js';
import type { EscortOrderBuyerNotifier, AdminOrderNotifier, ProductRewardGranter } from '../domain/notification-interfaces.js';
import pino from 'pino';

/**
 * Exception indicating a workflow state violation that prevents processing.
 * The operation should be released and retried rather than logged as a failure.
 */
export class WorkflowStateException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'WorkflowStateException';
  }
}

const DEFAULT_BATCH_SIZE = 20;

export class FiatOrderPostPaymentWorker {
  private readonly log: pino.Logger;

  constructor(
    private readonly fiatOrderRepository: FiatOrderRepository,
    private readonly buyerNotificationService: { notifyPaymentSucceeded(order: FiatOrder): void },
    private readonly escortDispatchHandoffService: EscortDispatchHandoffService,
    private readonly escortOrderBuyerNotificationService: EscortOrderBuyerNotifier,
    private readonly adminNotificationService: AdminOrderNotifier,
    private readonly productRewardService: ProductRewardGranter,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async processPendingOrders(): Promise<void> {
    const orders = await this.fiatOrderRepository.findOrdersPendingPostPayment(DEFAULT_BATCH_SIZE);
    await processWithConcurrencyLimit(orders, order => this.processSingleOrder(order), 5);
  }

  async processSingleOrder(order: FiatOrder): Promise<void> {
    const claimTime = new Date();
    const claimed = await this.fiatOrderRepository.claimFulfillmentProcessing(
      order.orderNumber,
      claimTime,
    );
    if (!claimed) return;

    try {
      const fulfillmentProduct = toFulfillmentProduct(order);

      // Step 1: Buyer notification (idempotent)
      if (!isBuyerNotified(order)) {
        this.buyerNotificationService.notifyPaymentSucceeded(order);
        await this.fiatOrderRepository.markBuyerNotifiedIfNeeded(order.orderNumber, new Date());
      }

      // Step 2: Escort handoff (conditional)
      if (shouldAutoCreateEscortOrder(order) && !isAdminNotified(order)) {
        const handoffResult = await this.escortDispatchHandoffService.handoffFromFiatPayment(
          order.guildId,
          order.buyerUserId,
          fulfillmentProduct,
          order.orderNumber,
        );
        if (!handoffResult.isOk()) {
          throw new WorkflowStateException(handoffResult.getError().message);
        }

        const dispatchOrder = handoffResult.getValue();
        const adminClaimTime = new Date();
        const adminClaimed = await this.fiatOrderRepository.claimAdminNotificationProcessing(
          order.orderNumber,
          adminClaimTime,
        );
        if (adminClaimed) {
          try {
            this.escortOrderBuyerNotificationService.notifyEscortOrderCreated(dispatchOrder);
            this.adminNotificationService.notifyAdminsOrderCreated(
              dispatchOrder.guildId,
              dispatchOrder.customerUserId,
              dispatchOrder,
            );
            await this.fiatOrderRepository.markAdminNotifiedIfNeeded(
              order.orderNumber,
              adminClaimTime,
            );
          } catch (e) {
            await this.fiatOrderRepository.releaseAdminNotificationProcessing(order.orderNumber);
            throw e;
          }
        } else {
          this.log.warn(
            { orderNumber: order.orderNumber },
            'Admin notification claim failed, another worker is processing',
          );
        }
      }

      // Step 3: Reward grant (idempotent)
      if (hasFulfillmentReward(order) && !isRewardGranted(order)) {
        const rewardResult = await this.productRewardService.grantReward({
          guildId: order.guildId,
          userId: order.buyerUserId,
          product: fulfillmentProduct,
          amount: fulfillmentProduct.rewardAmount!,
          description: `法幣商品獎勵: ${fulfillmentProduct.name}`,
        });
        if (rewardResult.isErr()) {
          throw new WorkflowStateException(rewardResult.getError().message);
        }
        await this.fiatOrderRepository.markRewardGrantedIfNeeded(
          order.orderNumber,
          new Date(),
        );
      }

      // Step 4: Mark fulfilled (Java: no release on null return)
      await this.fiatOrderRepository.markFulfilledIfNeeded(order.orderNumber, new Date());
    } catch (e) {
      await this.fiatOrderRepository.releaseFulfillmentProcessing(order.orderNumber);
      if (e instanceof WorkflowStateException) {
        this.log.warn({ orderNumber: order.orderNumber, error: e }, 'Workflow state violation processing paid fiat order');
      } else {
        this.log.error({ orderNumber: order.orderNumber, error: e }, 'Failed to process paid fiat order');
      }
    }
  }
}
