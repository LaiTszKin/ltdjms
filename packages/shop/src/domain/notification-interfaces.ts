import type { DispatchOrderSnapshot } from './escort-dispatch-handoff-service.js';
import type { Product } from './product-types.js';

export type EscortOrderBuyerNotifier = {
  notifyEscortOrderCreated(dispatchOrder: DispatchOrderSnapshot): void;
};

/**
 * Service interface for notifying admins of new orders.
 */
export type AdminOrderNotifier = {
  notifyAdminsOrderCreated(guildId: number, buyerUserId: number, dispatchOrder: DispatchOrderSnapshot): void;
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
