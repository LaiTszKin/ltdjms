// Events
export type {
  ProductChangedEvent,
  RedemptionCodesGeneratedEvent,
  ProductRedemptionCompletedEvent,
} from './events/index.js';

// Domain
export type { FiatOrder } from './domain/fiat-order.js';
export type { EcpayCallbackPayload } from './domain/ecpay-callback-payload.js';

export { createRedemptionCode } from './domain/redemption-code.js';
export type { RedemptionCode } from './domain/redemption-code.js';
export type { RedemptionCodeRepository, CodeStats } from './domain/redemption-code-repository.js';
export type { Product, ProductRepository } from './domain/product-types.js';

// Services
export { FiatOrderProcessingScheduler } from './services/fiat-order-processing-scheduler.js';

export type { ShopService, ShopPage } from './services/shop.service.js';

export type { RedemptionCodeGenerator } from './services/redemption-code-generator.js';

export type { RedemptionService } from './services/redemption.service.js';
export type { RedemptionResult } from './services/redemption.service.js';

export type { ShopCommandHandler } from './commands/shop-handler.js';

// Web
export { EcpayCallbackHttpServer } from './web/ecpay-callback-server.js';

// DI
export {
  configureContainer,
  SHOP_TOKENS,
  type ProductRewardService,
  type RedemptionTransactionService,
} from './di/shop-module.js';

export type { EscortDispatchHandoffService } from './domain/escort-dispatch-handoff-service.js';

