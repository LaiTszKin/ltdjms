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
import type { Product } from '../domain/product-types.js';
import pino from 'pino';

const DEFAULT_BATCH_SIZE = 20;

/** Snapshot of a dispatch order used for notification callbacks. */
export interface DispatchOrderSnapshot {
  guildId: number;
  customerUserId: number;
  orderNumber: string;
  sourceProductName?: string | null;
  sourceType?: string | null;
  sourceEscortOptionCode?: string | null;
  sourceCurrencyPrice?: number | null;
  sourceFiatPriceTwd?: number | null;
  sourceReference?: string | null;
}

/** Service interface for auto-creating escort orders from fiat payments. */
export interface EscortDispatchHandoffService {
  handoffFromFiatPayment(
    guildId: number,
    buyerUserId: number,
    product: Product | null,
    sourceReference: string,
  ): Promise<{ isOk: () => boolean; getError: () => { message: string }; getValue: () => DispatchOrderSnapshot }>;
}

/**
 * Service interface for notifying buyers of escort order creation.
 * The parameter is `any` because the notification services accept domain objects
 * whose declared types use incompatible parameter counts across packages.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type EscortOrderBuyerNotifier = {
  notifyEscortOrderCreated(dispatchOrder: any): void;
};

/**
 * Service interface for notifying admins of new orders.
 * See EscortOrderBuyerNotifier for why dispatchOrder is `any`.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AdminOrderNotifier = {
  notifyAdminsOrderCreated(guildId: number, buyerUserId: number, dispatchOrder: any): void;
};

/** Reward grant request shape. */
export interface GrantRewardRequest {
  guildId: number;
  userId: number;
  product: Product;
  amount: number;
  description: string;
}

/** Service interface for granting product rewards. */
export interface ProductRewardGranter {
  grantReward(request: GrantRewardRequest): Promise<{ isErr: () => boolean; getError: () => { message: string } }>;
}

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
    for (const order of orders) {
      await this.processSingleOrder(order);
    }
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
          throw new Error(handoffResult.getError().message);
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
          throw new Error(
            `Fiat admin notification is already being processed: orderNumber=${order.orderNumber}`,
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
          throw new Error(rewardResult.getError().message);
        }
        await this.fiatOrderRepository.markRewardGrantedIfNeeded(
          order.orderNumber,
          new Date(),
        );
      }

      // Step 4: Mark fulfilled
      const marked = await this.fiatOrderRepository.markFulfilledIfNeeded(order.orderNumber, new Date());
      if (!marked) {
        await this.fiatOrderRepository.releaseFulfillmentProcessing(order.orderNumber);
        return;
      }
    } catch (e) {
      await this.fiatOrderRepository.releaseFulfillmentProcessing(order.orderNumber);
      this.log.warn({ orderNumber: order.orderNumber, error: e }, 'Failed to process paid fiat order');
    }
  }
}
